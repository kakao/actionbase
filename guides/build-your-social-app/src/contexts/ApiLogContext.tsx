import React, {createContext, ReactNode, useCallback, useContext, useRef, useState} from 'react';
import {getCommandCategory, CommandCategory} from '../utils/command';

type ApiType = CommandCategory | 'API';

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
}

interface ApiLogContextProps {
  apiLogs: ApiLog[];
  addApiLog: (method: string, url: string, success: boolean, status?: number, payload?: any, requestBody?: any) => void;
  clearApiLogs: () => void;
}

const ApiLogContext = createContext<ApiLogContextProps | undefined>(undefined);

export const ApiLogProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const [apiLogs, setApiLogs] = useState<ApiLog[]>([]);
  const logIdCounterRef = useRef(0);

  const getApiType = (url: string, requestBody?: any): ApiType => {
    if (url.includes('/api/command') && requestBody?.command) {
      return getCommandCategory(requestBody.command);
    }
    if (url.includes('/graph/')) {
      if (url.includes('/mutate')) return 'DML';
      if (url.includes('/get')) return 'GET';
      if (url.includes('/scan')) return 'SCAN';
      if (url.includes('/count')) return 'COUNT';
    }
    return 'API';
  };

  const addApiLog = useCallback((method: string, url: string, success: boolean, status?: number, payload?: any, requestBody?: any) => {
    setApiLogs(prev => [...prev, {
      id: logIdCounterRef.current++,
      method,
      url,
      timestamp: new Date(),
      success,
      status,
      payload,
      requestBody,
      apiType: getApiType(url, requestBody),
    }]);
  }, []);

  const clearApiLogs = useCallback(() => {
    setApiLogs([]);
  }, []);

  return (
    <ApiLogContext.Provider value={{apiLogs, addApiLog, clearApiLogs}}>
      {children}
    </ApiLogContext.Provider>
  );
};

export const useApiLog = () => {
  const context = useContext(ApiLogContext);
  if (!context) throw new Error('useApiLog must be used within ApiLogProvider');
  return context;
};

