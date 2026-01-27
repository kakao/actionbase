import React, { createContext, ReactNode, useCallback, useContext, useRef, useState } from 'react';
import { getCommandCategory, getProxiedApiPattern, CommandCategory } from '../utils/command';

type ApiType = CommandCategory;

interface ApiLog {
  id: number;
  method: string;
  url: string;
  timestamp: Date;
  success: boolean;
  status?: number;
  payload?: any;
  requestBody?: any;
  apiType: ApiType;
  proxiedTo?: string;
  latencyMs?: number;
}

interface ApiLogContextProps {
  apiLogs: ApiLog[];
  addApiLog: (
    method: string,
    url: string,
    success: boolean,
    status?: number,
    payload?: any,
    requestBody?: any,
    latencyMs?: number
  ) => void;
  clearApiLogs: () => void;
}

const ApiLogContext = createContext<ApiLogContextProps | undefined>(undefined);

export const ApiLogProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [apiLogs, setApiLogs] = useState<ApiLog[]>([]);
  const logIdCounterRef = useRef(0);

  const getApiType = (method: string, url: string, requestBody?: any): ApiType => {
    // CLI command
    if (url.includes('/api/command') && requestBody?.command) {
      return getCommandCategory(requestBody.command);
    }
    // REST API
    if (url.includes('/graph/')) {
      // DDL: create database/table
      if (url.includes('/graph/v2/service')) return 'DDL';
      // Query operations
      if (url.includes('/edges/get')) return 'GET';
      if (url.includes('/edges/scan')) return 'SCAN';
      if (url.includes('/edges/count')) return 'COUNT';
      // DML: mutation (POST to /edges without get/scan/count)
      if (url.includes('/edges') && method === 'POST' && requestBody?.mutations) return 'DML';
    }
    return 'ETC';
  };

  const addApiLog = useCallback(
    (
      method: string,
      url: string,
      success: boolean,
      status?: number,
      payload?: any,
      requestBody?: any,
      latencyMs?: number
    ) => {
      const isCliCommand = url.includes('/api/command') && requestBody?.command;
      const displayUrl = isCliCommand ? `actionbase> ${requestBody.command}` : url;
      const proxiedTo = isCliCommand
        ? (getProxiedApiPattern(requestBody.command) ?? undefined)
        : undefined;

      setApiLogs((prev) => [
        ...prev,
        {
          id: logIdCounterRef.current++,
          method,
          url: displayUrl,
          timestamp: new Date(),
          success,
          status,
          payload,
          requestBody,
          apiType: getApiType(method, url, requestBody),
          proxiedTo,
          latencyMs,
        },
      ]);
    },
    []
  );

  const clearApiLogs = useCallback(() => {
    setApiLogs([]);
  }, []);

  return (
    <ApiLogContext.Provider value={{ apiLogs, addApiLog, clearApiLogs }}>
      {children}
    </ApiLogContext.Provider>
  );
};

export const useApiLog = () => {
  const context = useContext(ApiLogContext);
  if (!context) throw new Error('useApiLog must be used within ApiLogProvider');
  return context;
};
