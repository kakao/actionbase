import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState} from "react";
import Joyride, {ACTIONS, Callback, EVENTS, STATUS} from "react-joyride";
import {useLocation, useNavigate} from "react-router-dom";
import {steps} from "../constants/HandsOnSteps";

const prevBtnText = "< prev"
const nextBtnText = "next >"

interface NavigationEvent {
  url: string;
}

const navigationNextEvent = new Map<number, NavigationEvent>([
  [3, {url: '/search'}],
  [5, {url: '/profile/merlin'}],
  [7, {url: '/search'}],
  [8, {url: '/profile/emeth'}],
  [10, {url: '/search'}],
  [11, {url: '/profile/merlin'}],
  [12, {url: '/post/1'}],
  [15, {url: '/profile/doki'}],
  [17, {url: '/followings/doki'}],
  [19, {url: '/'}],
]);

const navigationPrevEvent = new Map<number, NavigationEvent>([
  [6, {url: '/search'}],
  [8, {url: '/profile/merlin'}],
  [9, {url: '/search'}],
  [12, {url: '/search'}],
  [13, {url: '/profile/merlin'}],
  [16, {url: '/profile/doki'}],
  [18, {url: '/profile/doki'}],
  [20, {url: '/followings/doki'}],
]);

const refreshEvent = new Set([6, 9, 13])

interface TourContextType {
  stepIndex: number;
  setStepIndex: React.Dispatch<React.SetStateAction<number>>;
  eventType: string | null;
  setEventType: React.Dispatch<React.SetStateAction<string | null>>;
}

const TourContext = createContext<TourContextType | null>(null);

export const useTour = () => {
  const context = useContext(TourContext);
  if (!context) {
    throw new Error("useTour must be used within TourProvider");
  }
  return context;
};

export const TourProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const location = useLocation();
  const navigate = useNavigate();

  const [run, setRun] = useState<boolean>(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [eventType, setEventType] = useState<string | null>(null);

  const handleJoyrideCallback: Callback = useCallback((data) => {
    const {status, action, index, type} = data;

    if (status === STATUS.FINISHED ||
      status === STATUS.SKIPPED ||
      action === ACTIONS.CLOSE ||
      (type === EVENTS.STEP_AFTER && index === data.size - 1)) {
      setRun(false);
      setStepIndex(0);
      window.dispatchEvent(new CustomEvent('tourStepChange', {detail: {stepIndex: 0}}));
      return;
    }

    if (type === EVENTS.STEP_AFTER) {
      const pathname = location.pathname

      if (action === ACTIONS.NEXT) {
        const nextIndex = index + 1;

        if (refreshEvent.has(index)) {
          window.dispatchEvent(new CustomEvent('tourStepRefresh', {detail: {stepIndex: nextIndex}}));
        }

        const event = navigationNextEvent.get(index)
        if (navigationNextEvent.has(index) && event !== undefined) {
          if (pathname !== event.url) {
            setEventType(ACTIONS.NEXT);
            window.dispatchEvent(new CustomEvent('tourStepChange', {detail: {stepIndex: nextIndex}}));
            navigate(event.url);
            return;
          }
        }

        if (nextIndex < steps.length) {
          setStepIndex(nextIndex);
          window.dispatchEvent(new CustomEvent('tourStepChange', {detail: {stepIndex: nextIndex}}));
        }
      } else if (action === ACTIONS.PREV) {
        const prevIndex = index - 1;

        const event = navigationPrevEvent.get(index)
        if (navigationPrevEvent.has(index) && event !== undefined) {
          if (pathname !== event.url) {
            setEventType(ACTIONS.PREV);
            window.dispatchEvent(new CustomEvent('tourStepChange', {detail: {stepIndex: prevIndex}}));
            navigate(event.url);
            return;
          }
        }

        if (prevIndex >= 0) {
          setStepIndex(prevIndex);
          window.dispatchEvent(new CustomEvent('tourStepChange', {detail: {stepIndex: prevIndex}}));
        }
      }
    }
  }, [navigate, stepIndex]);

  useEffect(() => {
    setRun(true);
    setStepIndex(0);
    window.dispatchEvent(new CustomEvent('tourStepChange', {detail: {stepIndex: 0}}));
  }, []);

  const contextValue = useMemo(() => ({
    stepIndex,
    setStepIndex,
    eventType,
    setEventType
  }), [stepIndex, eventType]);

  return (
    <TourContext.Provider value={contextValue}>
      <Joyride
        run={run}
        steps={steps}
        stepIndex={stepIndex}
        callback={handleJoyrideCallback}
        continuous={true}
        disableOverlayClose={true}
        styles={{
          overlay: {
            backgroundColor: 'rgba(0, 0, 0, 0.3)',
          },
          buttonNext: {
            backgroundColor: '#3c53f7',
            fontSize: '14px',
            fontWeight: 'bold',
            borderRadius: '6px'
          },
          buttonBack: {
            color: '#8e8e8e',
            border: '1px solid #8e8e8e',
            fontSize: '14px',
            fontWeight: 'bold',
            borderRadius: '6px'
          }
        }}
        locale={{
          back: prevBtnText,
          close: 'Close',
          last: 'Bye 👋🏻',
          next: nextBtnText,
          skip: 'Skip',
        }}
      />
      {children}
    </TourContext.Provider>
  );
};
