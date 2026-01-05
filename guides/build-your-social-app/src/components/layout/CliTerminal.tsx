import React, {useEffect, useRef, useState} from 'react';
import steps from '../../modules/HandsOnStep';
import {TUTORIAL_STEP_KEY} from '../../modules/DriverContext';
import {useApiLog} from '../../modules/ApiLogContext';
import {setApiLogCallback} from '../../wrapper/apiClient';

import '../../styles/cli-terminal.css';

interface HistoryItem {
  type: 'title' | 'command';
  prompt: string;
  content?: string;
  originalText?: string; // Store original command text for copying
  result?: string; // Result output without prompt
}

const PROMPT_PREFIX = 'actionbase'

const CliTerminal: React.FC = () => {
  const [currentStep, setCurrentStep] = useState(0);
  const [currentPrompt, setCurrentPrompt] = useState('');
  const [currentTyping, setCurrentTyping] = useState('');

  const [commandHistory, setCommandHistory] = useState<HistoryItem[]>([]);
  const [isTyping, setIsTyping] = useState(false);
  const [isTypingCommand, setIsTypingCommand] = useState(false);
  const [currentTitle, setCurrentTitle] = useState('');
  const [showTerminalInput, setShowTerminalInput] = useState(false);
  const [showToast, setShowToast] = useState(false);

  const terminalEndRef = useRef<HTMLDivElement>(null);
  const apiLogEndRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);
  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const skipTypingRef = useRef<boolean>(false);
  const currentCommandDataRef = useRef<{commandIdx: number, commands: any[], stepDatabase: string | undefined, stepTable: string | undefined, formatPrompt: (db?: string, table?: string) => string} | null>(null);
  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const [shouldAutoScroll, setShouldAutoScroll] = useState(true);
  const shouldAutoScrollRef = useRef<boolean>(true);
  const isUserScrollingRef = useRef<boolean>(false);
  const lastScrollTopRef = useRef<number>(0);
  const isAutoScrollingRef = useRef<boolean>(false);

  const {apiLogs, addApiLog} = useApiLog();
  const [expandedPayloads, setExpandedPayloads] = useState<Set<number>>(new Set());

  useEffect(() => {
    setApiLogCallback(addApiLog);
  }, [addApiLog]);

  useEffect(() => {
    if (apiLogEndRef.current) {
      apiLogEndRef.current.scrollIntoView({behavior: 'smooth'});
    }
  }, [apiLogs]);

  // Check if user is at the bottom of the scroll container
  const isAtBottom = (element: HTMLElement) => {
    const threshold = 50; // pixels from bottom
    return element.scrollHeight - element.scrollTop - element.clientHeight < threshold;
  };

  // Handle scroll events to detect user interaction
  useEffect(() => {
    const terminalBody = terminalBodyRef.current;
    if (!terminalBody) return;

    const handleScroll = () => {
      const currentScrollTop = terminalBody.scrollTop;
      const scrollDifference = currentScrollTop - lastScrollTopRef.current;

      // If this is an auto-scroll, just update position and return
      if (isAutoScrollingRef.current) {
        isAutoScrollingRef.current = false;
        lastScrollTopRef.current = currentScrollTop;
        return;
      }

      // If user scrolled up (scrollTop decreased), immediately disable auto-scroll
      if (scrollDifference < -1) { // Even tiny upward movements disable auto-scroll
        isUserScrollingRef.current = true;
        shouldAutoScrollRef.current = false;
        setShouldAutoScroll(false);
        lastScrollTopRef.current = currentScrollTop;
        return;
      }

      // Update last scroll position
      lastScrollTopRef.current = currentScrollTop;

      // Check if user is at bottom
      if (isAtBottom(terminalBody)) {
        // User scrolled to bottom, enable auto-scroll immediately
        isUserScrollingRef.current = false;
        shouldAutoScrollRef.current = true;
        setShouldAutoScroll(true);
      } else {
        // User is not at bottom, keep auto-scroll disabled
        isUserScrollingRef.current = true;
        shouldAutoScrollRef.current = false;
        setShouldAutoScroll(false);
      }
    };

    // Handle wheel events to detect user scrolling
    const handleWheel = (e: WheelEvent) => {
      // If user is scrolling up, immediately disable auto-scroll
      if (e.deltaY < 0) {
        isUserScrollingRef.current = true;
        shouldAutoScrollRef.current = false;
        setShouldAutoScroll(false);
        // Cancel any pending auto-scroll
        if (terminalBodyRef.current) {
          lastScrollTopRef.current = terminalBodyRef.current.scrollTop;
        }
      } else {
        // Scrolling down - user is interacting, check if at bottom in scroll handler
        isUserScrollingRef.current = true;
      }
    };

    const handleTouchStart = () => {
      isUserScrollingRef.current = true;
    };

    terminalBody.addEventListener('scroll', handleScroll, { passive: true });
    terminalBody.addEventListener('wheel', handleWheel, { passive: true });
    terminalBody.addEventListener('touchstart', handleTouchStart, { passive: true });
    terminalBody.addEventListener('touchmove', handleTouchStart, { passive: true });

    // Initialize scroll position
    lastScrollTopRef.current = terminalBody.scrollTop;

    return () => {
      terminalBody.removeEventListener('scroll', handleScroll);
      terminalBody.removeEventListener('wheel', handleWheel);
      terminalBody.removeEventListener('touchstart', handleTouchStart);
      terminalBody.removeEventListener('touchmove', handleTouchStart);
    };
  }, []);

  // Auto scroll to bottom when command history changes or terminal input appears
  useEffect(() => {
    // During typing, always auto-scroll if user hasn't manually scrolled
    // This ensures multiline commands scroll down as each line is typed
    if (isTypingCommand && !isUserScrollingRef.current) {
      // Use requestAnimationFrame to ensure DOM has updated after typing
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          if (terminalEndRef.current && terminalBodyRef.current && !isUserScrollingRef.current) {
            isAutoScrollingRef.current = true;
            terminalEndRef.current.scrollIntoView({behavior: 'smooth', block: 'end'});
          }
        });
      });
      return;
    }

    // Only auto-scroll if explicitly enabled and user is not manually scrolling
    // Use ref for immediate check (no state delay)
    if (!shouldAutoScrollRef.current || !shouldAutoScroll || isUserScrollingRef.current) {
      return;
    }

    if (commandHistoryRef.current && terminalEndRef.current) {
      // Use setTimeout to ensure DOM has updated
      const timeoutId = setTimeout(() => {
        // Double-check that auto-scroll is still enabled using ref (immediate check)
        if (!shouldAutoScrollRef.current || isUserScrollingRef.current) {
          return;
        }

        if (terminalEndRef.current && terminalBodyRef.current) {
          // Mark that this is an auto-scroll, not user-initiated
          isAutoScrollingRef.current = true;
          terminalEndRef.current.scrollIntoView({behavior: 'smooth', block: 'end'});
        }
      }, 100);

      return () => {
        clearTimeout(timeoutId);
      };
    }
  }, [commandHistory, showTerminalInput, shouldAutoScroll, isTypingCommand]);

  useEffect(() => {
    // Load current step from localStorage
    const savedStep = localStorage.getItem(TUTORIAL_STEP_KEY);
    if (savedStep) {
      const stepIndex = parseInt(savedStep, 10);
      setCurrentStep(stepIndex);
    }
  }, []);

  useEffect(() => {
    // Listen for step changes
    const handleStorageChange = () => {
      const savedStep = localStorage.getItem(TUTORIAL_STEP_KEY);
      if (savedStep) {
        const stepIndex = parseInt(savedStep, 10);
        setCurrentStep(stepIndex);
      }
    };

    window.addEventListener('storage', handleStorageChange);

    // Also check periodically for changes
    const interval = setInterval(() => {
      const savedStep = localStorage.getItem(TUTORIAL_STEP_KEY);
      if (savedStep) {
        const stepIndex = parseInt(savedStep, 10);
        if (stepIndex !== currentStep) {
          setCurrentStep(stepIndex);
        }
      }
    }, 500);

    return () => {
      window.removeEventListener('storage', handleStorageChange);
      clearInterval(interval);
    };
  }, [currentStep]);

  // Typing effect for current step
  useEffect(() => {
    if (currentStep >= steps.length) return;

    const step = steps[currentStep];
    const {title, commands = []} = step;

    const stepDatabase = step.database;
    const stepTable = step.table;

    const formatPrompt = (database?: string, table?: string) => {
      if (!database && !table) {
        return PROMPT_PREFIX;
      }
      if (database && table) {
        return `${PROMPT_PREFIX}(${database}:${table})`;
      }
      if (database) {
        return `${PROMPT_PREFIX}(${database})`;
      }
      return `${PROMPT_PREFIX}(${table})`;
    };

    // Clear previous timeout
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    }

    // Clear current typing and history
    setCurrentTyping('');
    setIsTyping(false);
    setIsTypingCommand(false);
    setCommandHistory([]);
    setCurrentPrompt('');
    setCurrentTitle('');
    setShowTerminalInput(false);
    skipTypingRef.current = false;
    currentCommandDataRef.current = null;
    shouldAutoScrollRef.current = true; // Reset auto-scroll ref when step changes
    setShouldAutoScroll(true); // Reset auto-scroll when step changes
    isUserScrollingRef.current = false; // Reset user scrolling flag
    isAutoScrollingRef.current = false; // Reset auto-scrolling flag

    const baseTypingSpeed = 50; // base milliseconds per character for title
    const commandTypingSpeed = 15; // base milliseconds per character for commands (faster than title)

    let lastDatabase = stepDatabase;
    let lastTable = stepTable;

    // Add title to history immediately (no typing effect)
    const titleHistoryItem = {
      type: 'title' as const,
      prompt: formatPrompt(stepDatabase, stepTable),
      content: title
    };

    setCommandHistory([titleHistoryItem]);
    setCurrentTitle('');

    // Title complete - add commands one by one
    lastDatabase = stepDatabase;
    lastTable = stepTable;
    setCurrentTyping('');
    setIsTypingCommand(false);
    setCurrentPrompt(''); // Clear prompt temporarily

    // Add commands one by one with typing effect
    if (commands.length > 0) {
          let commandIdx = 0;
          let currentTypingCommandIndex = -1; // Track which command is currently being typed

          // Store command data for skip functionality
          currentCommandDataRef.current = {
            commandIdx: 0,
            commands,
            stepDatabase,
            stepTable,
            formatPrompt
          };

          const addNextCommand = () => {
            if (commandIdx < commands.length) {
              const currentCommand = commands[commandIdx];
              const cmdDatabase = currentCommand.database ?? stepDatabase;
              const cmdTable = currentCommand.table ?? stepTable;
              const commandText = currentCommand.text;
              const commandResult = currentCommand.result;

              // Update current command data
              currentCommandDataRef.current = {
                commandIdx,
                commands,
                stepDatabase,
                stepTable,
                formatPrompt
              };

              // First, add command with prompt only (empty content)
              const commandItem = {
                type: 'command' as const,
                prompt: formatPrompt(cmdDatabase, cmdTable),
                content: '',
                originalText: commandText, // Store original text for copying
              };

              setCommandHistory(prev => {
                const newHistory = [...prev, commandItem];
                // Track the index of the command we're about to type
                currentTypingCommandIndex = newHistory.length - 1;
                return newHistory;
              });

              // All commands (both single-line and multi-line) use typing effect
              setIsTypingCommand(true); // Set typing command flag
              let commandTextIndex = 0;

              const typeCommand = () => {
                // Check if skip typing is requested
                if (skipTypingRef.current) {
                  skipTypingRef.current = false;
                  // Immediately complete the current command
                  setCommandHistory(prev => {
                    const newHistory = [...prev];
                    for (let i = newHistory.length - 1; i >= 0; i--) {
                      if (newHistory[i].type === 'command' &&
                          newHistory[i].prompt === formatPrompt(cmdDatabase, cmdTable)) {
                        newHistory[i] = {
                          ...newHistory[i],
                          content: commandText
                        };
                        break;
                      }
                    }
                    return newHistory;
                  });

                  // Move to next command
                  currentTypingCommandIndex = -1;
                  setIsTypingCommand(false);

                  // If result exists, add it after command text
                  if (commandResult) {
                    const resultItem = {
                      type: 'command' as const,
                      prompt: '', // No prompt for result
                      content: '',
                      result: commandResult,
                    };
                    setCommandHistory(prev => [...prev, resultItem]);
                  }

                  commandIdx++;

                  if (commandIdx < commands.length) {
                    // Add next command after a delay
                    typingTimeoutRef.current = setTimeout(addNextCommand, 300);
                  } else {
                    // All commands added, show idle prompt
                    const lastCommand = commands[commands.length - 1];
                    const lastCmdDatabase = lastCommand.database ?? stepDatabase;
                    const lastCmdTable = lastCommand.table ?? stepTable;
                    const idleDatabase = step.finalDatabase ?? lastCmdDatabase;
                    const idleTable = step.finalTable ?? lastCmdTable;
                    setCurrentPrompt(formatPrompt(idleDatabase, idleTable));
                    setIsTyping(false);
                    setIsTypingCommand(false);
                  }
                  return;
                }

                if (commandTextIndex < commandText.length) {
                  const currentCommandText = commandText.substring(0, commandTextIndex + 1);

                  setCommandHistory(prev => {
                    const newHistory = [...prev];
                    // Find the last command item that matches our prompt and update it
                    for (let i = newHistory.length - 1; i >= 0; i--) {
                      if (newHistory[i].type === 'command' &&
                          newHistory[i].prompt === formatPrompt(cmdDatabase, cmdTable)) {
                        // Update the command content
                        newHistory[i] = {
                          ...newHistory[i],
                          content: currentCommandText
                        };
                        break;
                      }
                    }
                    return newHistory;
                  });

                  commandTextIndex++;

                  // Check if we're inside an HTML tag (between < and >)
                  const textUpToIndex = commandText.substring(0, commandTextIndex);
                  const lastOpenTag = textUpToIndex.lastIndexOf('<');
                  const lastCloseTag = textUpToIndex.lastIndexOf('>');
                  const isInTag = lastOpenTag > lastCloseTag;

                  let speed;
                  if (isInTag) {
                    // Fast typing for HTML tags (1-3ms per character)
                    speed = 1 + Math.random() * 2;
                  } else {
                    // Normal typing speed for regular text
                    const variation = Math.random() * 10 - 5;
                    speed = commandTypingSpeed + variation;
                  }

                  typingTimeoutRef.current = setTimeout(typeCommand, Math.max(1, speed));
                } else {
                  // Command typing complete
                  currentTypingCommandIndex = -1; // Clear current typing command index
                  setIsTypingCommand(false); // Clear typing command flag

                  // If result exists, add it after command text
                  if (commandResult) {
                    const resultItem = {
                      type: 'command' as const,
                      prompt: '', // No prompt for result
                      content: '',
                      result: commandResult,
                    };
                    setCommandHistory(prev => [...prev, resultItem]);
                  }

                  commandIdx++;

                  if (commandIdx < commands.length) {
                    // Add next command after a delay
                    typingTimeoutRef.current = setTimeout(addNextCommand, 300);
                  } else {
                    // All commands added, show idle prompt
                    const lastCommand = commands[commands.length - 1];
                    const lastCmdDatabase = lastCommand.database ?? stepDatabase;
                    const lastCmdTable = lastCommand.table ?? stepTable;
                    const idleDatabase = step.finalDatabase ?? lastCmdDatabase;
                    const idleTable = step.finalTable ?? lastCmdTable;
                    setCurrentPrompt(formatPrompt(idleDatabase, idleTable));
                    setIsTyping(false);
                    setIsTypingCommand(false);
                  }
                }
              };

              // Start typing command after prompt delay
              typingTimeoutRef.current = setTimeout(typeCommand, 300);
            }
          };

          // Start adding commands after a short delay
          typingTimeoutRef.current = setTimeout(addNextCommand, 0);
        } else {
          // No commands: show idle prompt
          const idleDatabase = step.finalDatabase ?? lastDatabase;
          const idleTable = step.finalTable ?? lastTable;
          setCurrentPrompt(formatPrompt(idleDatabase, idleTable));
          setIsTyping(false);
          setIsTypingCommand(false);
        }

    return () => {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
    };
  }, [currentStep]);

  return (
    <div className="cli-terminal" id="cli-commands">
      <div className="terminal-header">
        <div className="terminal-buttons">
          <span className="terminal-btn close"></span>
          <span className="terminal-btn minimize"></span>
          <span className="terminal-btn maximize"></span>
        </div>
        <div className="terminal-title"></div>
      </div>

      <div className="terminal-body-container">
        <div className="terminal-body terminal-body-top" ref={terminalBodyRef}>
          <div className="command-history" ref={commandHistoryRef}>
            {commandHistory.map((item, index) => (
              <div key={index}>
                {item.type === 'title' && (
                  <div className="command-block active">
                    <div className="command-line">
                      <div className="command-line-inner">
                        <span className="step-title">
                          {item.content}
                        </span>
                      </div>
                    </div>
                  </div>
                )}
                {item.type === 'command' && (
                  <>
                    <div className="command-block active">
                      <div className="command-line">
                        <div className="command-line-inner">
                          {item.result ? (
                            // Render result without prompt
                            <div className="command-content-wrapper">
                              <div className="command-multiline">
                                <div className="command-line-item command-line-single">
                                  <span className="command-text" dangerouslySetInnerHTML={{__html: item.result}}></span>
                                </div>
                              </div>
                            </div>
                          ) : (
                            <div className="command-content-wrapper">
                              <div className="command-multiline">
                                {item.content ? (() => {
                                  // Split by newline and preserve whitespace
                                  const lines = item.content.split('\n').filter(line => line.trim() !== '');
                                  const isSingleLine = lines.length === 1;
                                  const isTypingThisCommand = isTypingCommand && index === commandHistory.length - 1;

                                  // For single-line commands, render as one block to allow natural wrapping
                                  if (isSingleLine) {
                                    const line = lines[0];
                                    const trimmedLine = line.trim();
                                    const showCursor = isTypingThisCommand;
                                    const lineWithCursor = trimmedLine + (showCursor ? '<span class="cursor">_</span>' : '');

                                    return (
                                      <div className="command-line-item command-line-single">
                                        <span className="prompt">{item.prompt}{" > "}</span>
                                        <span className="command-text" dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
                                      </div>
                                    );
                                  }

                                  // For multi-line commands, render each line separately
                                  return lines.map((line, lineIdx) => {
                                    // Preserve leading whitespace for indentation
                                    const leadingSpaces = line.match(/^\s*/)?.[0] || '';
                                    const trimmedLine = line.trim();
                                    const hasBackslash = trimmedLine.endsWith('\\') || trimmedLine.endsWith('\\\\');
                                    const lineText = hasBackslash
                                      ? (trimmedLine.endsWith('\\\\')
                                          ? trimmedLine.slice(0, -2).trim()
                                          : trimmedLine.slice(0, -1).trim())
                                      : trimmedLine;
                                    const isLastLine = lineIdx === lines.length - 1;
                                    const showCursor = isTypingThisCommand && isLastLine;
                                    const lineWithBackslash = lineText + (hasBackslash && !isLastLine ? ' \\' : '');
                                    const lineWithCursor = lineWithBackslash + (showCursor ? '<span class="cursor">_</span>' : '');

                                    return (
                                      <div key={lineIdx} className="command-line-item">
                                        {lineIdx === 0 ? (
                                          <>
                                            <span className="prompt">{item.prompt}{" > "}</span>
                                            <span className="command-text" dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
                                          </>
                                        ) : (
                                          <>
                                            <span className="command-text-indent" style={{paddingLeft: leadingSpaces.length > 0 ? `${leadingSpaces.length * 0.5}ch` : '0'}} dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
                                          </>
                                        )}
                                      </div>
                                    );
                                  });
                                })() : (
                                  // Show prompt only when content is empty
                                  <div className="command-line-item">
                                    <span className="prompt">{item.prompt}{" > "}</span>
                                  </div>
                                )}
                              </div>
                            </div>
                          )}
                          {item.type === 'command' && !item.result && (
                            <button
                              className="copy-command-btn"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  // Skip typing if currently typing this command
                                  if (isTypingCommand && index === commandHistory.length - 1) {
                                    skipTypingRef.current = true;
                                    // Scroll to bottom when skip button is clicked during typing
                                    requestAnimationFrame(() => {
                                      requestAnimationFrame(() => {
                                        if (terminalEndRef.current && terminalBodyRef.current) {
                                          isAutoScrollingRef.current = true;
                                          terminalEndRef.current.scrollIntoView({behavior: 'smooth', block: 'end'});
                                        }
                                      });
                                    });
                                  }
                                  // Use original text if available (for copying full command even while typing)
                                  const textToProcess = item.originalText || item.content || '';
                                  if (textToProcess) {
                                    const lines = textToProcess.split('\n').filter(line => line.trim() !== '');
                                    const commandLines = lines.map((line, lineIdx) => {
                                      const leadingSpaces = line.match(/^\s*/)?.[0] || '';
                                      const trimmedLine = line.trim();
                                      const hasBackslash = trimmedLine.endsWith('\\') || trimmedLine.endsWith('\\\\');
                                      let lineText = hasBackslash
                                        ? (trimmedLine.endsWith('\\\\')
                                            ? trimmedLine.slice(0, -2).trim()
                                            : trimmedLine.slice(0, -1).trim())
                                        : trimmedLine;

                                      // Remove HTML tags (e.g., <span>, </span>)
                                      lineText = lineText.replace(/<[^>]*>/g, '');

                                      const isLastLine = lineIdx === lines.length - 1;

                                      return `${leadingSpaces}${lineText}${hasBackslash && !isLastLine ? ' \\' : ''}`;
                                    });
                                    const commandToCopy = commandLines.join('\n');
                                    navigator.clipboard.writeText(commandToCopy).then(() => {
                                      // Show toast notification
                                      setShowToast(true);
                                      setTimeout(() => {
                                        setShowToast(false);
                                      }, 900);
                                    }).catch(err => {
                                      console.error('Failed to copy:', err);
                                    });
                                  }
                                }}
                              title="Copy command"
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                              </svg>
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  </>
                )}
              </div>
            ))}

            <div className="terminal-input">
              {currentTyping && !isTypingCommand && (
                <div>
                  {currentPrompt && <span className="prompt">{currentPrompt}{" > "}</span>} <span className="step-title">{currentTyping}</span>
                </div>
              )}

              {!currentTyping && !isTyping && !isTypingCommand && (
                <div className="command-line-inner">
                  {currentPrompt && <span className="prompt">{currentPrompt}{" > "}</span>} <span className="cursor">_</span>
                </div>
              )}
            </div>
            <div ref={terminalEndRef}/>
          </div>
        </div>

        <div className="terminal-divider"></div>

        <div className="terminal-body terminal-body-bottom">
          <div className="api-log-content">
            {apiLogs.length === 0 ? (
              <div className="api-log-empty"></div>
            ) : (
              apiLogs.map((log) => {
                const isExpanded = expandedPayloads.has(log.id);
                const hasPayload = log.payload !== undefined;
                const hasRequestBody = log.requestBody !== undefined;
                const hasExpandableContent = hasPayload || hasRequestBody;

                return (
                  <div key={log.id} className={`api-log-item ${!log.success ? 'api-log-item-error' : ''}`}>
                    <div
                      className={`api-log-header-line ${hasExpandableContent ? 'api-log-header-clickable' : ''}`}
                      onClick={() => {
                        if (hasExpandableContent) {
                          setExpandedPayloads(prev => {
                            const newSet = new Set(prev);
                            if (isExpanded) {
                              newSet.delete(log.id);
                            } else {
                              newSet.add(log.id);
                            }
                            return newSet;
                          });
                        }
                      }}
                    >
                      <span className={`api-log-expand-icon ${!hasExpandableContent ? 'api-log-expand-icon-empty' : ''}`}>
                        {hasExpandableContent && (
                          <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            style={{
                              transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
                              transition: 'transform 0.2s ease'
                            }}
                          >
                            <path d="M9 18l6-6-6-6"/>
                          </svg>
                        )}
                      </span>
                      <span className={`api-log-method api-log-method-${log.method.toLowerCase()}`}>{log.method}</span>
                      <span className={`api-log-url ${!log.success ? 'api-log-url-error' : ''}`}>
                        {log.url}
                        {!log.success && log.status && <span className="api-log-status"> ({log.status})</span>}
                      </span>
                    </div>
                    {hasExpandableContent && isExpanded && (
                      <div className="api-log-body">
                        {hasRequestBody && (
                          <div className="api-log-request-body">
                            <div className="api-log-section-title">Request</div>
                            <pre>{typeof log.requestBody === 'string' ? log.requestBody : JSON.stringify(log.requestBody, null, 2)}</pre>
                          </div>
                        )}
                        {hasPayload && (
                          <div className="api-log-payload">
                            <div className="api-log-section-title">Response</div>
                            <pre>{JSON.stringify(log.payload, null, 2)}</pre>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })
            )}
            <div ref={apiLogEndRef}/>
          </div>
        </div>
      </div>
      {showToast && (
        <div className="toast-notification">
          Copied!
        </div>
      )}
    </div>
  );
};

export default CliTerminal;
