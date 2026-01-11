import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {driver, Driver} from "driver.js";
import "driver.js/dist/driver.css";
import {useNavigate} from "react-router-dom";
import {useToast} from "./ToastContext";
import {DESCRIPTION, TITLE} from "../constants/breadCrumbSteps";
import {ButtonEvent, StepEvent, stepNextEvent, stepPrevEvent, stepVerifiers} from "../constants/driverSteps";

export const STEP = {
  NEXT: 'next',
  PREV: 'prev',
  CLOSE: 'close',
  RELOAD: 'reload'
}

const BUTTON_TEXT = {
  PREV: "< prev",
  NEXT: "next >"
}

const TOAST_DURATION = 1700

interface DriverContextType {
  stepIndex: number;
  setStepIndex: React.Dispatch<React.SetStateAction<number>>;
  moveNext: () => void;
  buttonEvent: ButtonEvent | undefined,
}

const DriverContext = createContext<DriverContextType | null>(null);

const waitForElement = (selector: string[], timeout = 3000): Promise<void> => {
  return new Promise<void>((resolve, reject) => {
    const selectors = Array.isArray(selector) ? selector : [selector];

    const checkSelectors = () => {
      return selectors.some(sel => document.querySelector(sel) !== null);
    };

    if (checkSelectors()) {
      resolve();
      return;
    }

    const observer = new MutationObserver(() => {
      if (checkSelectors()) {
        observer.disconnect();
        resolve();
      }
    });
    observer.observe(document.body, {childList: true, subtree: true});
    setTimeout(() => {
      observer.disconnect();
      reject();
    }, timeout);
  });
};

export const useDriver = () => {
  const context = useContext(DriverContext);
  if (!context) {
    throw new Error("useDriver must be used within DriverProvider");
  }
  return context;
};

