import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
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
  const {stepIndex} = useDriver()
  const [commandHistory, setCommandHistory] = useState<CommandHistory[]>([]);

  const clickedButtonsRef = useRef<Set<number>>(new Set());
  const currentCommandDataRef = useRef<{ command: string, stepDatabase: string | undefined, formatPrompt: (db?: string) => string } | null>(null);
  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);

  const formatPrompt = useCallback((database?: string) => database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX, []);

  const appendCommandHistory = useCallback(async (item: CommandHistory) => {
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
      setCommandHistory(prev => [...prev, {type: 'command', prompt: '', content: '', result, stepIndex: stepIndex}]);
    } catch (err: any) {
      const result = `<p class="command-result error">${err.responseData?.error || err.message || 'Failed to execute command'}</p>`;
      console.error('Failed to execute command:', err);
      setCommandHistory(
        prev =>
          [...prev,
            {type: 'command', prompt: '', content: '', result, stepIndex: stepIndex}]);
    }

    const itemIndex = commandHistory.findIndex(cmd => (cmd.content === command));
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
  }, [stepIndex]);

  useEffect(() => {
    if (stepIndex >= steps.length) return;

    const stepCommand = steps.find(value => value.stepIndex == stepIndex);
    if (stepCommand == undefined || stepCommand.stepIndex != stepIndex) return;

    const {command} = stepCommand;
    const stepDatabase = stepCommand.database;

    currentCommandDataRef.current = null;

    if (command) {
      currentCommandDataRef.current = {command, stepDatabase, formatPrompt};
      const prompt = formatPrompt(stepDatabase ?? '');
      setCommandHistory(prev => [...prev, {type: 'command', prompt, content: command, stepIndex: stepIndex}]);
    }
  }, [stepIndex, formatPrompt]);

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
  }, [commandHistory.length]);

  const runCommand = useCallback((item: CommandHistory, index: number) => {
    const isCurrentStep = item.stepIndex === undefined || item.stepIndex === stepIndex;
    if (!isCurrentStep || item.result) return;

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

    appendCommandHistory(item);
  }, [stepIndex, appendCommandHistory]);

  const renderCommand = useCallback((item: CommandHistory, index: number, hideCursor: boolean = false) => {
    if (!item.content) {
      return <div className="command-line-item"><span className="prompt">{item.prompt}{"> "}</span></div>;
    }

    const lines = item.content.split('\n').filter(line => line.trim() !== '');
    const isLastItem = index === commandHistory.length - 1;
    const shouldShowCursor = isLastItem && !item.result && !hideCursor;
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
      if (item.stepIndex === stepIndex && !item.result) {
        return i;
      }
    }
    return -1;
  }, [commandHistory, stepIndex]);

  const needDefaultPrompt = useMemo(() => {
    if (stepIndex >= steps.length) return false;

    const stepCommand = steps.find(value => value.stepIndex === stepIndex);
    if (!stepCommand || !stepCommand.command) {
      return true;
    }

    const currentStepCommands = commandHistory.filter(item => item.stepIndex === stepIndex);
    const hasCommand = currentStepCommands.some(item => item.content === stepCommand.command);
    const hasResult = currentStepCommands.some(item => item.result);
    return hasCommand && hasResult;
  }, [commandHistory, stepIndex]);

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
                            <div className="command-multiline">{renderCommand(item, index, needDefaultPrompt)}</div>
                          </div>
                        )}
                        {!item.result && (() => {
                          const isClicked = clickedButtonsRef.current.has(index);
                          const isLastCommand = index === lastCommandIndex;
                          const shouldHide = !isLastCommand || (item.stepIndex !== undefined && item.stepIndex !== stepIndex) || isClicked;
                          return (
                            <button
                              className={`run-command-btn ${shouldHide ? 'hidden-step-btn' : ''}`}
                              onClick={(e) => {
                                if (isClicked) return;
                                runCommand(item, index);
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
            );
          })}
          {needDefaultPrompt && (
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
