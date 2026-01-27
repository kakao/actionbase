import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useApiLog} from '../../contexts/ApiLogContext';
import {setApiLogCallback} from '../../api/client';
import '../../styles/api-log.css';

type TabType = 'request' | 'response';

const ApiLogs: React.FC = () => {
  const [expandedPayloads, setExpandedPayloads] = useState<Set<number>>(new Set());
  const [activeTabs, setActiveTabs] = useState<Map<number, TabType>>(new Map());
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

  const toggleExpandedPayload = useCallback((logId: number, hasRequest: boolean) => {
    setExpandedPayloads(prev => {
      const newSet = new Set(prev);
      if (newSet.has(logId)) {
        newSet.delete(logId);
      } else {
        newSet.add(logId);
        setActiveTabs(prevTabs => {
          const newTabs = new Map(prevTabs);
          if (!newTabs.has(logId)) {
            newTabs.set(logId, hasRequest ? 'request' : 'response');
          }
          return newTabs;
        });
      }
      return newSet;
    });
  }, []);

  const switchTab = useCallback((logId: number, tab: TabType) => {
    setActiveTabs(prev => {
      const newTabs = new Map(prev);
      newTabs.set(logId, tab);
      return newTabs;
    });
  }, []);

  const getStatusClass = (status?: number) => {
    if (!status) return '';
    if (status >= 200 && status < 300) return 'status-success';
    if (status >= 400) return 'status-error';
    return 'status-warning';
  };

  const ACTION_GAP_MS = 500;
  const shouldShowSeparator = (currentIndex: number) => {
    if (currentIndex === 0) return false;
    const current = apiLogs[currentIndex];
    const prev = apiLogs[currentIndex - 1];
    return current.timestamp.getTime() - prev.timestamp.getTime() > ACTION_GAP_MS;
  };

  return (
    <div className="api-log-container">
      <div className="api-log-toolbar">
        <div className="api-log-toolbar-left">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>API</span>
          {apiLogs.length > 0 && <span className="api-log-count">{apiLogs.length} requests</span>}
        </div>
      </div>
      <div className="api-log-table-header">
        <div className="api-log-col-num">#</div>
        <div className="api-log-col-type">Type</div>
        <div className="api-log-col-name">URL</div>
        <div className="api-log-col-method">Method</div>
        <div className="api-log-col-status">Status</div>
        <div className="api-log-col-latency">Time</div>
      </div>
      <div className="api-log-content" ref={apiLogContentRef}>
        {apiLogs.length === 0 ? (
          <div className="api-log-empty">Recording network activity...</div>
        ) : (
          apiLogs.map((log, index) => {
            const isExpanded = expandedPayloads.has(log.id);
            const hasRequest = log.requestBody !== undefined;
            const hasResponse = log.payload !== undefined;
            const hasExpandableContent = hasRequest || hasResponse;
            const activeTab = activeTabs.get(log.id) || (hasRequest ? 'request' : 'response');
            const showSeparator = shouldShowSeparator(index);

            return (
              <React.Fragment key={log.id}>
                {showSeparator && <div className="api-log-separator" />}
                <div className={`api-log-row ${isExpanded ? 'expanded' : ''} ${!log.success ? 'error' : ''}`}>
                <div
                  className={`api-log-row-main ${hasExpandableContent ? 'clickable' : ''}`}
                  onClick={() => hasExpandableContent && toggleExpandedPayload(log.id, hasRequest)}
                >
                  <div className="api-log-col-num">
                    <span className="api-log-num">{log.id + 1}</span>
                  </div>
                  <div className="api-log-col-type">
                    <span className={`api-log-type api-log-type-${log.apiType.toLowerCase()}`}>{log.apiType}</span>
                  </div>
                  <div className="api-log-col-name">
                    <span className={`api-log-expand-icon ${!hasExpandableContent ? 'hidden' : ''}`}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)'}}>
                        <path d="M9 18l6-6-6-6"/>
                      </svg>
                    </span>
                    <div className="api-log-url-container">
                      <span className="api-log-url" title={log.url}>{log.url}</span>
                      {log.proxiedTo && (
                        <span className="api-log-proxied" title={`Proxied to: ${log.proxiedTo}`}>
                          <span className="api-log-proxied-arrow">-&gt;</span>
                          <span className="api-log-proxied-url">{log.proxiedTo}</span>
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="api-log-col-method">
                    <span className={`api-log-method api-log-method-${log.method.toLowerCase()}`}>{log.method}</span>
                  </div>
                  <div className="api-log-col-status">
                    <span className={`api-log-status ${getStatusClass(log.status)}`}>
                      {log.status || '—'}
                    </span>
                  </div>
                  <div className="api-log-col-latency">
                    <span className={`api-log-latency ${log.latencyMs !== undefined && log.latencyMs < 100 ? 'fast' : ''}`}>
                      {log.latencyMs !== undefined ? `${log.latencyMs}ms` : '—'}
                    </span>
                  </div>
                </div>
                {hasExpandableContent && isExpanded && (
                  <div className="api-log-details">
                    <div className="api-log-details-tabs">
                      {hasRequest && (
                        <span
                          className={`tab ${activeTab === 'request' ? 'active' : ''}`}
                          onClick={() => switchTab(log.id, 'request')}
                        >
                          Request
                        </span>
                      )}
                      {hasResponse && (
                        <span
                          className={`tab ${activeTab === 'response' ? 'active' : ''}`}
                          onClick={() => switchTab(log.id, 'response')}
                        >
                          Response
                        </span>
                      )}
                    </div>
                    <div className="api-log-details-content">
                      {activeTab === 'request' && hasRequest && (
                        <div className="api-log-section">
                          <pre>{typeof log.requestBody === 'string' ? log.requestBody : JSON.stringify(log.requestBody, null, 2)}</pre>
                        </div>
                      )}
                      {activeTab === 'response' && hasResponse && (
                        <div className="api-log-section">
                          <pre>{JSON.stringify(log.payload, null, 2)}</pre>
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
              </React.Fragment>
            );
          })
        )}
      </div>
    </div>
  );
};

export default ApiLogs
