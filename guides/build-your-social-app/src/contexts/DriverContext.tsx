import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {driver, Driver, DriveStep} from "driver.js";
import "driver.js/dist/driver.css";
import {useNavigate} from "react-router-dom";
import {useToast} from "./ToastContext";
import {run} from "../api/cli";
import {getNextNavigation, getPrevNavigation, getStepCommand, getStepConfig, getStepVerifier, STEP, stepsConfig,} from "../constants/stepsConfig";
import {getAnalyticsChoice, initAnalytics, loadUmamiScript, setAnalyticsChoice, clearAnalyticsChoice} from "../utils/analytics";
import {getStorageItem, getStorageNumber, removeStorageItem, setStorageItem, setStorageNumber, STORAGE_KEYS} from "../utils/storage";

const BUTTON_TEXT = {
  NEXT: "next ↵"
}

const TOAST_DURATION = 1700

export interface CommandHistory {
  prompt: string;
  content?: string;
  result?: string;
  stepIndex?: number;
}

interface TerminalContext {
  database?: string;
}

interface DriverContextType {
  stepIndex: number;

  currentCommand: CommandHistory | null;
  commandHistory: CommandHistory[];
  terminalContext: TerminalContext;
  isExecuting: boolean;

  executeCommand: () => Promise<void>;
  resetStep: () => void;
}

const DriverContext = createContext<DriverContextType | null>(null);

const PROMPT_PREFIX = 'actionbase';

const formatPrompt = (database?: string) => {
  return database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX;
};

const waitForElement = (selector: string[], timeout = 3000): Promise<void> => {
  return new Promise<void>((resolve, reject) => {
    const selectors = Array.isArray(selector) ? selector : [selector];

    const checkSelectors = () => {
      return selectors.some(sel => document.querySelector(sel) !== null);
    };

    if (checkSelectors()) {
      resolve();
      return;
    }

    const observer = new MutationObserver(() => {
      if (checkSelectors()) {
        observer.disconnect();
        resolve();
      }
    });
    observer.observe(document.body, {childList: true, subtree: true});
    setTimeout(() => {
      observer.disconnect();
      reject();
    }, timeout);
  });
};

export const useDriver = () => {
  const context = useContext(DriverContext);
  if (!context) {
    throw new Error("useDriver must be used within DriverProvider");
  }
  return context;
};

export const STEP_INDEX_STORAGE_KEY = STORAGE_KEYS.STEP_INDEX;

export {STEP};

const getStoredStepIndex = (): number => getStorageNumber(STORAGE_KEYS.STEP_INDEX, 0);
const getStoredCommandHistory = (): CommandHistory[] => getStorageItem<CommandHistory[]>(STORAGE_KEYS.COMMAND_HISTORY, []);
const getStoredTerminalContext = (): TerminalContext => getStorageItem<TerminalContext>(STORAGE_KEYS.TERMINAL_CONTEXT, {});

