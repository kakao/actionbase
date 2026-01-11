import React, {useCallback, useEffect, useRef, useState} from 'react';
import {run} from "../../api/cli";
import stepCommands from "../../constants/handsOnStepCommand";
import '../../styles/cli-terminal.css';
import {STEP, useDriver} from "../../contexts/DriverContext";

interface CommandHistory {
  prompt: string;
  content?: string;
  result?: string;
  stepIndex?: number;
  database?: string | undefined;
  isContextChanged?: boolean;
}

interface Context {
  database: string | undefined
}

const PROMPT_PREFIX = 'actionbase';

const CliTerminal: React.FC = () => {
  const {stepIndex, buttonEvent, moveNext} = useDriver()

  const [currentCommand, setCurrentCommand] = useState<CommandHistory | null>();
  const [currentContext, setCurrentContext] = useState<Context | null>();
  const [commandHistory, setCommandHistory] = useState<CommandHistory[]>([]);
  const [isDefaultPromptEnabled, setDefaultPromptEnabled] = useState<boolean>(true);
  const [isCommandClicked, setCommandClicked] = useState<boolean>(false);

  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);
  const currentCommandRef = useRef<CommandHistory | undefined>(undefined);
  const currentContextRef = useRef<Context | null>(null);

  function formatPrompt(database?: string) {
    return database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX;
  }

  function renderCurrentCommand(event: CustomEvent) {
    setDefaultPromptEnabled(true);

    const latestCurrentCommand = currentCommandRef.current;
    if (latestCurrentCommand) {
      setCommandHistory(prev => [...prev, latestCurrentCommand]);
    }

    const stepIndex = event.detail.nextIndex;
    const stepCommand = stepCommands.find(value => value.stepIndex == stepIndex);
    if (stepCommand == undefined || stepCommand.stepIndex != stepIndex) return;
    const {command, context} = stepCommand;

    if (command) {
      const prompt = formatPrompt(currentContextRef.current?.database);
      setCurrentCommand({prompt, content: command, stepIndex: stepIndex, database: context?.database, isContextChanged: context !== undefined});
    }

    setDefaultPromptEnabled(false);
  }

  function moveScrollbar() {
    if (terminalBodyRef.current && commandHistoryRef.current) {
      const scrollContainer = terminalBodyRef.current;
      let targetElement: HTMLElement | null = null;

      if (currentCommand) {
        const runButton = document.getElementById('run-command-btn');
        if (runButton) {
          const commandBlock = runButton.closest('.command-block') as HTMLElement;
          if (commandBlock) {
            targetElement = commandBlock.querySelector('.command-line-item') as HTMLElement;
          }
        }
      }

      if (!targetElement && commandHistory.length > 0) {
        const commandBlocks = commandHistoryRef.current.querySelectorAll('.command-block');
        const lastCommandBlock = commandBlocks[commandBlocks.length - 1] as HTMLElement;
        if (lastCommandBlock) {
          targetElement = lastCommandBlock.querySelector('.command-line-item') as HTMLElement;
        }
      }

      if (targetElement) {
        const targetRect = targetElement.getBoundingClientRect();
        const containerRect = scrollContainer.getBoundingClientRect();

        scrollContainer.scrollTop = scrollContainer.scrollTop + (targetRect.top - containerRect.top) - 20;
      }
    }
  }

  function clearUnExecutedCurrentCommand() {
    const isUnExecutedCommandRemained = !isCommandClicked &&
      currentCommandRef.current &&
      (buttonEvent?.type === STEP.NEXT || buttonEvent?.type === STEP.PREV);

    if (isUnExecutedCommandRemained) {
      const stepCommandToCheck = buttonEvent.type === STEP.NEXT ? stepIndex + 1 : stepIndex;

      const nextCommand = stepCommands.find(step => step.stepIndex === stepCommandToCheck);
      if (!nextCommand || (nextCommand && !nextCommand.command)) {
        const latestCurrentCommand = currentCommandRef.current;
        if (latestCurrentCommand) {
          setCommandHistory(prev => [...prev, latestCurrentCommand]);
        }

        setCurrentCommand(null);
        setDefaultPromptEnabled(false);
        currentCommandRef.current = undefined;
      }
    }
  }

  const handleRunCommand = useCallback((item: CommandHistory) => {
    runCommand(item, stepIndex)
  }, []);

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

  const call = useCallback(async (command: string) => {
    try {
      const response = await run({command: command});

      if (response.error) {
        return `<p class="command-result error">${response.error}</p>`;
      }

      if (response.result) {
        return `<p class="command-result">${response.result}</p>`;
      }

      return response.success ? '<p class="command-result success">✓ Success</p>' : '<p class="command-result error">Failed</p>';
    } catch (err: any) {
      console.error('Failed to execute command:', err);
      return `<p class="command-result error">${err.responseData?.error || err.message || 'Failed to execute command'}</p>`;
    }
  }, []);

  const runCommand = useCallback(async (item: CommandHistory, stepIndex: number) => {
    const command = item.content || '';
    if (!command) return;

    const normalizedCommand = command.replaceAll('\\\n', '')
    const result = await call(normalizedCommand)

    setCommandHistory(prev => [...prev, {...item, result}]);
    setDefaultPromptEnabled(true);
    setCommandClicked(false);
    setCurrentCommand(null);

    if (currentCommandRef.current) {
      currentCommandRef.current = undefined;
    }

    if (item.isContextChanged) {
      const newContext = {database: item.database};
      setCurrentContext(newContext);
      currentContextRef.current = newContext;
    }

    moveNext();
  }, []);

  useEffect(() => {
    setTimeout(() => {
      moveScrollbar()
    }, 0);
  }, [currentCommand, commandHistory]);

  useEffect(() => {
    if (currentCommand) {
      currentCommandRef.current = currentCommand;
    }
  }, [currentCommand]);

  useEffect(() => {
    if (currentContext) {
      currentContextRef.current = currentContext;
    }
  }, [currentContext]);

  useEffect(() => {
    window.addEventListener('render', renderCurrentCommand as EventListener);
    return () => {
      window.removeEventListener('render', renderCurrentCommand as EventListener);
    };
  }, []);

  useEffect(() => {
    clearUnExecutedCurrentCommand()
  }, [buttonEvent]);

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
                      id="run-command-btn"
                      className={`run-command-btn driver-active-el`}
                      onClick={(e) => {
                        setCommandClicked(true)
                        handleRunCommand(currentCommand)
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
                    <span className="prompt">{formatPrompt(currentContext?.database)}{"> "}</span>
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
