import React, {createContext, ReactNode, useCallback, useContext, useEffect, useState} from 'react';
import {flushSync} from 'react-dom';
import {ToastContainer} from "../components/layout/Toast";

type ToastType = 'success' | 'error' | 'info' | 'warning';

interface ToastContextType {
  showToast: (message: string, type?: ToastType, duration?: number) => void;
}

const ToastContext = createContext<ToastContextType | null>(null);

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
};

interface Toast {
  id: string;
  message: string;
  type?: ToastType;
  duration?: number;
}

export const ToastProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const showToast = useCallback((message: string, type: ToastType = 'info', duration?: number) => {
    const id = Math.random().toString(36).substring(2, 9);
    flushSync(() => {
      const newToasts = [{id, message, type, duration}];
      setToasts(newToasts);
    });
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts(prev => prev.filter(toast => toast.id !== id));
  }, []);

  const removeAllToasts = useCallback(() => {
    setToasts([]);
  }, []);

  useEffect(() => {
    if (toasts.length === 0) {
      return;
    }

    const handleCloseToast = () => {
      removeAllToasts();
    };

    const handleClick = (event: MouseEvent) => {
      const target = event.target as HTMLElement;

      if (target.closest('.driver-popover')) {
        return;
      }

      removeAllToasts();
    };

    window.addEventListener('close-toast', handleCloseToast);
    document.addEventListener('click', handleClick, true);

    return () => {
      window.removeEventListener('close-toast', handleCloseToast);
      document.removeEventListener('click', handleClick, true);
    };
  }, [toasts.length, removeAllToasts]);

  return (
    <ToastContext.Provider value={{showToast}}>
      {children}
      <ToastContainer toasts={toasts} removeToast={removeToast}/>
    </ToastContext.Provider>
  );
};

