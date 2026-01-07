import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {runCommand} from "../../api/cli";
import steps from "../../constants/HandsOnStep";
import '../../styles/cli-terminal.css';

interface CommandHistory {
  prompt: string;
  content?: string;
  result?: string;
  stepIndex?: number;
}

const PROMPT_PREFIX = 'actionbase';

const CliTerminal: React.FC = () => {
  const [currentStep, setCurrentStep] = useState<number>(0);
  const [currentPrompt, setCurrentPrompt] = useState('');
  const [commandHistory, setCommandHistory] = useState<CommandHistory[]>([]);

  const clickedButtonsRef = useRef<Set<number>>(new Set());
  const currentCommandDataRef = useRef<{ commandIdx: number, commands: any[], stepDatabase: string | undefined, formatPrompt: (db?: string) => string } | null>(null);
  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);

  const formatPrompt = useCallback((database?: string) => database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX, []);

  const createResultMessage = useCallback((response: any) => {
    if (response.error) return `<p class="command-result error">${response.error}</p>`;
    if (response.result) return `<p class="command-result">${response.result}</p>`;
    return response.success ? '<p class="command-result success">✓ Success</p>' : '<p class="command-result error">Failed</p>';
  }, []);

  const createErrorMessage = useCallback((err: any) =>
    `<p class="command-result error">${err.responseData?.error || err.message || 'Failed to execute command'}</p>`, []);

  const addCommandToHistory = useCallback((command: any, cmdDatabase: string, formatPromptFn: (db?: string) => string) => {
    const prompt = formatPromptFn(cmdDatabase);
    setCommandHistory(prev => [...prev, {type: 'command', prompt, content: command.text, stepIndex: currentStep}]);
    if (command.result) {
      setCommandHistory(prev => [...prev, {type: 'command', prompt: '', content: '', result: command.result, stepIndex: currentStep}]);
    }
    setCurrentPrompt('');
  }, [currentStep]);

  const proceedToNextCommand = useCallback(() => {
    if (!currentCommandDataRef.current) return;
    const {commandIdx, commands, stepDatabase, formatPrompt: formatPromptFn} = currentCommandDataRef.current;

    if (commandIdx < commands.length - 1) {
      const nextCommandIdx = commandIdx + 1;
      const nextCommand = commands[nextCommandIdx];
      const nextCmdDatabase = nextCommand.database ?? stepDatabase;
      currentCommandDataRef.current = {commandIdx: nextCommandIdx, commands, stepDatabase, formatPrompt: formatPromptFn};
      addCommandToHistory(nextCommand, nextCmdDatabase, formatPromptFn);
    } else {
      const lastCommand = commands[commands.length - 1];
      const idleDatabase = lastCommand.database ?? stepDatabase;
      setCurrentPrompt(formatPromptFn(idleDatabase));
    }
  }, [currentStep, formatPrompt, addCommandToHistory]);

  const executeCommand = useCallback(async (item: CommandHistory) => {
    const command = item.content || '';
    if (!command) return;

    const normalizedCommand = command.replaceAll('\\\n', '')

    let result: string;
    try {
      const response = await runCommand({command: normalizedCommand});
      result = createResultMessage(response);
    } catch (err: any) {
      result = createErrorMessage(err);
      console.error('Failed to execute command:', err);
    }

    setCommandHistory(prev => [...prev, {type: 'command', prompt: '', content: '', result, stepIndex: currentStep}]);

    const itemIndex = commandHistory.findIndex(cmd =>
      (cmd.content === command)
    );
    if (itemIndex !== -1) {
      clickedButtonsRef.current.delete(itemIndex);
    }

    setTimeout(() => {
      if (terminalBodyRef.current) {
        terminalBodyRef.current.scrollTop = terminalBodyRef.current.scrollHeight;
      }
      if (commandHistoryRef.current) {
        commandHistoryRef.current.scrollTop = commandHistoryRef.current.scrollHeight;
      }
    }, 100);

    proceedToNextCommand();
  }, [createResultMessage, createErrorMessage, currentStep, proceedToNextCommand]);

  useEffect(() => {
    const handleTourStepChange = (event: CustomEvent) => {
      const {stepIndex} = event.detail;
      if (stepIndex !== undefined && stepIndex >= 0) {
        setCurrentStep(stepIndex);
      }
    };

    window.addEventListener('tourStepChange', handleTourStepChange as EventListener);

    return () => {
      window.removeEventListener('tourStepChange', handleTourStepChange as EventListener);
    };
  }, []);

  useEffect(() => {
    if (currentStep >= steps.length) return;

    const step = steps[currentStep];
    const {commands = []} = step;
    const stepDatabase = step.database;

    setCurrentPrompt('');
    currentCommandDataRef.current = null;

    if (commands.length > 0) {
      currentCommandDataRef.current = {commandIdx: 0, commands, stepDatabase, formatPrompt};

      const addNextCommand = () => {
        if (!currentCommandDataRef.current) return;
        const {commandIdx, commands: cmdList, stepDatabase: db, formatPrompt: formatPromptFn} = currentCommandDataRef.current;
        if (commandIdx >= cmdList.length) return;

        const currentCommand = cmdList[commandIdx];
        const cmdDatabase = currentCommand.database ?? db;
        currentCommandDataRef.current = {commandIdx, commands: cmdList, stepDatabase: db, formatPrompt: formatPromptFn};
        addCommandToHistory(currentCommand, cmdDatabase, formatPromptFn);
      };

      addNextCommand();
    } else {
      setCurrentPrompt(formatPrompt(stepDatabase));
    }
  }, [currentStep, formatPrompt, addCommandToHistory]);

  useEffect(() => {
    if (commandHistory.length === 0) return;

    setTimeout(() => {
      if (commandHistoryRef.current && terminalBodyRef.current) {
        const commandBlocks = commandHistoryRef.current.querySelectorAll('.command-block');
        const lastCommandBlock = commandBlocks[commandBlocks.length - 1] as HTMLElement;
        if (lastCommandBlock) {
          const commandLineItem = lastCommandBlock.querySelector('.command-line-item') as HTMLElement;
          if (commandLineItem) {
            const scrollContainer = terminalBodyRef.current;
            const historyContainer = commandHistoryRef.current;

            const itemRect = commandLineItem.getBoundingClientRect();
            const historyRect = historyContainer.getBoundingClientRect();

            const relativeOffset = itemRect.top - historyRect.top;

            scrollContainer.scrollTop = scrollContainer.scrollTop + relativeOffset;
          }
        }
      }
    }, 0);
  }, [commandHistory.length]);

  const handleCommandRun = useCallback((item: CommandHistory, index: number) => {
    const isCurrentStep = item.stepIndex === undefined || item.stepIndex === currentStep;
    if (!isCurrentStep || item.result) return;

    const textToProcess = item.content || '';
    if (!textToProcess) return;

    if (clickedButtonsRef.current.has(index)) {
      return;
    }

    clickedButtonsRef.current.add(index);

    setTimeout(() => {
      if (terminalBodyRef.current) {
        terminalBodyRef.current.scrollTop = terminalBodyRef.current.scrollHeight;
      }
      if (commandHistoryRef.current) {
        commandHistoryRef.current.scrollTop = commandHistoryRef.current.scrollHeight;
      }
    }, 0);

    executeCommand(item);
  }, [currentStep, executeCommand]);

  const renderCommandContent = useCallback((item: CommandHistory, index: number) => {
    if (!item.content) {
      return <div className="command-line-item"><span className="prompt">{item.prompt}{"> "}</span></div>;
    }

    const lines = item.content.split('\n').filter(line => line.trim() !== '');
    const isLastItem = index === commandHistory.length - 1;
    const shouldShowCursor = isLastItem && !item.result;
    const cursor = shouldShowCursor ? '<span class="cursor">_</span>' : '';

    if (lines.length === 1) {
      return (
        <div className="command-line-item command-line-single">
          <span className="prompt">{item.prompt}{"> "}</span>
          <span className="command-text" dangerouslySetInnerHTML={{__html: lines[0].trim() + cursor}}></span>
        </div>
      );
    }

    return lines.map((line, lineIdx) => {
      const hasBackslash = line.endsWith('\\');
      const lineText = hasBackslash ? line.slice(0, -1).trim() : line;
      const isLastLine = lineIdx === lines.length - 1;
      const lineWithCursor = lineText + (hasBackslash && !isLastLine ? ' \\' : '') + (shouldShowCursor && isLastLine ? cursor : '');

      return (
        <div key={lineIdx} className="command-line-item">
          {lineIdx === 0 ? (
            <>
              <span className="prompt">{item.prompt}{"> "}</span>
              <p className="command-text" dangerouslySetInnerHTML={{__html: lineWithCursor}}></p>
            </>
          ) : (
            <span className="command-text-indent" dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
          )}
        </div>
      );
    });
  }, [commandHistory.length]);

  const lastCommandIndex = useMemo(() => {
    for (let i = commandHistory.length - 1; i >= 0; i--) {
      const item = commandHistory[i];
      if (item.stepIndex === currentStep && !item.result) {
        return i;
      }
    }
    return -1;
  }, [commandHistory, currentStep]);

  return (
    <div className="terminal-body-container" id="cli-commands">
      <div className="terminal-body" ref={terminalBodyRef}>
        <div className="command-history" ref={commandHistoryRef}>
          {commandHistory.map((item, index) => (
            <div key={index}>
              {(
                <div className="command-block">
                  <div className="command-line">
                    <div className="command-line-inner">
                      {item.result ? (
                        <div className="command-content-wrapper">
                          <div className="command-multiline">
                            <div className="command-line-item command-line-single">
                              <span className="command-text" dangerouslySetInnerHTML={{__html: item.result}}></span>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <div className="command-content-wrapper">
                          <div className="command-multiline">{renderCommandContent(item, index)}</div>
                        </div>
                      )}
                      {!item.result && (() => {
                        const isClicked = clickedButtonsRef.current.has(index);
                        const isLastCommand = index === lastCommandIndex;
                        const shouldHide = !isLastCommand || (item.stepIndex !== undefined && item.stepIndex !== currentStep) || isClicked;
                        return (
                          <button
                            className={`run-command-btn ${shouldHide ? 'hidden-step-btn' : ''}`}
                            onClick={(e) => {
                              if (isClicked) return;
                              handleCommandRun(item, index);
                            }}
                            title="Copy command"
                            disabled={isClicked}
                          >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M18 4V16Q14 16 6 16H4M4 16L8 12M4 16L8 20"/>
                            </svg>
                          </button>
                        );
                      })()}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}

          {currentPrompt && (
            <div className="command-block command-block-prompt">
              <div className="command-line">
                <div className="command-line-inner">
                  <div className="command-line-item">
                    <span className="prompt">{currentPrompt}{"> "}</span>
                    <span className="cursor">_</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CliTerminal;
