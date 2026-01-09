import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {driver, Driver} from "driver.js";
import "driver.js/dist/driver.css";
import {useNavigate} from "react-router-dom";

export const STEP = {
  NEXT: 'next',
  PREV: 'prev',
  CLOSE: 'close'
}

const BUTTON_TEXT = {
  PREV: "< prev",
  NEXT: "next >"
}

interface ButtonEvent {
  type: string | undefined;
}

interface StepEvent {
  to?: string;
  target?: string[];
}

const stepNextEvent = new Map<number, StepEvent>([
  [1, {target: ["[id='run-command-btn-2-active']"]}],
  [2, {target: ["[id='run-command-btn-3-active']"]}],
  [3, {to: '/search', target: ["[id='search-results-list']"]}],
  [5, {target: ["[id='run-command-btn-6-active']"]}],
  [6, {to: '/profile/merlin', target: ["[id='btn-profile-following']", "[id='run-command-btn-7-active']"]}],
  [7, {target: ["[id='btn-profile-following']"]}],
  [8, {target: ["[id='run-command-btn-9-active']"]}],
  [9, {target: ["[id='run-command-btn-10-active']"]}],
  [10, {target: ["[id='profile-followers']"]}],
  [11, {target: ["[id='run-command-btn-12-active']"]}],
  [12, {to: '/followers/merlin', target: ["[id='followers-list']"]}],
  [14, {to: '/post/1', target: ["[id='run-command-btn-15-active']"]}],
  [15, {target: ["[id='btn-likes']"]}],
  [16, {target: ["[id='run-command-btn-17-active']"]}],
  [18, {to: '/'}],
]);

