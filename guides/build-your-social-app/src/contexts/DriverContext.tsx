import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from "react";
import {driver, Driver} from "driver.js";
import "driver.js/dist/driver.css";
import {useNavigate} from "react-router-dom";

const STEP = {
  NEXT: 'next',
  PREV: 'prev'
}
const prevBtnText = "< prev"
const nextBtnText = "next >"

interface NavigationEvent {
  url: string;
  target?: string[];
}

const navigationNextEvent = new Map<number, NavigationEvent>([
  [3, {url: '/search', target: ["[id='search-results-list']"]}],
  [6, {url: '/profile/merlin', target: ["[id='btn-profile-following']"]}],
  [12, {url: '/followers/merlin', target: ["[id='followers-list']"]}],
  [14, {url: '/post/1'}],
  [18, {url: '/'}],
]);

const navigationPrevEvent = new Map<number, NavigationEvent>([
  [4, {url: '/search', target: ["[id='cli-commands']"]}],
  [7, {url: '/search'}],
  [13, {url: '/profile/merlin'}],
  [15, {url: '/followers/merlin'}],
  [19, {url: '/post/1'}],
]);

interface DriverContextType {
  stepIndex: number;
  setStepIndex: React.Dispatch<React.SetStateAction<number>>;
  isCallbackExecuted: boolean;
  runCommandExecutedCallback: () => void;
  moveNext: () => void;
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
  const [isCallbackExecuted, setCallbackExecuted] = useState<boolean>(false);
  const driverObj = useRef<Driver | null>(null);

  const moveAfterRendering = (type: string, selector: string) => {
    return async () => {
      if (driverObj.current) {
        const activeIndex = driverObj.current.getActiveIndex();
        if (activeIndex == undefined) {
          console.error('Failed to get active index');
          return;
        }

        const indexToDrive = type === STEP.NEXT ? activeIndex + 1 : activeIndex - 1;

        const navigationEvent = type === STEP.NEXT ? navigationNextEvent.get(activeIndex) : navigationPrevEvent.get(activeIndex);
        if (navigationEvent) {
          navigate(navigationEvent.url);
        }

        window.dispatchEvent(new CustomEvent('render', {detail: {nextIndex: indexToDrive}}));
        setCallbackExecuted(true);

        let targetSelectors
        if (navigationEvent && navigationEvent.target) {
          targetSelectors = [...navigationEvent.target, selector];
        } else {
          targetSelectors = [selector]
        }

        try {
          await waitForElement(targetSelectors);
          await new Promise(r => setTimeout(r, 100));
          driverObj.current.drive(indexToDrive);
          setStepIndex(indexToDrive)
        } catch (error) {
          console.error('Failed to find target element');
        }
      }
    }
  }

  const handleRunCommandExecuted = useCallback(moveAfterRendering(STEP.NEXT, `[id="run-command-btn-${stepIndex + 1}-active"]`), [stepIndex]);

  const moveNext = useCallback(() => {
    if (driverObj.current) {
      driverObj.current.moveNext();
    }
  }, []);

  const moveStepAfterNavigate = (type: string) => {
    return async () => {
      setCallbackExecuted(false);

      if (driverObj.current) {
        const activeIndex = driverObj.current.getActiveIndex();
        if (!activeIndex) {
          console.error('Failed to get active index');
          return;
        }

        const navigationEvent = type === STEP.NEXT ? navigationNextEvent.get(activeIndex) : navigationPrevEvent.get(activeIndex);
        if (!navigationEvent) {
          console.error('Failed to get event to navigate');
          return;
        }

        navigate(navigationEvent.url);

        try {
          if (navigationEvent.target) {
            await waitForElement(navigationEvent.target);
            await new Promise(r => setTimeout(r, 100));
          }

          const indexToDrive = type === STEP.NEXT ? activeIndex + 1 : activeIndex - 1;
          driverObj.current.drive(indexToDrive)
          setStepIndex(indexToDrive)
        } catch (error) {
          console.error('Failed to find target element');
        }
      }
    };
  };

  useEffect(() => {
    if (!driverObj.current) {
      driverObj.current = driver({
        disableActiveInteraction: true,
        showProgress: false,
        showButtons: ['next', 'previous', 'close'],
        allowClose: true,
        overlayColor: 'rgba(0, 0, 0, 0.7)',
        prevBtnText: prevBtnText,
        nextBtnText: nextBtnText,
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
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-2-active"]`)
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
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-3-active"]`)
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
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-2-active']"),
              onNextClick: moveStepAfterNavigate(STEP.NEXT)
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
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-3-active']"),
            },
          },
          { // 5
            popover: {
              title: '<span class="driver-popover-title-number">4</span> Follows',
              side: 'right',
              align: 'start',
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-6-active"]`)
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
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-7-active"]`)
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
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-6-active']"),
            },
          },
          { // 8
            element: "[id='btn-profile-following']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-7-active']"),
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-9-active"]`)
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
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-10-active"]`)
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
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-9-active']"),
            },
          },
          { // 11
            element: "[id='profile-followers']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-10-active']"),
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-12-active"]`)
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
              onNextClick: moveStepAfterNavigate(STEP.NEXT)
            },
          },
          { // 13
            element: "[id='followers-list']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-12-active']"),
            },
          },
          { // 14
            popover: {
              title: '<span class="driver-popover-title-number">5</span> Likes',
              side: 'over',
              align: 'start',
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-15-active"]`)
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
              onPrevClick: moveStepAfterNavigate(STEP.PREV)
            },
          },
          { // 16
            element: "[id='btn-likes']",
            popover: {
              description: "Yeah 🎉. (In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-15-active']"),
              onNextClick: moveAfterRendering(STEP.NEXT, `[id="run-command-btn-17-active"]`)
            },
          },
          { // 17
            element: "[id='run-command-btn-17-active']",
            popover: {
              title: "Get follows",
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
              onPrevClick: moveAfterRendering(STEP.PREV, "[id='run-command-btn-17-active']"),
              onNextClick: moveStepAfterNavigate(STEP.NEXT)
            },
          },
          { // 19
            element: "[class='mobile-frame']",
            popover: {
              title: '<span class="driver-popover-title-number">6</span> Feed',
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              onPrevClick: moveStepAfterNavigate(STEP.PREV)
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
                  setCallbackExecuted(false);
                  driverObj.current.destroy();
                }
              }
            },
          },
        ],
        onDestroyStarted: () => {
          setCallbackExecuted(false);
          setStepIndex(0);
        },
        onDestroyed: () => {
          setCallbackExecuted(false);
          setStepIndex(0);
        },
        onCloseClick: () => {
          if (driverObj.current) {
            driverObj.current.destroy();
          }
        },
        onPrevClick: () => {
          if (driverObj.current) {
            setCallbackExecuted(false);
            const previousIndex = driverObj.current.getActiveIndex()! - 1
            setStepIndex(previousIndex)
            driverObj.current.moveTo(previousIndex);
          }
        },
        onNextClick: () => {
          if (driverObj.current) {
            setCallbackExecuted(false);
            const nextIndex = driverObj.current.getActiveIndex()! + 1
            setStepIndex(nextIndex)
            driverObj.current.moveTo(nextIndex);
          }
        }
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

  const contextValue = useMemo(() => ({
    stepIndex,
    setStepIndex,
    isCallbackExecuted,
    runCommandExecutedCallback: handleRunCommandExecuted,
    moveNext
  }), [stepIndex, handleRunCommandExecuted, moveNext]);

  return (
    <DriverContext.Provider value={contextValue}>
      {children}
    </DriverContext.Provider>
  );
};
