import React, {createContext, ReactNode, useCallback, useContext, useRef, useState} from 'react';

interface ApiLog {
  id: number;
  method: string;
  url: string;
  timestamp: Date;
  success: boolean;
  status?: number;
  payload?: any;
  requestBody?: any;
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

