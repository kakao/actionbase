import React, {useCallback, useEffect, useRef, useState} from 'react';
import {runCommand} from "../../api/cli";
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

const escapeHTML = (text: string) => text.replace(/</g, '&lt;').replace(/>/g, '&gt;');

const CliTerminal: React.FC = () => {
  const [currentStep, setCurrentStep] = useState<number>(0);
  const [currentPrompt, setCurrentPrompt] = useState('');
  const [commandHistory, setCommandHistory] = useState<HistoryItem[]>([]);

  const clickedButtonsRef = useRef<Set<number>>(new Set());
  const currentCommandDataRef = useRef<{ commandIdx: number, commands: any[], stepDatabase: string | undefined, formatPrompt: (db?: string) => string } | null>(null);
  const terminalBodyRef = useRef<HTMLDivElement>(null);
  const commandHistoryRef = useRef<HTMLDivElement>(null);

  const formatPrompt = useCallback((database?: string) => database ? `${PROMPT_PREFIX}(${database})` : PROMPT_PREFIX, []);

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

  const addCommandToHistory = useCallback((command: any, cmdDatabase: string, formatPromptFn: (db?: string) => string) => {
    const prompt = formatPromptFn(cmdDatabase);
    setCommandHistory(prev => [...prev, {type: 'command', prompt, content: command.text, originalText: command.text, stepIndex: currentStep}]);
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

    let result: string;
    try {
      const response = await runCommand({command: processedRequest});
      result = createResultMessage(response);
    } catch (err: any) {
      result = createErrorMessage(err);
      console.error('Failed to execute command:', err);
    }
    setCommandHistory(prev => [...prev, {type: 'command', prompt: '', content: '', result, stepIndex: currentStep}]);

    const itemIndex = commandHistory.findIndex(cmd =>
      cmd.type === 'command' &&
      (cmd.originalText === textToProcess || cmd.content === textToProcess)
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
  }, [processCommandText, normalizeCommandForRequest, processCommandRequest, createResultMessage, createErrorMessage, currentStep, proceedToNextCommand]);

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
      const idleDatabase = step.finalDatabase ?? stepDatabase;
      setCurrentPrompt(formatPrompt(idleDatabase));
    }
  }, [currentStep, formatPrompt, addCommandToHistory]);

  const handleCommandRun = useCallback((item: HistoryItem, index: number) => {
    const isCurrentStep = item.stepIndex === undefined || item.stepIndex === currentStep;
    if (!isCurrentStep || item.result || item.result === 'executed') return;

    const textToProcess = item.originalText || item.content || '';
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

  const processLineForDisplay = useCallback((line: string) => {
    const leadingSpaces = line.match(/^\s*/)?.[0] || '';
    const trimmedLine = line.trim();
    const endsWithDoubleBackslash = trimmedLine.endsWith('\\\\');
    const hasBackslash = endsWithDoubleBackslash || trimmedLine.endsWith('\\');
    const lineText = hasBackslash ? (endsWithDoubleBackslash ? trimmedLine.slice(0, -2).trim() : trimmedLine.slice(0, -1).trim()) : trimmedLine;
    return {leadingSpaces, lineText, hasBackslash};
  }, []);

  const renderCommandContent = useCallback((item: HistoryItem, index: number) => {
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
  }, [commandHistory.length, processLineForDisplay]);

  return (
    <div className="terminal-body-container" id="cli-commands">
      <div className="terminal-body terminal-body-top" ref={terminalBodyRef}>
        <div className="command-history" ref={commandHistoryRef}>
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
                      {(!item.result || item.result === 'executed') && (() => {
                        const commandText = item.originalText || item.content || '';
                        const isClicked = clickedButtonsRef.current.has(index);
                        return (
                          <button
                            className={`run-command-btn ${item.result === 'executed' || isClicked ? 'disabled' : ''} ${(item.stepIndex !== undefined && item.stepIndex !== currentStep) ? 'hidden-step-btn' : ''}`}
                            onClick={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                              if (isClicked || item.result === 'executed') return;
                              handleCommandRun(item, index);
                            }}
                            title="Copy command"
                            disabled={item.result === 'executed' || isClicked}
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
        </div>
      </div>
    </div>
  );
};

export default CliTerminal;
