import {useEffect, DependencyList, useLayoutEffect} from 'react';
import {TUTORIAL_STEP_KEY, useDriver} from './DriverContext';

export const useStepAutoAdvance = (steps: number[], deps?: DependencyList) => {
  const {moveNextStep} = useDriver();

  useEffect(() => {
    const checkAndAdvance = () => {
      const stepString = typeof window !== 'undefined'
        ? window.localStorage.getItem(TUTORIAL_STEP_KEY)
        : null;
      const currentStep = stepString ? parseInt(stepString, 10) : 0;

      if (steps.includes(currentStep)) {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            moveNextStep();
          });
        });
      }
    };

    // Small delay to ensure React has finished rendering
    const timer = setTimeout(checkAndAdvance, 100);

    return () => clearTimeout(timer);
  }, [steps.join(','), moveNextStep, ...(deps || [])]);
};
