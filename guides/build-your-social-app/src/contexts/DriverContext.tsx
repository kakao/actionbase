import React, {createContext, ReactNode, useContext, useEffect, useRef, useState} from "react";
import {driver, Driver} from "driver.js";
import "driver.js/dist/driver.css";
import {useNavigate} from "react-router-dom";

const prevBtnText = "< prev"
const nextBtnText = "next >"

interface NavigationEvent {
  url: string;
  target?: string;
}

const navigationNextEvent = new Map<number, NavigationEvent>([
  [3, {url: '/search', target: "[id='search-results-list']"}],
  [5, {url: '/profile/merlin', target: "[id='btn-profile-following']"}],
  [11, {url: '/post/1', target: "[id='post-detail-actions']"}],
  [14, {url: '/', target: ""}],
]);

const navigationPrevEvent = new Map<number, NavigationEvent>([
  [4, {url: '/search', target: "[id='cli-commands']"}],
  [6, {url: '/search'}],
  [12, {url: '/profile/merlin'}],
  [15, {url: '/post/1'}],
]);

const waitForElement = (selector: any, timeout = 3000) => {
  return new Promise((resolve, reject) => {
    if (document.querySelector(selector)) {
      return resolve();
    }
    const observer = new MutationObserver(() => {
      if (document.querySelector(selector)) {
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

interface DriverContextType {
  stepIndex: number;
  setStepIndex: React.Dispatch<React.SetStateAction<number>>;
}

const DriverContext = createContext<DriverContextType | null>(null);

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
  const driverObj = useRef<Driver | null>(null);

  const navigateAndMoveNext = () => {
    return async () => {
      const activeIndex = driverObj.current!.getActiveIndex();
      if (activeIndex == undefined) {
        console.error('Failed to get active index');
        return;
      }

      const navigationEvent = navigationNextEvent.get(activeIndex);
      if (navigationEvent == undefined) {
        console.error('Failed to get event to navigate');
        return;
      }

      navigate(navigationEvent.url);

      try {
        if (navigationEvent.target) {
          await waitForElement(navigationEvent.target);
          await new Promise(r => setTimeout(r, 100));
        }
        driverObj.current!.moveNext();
        setStepIndex(activeIndex + 1)
      } catch (error) {
        console.error('Failed to find target element');
      }
    };
  };

  const navigateAndMovePrevious = () => {
    return async () => {
      const activeIndex = driverObj.current!.getActiveIndex();
      if (!activeIndex) {
        console.error('Failed to get active index');
        return;
      }

      const navigationEvent = navigationPrevEvent.get(activeIndex);
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
        driverObj.current!.movePrevious();
        setStepIndex(activeIndex - 1)
      } catch (error) {
        console.error('Failed to find target element');
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
        overlayColor: 'rgba(0, 0, 0, 0.3)',
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
            },
          },
          { // 2
            element: "[id='cli-commands']",
            popover: {
              title: "Load preset data",
              description: "Let's load prepared data",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
            },
          },
          { // 3
            element: "[id='cli-commands']",
            popover: {
              title: "Set context",
              description: "Set Database context as 'social'",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onNextClick: navigateAndMoveNext()
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
              onPrevClick: navigateAndMovePrevious()
            },
          },
          { // 5
            popover: {
              title: '<span class="driver-popover-title-number">4</span>Follows',
              side: 'right',
              align: 'start',
              onNextClick: navigateAndMoveNext()
            },
          },
          { // 6
            element: "[id='cli-commands']",
            popover: {
              title: "Create table",
              description: "First, we have to create a table that represents the `user follow` relation. Click.",
              side: 'right',
              align: 'start',
              nextBtnText: 'done',
              onPrevClick: navigateAndMovePrevious()
            },
          },
          { // 7
            element: "[id='cli-commands']",
            popover: {
              title: "me -> merlin",
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              nextBtnText: 'done',
            },
          },
          { // 8
            element: "[id='cli-commands']",
            popover: {
              title: "Get follows",
              description: "(In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
            },
          },
          { // 9
            element: "[id='cli-commands']",
            popover: {
              title: "Get follows count",
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
            },
          },
          { // 10
            element: "[id='cli-commands']",
            popover: {
              title: "Scan follows",
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
            },
          },
          { // 11
            popover: {
              title: '<span class="driver-popover-title-number">5</span> Likes',
              side: 'over',
              align: 'start',
              onNextClick: navigateAndMoveNext()
            },
          },
          { // 12
            element: "[id='cli-commands']",
            popover: {
              title: "me -> merlin's post",
              description: "Insert an Edge to add a like to this post. Click.",
              side: 'right',
              align: 'start',
              nextBtnText: "done",
              onPrevClick: navigateAndMovePrevious()
            },
          },
          { // 13
            element: "[id='cli-commands']",
            popover: {
              title: "Get follows",
              description: "(In Progress)",
              side: 'right',
              nextBtnText: "done",
              align: 'start',
            },
          },
          { // 14
            popover: {
              title: "Same as follows",
              description: "(In Progress)",
              side: 'over',
              align: 'start',
              onNextClick: navigateAndMoveNext()
            },
          },
          { // 15
            element: "[class='mobile-frame']",
            popover: {
              title: '<span class="driver-popover-title-number">6</span> Feed',
              description: "(In Progress)",
              side: 'right',
              align: 'start',
              onPrevClick: navigateAndMovePrevious()
            },
          },
          { // 16
            popover: {
              title: '<span class="driver-popover-title-number">7</span> End 🎉',
              description: "The tutorial has ended. Thanks for joining the tour!",
              side: 'over',
              align: 'center',
            },
          },
          { // 16
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
        onDestroyStarted: () => {
          setStepIndex(0);
        },
        onDestroyed: () => {
          setStepIndex(0);
        },
        onCloseClick: () => {
          if (driverObj.current) {
            driverObj.current.destroy();
          }
        },
        onPrevClick: () => {
          if (driverObj.current) {
            const previousIndex = driverObj.current.getActiveIndex()! - 1
            setStepIndex(previousIndex)
            driverObj.current.moveTo(previousIndex);
          }
        },
        onNextClick: () => {
          if (driverObj.current) {
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

  return (
    <DriverContext.Provider value={{stepIndex, setStepIndex}}>
      {children}
    </DriverContext.Provider>
  );
};
