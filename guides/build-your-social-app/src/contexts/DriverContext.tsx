import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {driver, Driver, DriveStep} from "driver.js";
import "driver.js/dist/driver.css";
import {useNavigate} from "react-router-dom";
import {useToast} from "./ToastContext";
import {run} from "../api/cli";
import {getNextNavigation, getPrevNavigation, getStepCommand, getStepConfig, getStepVerifier, STEP, stepsConfig,} from "../constants/stepsConfig";

const BUTTON_TEXT = {
  PREV: "< prev",
  NEXT: "next >"
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

export const STEP_INDEX_STORAGE_KEY = 'active-step-index';
const COMMAND_HISTORY_STORAGE_KEY = 'command-history';
const TERMINAL_CONTEXT_STORAGE_KEY = 'terminal-context';

export {STEP};

export const DriverProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const navigate = useNavigate();
  const {showToast} = useToast();

  const getStoredStepIndex = (): number => {
    try {
      const activeIndex = localStorage.getItem(STEP_INDEX_STORAGE_KEY);
      if (activeIndex !== null) {
        const index = parseInt(activeIndex, 10);
        if (!isNaN(index) && index >= 0) {
          return index;
        }
      }
    } catch (error) {
      console.error('Failed to get current active step index:', error);
    }
    return 0;
  };

  const getStoredCommandHistory = (): CommandHistory[] => {
    try {
      const stored = localStorage.getItem(COMMAND_HISTORY_STORAGE_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (error) {
      console.error('Failed to get command history:', error);
    }
    return [];
  };

  const getStoredTerminalContext = (): TerminalContext => {
    try {
      const stored = localStorage.getItem(TERMINAL_CONTEXT_STORAGE_KEY);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (error) {
      console.error('Failed to get terminal context:', error);
    }
    return {};
  };

  const [stepIndex, setStepIndex] = useState(getStoredStepIndex);
  const [currentCommand, setCurrentCommand] = useState<CommandHistory | null>(null);
  const [commandHistory, setCommandHistory] = useState<CommandHistory[]>(getStoredCommandHistory);
  const [terminalContext, setTerminalContext] = useState<TerminalContext>(getStoredTerminalContext);
  const [isExecuting, setIsExecuting] = useState(false);

  const driverObj = useRef<Driver | null>(null);
  const showToastRef = useRef(showToast);
  const currentCommandRef = useRef(currentCommand);
  const isInitializedRef = useRef(false);

  useEffect(() => {
    try {
      localStorage.setItem(STEP_INDEX_STORAGE_KEY, stepIndex.toString());
    } catch (error) {
      console.error('Failed to save current active step index:', error);
    }
  }, [stepIndex]);

  useEffect(() => {
    showToastRef.current = showToast;
  }, [showToast]);

  useEffect(() => {
    currentCommandRef.current = currentCommand;
  }, [currentCommand]);

  useEffect(() => {
    try {
      localStorage.setItem(COMMAND_HISTORY_STORAGE_KEY, JSON.stringify(commandHistory));
    } catch (error) {
      console.error('Failed to save command history:', error);
    }
  }, [commandHistory]);

  useEffect(() => {
    try {
      localStorage.setItem(TERMINAL_CONTEXT_STORAGE_KEY, JSON.stringify(terminalContext));
    } catch (error) {
      console.error('Failed to save terminal context:', error);
    }
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
      const stepConfig = getStepConfig(currentCommand.stepIndex!);
      if (stepConfig?.command?.context?.database) {
        setTerminalContext({database: stepConfig.command.context.database});
      }

      // Clear current command
      setCurrentCommand(null);

      // Auto-advance to next step
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

    // Validate for NEXT
    if (type === STEP.NEXT) {
      if (!await isStepValid(currentIndex)) {
        showToastRef.current("Please complete the current step before proceeding.", TOAST_DURATION);
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

    // Clear current command if moving away without executing
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
    try {
      localStorage.removeItem(STEP_INDEX_STORAGE_KEY);
      localStorage.removeItem(COMMAND_HISTORY_STORAGE_KEY);
      localStorage.removeItem(TERMINAL_CONTEXT_STORAGE_KEY);
      setStepIndex(0);
      setCurrentCommand(null);
      setCommandHistory([]);
      setTerminalContext({});

      if (driverObj.current) {
        driverObj.current.destroy();
        setTimeout(() => {
          if (driverObj.current) {
            driverObj.current.drive(0);
          }
        }, 100);
      }
    } catch (error) {
      console.error('Failed to reset step:', error);
    }
  }, []);

  const generateDriverSteps = useCallback((): DriveStep[] => {
    return stepsConfig.map(step => {
      const title = step.titleNumber
        ? `<span class="driver-popover-title-number">${step.titleNumber}</span> ${step.title || ''}`
        : step.title;

      const popover: DriveStep['popover'] = {
        title,
        description: step.description,
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
      if (step.navigation?.prev) {
        popover.onPrevClick = createNavigationHandler(STEP.PREV);
      }

      const driverStep: DriveStep = {popover};

      if (step.element) {
        driverStep.element = step.element;
      }

      return driverStep;
    });
  }, [createNavigationHandler]);

  useEffect(() => {
    if (!driverObj.current) {
      driverObj.current = driver({
        disableActiveInteraction: true,
        showProgress: false,
        showButtons: ['next', 'previous', 'close'],
        allowClose: true,
        overlayColor: 'rgba(0, 0, 0, 0.4)',
        prevBtnText: BUTTON_TEXT.PREV,
        nextBtnText: BUTTON_TEXT.NEXT,
        doneBtnText: 'Bye',
        allowKeyboardControl: true,
        overlayClickBehavior: () => {
          window.dispatchEvent(new CustomEvent('close-toast'));
        },
        steps: generateDriverSteps(),
        onPrevClick: () => {
          if (driverObj.current) {
            const index = driverObj.current.getActiveIndex();
            if (index !== undefined) {
              // Clear command without adding to history for default prev
              if (currentCommandRef.current) {
                clearCurrentCommand(true);
              }
              setStepIndex(index - 1);
              driverObj.current.moveTo(index - 1);
            }
          }
        },
        onNextClick: async () => {
          if (driverObj.current) {
            const index = driverObj.current.getActiveIndex();
            if (index !== undefined) {
              if (!await isStepValid(index)) {
                showToastRef.current("Please complete the current step before proceeding.", TOAST_DURATION);
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
