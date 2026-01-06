import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useApiLog} from '../../contexts/ApiLogContext';
import {runCommand} from "../../api/cli";
import {setApiLogCallback} from '../../api/client';
import steps from "../../constants/HandsOnStep";
import '../../styles/cli-terminal.css';

interface HistoryItem {
  type: 'title' | 'command';
  prompt: string;
  content?: string;
  originalText?: string;
  result?: string;
  isExecuting?: boolean;
  stepIndex?: number;
}

const PROMPT_PREFIX = 'actionbase';
const COMMAND_TYPING_SPEED = 15;
const TYPING_DELAY = 300;

const escapeHTML = (text: string) => text.replace(/</g, '&lt;').replace(/>/g, '&gt;');

const CliTerminal: React.FC = () => {
  const [currentStep, setCurrentStep] = useState<number>(0);
  const [currentPrompt, setCurrentPrompt] = useState('');
  const [commandHistory, setCommandHistory] = useState<HistoryItem[]>([]);
  const [isTypingCommand, setIsTypingCommand] = useState(false);
  const [expandedPayloads, setExpandedPayloads] = useState<Set<number>>(new Set());

  const terminalEndRef = useRef<HTMLDivElement>(null);
  const apiLogEndRef = useRef<HTMLDivElement>(null);
  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const currentCommandDataRef = useRef<{ commandIdx: number, commands: any[], stepDatabase: string | undefined, formatPrompt: (db?: string) => string } | null>(null);
  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const scrollRefs = useRef({shouldAutoScroll: true, isUserScrolling: false, lastScrollTop: 0, isAutoScrolling: false, shouldScrollDuringTyping: true, userScrolledUp: false});

  const {apiLogs, addApiLog} = useApiLog();

  const formatPrompt = useCallback((database?: string) => database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX, []);

  const resetScrollState = useCallback(() => {
    const refs = scrollRefs.current;
    refs.shouldAutoScroll = true;
    refs.shouldScrollDuringTyping = true;
    refs.isUserScrolling = false;
    refs.userScrolledUp = false;
  }, []);

  const updateScrollState = useCallback((atBottom: boolean) => {
    const refs = scrollRefs.current;
    refs.isUserScrolling = !atBottom;
    refs.shouldAutoScroll = atBottom;
    refs.shouldScrollDuringTyping = atBottom;
    if (atBottom) refs.userScrolledUp = false;
  }, []);

  const scrollToBottom = useCallback((force = false) => {
    if (!terminalEndRef.current || !terminalBodyRef.current) return;
    const refs = scrollRefs.current;
    if ((!force && !refs.shouldScrollDuringTyping) || (force && refs.userScrolledUp)) return;
    if (force) resetScrollState();
    requestAnimationFrame(() => {
      refs.isAutoScrolling = true;
      terminalEndRef.current?.scrollIntoView({behavior: 'smooth', block: 'end'});
    });
  }, [resetScrollState]);

  const updateCommandInHistory = useCallback((predicate: (item: HistoryItem) => boolean, updater: (item: HistoryItem) => HistoryItem) => {
    setCommandHistory(prev => {
      const newHistory = [...prev];
      for (let i = newHistory.length - 1; i >= 0; i--) {
        if (predicate(newHistory[i])) {
          newHistory[i] = updater(newHistory[i]);
          break;
        }
      }
      return newHistory;
    });
  }, []);

  const createResultMessage = useCallback((response: any) => {
    if (response.error) return `<span class="command-result error">${escapeHTML(response.error)}</span>`;
    if (response.result) return `<span class="command-result">${escapeHTML(response.result)}</span>`;
    return response.success ? '<span class="command-result success">✓ Success</span>' : '<span class="command-result error">Failed</span>';
  }, []);

  const createErrorMessage = useCallback((err: any) =>
    `<span class="command-result error">${escapeHTML(err.responseData?.error || err.message || 'Failed to execute command')}</span>`, []);

  const processCommandText = useCallback((text: string) => {
    return text.split('\n').filter(line => line.trim() !== '').map((line, idx, lines) => {
      const leadingSpaces = line.match(/^\s*/)?.[0] || '';
      const trimmedLine = line.trim();
      const endsWithDoubleBackslash = trimmedLine.endsWith('\\\\');
      const hasBackslash = endsWithDoubleBackslash || trimmedLine.endsWith('\\');
      const lineText = hasBackslash ? (endsWithDoubleBackslash ? trimmedLine.slice(0, -2).trim() : trimmedLine.slice(0, -1).trim()) : trimmedLine;
      return `${leadingSpaces}${lineText}${hasBackslash && idx !== lines.length - 1 ? ' \\' : ''}`;
    });
  }, []);

  const normalizeCommandForRequest = useCallback((commandLines: string[]) => {
    return commandLines.map(cmd => {
      if (cmd.endsWith('\\\\')) return cmd.slice(0, -2).trim() + " ";
      if (cmd.endsWith('\\')) return cmd.slice(0, -1).trim() + " ";
      return cmd;
    }).join('');
  }, []);

  const minifyJsonContent = useCallback((content: string): string => {
    try {
      return JSON.stringify(JSON.parse(content));
    } catch {
      const placeholders: string[] = [];
      const contentWithPlaceholders = content.replace(/__[A-Z_]+__/g, (match) => {
        placeholders.push(match);
        return `"__PLACEHOLDER_${placeholders.length - 1}__"`;
      });
      try {
        let minified = JSON.stringify(JSON.parse(contentWithPlaceholders));
        placeholders.forEach((value, index) => {
          minified = minified.replace(`"__PLACEHOLDER_${index}__"`, value);
        });
        return minified;
      } catch {
        return content.replace(/\s+/g, ' ').trim();
      }
    }
  }, []);

  const processCommandRequest = useCallback((commandRequest: string) => {
    let result = '';
    let i = 0;

    while (i < commandRequest.length) {
      const isFlagStart = commandRequest[i] === '-' && commandRequest[i + 1] === '-' && (i === 0 || /\s/.test(commandRequest[i - 1]));

      if (isFlagStart) {
        const flagMatch = commandRequest.slice(i).match(/^--[a-zA-Z0-9_-]+/);
        if (flagMatch) {
          const flagName = flagMatch[0];
          let pos = i + flagName.length;
          while (pos < commandRequest.length && /\s/.test(commandRequest[pos])) pos++;

          const quoteChar = commandRequest[pos];
          if (quoteChar === '\'' || quoteChar === '"') {
            const jsonStart = pos + 1;
            let jsonEnd = jsonStart;
            while (jsonEnd < commandRequest.length) {
              if (commandRequest[jsonEnd] === quoteChar && (jsonEnd === jsonStart || commandRequest[jsonEnd - 1] !== '\\')) break;
              jsonEnd++;
            }

            if (jsonEnd < commandRequest.length) {
              const jsonContent = commandRequest.substring(jsonStart, jsonEnd);
              result += flagName + commandRequest.substring(i + flagName.length, jsonStart) + minifyJsonContent(jsonContent) + quoteChar;
              i = jsonEnd + 1;
              continue;
            }
          }
        }
      }
      result += commandRequest[i++];
    }
    return result;
  }, [minifyJsonContent]);

  const typeCommand = useCallback((commandText: string, cmdDatabase: string, formatPromptFn: (db?: string) => string, onComplete?: () => void) => {
    let index = 0;
    const prompt = formatPromptFn(cmdDatabase);
    const typeNext = () => {
      if (index >= commandText.length) {
        setIsTypingCommand(false);
        onComplete?.();
        return;
      }
      index++;
      updateCommandInHistory(
        (item) => item.type === 'command' && item.prompt === prompt,
        (item) => ({...item, content: commandText.substring(0, index)})
      );
      const refs = scrollRefs.current;
      if (refs.shouldScrollDuringTyping && !refs.userScrolledUp) scrollToBottom();
      const text = commandText.substring(0, index);
      const isInTag = text.lastIndexOf('<') > text.lastIndexOf('>');
      const speed = isInTag ? 1 + Math.random() * 2 : COMMAND_TYPING_SPEED + Math.random() * 10 - 5;
      typingTimeoutRef.current = setTimeout(typeNext, Math.max(1, speed));
    };
    typingTimeoutRef.current = setTimeout(typeNext, TYPING_DELAY);
  }, [updateCommandInHistory, scrollToBottom]);

  const addCommandToHistory = useCallback((command: any, cmdDatabase: string, formatPromptFn: (db?: string) => string) => {
    setCommandHistory(prev => [...prev, {type: 'command', prompt: formatPromptFn(cmdDatabase), content: '', originalText: command.text, stepIndex: currentStep}]);
    setIsTypingCommand(true);
    scrollRefs.current.shouldScrollDuringTyping = true;
    typeCommand(command.text, cmdDatabase, formatPromptFn, () => {
      if (command.result) {
        setCommandHistory(prev => [...prev, {type: 'command', prompt: '', content: '', result: command.result, stepIndex: currentStep}]);
      }
      setCurrentPrompt('');
      setIsTypingCommand(false);
    });
  }, [currentStep, typeCommand]);

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
      const step = steps[currentStep];
      const lastCommand = commands[commands.length - 1];
      const idleDatabase = step.finalDatabase ?? (lastCommand.database ?? stepDatabase);
      setCurrentPrompt(formatPromptFn(idleDatabase));
    }
  }, [currentStep, formatPrompt, addCommandToHistory]);

  const executeCommand = useCallback(async (item: HistoryItem) => {
    const textToProcess = item.originalText || item.content || '';
    if (!textToProcess) return;

    const commandLines = processCommandText(textToProcess);
    navigator.clipboard.writeText(commandLines.join('\n')).catch(console.error);
    const processedRequest = processCommandRequest(normalizeCommandForRequest(commandLines));
    const markAsExecuted = () => updateCommandInHistory(
      (cmd) => cmd.type === 'command' && cmd.prompt === item.prompt && cmd.originalText === item.originalText && !cmd.result,
      (cmd) => ({...cmd, isExecuting: false, result: 'executed'})
    );

    let result: string;
    try {
      const response = await runCommand({command: processedRequest});
      result = createResultMessage(response);
    } catch (err: any) {
      result = createErrorMessage(err);
      console.error('Failed to execute command:', err);
    }
    markAsExecuted();
    setCommandHistory(prev => [...prev, {type: 'command', prompt: '', content: '', result, stepIndex: currentStep}]);
    scrollToBottom(true);
    proceedToNextCommand();
  }, [processCommandText, normalizeCommandForRequest, processCommandRequest, createResultMessage, createErrorMessage, updateCommandInHistory, currentStep, scrollToBottom, proceedToNextCommand]);

  useEffect(() => {
    setApiLogCallback(addApiLog);
  }, [addApiLog]);

  useEffect(() => {
    apiLogEndRef.current?.scrollIntoView({behavior: 'smooth'});
  }, [apiLogs]);

  useEffect(() => {
    const terminalBody = terminalBodyRef.current;
    if (!terminalBody) return;

    const isAtBottom = (el: HTMLElement) => el.scrollHeight - el.scrollTop - el.clientHeight < 50;
    const stopAutoScroll = () => {
      const refs = scrollRefs.current;
      refs.isUserScrolling = true;
      refs.shouldAutoScroll = false;
      refs.shouldScrollDuringTyping = false;
      refs.userScrolledUp = true;
    };

    const handleWheel = (e: WheelEvent) => {
      if (e.deltaY < 0) stopAutoScroll();
    };

    const handleScroll = () => {
      const refs = scrollRefs.current;
      const currentScrollTop = terminalBody.scrollTop;
      const scrollDifference = currentScrollTop - refs.lastScrollTop;
      const atBottom = isAtBottom(terminalBody);

      if (refs.isAutoScrolling) {
        refs.isAutoScrolling = false;
        refs.lastScrollTop = currentScrollTop;
        if (atBottom) updateScrollState(true);
        return;
      }

      if (scrollDifference < 0) {
        stopAutoScroll();
        refs.lastScrollTop = currentScrollTop;
        return;
      }

      if (scrollDifference > 0) updateScrollState(atBottom);
      refs.lastScrollTop = currentScrollTop;
    };

    terminalBody.addEventListener('wheel', handleWheel, {passive: true});
    terminalBody.addEventListener('scroll', handleScroll, {passive: true});
    scrollRefs.current.lastScrollTop = terminalBody.scrollTop;

    return () => {
      terminalBody.removeEventListener('wheel', handleWheel);
      terminalBody.removeEventListener('scroll', handleScroll);
    };
  }, [updateScrollState]);

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

    if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);

    setIsTypingCommand(false);
    setCurrentPrompt('');
    currentCommandDataRef.current = null;
    scrollRefs.current.isAutoScrolling = false;
    resetScrollState();

    if (commands.length > 0) {
      currentCommandDataRef.current = {commandIdx: 0, commands, stepDatabase, formatPrompt};
      scrollToBottom(true);

      const addNextCommand = () => {
        if (!currentCommandDataRef.current) return;
        const {commandIdx, commands: cmdList, stepDatabase: db, formatPrompt: formatPromptFn} = currentCommandDataRef.current;
        if (commandIdx >= cmdList.length) return;

        const currentCommand = cmdList[commandIdx];
        const cmdDatabase = currentCommand.database ?? db;
        currentCommandDataRef.current = {commandIdx, commands: cmdList, stepDatabase: db, formatPrompt: formatPromptFn};
        addCommandToHistory(currentCommand, cmdDatabase, formatPromptFn);
      };

      typingTimeoutRef.current = setTimeout(addNextCommand, 0);
    } else {
      const idleDatabase = step.finalDatabase ?? stepDatabase;
      setCurrentPrompt(formatPrompt(idleDatabase));
    }

    return () => {
      if (typingTimeoutRef.current) clearTimeout(typingTimeoutRef.current);
    };
  }, [currentStep, formatPrompt, typeCommand, scrollToBottom, resetScrollState, addCommandToHistory]);

  const handleCommandButtonClick = useCallback((item: HistoryItem, index: number) => {
    const isCurrentStep = item.stepIndex === undefined || item.stepIndex === currentStep;
    if (!isCurrentStep || item.isExecuting || item.result || item.result === 'executed') return;

    scrollToBottom(true);
    setCommandHistory(prev => {
      const newHistory = [...prev];
      if (newHistory[index]) newHistory[index] = {...newHistory[index], isExecuting: true};
      return newHistory;
    });

    if (isTypingCommand && index === commandHistory.length - 1 && item.originalText) {
      scrollRefs.current.shouldScrollDuringTyping = true;
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
        typingTimeoutRef.current = null;
      }
      updateCommandInHistory(
        (cmd) => cmd.type === 'command' && cmd.prompt === item.prompt && cmd.originalText === item.originalText,
        (cmd) => ({...cmd, content: item.originalText!})
      );
      setIsTypingCommand(false);
    }
    executeCommand(item);
  }, [currentStep, isTypingCommand, commandHistory.length, updateCommandInHistory, scrollToBottom, executeCommand]);

  const processLineForDisplay = useCallback((line: string) => {
    const leadingSpaces = line.match(/^\s*/)?.[0] || '';
    const trimmedLine = line.trim();
    const endsWithDoubleBackslash = trimmedLine.endsWith('\\\\');
    const hasBackslash = endsWithDoubleBackslash || trimmedLine.endsWith('\\');
    const lineText = hasBackslash ? (endsWithDoubleBackslash ? trimmedLine.slice(0, -2).trim() : trimmedLine.slice(0, -1).trim()) : trimmedLine;
    return {leadingSpaces, lineText, hasBackslash};
  }, []);

  const toggleExpandedPayload = useCallback((logId: number) => {
    setExpandedPayloads(prev => {
      const newSet = new Set(prev);
      if (newSet.has(logId)) newSet.delete(logId);
      else newSet.add(logId);
      return newSet;
    });
  }, []);

  const renderCommandContent = useCallback((item: HistoryItem, index: number) => {
    if (!item.content) {
      return <div className="command-line-item"><span className="prompt">{item.prompt}{"> "}</span></div>;
    }

    const lines = item.content.split('\n').filter(line => line.trim() !== '');
    const isLastItem = index === commandHistory.length - 1;
    const shouldShowCursor = (isTypingCommand && isLastItem) || (isLastItem && !item.result);
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
      const {leadingSpaces, lineText, hasBackslash} = processLineForDisplay(line);
      const isLastLine = lineIdx === lines.length - 1;
      const lineWithCursor = lineText + (hasBackslash && !isLastLine ? ' \\' : '') + (shouldShowCursor && isLastLine ? cursor : '');

      return (
        <div key={lineIdx} className="command-line-item">
          {lineIdx === 0 ? (
            <>
              <span className="prompt">{item.prompt}{"> "}</span>
              <span className="command-text" dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
            </>
          ) : (
            <span className="command-text-indent" style={{paddingLeft: `${leadingSpaces.length * 0.5}ch`}} dangerouslySetInnerHTML={{__html: lineWithCursor}}></span>
          )}
        </div>
      );
    });
  }, [isTypingCommand, commandHistory.length, processLineForDisplay]);

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
          <div className="command-history">
            {commandHistory.map((item, index) => (
              <div key={index}>
                {item.type === 'title' && (
                  <div className="command-block active">
                    <div className="command-line">
                      <div className="command-line-inner">
                        <span className="step-title">{item.content}</span>
                      </div>
                    </div>
                  </div>
                )}
                {item.type === 'command' && (
                  <div className="command-block active">
                    <div className="command-line">
                      <div className="command-line-inner">
                        {item.result && item.result !== 'executed' ? (
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
                        {(!item.result || item.result === 'executed') && (
                          <button
                            className={`copy-command-btn ${item.isExecuting || item.result === 'executed' ? 'disabled' : ''} ${(item.stepIndex !== undefined && item.stepIndex !== currentStep) ? 'hidden-step-btn' : ''}`}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleCommandButtonClick(item, index);
                            }}
                            title="Copy command"
                            disabled={item.isExecuting || item.result === 'executed'}
                          >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                              <path d="M18 4V16Q14 16 6 16H4M4 16L8 12M4 16L8 20"/>
                            </svg>
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            ))}

            {currentPrompt && !isTypingCommand && (
              <div className="command-block active command-block-prompt">
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
                const hasExpandableContent = log.payload !== undefined || log.requestBody !== undefined;

                return (
                  <div key={log.id} className={`api-log-item ${!log.success ? 'api-log-item-error' : ''}`}>
                    <div
                      className={`api-log-header-line ${hasExpandableContent ? 'api-log-header-clickable' : ''}`}
                      onClick={() => hasExpandableContent && toggleExpandedPayload(log.id)}
                    >
                      <span className={`api-log-expand-icon ${!hasExpandableContent ? 'api-log-expand-icon-empty' : ''}`}>
                        {hasExpandableContent && (
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 0.2s ease'}}>
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
                        {log.requestBody !== undefined && (
                          <div className="api-log-request-body">
                            <div className="api-log-section-title">Request</div>
                            <pre>{typeof log.requestBody === 'string' ? log.requestBody : JSON.stringify(log.requestBody, null, 2)}</pre>
                          </div>
                        )}
                        {log.payload !== undefined && (
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
    </div>
  );
};

export default CliTerminal;
