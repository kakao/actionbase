import React, {useCallback, useEffect, useRef, useState} from 'react';
import {run} from "../../api/cli";
import steps from "../../constants/HandsOnStepCommand";
import '../../styles/cli-terminal.css';
import {useDriver} from "../../contexts/DriverContext";

interface CommandHistory {
  prompt: string;
  content?: string;
  result?: string;
  stepIndex?: number;
}

const PROMPT_PREFIX = 'actionbase';

const CliTerminal: React.FC = () => {
  const {stepIndex, isCallbackExecuted, runCommandExecutedCallback, moveNext} = useDriver()
  const [currentCommand, setCurrentCommand] = useState<CommandHistory | null>();
  const [commandHistory, setCommandHistory] = useState<CommandHistory[]>([]);
  const [isDefaultPromptEnabled, setDefaultPromptEnabled] = useState<boolean>(true);
  const [isCommandExecuted, setCommandExecuted] = useState<boolean>(true);

  const currentCommandDataRef = useRef<{ command: string, stepDatabase: string | undefined, formatPrompt: (db?: string) => string } | null>(null);
  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);
  const currentCommandRef = useRef<CommandHistory | undefined>(undefined);

  function formatPrompt(database?: string) {
    return database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX;
  }

  function appendCommand(stepIndex: number) {
    const stepCommand = steps.find(value => value.stepIndex == stepIndex);
    if (stepCommand == undefined || stepCommand.stepIndex != stepIndex) return;

    const {command} = stepCommand;
    const stepDatabase = stepCommand.database;

    currentCommandDataRef.current = null;

    if (command) {
      currentCommandDataRef.current = {command, stepDatabase, formatPrompt};
      const prompt = formatPrompt(stepDatabase ?? '');
      setCurrentCommand({prompt, content: command, stepIndex: stepIndex});
    }
  }

  const renderCommand = useCallback((item: CommandHistory, index: number, hideCursor: boolean = true) => {
    if (!item.content) {
      return <div className="command-line-item"><span className="prompt">{item.prompt}{"> "}</span></div>;
    }

    const lines = item.content.split('\n').filter(line => line.trim() !== '');
    const cursor = !hideCursor ? '<span class="cursor">_</span>' : '';

    if (lines.length === 1) {
      return (
        <div className="command-line-item command-line-single">
          <span className="prompt">{item.prompt}{"> "}</span><span className="command-text" dangerouslySetInnerHTML={{__html: lines[0].trim() + cursor}}></span>
        </div>
      );
    }

    return lines.map((line, lineIdx) => {
      const hasBackslash = line.endsWith('\\');
      const lineText = hasBackslash ? line.slice(0, -1).trim() : line;
      const isLastLine = lineIdx === lines.length - 1;
      const lineWithCursor = lineText + (hasBackslash && !isLastLine ? ' \\' : '') + (!hideCursor && isLastLine ? cursor : '');

      return (
        <div key={lineIdx} className="command-line-item">
          {lineIdx === 0 ? (
            <>
              <span className="prompt">{item.prompt}{"> "}</span><p className="command-text" dangerouslySetInnerHTML={{__html: lineWithCursor}}></p>
            </>
          ) : (
            <span className="command-text-indent" dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
          )}
        </div>
      );
    });
  }, []);

  const runCommand = useCallback(async (item: CommandHistory, stepIndex: number) => {
    const command = item.content || '';
    if (!command) return;

    const normalizedCommand = command.replaceAll('\\\n', '')

    try {
      const response = await run({command: normalizedCommand});
      let result: string;
      if (response.error) {
        result = `<p class="command-result error">${response.error}</p>`;
      } else if (response.result) {
        result = `<p class="command-result">${response.result}</p>`;
      } else {
        result = response.success ? '<p class="command-result success">✓ Success</p>' : '<p class="command-result error">Failed</p>';
      }
      setCommandHistory(prev => [...prev, {...item, result}]);
    } catch (err: any) {
      const result = `<p class="command-result error">${err.responseData?.error || err.message || 'Failed to execute command'}</p>`;
      setCommandHistory(prev => [...prev, {...item, result}]);
      console.error('Failed to execute command:', err);
    }

    if (currentCommandRef.current) {
      currentCommandRef.current = undefined;
    }

    setCurrentCommand(null);
    setDefaultPromptEnabled(true);
    setCommandExecuted(true);

    const itemStepIndex = item.stepIndex
    if (itemStepIndex) {
      const hasNextCommand = steps.find(step => step.stepIndex == itemStepIndex + 1)
      if (hasNextCommand) {
        runCommandExecutedCallback();
      } else {
        moveNext();
      }
    }
  }, []);

  const handleRunCommand = useCallback((item: CommandHistory, index: number) => {
    runCommand(item, stepIndex)
  }, []);

  useEffect(() => {
    if (commandHistory.length === 0) return;

    setTimeout(() => {
      if (commandHistoryRef.current && terminalBodyRef.current) {
        const commandBlocks = commandHistoryRef.current.querySelectorAll('.command-block');
        const lastCommandBlock = commandBlocks[commandBlocks.length - 1] as HTMLElement;
        if (lastCommandBlock) {
          const commandLine = lastCommandBlock.querySelector('.command-line-item') as HTMLElement;
          if (commandLine) {
            const scrollContainer = terminalBodyRef.current;
            const historyContainer = commandHistoryRef.current;

            const itemRect = commandLine.getBoundingClientRect();
            const historyRect = historyContainer.getBoundingClientRect();
            const relativeOffset = itemRect.top - historyRect.top;

            scrollContainer.scrollTop = scrollContainer.scrollTop + relativeOffset;
          }
        }
      }
    }, 0);
  }, [currentCommand, commandHistory]);

  useEffect(() => {
    if (currentCommand) {
      currentCommandRef.current = currentCommand;
    }
  }, [currentCommand]);

  useEffect(() => {
    function render(event: CustomEvent) {
      setCommandExecuted(false);
      setDefaultPromptEnabled(true);

      const latestCurrentCommand = currentCommandRef.current;
      if (latestCurrentCommand) {
        setCommandHistory(prev => [...prev, latestCurrentCommand]);
      }

      appendCommand(event.detail.nextIndex);
      setDefaultPromptEnabled(false);
    }

    window.addEventListener('render', render as EventListener);
    return () => {
      window.removeEventListener('render', render as EventListener);
    };
  }, []);

  useEffect(() => {
    if (!isCommandExecuted && !isCallbackExecuted) {
      const latestCurrentCommand = currentCommandRef.current;
      if (latestCurrentCommand) {
        setCommandHistory(prev => [...prev, latestCurrentCommand]);
      }
      setCurrentCommand(null);
      setCommandExecuted(true);
    }
  }, [stepIndex, isCallbackExecuted]);

  return (
    <div className="terminal-body-container" id="cli-commands">
      <div className="terminal-body" ref={terminalBodyRef}>
        <div className="command-history" ref={commandHistoryRef}>
          {commandHistory.map((item, index) => {
            return (
              <div key={index}>
                {(
                  <div className="command-block">
                    <div className="command-line">
                      <div className="command-line-inner">
                        <div className="command-content-wrapper">
                          <div className="command-multiline">{renderCommand(item, commandHistory.length)}</div>
                          <button className={`run-command-btn hidden-step-btn`} disabled={true}>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M18 4V16Q14 16 6 16H4M4 16L8 12M4 16L8 20"/>
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                    <div className="command-line-inner">
                      <div className="command-content-wrapper">
                        {item.result && (
                          <div className="command-content-wrapper">
                            <div className="command-multiline result">
                              <div className="command-line-item command-line-single">
                                <span className="command-text" dangerouslySetInnerHTML={{__html: item.result}}></span>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
          {currentCommand && (
            <div className="command-block">
              <div className="command-line">
                <div className="command-line-inner">
                  <div className="command-content-wrapper">
                    <div className="command-multiline">{renderCommand(currentCommand, commandHistory.length, false)}</div>
                    <button
                      id={`run-command-btn-${currentCommand.stepIndex}-active`}
                      className={`run-command-btn driver-active-el`}
                      onClick={(e) => {
                        handleRunCommand(currentCommand, commandHistory.length)
                      }}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M18 4V16Q14 16 6 16H4M4 16L8 12M4 16L8 20"/>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
              <div className="command-line-inner">
                <div className="command-content-wrapper">
                  {currentCommand.result && (
                    <div className="command-content-wrapper">
                      <div className="command-multiline result">
                        <div className="command-line-item command-line-single">
                          <span className="command-text" dangerouslySetInnerHTML={{__html: currentCommand.result}}></span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
          {isDefaultPromptEnabled && (
            <div className="command-block command-block-prompt">
              <div className="command-line">
                <div className="command-line-inner">
                  <div className="command-line-item">
                    <span className="prompt">{formatPrompt(steps.find(s => s.stepIndex === stepIndex)?.database)}{"> "}</span>
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
