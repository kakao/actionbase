import {useEffect} from 'react';
import {useTour} from '../contexts/TourContext';
import {ACTIONS} from "react-joyride";

export function useNavigateStep(isLoading: boolean = false) {
  const {eventType, setStepIndex, setEventType} = useTour();

  useEffect(() => {
    if (!isLoading) {
      if (eventType === ACTIONS.NEXT) {
        setStepIndex(prev => prev + 1);
        setEventType(null);
      } else if (eventType === ACTIONS.PREV) {
        setStepIndex(prev => prev - 1);
        setEventType(null);
      }
    }
  }, [isLoading, eventType, setStepIndex, setEventType]);
}
