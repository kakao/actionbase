import React, {createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState} from "react";
import {driver} from "driver.js";
import "driver.js/dist/driver.css";

export enum ButtonType {
  Next = 'next',
  Prev = 'prev',
}

interface DriverContextProps {
  currentStep: number;
  setCurrentStep: (step: number) => void;
  buttonType: ButtonType | null;
  setButtonType: (type: ButtonType | null) => void;
  navigationUrl: string | null;
  setNavigationUrl: (url: string | null) => void;
  refresh: boolean;
  setRefresh: (refresh: boolean) => void;
  moveNextStep: () => void;
  movePrevStep: () => void;
  registerCallback: (name: string, callback: () => void) => void;
  executeCallback: (name: string) => void;
}

const prevBtnText = "< prev"
const nextBtnText = "next >"

const DriverContext = createContext<DriverContextProps | undefined>(undefined);

export const DriverProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const driverRef = useRef<any>(null);
  const callbacksRef = useRef<Record<string, () => void>>({});
  const stepsRef = useRef<any[]>([]);
  const [currentStep, setCurrentStep] = useState<number>(0);
  const [buttonType, setButtonType] = useState<ButtonType | null>(null);
  const [navigationUrl, setNavigationUrl] = useState<string | null>(null);
  const [refresh, setRefresh] = useState<boolean>(false);
  const isMovingRef = useRef(false);
  const savedStepRef = useRef<number>(0);

  const moveStep = useCallback((delta: number) => {
    if (!driverRef.current || isMovingRef.current) return;
    isMovingRef.current = true;

    setCurrentStep(prev => {
      const newIndex = prev + delta;
      if (newIndex < 0) {
        isMovingRef.current = false;
        return prev;
      }

      if (!stepsRef.current.length) {
        isMovingRef.current = false;
        return newIndex;
      }

      const elementSelector = stepsRef.current[newIndex]?.element;
      const prevElementSelector = stepsRef.current[prev]?.element;
      const isSameElement = elementSelector && elementSelector === prevElementSelector;
      const maxAttempts = 50
      let driveCalled = false;

      const drive = () => {
        if (driveCalled || !driverRef.current) return;
        driveCalled = true;
        driverRef.current.drive(newIndex);
        isMovingRef.current = false;
      };

      const scheduleDrive = (frames = 2) => {
        let count = 0;
        const run = () => {
          if (++count >= frames) drive();
          else requestAnimationFrame(run);
        };
        requestAnimationFrame(run);
      };

      if (!elementSelector) {
        scheduleDrive(2);
        return newIndex;
      }

      const element = document.querySelector(elementSelector);
      if (element) {
        scheduleDrive(isSameElement ? 3 : 2);
        return newIndex;
      }

      let attempts = 0;
      const attemptDrive = () => {
        if (document.querySelector(elementSelector) || attempts >= maxAttempts) {
          scheduleDrive(2);
        } else {
          attempts++;
          requestAnimationFrame(attemptDrive);
        }
      };
      requestAnimationFrame(attemptDrive);
      return newIndex;
    });
  }, []);

  const moveNextStep = useCallback(() => moveStep(1), [moveStep]);
  const movePrevStep = useCallback(() => moveStep(-1), [moveStep]);

  const initDriver = () => {
    const steps = [
        {
          popover: {
            title: "Welcome 🙌🏼 !",
            description: "Let's start building a Social Media App using Actionbase.",
            nextBtnText: "start",
            showButtons: ['next', 'close']
          }
        },
        {
          element: "[id='cli-commands']",
          popover: {
            title: "Prepare for tutorial",
            description: "You have to create Storage and Database. Please run the commands shown on the left.",
            nextBtnText: "done",
            showButtons: ['next'],
            side: 'right',
          }
        },
        {
          element: "[id='cli-commands']",
          popover: {
            title: "Load user_posts data",
            description: "Let's load user_posts and user_likes data.",
            nextBtnText: "done",
            showButtons: ['next', 'previous'],
            side: 'right',
          }
        },
        {
          element: "[id='nav-btn-search']",
          popover: {
            description: "Let's see how many users there are.",
            showButtons: ['previous', 'next'],
            side: 'left',
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/search')
            },
          },
        },
        {
          element: "[id='search-results-list']",
          popover: {
            description: "There are 8 users we can follow. Let's follow merlin.",
            showButtons: ['next', 'previous'],
            side: 'bottom',
          }
        },
        {
          element: "[id='cli-commands']",
          popover: {
            title: "Create user_follows table",
            description: "First, we have to create a table that represents the user follow relation. Please run the commands.",
            nextBtnText: 'done',
            showButtons: ['next', 'previous'],
            side: 'right',
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/profile/merlin')
            },
          }
        },
        {
          element: "[id='btn-profile-following']",
          popover: {
            title: "Follow merlin",
            description: "Insert an Edge to follow merlin. Click the 'Done' button when you're done.",
            nextBtnText: 'done',
            showButtons: ['next', 'previous'],
            side: 'bottom',
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/search')
            },
            onNextClick: () => {
              handleRefresh()
            },
          }
        },
        {
          element: "[id='btn-profile-following']",
          popover: {
            title: "Yeah 🎉",
            description: "Now you can see that you are following merlin.",
            showButtons: ['next', 'previous'],
            side: 'bottom',
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/search')
            },
          }
        },
        {
          element: "[id='searched_user_0']",
          popover: {
            description: "Also, let's follow emeth!",
            showButtons: ['next', 'previous'],
            nextBtnText: "go",
            side: 'bottom',
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/profile/merlin')
            },
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/profile/emeth')
            },
          }
        },
        {
          element: "[id='btn-profile-following']",
          popover: {
            title: "Follow emeth",
            description: "Insert Edges to follow emeth. Click the 'done' button when you're done.",
            nextBtnText: 'done',
            showButtons: ['next', 'previous'],
            side: 'bottom',
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/search')
            },
            onNextClick: () => {
              handleRefresh()
            },
          }
        },
        {
          element: "[id='btn-profile-following']",
          popover: {
            title: "Yeah 🎉",
            description: "You can see we're following emeth.",
            showButtons: ['next', 'previous'],
            side: 'bottom',
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/search')
            },
          }
        },
        {
          element: "[id='searched_user_1']",
          popover: {
            description: "Let's take a look at merlin's home.",
            showButtons: ['next', 'previous'],
            nextBtnText: "go",
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/profile/emeth')
            },
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/profile/merlin')
            },
            side: 'bottom'
          }
        },
        {
          element: "[id='profile-post-0']",
          popover: {
            description: "Let's see this post.",
            showButtons: ['previous', 'next'],
            side: 'right',
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/search')
            },
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/post/1')
            },
          }
        },
        {
          element: "[id='post-detail-actions']",
          popover: {
            title: "Add a like",
            description: "Insert an Edge to add a like to this post. Click the 'done' button when you're done.",
            showButtons: ['next', 'previous'],
            nextBtnText: "done",
            side: 'bottom',
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/profile/merlin')
            },
            onNextClick: () => {
              handleRefresh()
            },
          }
        },
        {
          element: "[id='post-detail-actions']",
          popover: {
            title: "Yeah 🎉",
            description: "You can see the like data has changed.",
            showButtons: ['next', 'previous'],
            side: 'bottom',
            onNextClick: () => {
              handleRefresh()
            },
          }
        },
        {
          element: "[id='nav-btn-profile']",
          popover: {
            description: "And then, let's go to your home!",
            showButtons: ['previous', 'next'],
            side: 'left',
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/profile/doki')
            },
          }
        },
        {
          element: "[id='profile-stats-bottom']",
          popover: {
            title: "See counts of status",
            side: 'bottom',
            description: "You can see these values are the same as the results.",
            showButtons: ['next', 'previous'],
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/post/1')
            },
          }
        },
        {
          element: "[id='profile-follows']",
          popover: {
            description: "Let's see whom you follow.",
            showButtons: ['next', 'previous'],
            nextBtnText: 'go',
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/followings/doki')
            },
            side: 'bottom'
          }
        },
        {
          element: "[id='followers-list']",
          popover: {
            title: "See followers list",
            description: "You can see these values are the same as the results.",
            showButtons: ['next', 'previous'],
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/profile/doki')
            },
            side: 'top'
          }
        },
        {
          element: "[id='nav-btn-feed']",
          popover: {
            description: "Let's see the feed.",
            showButtons: ['previous', 'next'],
            side: 'top',
            onPrevClick: () => {
              navigateAndMove(ButtonType.Prev, '/followings/doki')
            },
            onNextClick: () => {
              navigateAndMove(ButtonType.Next, '/')
            },
          }
        },
        {
          element: "[id='feed-post']",
          popover: {
            title: "See following's posts",
            description: "You can see your following's posts!",
            showButtons: ['next', 'previous'],
            side: 'top'
          }
        },
        {
          popover: {
            title: "End",
            description: "The tutorial has ended. Thanks for joining the tour!",
            showButtons: ['next'],
            nextBtnText: 'Bye 👋🏻',
            onNextClick: () => {
              setCurrentStep(0);
              driverRef.current.destroy();
            },
          }
        },
      ] as any;
    stepsRef.current = steps;
    driverRef.current = driver({
      stagePadding: 10,
      showProgress: true,
      overlayOpacity: 0.3,
      allowKeyboardControl: true,
      allowClose: false,
      onPrevClick: () => window.dispatchEvent(new CustomEvent('buttonTypeChange', {detail: {type: ButtonType.Prev}})),
      onNextClick: () => window.dispatchEvent(new CustomEvent('buttonTypeChange', {detail: {type: ButtonType.Next}})),
      steps,
      onDestroyed: () => setCurrentStep(savedStepRef.current),
      onCloseClick: () => {
        setCurrentStep(0);
        driverRef.current?.destroy();
      },
      prevBtnText: prevBtnText,
      nextBtnText: nextBtnText,
    });
  }

  const registerCallback = useCallback((name: string, callback: () => void) => {
    callbacksRef.current[name] = callback;
  }, []);

  const executeCallback = useCallback((name: string) => {
    callbacksRef.current[name]?.();
  }, []);

  const navigateAndMove = useCallback((type: ButtonType, url: string) => {
    setRefresh(false);
    setButtonType(type);
    setNavigationUrl(url);
    window.dispatchEvent(new CustomEvent('buttonTypeChange', {detail: {type, url}}));
  }, [setRefresh, setButtonType, setNavigationUrl]);

  const handleRefresh = useCallback(() => {
    setButtonType(null);
    setNavigationUrl(null);
    setRefresh(true);
    window.dispatchEvent(new CustomEvent('buttonTypeChange', {detail: {refresh: true}}));
  }, [setButtonType, setNavigationUrl, setRefresh]);

  useEffect(() => {
    savedStepRef.current = currentStep;
  }, [currentStep]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (!driverRef.current) initDriver();
      driverRef.current.drive(0);
    }, 200);

    const isOverlayElement = (target: HTMLElement) =>
      target?.classList?.contains('driver-overlay') ||
      target?.classList?.contains('driver-overlay-item') ||
      !!target?.closest('.driver-overlay');

    const handleOverlayEvent = (e: Event) => {
      const target = e.target as HTMLElement;
      if (isOverlayElement(target)) {
        e.preventDefault();
        e.stopPropagation();
        e.stopImmediatePropagation();
        return false;
      }
    };

    const eventTypes = ['click', 'mousedown', 'touchstart'];
    eventTypes.forEach(type => document.addEventListener(type, handleOverlayEvent, true));

    return () => {
      clearTimeout(timer);
      eventTypes.forEach(type => document.removeEventListener(type, handleOverlayEvent, true));
    };
  }, []);

  return (
    <DriverContext.Provider value={{
      currentStep,
      setCurrentStep,
      buttonType,
      setButtonType,
      navigationUrl,
      setNavigationUrl,
      refresh,
      setRefresh,
      moveNextStep,
      movePrevStep,
      registerCallback,
      executeCallback
    }}>
      {children}
    </DriverContext.Provider>
  );
};

export const useDriver = () => {
  const context = useContext(DriverContext);
  if (!context) throw new Error("useDriver must be used within DriverProvider");
  return context;
};
