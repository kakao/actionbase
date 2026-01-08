import React, {useLayoutEffect, useState} from 'react';
import '../../styles/toast.css';

interface ToastProps {
  message: string;
  duration?: number;
  onClose: () => void;
}

export const Toast: React.FC<ToastProps> = ({message, duration = 500, onClose}) => {
  const [isClosing, setIsClosing] = useState(false);
  const toastRef = React.useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (toastRef.current) {
      toastRef.current.style.opacity = '1';
    }
  }, []);

  React.useEffect(() => {
    const timer = setTimeout(() => {
      setIsClosing(true);
      setTimeout(onClose, 100);
    }, duration);

    return () => clearTimeout(timer);
  }, [duration, onClose]);

  return (
    <div
      ref={toastRef}
      className={`toast ${isClosing ? 'toast-closing' : ''}`}
      style={{
        opacity: isClosing ? undefined : 1,
        transform: isClosing ? undefined : 'scale(1)',
        transition: isClosing ? undefined : 'none'
      }}
    >
      <div className="toast-content">
        <span className="toast-message">{message}</span>
      </div>
    </div>
  );
};

interface ToastContainerProps {
  toasts: Array<{ id: string; message: string }>;
  removeToast: (id: string) => void;
}

export const ToastContainer: React.FC<ToastContainerProps> = ({toasts, removeToast}) => {
  return (
    <div className="toast-container">
      {toasts.map(toast => (
        <Toast
          key={toast.id}
          message={toast.message}
          onClose={() => removeToast(toast.id)}
        />
      ))}
    </div>
  );
};