const stepPrevEvent = new Map<number, StepEvent>([
  [3, {target: ["[id='run-command-btn-2-active']"]}],
  [4, {to: '/search', target: ["[id='cli-commands']", "[id='run-command-btn-3-active']"]}],
  [7, {to: '/search'}],
  [8, {target: ["[id='run-command-btn-7-active']"]}],
  [10, {target: ["[id='run-command-btn-9-active']"]}],
  [11, {target: ["[id='run-command-btn-10-active']"]}],
  [13, {to: '/profile/merlin', target: ["[id='run-command-btn-12-active']"]}],
  [15, {to: '/followers/merlin'}],
  [16, {target: ["[id='run-command-btn-15-active']"]}],
  [18, {target: ["[id='run-command-btn-17-active']"]}],
  [19, {to: '/post/1'}],
]);

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

  const [stepIndex, setStepIndex] = useState(0);
  const [buttonEvent, setButtonEvent] = useState<ButtonEvent | undefined>(undefined);

  const driverObj = useRef<Driver | null>(null);
  const setButtonEventRef = useRef(setButtonEvent);

  const onMoveAfter = useCallback(
    (type: string, stepEvents: Map<number, StepEvent>, eventType: string | undefined = undefined, timeout: number = 100) => {
      if (!(type === STEP.NEXT || type === STEP.PREV)) {
        console.error('Unsupported eventType:', type);
        return;
      }
      return async () => {
        setButtonEvent({type: type})

        if (driverObj.current) {
          const activeIndex = driverObj.current.getActiveIndex();
          if (!activeIndex) {
            console.error('Failed to get active index');
            return;
          }

          const stepEvent = stepEvents.get(activeIndex);
          if (!stepEvent) {
            console.error('Failed to get target stepEvent');
            return;
          }
          if (stepEvent.to) {
            navigate(stepEvent.to);
          }

          const indexToDrive = type === STEP.NEXT ? activeIndex + 1 : activeIndex - 1;
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
      const activeStep = driverObj.current.getActiveStep();

      if (activeStep?.popover?.onNextClick) {
        const element = activeStep.element
          ? (typeof activeStep.element === 'string'
            ? document.querySelector(activeStep.element) as HTMLElement
            : activeStep.element as HTMLElement)
          : null;
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

  useEffect(() => {
    setButtonEventRef.current = setButtonEvent;
  }, [setButtonEvent]);

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
        steps: [
          { // 0
            popover: {
              title: '<span class="driver-popover-title-number">1</span> Welcome to Actionbase 🙌🏼',
              side: 'over',
              align: 'center',
              nextBtnText: "start",
            },
          },
          { // 1
            popover: {
              title: '<span class="driver-popover-title-number">2</span> Prepare tutoral',
              side: 'right',
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            }
          },
          { // 2
            element: "[id='run-command-btn-2-active']",
            popover: {
              title: "Load preset data",
              description: "Let's load prepared data",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 3
            element: "[id='run-command-btn-3-active']",
            popover: {
              title: "Set context",
              description: "Set Database context as 'social'",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent)
            },
          },
          { // 4
            element: "[id='search-results-list']",
            popover: {
              title: '<span class="driver-popover-title-number">3</span> Check Prepared data',
              description: "(In Progress)",
              side: 'bottom',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render')
            },
          },
          { // 5
            popover: {
              title: '<span class="driver-popover-title-number">4</span> Follows',
              side: 'right',
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 6
            element: "[id='run-command-btn-6-active']",
            popover: {
              title: "Create table",
              description: "First, we have to create a table that represents the `user follow` relation. Click.",
              side: 'right',
              align: 'start',
              nextBtnText: 'done',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 7
            element: "[id='run-command-btn-7-active']",
            popover: {
              title: "me -> merlin",
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              nextBtnText: 'done',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'reload'),
            },
          },
          { // 8
            element: "[id='btn-profile-following']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 9
            element: "[id='run-command-btn-9-active']",
            popover: {
              title: "Get follows",
              description: "(In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 10
            element: "[id='run-command-btn-10-active']",
            popover: {
              title: "Get follows count",
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'reload'),
            },
          },
          { // 11
            element: "[id='profile-followers']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 12
            element: "[id='run-command-btn-12-active']",
            popover: {
              title: "Scan follows",
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent),
            },
          },
          { // 13
            element: "[id='followers-list']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
            },
          },
          { // 14
            popover: {
              title: '<span class="driver-popover-title-number">5</span> Likes',
              side: 'over',
              align: 'start',
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 15
            element: "[id='run-command-btn-15-active']",
            popover: {
              title: "me -> merlin's post",
              description: "Insert an Edge to add a like to this post. Click.",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'reload')
            },
          },
          { // 16
            element: "[id='btn-likes']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent, 'render')
            },
          },
          { // 17
            element: "[id='run-command-btn-17-active']",
            popover: {
              title: "Get likes",
              description: "(In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
            },
          },
          { // 18
            popover: {
              title: "Same as likes",
              description: "(In Progress)",
              side: 'over',
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent, 'render'),
              onNextClick: onMoveAfter(STEP.NEXT, stepNextEvent)
            },
          },
          { // 19
            element: "[class='mobile-frame']",
            popover: {
              title: '<span class="driver-popover-title-number">6</span> Feed',
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              onPrevClick: onMoveAfter(STEP.PREV, stepPrevEvent)
            },
          },
          { // 20
            popover: {
              title: '<span class="driver-popover-title-number">7</span> End 🎉',
              description: "The tutorial has ended. Thanks for joining the tour!",
              side: 'over',
              align: 'center',
            },
          },
          { // 21
            popover: {
              title: '<span class="driver-popover-title-number">8</span> Goodbye!',
              description: "(In Progress)",
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
        onDestroyStarted: () => {
          setStepIndex(0);
        },
        onDestroyed: () => {
          setStepIndex(0);
        },
        onCloseClick: () => {
          setButtonEventRef.current({type: STEP.CLOSE});

          if (driverObj.current) {
            driverObj.current.destroy();
          }
        },
        onPrevClick: () => {
          setButtonEventRef.current({type: STEP.PREV});

          if (driverObj.current) {
            const previousIndex = driverObj.current.getActiveIndex()! - 1
            setStepIndex(previousIndex)
            driverObj.current.moveTo(previousIndex);
          }
        },
        onNextClick: () => {
          setButtonEventRef.current({type: STEP.NEXT});

          if (driverObj.current) {
            const nextIndex = driverObj.current.getActiveIndex()! + 1
            setStepIndex(nextIndex)
            driverObj.current.moveTo(nextIndex);
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