export const DriverProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const navigate = useNavigate();
  const {showToast} = useToast();

  const [stepIndex, setStepIndex] = useState(getStoredStepIndex);
  const [currentCommand, setCurrentCommand] = useState<CommandHistory | null>(null);
  const [commandHistory, setCommandHistory] = useState<CommandHistory[]>(getStoredCommandHistory);
  const [terminalContext, setTerminalContext] = useState<TerminalContext>(getStoredTerminalContext);
  const [isExecuting, setIsExecuting] = useState(false);
  const [showRestartNotice, setShowRestartNotice] = useState(() => {
    const notice = getStorageItem<string>(STORAGE_KEYS.RESTART_NOTICE, '');
    if (notice === 'true') {
      removeStorageItem(STORAGE_KEYS.RESTART_NOTICE);
      return true;
    }
    return false;
  });

  const driverObj = useRef<Driver | null>(null);
  const showToastRef = useRef(showToast);
  const currentCommandRef = useRef(currentCommand);
  const isInitializedRef = useRef(false);

  useEffect(() => {
    setStorageNumber(STORAGE_KEYS.STEP_INDEX, stepIndex);
  }, [stepIndex]);

  // Initialize analytics on mount if user already opted in
  useEffect(() => {
    initAnalytics();
  }, []);

  // Handle analytics consent and advance from step 0
  const handleAnalyticsStart = useCallback((enableAnalytics: boolean) => {
    if (!driverObj.current) return;

    setAnalyticsChoice(enableAnalytics ? 'yes' : 'no');
    if (enableAnalytics) {
      loadUmamiScript();
    }

    // Advance to step 1
    setStepIndex(1);
    driverObj.current.drive(1);
  }, []);

  useEffect(() => {
    showToastRef.current = showToast;
  }, [showToast]);

  useEffect(() => {
    currentCommandRef.current = currentCommand;
  }, [currentCommand]);

  useEffect(() => {
    setStorageItem(STORAGE_KEYS.COMMAND_HISTORY, commandHistory);
  }, [commandHistory]);

  useEffect(() => {
    setStorageItem(STORAGE_KEYS.TERMINAL_CONTEXT, terminalContext);
  }, [terminalContext]);

  const isStepValid = useCallback(async (index: number) => {
    const stepVerifier = getStepVerifier(index);
    if (!stepVerifier) {
      return true;
    }

    try {
      return await stepVerifier();
    } catch (err) {
      return false;
    }
  }, []);

  const setCommandForStep = useCallback((targetIndex: number) => {
    const stepCommand = getStepCommand(targetIndex);

    if (stepCommand) {
      const prompt = formatPrompt(terminalContext.database);
      setCurrentCommand({
        prompt,
        content: stepCommand.content,
        stepIndex: targetIndex,
      });
    } else {
      setCurrentCommand(null);
    }
  }, [terminalContext.database]);

  const clearCurrentCommand = useCallback((addToHistory: boolean = true) => {
    const command = currentCommandRef.current;
    if (command && addToHistory) {
      // Add unexecuted command to history (without result)
      setCommandHistory(prev => [...prev, command]);
    }
    setCurrentCommand(null);
  }, []);

  const executeCommand = useCallback(async () => {
    if (!currentCommand?.content || isExecuting) return;

    setIsExecuting(true);

    try {
      // Check if mutation step is already completed (verifier passes)
      const stepConfig = getStepConfig(currentCommand.stepIndex!);
      if (stepConfig?.command?.skipIfDone) {
        const stepVerifier = getStepVerifier(currentCommand.stepIndex!);
        if (stepVerifier) {
          try {
            const alreadyDone = await stepVerifier();
            if (alreadyDone) {
              // Skip execution, show "already done" message
              const result = '<p class="command-result success">✓ Already done</p>';
              setCommandHistory(prev => [...prev, {...currentCommand, result}]);
              setCurrentCommand(null);
              currentCommandRef.current = null;

              if (driverObj.current) {
                const activeStep = driverObj.current.getActiveStep();
                if (activeStep?.popover?.onNextClick) {
                  const element = activeStep.element as HTMLElement;
                  activeStep.popover.onNextClick(element || undefined, activeStep, {
                    config: driverObj.current.getConfig(),
                    state: driverObj.current.getState(),
                    driver: driverObj.current
                  });
                }
              }
              return;
            }
          } catch {
            // Verifier failed, proceed with execution
          }
        }
      }

      const normalizedCommand = currentCommand.content.replaceAll('\\\n', '');
      let result: string;

      try {
        const response = await run({command: normalizedCommand});

        if (response.error) {
          result = `<p class="command-result error">${response.error}</p>`;
        } else if (response.result) {
          result = `<p class="command-result">${response.result}</p>`;
        } else {
          result = response.success
            ? '<p class="command-result success">✓ Success</p>'
            : '<p class="command-result error">Failed</p>';
        }
      } catch (err: any) {
        console.error('Failed to execute command:', err);
        result = `<p class="command-result error">${err.responseData?.error || err.message || 'Failed to execute command'}</p>`;
      }

      // Add executed command to history with result
      setCommandHistory(prev => [...prev, {...currentCommand, result}]);

      // Update terminal context if command changes it
      if (stepConfig?.command?.context?.database) {
        setTerminalContext({database: stepConfig.command.context.database});
      }

      if (stepConfig?.command?.reload) {
        window.dispatchEvent(new CustomEvent('reload'));
      }

      // Clear current command
      setCurrentCommand(null);
      currentCommandRef.current = null;

      if (driverObj.current) {
        const activeStep = driverObj.current.getActiveStep();
        if (activeStep?.popover?.onNextClick) {
          const element = activeStep.element as HTMLElement;
          activeStep.popover.onNextClick(element || undefined, activeStep, {
            config: driverObj.current.getConfig(),
            state: driverObj.current.getState(),
            driver: driverObj.current
          });
        }
      }
    } finally {
      setIsExecuting(false);
    }
  }, [currentCommand, isExecuting]);

  const navigateToStep = useCallback(async (
    type: typeof STEP.NEXT | typeof STEP.PREV,
    currentIndex: number
  ) => {
    if (!driverObj.current) return;

    // For NEXT: if there's a command to execute, click the run button
    if (type === STEP.NEXT) {
      const runButton = document.getElementById('run-command-btn');
      if (runButton && currentCommandRef.current?.content) {
        runButton.click();
        return;
      }

      if (!await isStepValid(currentIndex)) {
        showToastRef.current("Please complete the current step before proceeding.", 'warning', TOAST_DURATION);
        return;
      }
    }

    const getNavConfig = type === STEP.NEXT ? getNextNavigation : getPrevNavigation;
    const navConfig = getNavConfig(currentIndex);

    if (!navConfig) {
      console.error('Failed to get navigation config for step', currentIndex);
      return;
    }

    const targetIndex = type === STEP.NEXT ? currentIndex + 1 : currentIndex - 1;

    if (currentIndex === 0 && type === STEP.NEXT) {
      setShowRestartNotice(false);
    }

    if (currentCommand) {
      clearCurrentCommand(true);
    }

    // Navigate route if needed
    if (navConfig.to) {
      navigate(navConfig.to);
    }

    // Set command for target step
    setCommandForStep(targetIndex);

    // Wait for elements if needed
    if (navConfig.waitFor && navConfig.waitFor.length > 0) {
      try {
        await waitForElement(navConfig.waitFor);
        await new Promise(r => setTimeout(r, 100));
      } catch (error) {
        console.error('Failed to find target elements');
        return;
      }
    }

    // Drive to target step
    driverObj.current.drive(targetIndex);
    setStepIndex(targetIndex);
  }, [isStepValid, currentCommand, clearCurrentCommand, setCommandForStep, navigate]);

  const createNavigationHandler = useCallback(
    (type: typeof STEP.NEXT | typeof STEP.PREV) => {
      return async () => {
        if (!driverObj.current) return;

        let currentIndex = driverObj.current.getActiveIndex();
        if (currentIndex === undefined) {
          currentIndex = getStoredStepIndex();
        }
        if (currentIndex === undefined) {
          console.error('Failed to get active index');
          return;
        }

        await navigateToStep(type, currentIndex);
      };
    },
    [navigateToStep]
  );

  const resetStep = useCallback(() => {
    removeStorageItem(STORAGE_KEYS.STEP_INDEX);
    removeStorageItem(STORAGE_KEYS.COMMAND_HISTORY);
    removeStorageItem(STORAGE_KEYS.TERMINAL_CONTEXT);
    clearAnalyticsChoice();
    setStorageItem(STORAGE_KEYS.RESTART_NOTICE, 'true');
    window.location.href = '/';
  }, []);

  const generateDriverSteps = useCallback((): DriveStep[] => {
    return stepsConfig.map(step => {
      const title = step.titleNumber
        ? `<span class="driver-popover-title-number">${step.titleNumber}</span> ${step.title || ''}`
        : step.title;

      let description = step.description;

      const popover: DriveStep['popover'] = {
        title,
        description,
        side: step.popover?.side || 'bottom',
        align: step.popover?.align || 'start',
      };

      if (step.popover?.nextBtnText) {
        popover.nextBtnText = step.popover.nextBtnText;
      }
      if (step.popover?.showButtons) {
        popover.showButtons = step.popover.showButtons;
      }

      // Add navigation handlers
      if (step.navigation?.next) {
        popover.onNextClick = createNavigationHandler(STEP.NEXT);
      }

      const driverStep: DriveStep = {popover};

      if (step.element) {
        driverStep.element = step.element;
      }

      return driverStep;
    });
  }, [createNavigationHandler, showRestartNotice]);

  useEffect(() => {
    if (!driverObj.current) {
      driverObj.current = driver({
        disableActiveInteraction: true,
        showProgress: false,
        showButtons: ['next'],
        allowClose: false,
        smoothScroll: false,
        overlayColor: 'rgba(0, 0, 0, 0.4)',
        nextBtnText: BUTTON_TEXT.NEXT,
        doneBtnText: 'Explore',
        allowKeyboardControl: false,
        overlayClickBehavior: () => {
          window.dispatchEvent(new CustomEvent('close-toast'));
        },
        steps: generateDriverSteps(),
        onNextClick: async () => {
          if (driverObj.current) {
            const index = driverObj.current.getActiveIndex();
            if (index !== undefined) {
              // If there's a command to execute, click the run button
              const runButton = document.getElementById('run-command-btn');
              if (runButton && currentCommandRef.current?.content) {
                runButton.click();
                return;
              }

              if (!await isStepValid(index)) {
                showToastRef.current("Please complete the current step before proceeding.", 'warning', TOAST_DURATION);
                return;
              }

              // Clear command when using default next
              if (currentCommandRef.current) {
                clearCurrentCommand(true);
              }

              setStepIndex(index + 1);
              driverObj.current.moveTo(index + 1);
            }
          }
        },
      });
    }

    // Restore step on mount (only once)
    if (!isInitializedRef.current) {
      isInitializedRef.current = true;

      const restoreStep = async () => {
        if (!driverObj.current) return;

        const currentStepIndex = getStoredStepIndex();
        const stepConfig = getStepConfig(currentStepIndex);

        // Navigate to route if needed
        if (currentStepIndex > 0) {
          const prevNavigation = getNextNavigation(currentStepIndex - 1);
          if (prevNavigation?.to) {
            navigate(prevNavigation.to);
          }
        }

        // Set command FIRST so elements can render
        setCommandForStep(currentStepIndex);

        // Wait for step's target element after command is set
        if (stepConfig?.element) {
          try {
            await waitForElement([stepConfig.element], 5000);
          } catch {
            console.error('Failed to find step element during restore:', stepConfig.element);
          }
        }

        // Drive to the stored step
        driverObj.current.drive(currentStepIndex);
      };

      // Wait for initial React render to complete
      const timeoutId = setTimeout(restoreStep, 100);

      return () => {
        clearTimeout(timeoutId);
      };
    }
  }, [generateDriverSteps, isStepValid, setCommandForStep, clearCurrentCommand]);


  useEffect(() => {
    const handleKeyDown = async (e: KeyboardEvent) => {
      if (e.key !== 'Enter') return;
      if (!driverObj.current) return;

      e.preventDefault();
      e.stopPropagation();

      const index = driverObj.current.getActiveIndex();
      if (index === undefined) return;

      // On step 0, require explicit button click (no Enter)
      if (index === 0 && getAnalyticsChoice() === null) {
        return;
      }

      const runButton = document.getElementById('run-command-btn');
      if (runButton && currentCommandRef.current?.content && !isExecuting) {
        runButton.click();
        return;
      }

      const activeStep = driverObj.current.getActiveStep();
      if (activeStep?.popover?.onNextClick) {
        const element = activeStep.element as HTMLElement;
        activeStep.popover.onNextClick(element || undefined, activeStep, {
          config: driverObj.current.getConfig(),
          state: driverObj.current.getState(),
          driver: driverObj.current
        });
      } else {
        if (!await isStepValid(index)) {
          showToastRef.current("Please complete the current step before proceeding.", 'warning', TOAST_DURATION);
          return;
        }
        if (currentCommandRef.current) {
          clearCurrentCommand(true);
        }
        setStepIndex(index + 1);
        driverObj.current.moveTo(index + 1);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isExecuting, isStepValid, clearCurrentCommand, currentCommand, handleAnalyticsStart]);

  // Handle analytics button clicks via event delegation
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (target.id === 'analytics-start-btn') {
        handleAnalyticsStart(false);
      } else if (target.id === 'analytics-share-btn') {
        handleAnalyticsStart(true);
      }
    };
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [handleAnalyticsStart]);

  const contextValue = useMemo(() => ({
    stepIndex,
    currentCommand,
    commandHistory,
    terminalContext,
    isExecuting,
    executeCommand,
    resetStep,
  }), [
    stepIndex,
    currentCommand,
    commandHistory,
    terminalContext,
    isExecuting,
    executeCommand,
    resetStep,
  ]);

  return (
    <DriverContext.Provider value={contextValue}>
      {children}
    </DriverContext.Provider>
  );
};
