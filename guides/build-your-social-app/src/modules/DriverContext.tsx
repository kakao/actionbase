import React, {createContext, ReactNode, useContext, useEffect, useRef} from "react";
import {driver} from "driver.js";
import {useNavigate} from "react-router-dom";

import "driver.js/dist/driver.css";

interface DriverContextProps {
  moveNextStep: () => void;
  registerCallback: (name: string, callback: () => void) => void;
  executeCallback: (name: string) => void;
}

const prevBtnText = "< prev"
const nextBtnText = "next >"

const DriverContext = createContext<DriverContextProps | undefined>(undefined);

export const TUTORIAL_STEP_KEY = 'current-tutorial-step';
export const TUTORIAL_FIRST_STEP = '0';

export const DriverProvider: React.FC<{ children: ReactNode }> = ({children}) => {
  const navigate = useNavigate();

  const driverRef = useRef<any>(null);
  const callbacksRef = useRef<Record<string, () => void>>({});

  const initDriver = () => {
    driverRef.current = driver({
      stagePadding: 10,
      showProgress: true,
      overlayOpacity: 0.3,
      steps: [
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
            side: 'right'
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
            prevBtnText: prevBtnText
          }
        },
        {
          element: "[id='nav-btn-search']",
          popover: {
            description: "Let's see how many users there are.",
            showButtons: ['previous'],
            side: 'left',
            prevBtnText: prevBtnText
          },
        },
        {
          element: "[id='search-results-list']",
          popover: {
            description: "There are 8 users we can follow. Let's follow merlin.",
            showButtons: ['next', 'previous'],
            side: 'bottom',
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText
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
            prevBtnText: prevBtnText,
            onNextClick: () => {
              navigate("/profile/merlin");
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
            prevBtnText: prevBtnText,
            onNextClick: () => {
              window.location.reload()
              moveNextStep()
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
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText,
            onNextClick: () => {
              navigate("/search");
            }
          }
        },
        {
          element: "[id='searched_user_0']",
          popover: {
            description: "Also, let's follow emeth!",
            showButtons: ['next', 'previous'],
            nextBtnText: "go",
            side: 'bottom',
            prevBtnText: prevBtnText,
            onNextClick: () => {
              navigate("/profile/emeth");
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
            prevBtnText: prevBtnText,
            onNextClick: () => {
              window.location.reload()
              moveNextStep()
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
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText,
            onNextClick: () => {
              navigate("/search");
            }
          }
        },
        {
          element: "[id='searched_user_1']",
          popover: {
            description: "Let's take a look at merlin's home.",
            showButtons: ['next', 'previous'],
            nextBtnText: "go",
            prevBtnText: prevBtnText,
            onNextClick: () => {
              navigate("/profile/merlin");
            },
            side: 'bottom'
          }
        },
        {
          element: "[id='profile-post-0']",
          popover: {
            description: "Let's see this post.",
            showButtons: ['previous'],
            prevBtnText: prevBtnText,
            side: 'right',
          }
        },
        {
          element: "[id='post-detail-actions']",
          popover: {
            title: "Add a like",
            description: "Insert an Edge to add a like to this post. Click the 'done' button when you're done.",
            showButtons: ['next'],
            nextBtnText: "done",
            side: 'bottom',
            prevBtnText: prevBtnText,
            onNextClick: () => {
              window.location.reload()
              moveNextStep()
            },
          }
        },
        {
          element: "[id='post-detail-actions']",
          popover: {
            title: "Yeah 🎉",
            description: "You can see the like data has changed.",
            showButtons: ['next', 'previous'],
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText,
            side: 'bottom',
          }
        },
        {
          element: "[id='nav-btn-profile']",
          popover: {
            description: "And then, let's go to your home!",
            showButtons: ['previous'],
            prevBtnText: prevBtnText,
            side: 'left'
          }
        },
        {
          element: "[id='profile-stats-bottom']",
          popover: {
            title: "See counts of status",
            side: 'bottom',
            description: "You can see these values are the same as the results.",
            showButtons: ['next', 'previous'],
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText
          }
        },
        {
          element: "[id='profile-follows']",
          popover: {
            description: "Let's see whom you follow.",
            showButtons: ['next'],
            nextBtnText: 'go',
            prevBtnText: prevBtnText,
            onNextClick: () => {
              navigate("/followings/doki");
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
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText,
            side: 'top'
          }
        },
        {
          element: "[id='nav-btn-feed']",
          popover: {
            description: "Let's see the feed.",
            showButtons: ['previous'],
            prevBtnText: prevBtnText,
            side: 'top'
          }
        },
        {
          element: "[id='feed-post']",
          popover: {
            title: "See merlin's posts",
            description: "You can see your merlin's posts!",
            showButtons: ['next', 'previous'],
            prevBtnText: prevBtnText,
            nextBtnText: nextBtnText,
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
              driverRef.current.moveNext();
              driverRef.current.destroy();
            }
          }
        },
      ],
      onDestroyStarted: () => {
        localStorage.removeItem(TUTORIAL_STEP_KEY);
      },
      onDestroyed: () => {
        localStorage.removeItem(TUTORIAL_STEP_KEY);
      },
      onCloseClick: () => {
        localStorage.removeItem(TUTORIAL_STEP_KEY);
        driverRef.current.destroy();
      },
      onHighlightStarted: (element) => {
        const currentIndex = driverRef.current.getActiveIndex();
        if (currentIndex !== undefined) {
          localStorage.setItem(TUTORIAL_STEP_KEY, String(currentIndex));
        }
      }
    });
  }

  const starTour = () => {
    if (!driverRef.current) initDriver()
    const savedStep = localStorage.getItem(TUTORIAL_STEP_KEY);
    const startStep = savedStep ? parseInt(savedStep, 10) : 0;
    driverRef.current.drive(startStep)
  }

  const moveNextStep = () => {
    if (!driverRef.current) return;

    const currentIndex = driverRef.current.getActiveIndex();
    if (currentIndex === undefined || currentIndex === null) return;

    const nextIndex = currentIndex + 1;
    localStorage.setItem(TUTORIAL_STEP_KEY, String(nextIndex));
    driverRef.current.drive(nextIndex);
  };

  const registerCallback = (name: string, callback: () => void) => {
    callbacksRef.current[name] = callback;
  };

  const executeCallback = (name: string) => {
    if (callbacksRef.current[name]) {
      callbacksRef.current[name]();
    }
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      starTour();
    }, 200);

    return () => clearTimeout(timer);
  }, []);

  return (
    <DriverContext.Provider value={{moveNextStep, registerCallback, executeCallback}}>
      {children}
    </DriverContext.Provider>
  );
};

export const useDriver = () => {
  const context = useContext(DriverContext);
  if (!context) throw new Error("useDriver must be used within DriverProvider");
  return context;
};
