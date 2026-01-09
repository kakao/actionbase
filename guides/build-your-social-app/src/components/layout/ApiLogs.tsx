import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useApiLog} from '../../contexts/ApiLogContext';
import {setApiLogCallback} from '../../api/client';
import '../../styles/api-log.css';

const ApiLogs: React.FC = () => {
  const [expandedPayloads, setExpandedPayloads] = useState<Set<number>>(new Set());
  const {apiLogs, addApiLog} = useApiLog();
  const apiLogContentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setApiLogCallback(addApiLog);
  }, [addApiLog]);

  useEffect(() => {
    if (apiLogContentRef.current && apiLogs.length > 0) {
      apiLogContentRef.current.scrollTop = apiLogContentRef.current.scrollHeight;
    }
  }, [apiLogs]);

  const toggleExpandedPayload = useCallback((logId: number) => {
    setExpandedPayloads(prev => {
      const newSet = new Set(prev);
      if (newSet.has(logId)) newSet.delete(logId);
      else newSet.add(logId);
      return newSet;
    });
  }, []);

  return (
    <div className="api-log-container">
      <div className="api-log-content" ref={apiLogContentRef}>
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
                  <span>[{log.id + 1}]</span>
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
      </div>
    </div>
  );
};

export default ApiLogs
