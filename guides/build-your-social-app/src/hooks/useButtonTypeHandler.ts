import {DependencyList, useCallback, useEffect, useRef} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {ButtonType, useDriver} from '../contexts/DriverContext'

interface UseButtonTypeHandlerOptions {
  isLoading: boolean;
  dependencies?: DependencyList;
  setRefreshTrigger?: (updater: (prev: number) => number) => void;
}

const STEP_RESTRICTIONS: Record<string, (path: string) => boolean> = {
  "4": (path) => path.startsWith("/profile"),
  "8": (path) => path !== "/profile/emeth",
  "11": (path) => path !== "/profile/merlin",
  "15": (path) => path !== "/profile/doki",
  "16": (path) => path === "/followings/doki" || path === "/followers/doki",
  "17": (path) => path !== "/followings/doki",
  "18": (path) => path.startsWith("/profile"),
  "20": () => true
};

export const useNavigateAndNext = () => {
  const {currentStep, setRefresh, setButtonType, setNavigationUrl} = useDriver();

  return useCallback((path: string) => {
    if (STEP_RESTRICTIONS[String(currentStep)]?.(path)) return;

    setRefresh(false);
    setButtonType(ButtonType.Next);
    setNavigationUrl(path);
    window.dispatchEvent(new CustomEvent("buttonTypeChange", {detail: {type: ButtonType.Next, url: path}}));
  }, [currentStep, setRefresh, setButtonType, setNavigationUrl]);
};

export const useButtonTypeHandler = ({isLoading, dependencies = [], setRefreshTrigger}: UseButtonTypeHandlerOptions) => {
  const navigate = useNavigate();
  const location = useLocation();
  const {buttonType, setButtonType, navigationUrl, setNavigationUrl, refresh, setRefresh, moveNextStep, movePrevStep} = useDriver();
  const isProcessingRef = useRef(false);
  const pendingNavigationRef = useRef<{url: string, type: ButtonType} | null>(null);

  useEffect(() => {
    if (isLoading || !buttonType || isProcessingRef.current) return;

    isProcessingRef.current = true;
    const currentButtonType = buttonType;
    setButtonType(null);

    if (!refresh) {
      (currentButtonType === ButtonType.Next ? moveNextStep : movePrevStep)();
    }
    isProcessingRef.current = false;
  }, [isLoading, refresh, buttonType, moveNextStep, movePrevStep, setButtonType]);

  useEffect(() => {
    const handleButtonTypeChange = (event: CustomEvent) => {
      if (isProcessingRef.current) return;

      const {refresh: isRefresh, type, url: eventUrl} = event.detail || {};
      const url = eventUrl || navigationUrl;

      if (isRefresh) {
        setRefresh(false);
        setRefreshTrigger?.(prev => prev + 1);
        return;
      }

      if (!type || (type !== ButtonType.Next && type !== ButtonType.Prev)) return;
      const buttonType = type as ButtonType;
      if (eventUrl) setNavigationUrl(eventUrl);

      if (url && location.pathname !== url) {
        pendingNavigationRef.current = {url, type: buttonType};
        setNavigationUrl(url);
        navigate(url);
      } else {
        setButtonType(buttonType);
      }
    };

    window.addEventListener('buttonTypeChange', handleButtonTypeChange as EventListener);
    return () => window.removeEventListener('buttonTypeChange', handleButtonTypeChange as EventListener);
  }, [navigate, location.pathname, navigationUrl, setButtonType, setNavigationUrl, setRefresh, setRefreshTrigger]);

  useEffect(() => {
    if (!pendingNavigationRef.current || location.pathname !== pendingNavigationRef.current.url) return;

    const {type, url} = pendingNavigationRef.current;
    pendingNavigationRef.current = null;
    setNavigationUrl(null);

    const getSelector = (url: string) => {
      if (url.includes('/profile/')) return '[id="btn-profile-following"]';
      if (url.includes('/post/')) return '[id="post-detail-actions"]';
      if (url.startsWith('/followers/')) return '[id="followers-list"]';
      if (url.startsWith('/followings/')) return '[id="followers-list"]';
      if (url === '/search') return '[id="searched_user_0"]';
      if (url === '/') return '[id="nav-btn-feed"]';
      return null;
    };
    const selector = getSelector(url);

    if (!selector) {
      setButtonType(type);
      return;
    }

    const element = document.querySelector(selector);
    if (element) {
      setButtonType(type);
      return;
    }

    let attempts = 0;
    const maxAttempts = selector === '[id="followers-list"]' ? 50 : selector === '[id="btn-profile-following"]' ? 50 : 20;
    const checkElement = () => {
      const found = document.querySelector(selector);
      if (found || attempts >= maxAttempts) {
        setButtonType(type);
      } else {
        attempts++;
        requestAnimationFrame(checkElement);
      }
    };
    requestAnimationFrame(checkElement);
  }, [location.pathname, setButtonType, setNavigationUrl]);

  return {buttonType, setButtonType};
};

