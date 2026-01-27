import React, { useCallback, useEffect, useRef } from 'react';
import { useDriver, CommandHistory } from '../../contexts/DriverContext';
import '../../styles/cli-terminal.css';

const CliTerminal: React.FC = () => {
  const { currentCommand, commandHistory, isExecuting, executeCommand } = useDriver();

  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);

  const moveScrollbar = useCallback(() => {
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
        scrollContainer.scrollTop =
          scrollContainer.scrollTop + (targetRect.top - containerRect.top) - 20;
      }
    }
  }, [currentCommand, commandHistory.length]);

  useEffect(() => {
    const timeoutId = setTimeout(moveScrollbar, 0);
    return () => clearTimeout(timeoutId);
  }, [moveScrollbar]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        if (currentCommand && !isExecuting) {
          executeCommand();
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [currentCommand, isExecuting, executeCommand]);

  const renderCommand = useCallback((item: CommandHistory, hideCursor: boolean = true) => {
    if (!item.content) {
      return (
        <div className="command-line-item">
          <span className="prompt">
            {item.prompt}
            {'> '}
          </span>
        </div>
      );
    }

    const lines = item.content.split('\n').filter((line) => line.trim() !== '');
    const cursor = !hideCursor ? '<span class="cursor">_</span>' : '';

    if (lines.length === 1) {
      return (
        <div className="command-line-item command-line-single">
          <span className="prompt">
            {item.prompt}
            {'> '}
          </span>
          <span
            className="command-text"
            dangerouslySetInnerHTML={{ __html: lines[0].trim() + cursor }}
          ></span>
        </div>
      );
    }

    return lines.map((line, lineIdx) => {
      const hasBackslash = line.endsWith('\\');
      const lineText = hasBackslash ? line.slice(0, -1).trim() : line;
      const isLastLine = lineIdx === lines.length - 1;
      const lineWithCursor =
        lineText +
        (hasBackslash && !isLastLine ? ' \\' : '') +
        (!hideCursor && isLastLine ? cursor : '');

      return (
        <div key={lineIdx} className="command-line-item">
          {lineIdx === 0 ? (
            <>
              <span className="prompt">
                {item.prompt}
                {'> '}
              </span>
              <p className="command-text" dangerouslySetInnerHTML={{ __html: lineWithCursor }}></p>
            </>
          ) : (
            <span
              className="command-text-indent"
              dangerouslySetInnerHTML={{ __html: lineWithCursor }}
            ></span>
          )}
        </div>
      );
    });
  }, []);

  const formatPrompt = () => 'actionbase';

  const showDefaultPrompt = !currentCommand;

  return (
    <div className="terminal-body-container" id="cli-commands">
      <div className="terminal-body" ref={terminalBodyRef}>
        <div className="command-history" ref={commandHistoryRef}>
          {/* Command History */}
          {commandHistory.map((item, index) => {
            const isLastItem = index === commandHistory.length - 1;
            const shouldApplyPadding = isLastItem && !currentCommand;

            return (
              <div key={index}>
                <div className="command-block">
                  <div className="command-line">
                    <div className="command-line-inner">
                      <div className="command-content-wrapper">
                        <div className="command-multiline">{renderCommand(item)}</div>
                        <button className="run-command-btn hidden-step-btn" disabled={true}>
                          <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2.5"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          >
                            <path d="M18 4V16Q14 16 6 16H4M4 16L8 12M4 16L8 20" />
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
                              <span
                                className="command-text"
                                dangerouslySetInnerHTML={{ __html: item.result }}
                              ></span>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}

          {/* Current Command */}
          {currentCommand && (
            <div className="command-block" style={{ paddingBottom: '20px' }}>
              <div className="command-line">
                <div className="command-line-inner">
                  <div className="command-content-wrapper">
                    <div className="command-multiline">{renderCommand(currentCommand, false)}</div>
                    <button
                      id="run-command-btn"
                      className="run-command-btn driver-active-el"
                      disabled={isExecuting}
                      onClick={executeCommand}
                    >
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <path d="M18 4V16Q14 16 6 16H4M4 16L8 12M4 16L8 20" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Default Prompt */}
          {showDefaultPrompt && (
            <div className="command-block command-block-prompt" style={{ paddingBottom: '22.5px' }}>
              <div className="command-line">
                <div className="command-line-inner">
                  <div className="command-line-item">
                    <span className="prompt">
                      {formatPrompt()}
                      {'> '}
                    </span>
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