export const DriverProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const navigate = useNavigate();
  const {showToast} = useToast();

  const [stepIndex, setStepIndex] = useState(0);
  const [buttonEvent, setButtonEvent] = useState<ButtonEvent | undefined>(undefined);

  const driverObj = useRef<Driver | null>(null);
  const setButtonEventRef = useRef(setButtonEvent);
  const showToastRef = useRef(showToast);

  const onMoveAfter = useCallback(
    (type: string, stepEvents: Map<number, StepEvent>, eventType: string | undefined = undefined, stepIndex: number | undefined = undefined, timeout: number = 100) => {
      if (!(type === STEP.NEXT || type === STEP.PREV || type === STEP.RELOAD)) {
        console.error('Unsupported eventType:', type);
        return;
      }

      return async () => {
        if (driverObj.current) {

          let currentIndex = stepIndex;
          if (!currentIndex) {
            currentIndex = driverObj.current.getActiveIndex();
            if (!currentIndex) {
              console.error('Failed to get active index');
              return;
            }
          }

          if (type === STEP.NEXT) {
            if (!await isStepValid(currentIndex)) {
              showToastRef.current("Please complete the current step before proceeding.", TOAST_DURATION);
              return;
            }
          }

          setButtonEvent({type: type})

          const stepEvent = stepEvents.get(currentIndex);
          if (!stepEvent) {
            console.error('Failed to get target stepEvent');
            return;
          }

          if (stepEvent.to) {
            navigate(stepEvent.to);
          }

          const indexToDrive = type === STEP.NEXT ? currentIndex + 1 : currentIndex - 1;
          if (eventType) {
            window.dispatchEvent(new CustomEvent(eventType, {detail: {nextIndex: indexToDrive}}));
          }

          if (stepEvent.target) {
            try {
              await waitForElement(stepEvent.target);
              await new Promise(r => setTimeout(r, timeout));
            } catch (error) {
              console.error('Failed to find target elements');
            }
          }

          driverObj.current.drive(indexToDrive);
          setStepIndex(indexToDrive)
        }
      }
    }, [navigate, buttonEvent, setStepIndex]);

  const moveNext = useCallback(async () => {
    setButtonEvent({type: STEP.NEXT})

    if (driverObj.current) {
      const activeIndex = driverObj.current.getActiveIndex()
      if (activeIndex !== undefined) {
        if (!await isStepValid(activeIndex)) {
          showToastRef.current("Please complete the current step before proceeding.", TOAST_DURATION)
          const movePrev = onMoveAfter(STEP.RELOAD, stepPrevEvent, 'render', activeIndex + 1);
          if (movePrev) {
            await movePrev();
          }
          return;
        }
      }

      const activeStep = driverObj.current.getActiveStep();
      if (activeStep?.popover?.onNextClick) {
        const element = activeStep.element as HTMLElement
        if (element) {
          activeStep.popover.onNextClick(element, activeStep,
            {
              config: driverObj.current.getConfig(),
              state: driverObj.current.getState(),
              driver: driverObj.current
            });
        }
      }
    }
  }, [buttonEvent]);

  const isStepValid = async (stepIndex: number) => {
    const stepVerifier = stepVerifiers.get(stepIndex)
    if (!stepVerifier) {
      return true;
    }

    try {
      return await stepVerifier()
    } catch (err) {
      return false;
    }
  }

  useEffect(() => {
    setButtonEventRef.current = setButtonEvent;
    showToastRef.current = showToast;
  }, [setButtonEvent, showToast]);

  useEffect(() => {
    if (!driverObj.current) {
      driverObj.current = driver({
        disableActiveInteraction: true,
        showProgress: false,
        showButtons: ['next', 'previous', 'close'],
        allowClose: true,
        overlayColor: 'rgba(0, 0, 0, 0.4)',
        prevBtnText: BUTTON_TEXT.PREV,
        nextBtnText: BUTTON_TEXT.NEXT,
        doneBtnText: 'Bye 👋🏻',
        overlayClickBehavior: () => {
          window.dispatchEvent(new CustomEvent('close-toast'));
        },
        steps: [
          {
            popover: {
              title: `<span class="driver-popover-title-number">1</span> ${TITLE.STEP_0}`,
              description: DESCRIPTION.STEP_0,
              side: 'over',
              align: 'center',
              nextBtnText: "start",
            },
          },
          {
            popover: {
              title: `<span class="driver-popover-title-number">2</span> ${TITLE.STEP_1}`,
              description: DESCRIPTION.STEP_1,
              side: 'right',
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            }
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_2,
              description: DESCRIPTION.STEP_2,
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_3,
              description: DESCRIPTION.STEP_3,
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent)
            },
          },
          {
            element: "[id='search-results-list']",
            popover: {
              title: `<span class="driver-popover-title-number">3</span> ${TITLE.STEP_4}`,
              description: DESCRIPTION.STEP_4,
              side: 'bottom',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render')
            },
          },
          {
            popover: {
              title: `<span class="driver-popover-title-number">4</span> ${TITLE.STEP_5}`,
              description: DESCRIPTION.STEP_5,
              side: 'right',
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_6,
              description: DESCRIPTION.STEP_6,
              side: 'right',
              align: 'start',
              nextBtnText: 'done',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_7,
              description: DESCRIPTION.STEP_7,
              side: 'right',
              align: 'start',
              nextBtnText: 'done',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'reload'),
            },
          },
          {
            element: "[id='btn-profile-following']",
            popover: {
              description: DESCRIPTION.STEP_8,
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_9,
              description: DESCRIPTION.STEP_9,
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_10,
              description: DESCRIPTION.STEP_10,
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'reload'),
            },
          },
          {
            element: "[id='profile-followers']",
            popover: {
              description: DESCRIPTION.STEP_11,
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_12,
              description: DESCRIPTION.STEP_12,
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent),
            },
          },
          {
            element: "[id='followers-list']",
            popover: {
              description: DESCRIPTION.STEP_13,
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
            },
          },
          {
            popover: {
              title: `<span class="driver-popover-title-number">5</span> ${TITLE.STEP_14}`,
              description: DESCRIPTION.STEP_14,
              side: 'over',
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_15,
              description: DESCRIPTION.STEP_15,
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'reload')
            },
          },
          {
            element: "[id='btn-likes']",
            popover: {
              description: DESCRIPTION.STEP_16,
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          {
            element: "[id='run-command-btn']",
            popover: {
              title: TITLE.STEP_17,
              description: DESCRIPTION.STEP_17,
              side: 'right',
              nextBtnText: "done",
              align: 'start',
            },
          },
          {
            popover: {
              title: TITLE.STEP_18,
              description: DESCRIPTION.STEP_18,
              side: 'over',
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent)
            },
          },
          {
            element: "[class='mobile-frame']",
            popover: {
              title: `<span class="driver-popover-title-number">6</span> ${TITLE.STEP_19}`,
              description: DESCRIPTION.STEP_19,
              side: 'right',
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent)
            },
          },
          {
            popover: {
              title: `<span class="driver-popover-title-number">7</span> ${TITLE.STEP_20}`,
              description: DESCRIPTION.STEP_20,
              side: 'over',
              align: 'center',
            },
          },
          {
            popover: {
              title: `<span class="driver-popover-title-number">8</span> ${TITLE.STEP_21}`,
              description: DESCRIPTION.STEP_21,
              side: 'over',
              align: 'center',
              nextBtnText: 'Bye 👋🏻',
              onNextClick: () => {
                if (driverObj.current) {
                  driverObj.current.destroy();
                }
              }
            },
          },
        ],
        onPopoverRender: () => {
          setTimeout(() => {
            setButtonEventRef.current({type: undefined});
          }, 0);
        },
        onCloseClick: () => {
          setButtonEventRef.current({type: STEP.CLOSE});

          if (driverObj.current) {
            driverObj.current.destroy();
            setStepIndex(0);
          }
        },
        onPrevClick: () => {
          setButtonEventRef.current({type: STEP.PREV});

          if (driverObj.current) {
            const stepIndex = driverObj.current.getActiveIndex();
            if (stepIndex !== undefined) {
              setStepIndex(stepIndex - 1)
              driverObj.current.moveTo(stepIndex - 1);
            }
          }
        },
        onNextClick: async () => {
          if (driverObj.current) {
            const stepIndex = driverObj.current.getActiveIndex();
            if (stepIndex !== undefined) {
              if (!await isStepValid(stepIndex)) {
                showToastRef.current("Please complete the current step before proceeding.", TOAST_DURATION);
                return;
              }

              const event = {type: STEP.NEXT, isClicked: true};
              setButtonEventRef.current(event);

              setStepIndex(stepIndex + 1)
              driverObj.current.moveTo(stepIndex + 1);
            }
          }
        },
      });
    }

    const timer = setTimeout(() => {
      if (driverObj.current) {
        driverObj.current.drive();
      }
    }, 100);

    return () => {
      clearTimeout(timer);
    };
  }, []);

  const contextValue = useMemo(() => {
    return {
      stepIndex,
      setStepIndex,
      moveNext,
      buttonEvent,
    };
  }, [stepIndex, setStepIndex, moveNext, buttonEvent]);

  return (
    <DriverContext.Provider value={contextValue}>
      {children}
    </DriverContext.Provider>
  );
};
