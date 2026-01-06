import React, {createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState} from "react";
import Joyride, {ACTIONS, Callback, EVENTS, STATUS, Step} from "react-joyride";
import {useLocation, useNavigate} from "react-router-dom";

const prevBtnText = "< prev"
const nextBtnText = "next >"

const steps: Step[] = [
  { // 0
    target: 'body',
    title: "Welcome 🙌🏼 !",
    content: "Let's start building a Social Media App using Actionbase.",
    placement: 'center',
    disableBeacon: true,
    locale: {
      next: "start",
    },
  },
  { // 1
    target: "[id='cli-commands']",
    title: "Prepare for tutorial",
    content: "You have to create Storage and Database. Please run the commands shown on the left.",
    placement: 'right',
    disableBeacon: true,
    locale: {
      next: "done",
    },
  },
  { // 2
    target: "[id='cli-commands']",
    title: "Load user_posts data",
    content: "Let's load user_posts and user_likes data.",
    placement: 'right',
    disableBeacon: true,
    locale: {
      next: "done",
    },
  },
  { // 3
    target: "[id='nav-btn-search']",
    content: "Let's see how many users there are.",
    placement: 'top',
    disableBeacon: true,
  },
  { // 4
    target: "[id='search-results-list']",
    content: "There are 8 users we can follow. Let's follow merlin.",
    placement: 'bottom',
    disableBeacon: true,
  },
  { // 5
    target: "[id='cli-commands']",
    title: "Create user_follows table",
    content: "First, we have to create a table that represents the user follow relation. Please run the commands.",
    placement: 'right',
    disableBeacon: true,
    locale: {
      next: 'done',
    },
  },
  { // 6
    target: "[id='btn-profile-following']",
    title: "Follow merlin",
    content: "Insert an Edge to follow merlin. Click the 'Done' button when you're done.",
    placement: 'bottom',
    disableBeacon: true,
    locale: {
      next: 'done',
    },
  },
  { // 7
    target: "[id='btn-profile-following']",
    title: "Yeah 🎉",
    content: "Now you can see that you are following merlin.",
    placement: 'bottom',
    disableBeacon: true,
  },
  { // 8
    target: "[id='searched_user_0']",
    content: "Also, let's follow emeth!",
    placement: 'bottom',
    disableBeacon: true,
    locale: {
      next: "go",
    },
  },
  { // 9
    target: "[id='btn-profile-following']",
    title: "Follow emeth",
    content: "Insert Edges to follow emeth. Click the 'done' button when you're done.",
    placement: 'bottom',
    disableBeacon: true,
    locale: {
      next: 'done',
    },
  },
  { // 10
    target: "[id='btn-profile-following']",
    title: "Yeah 🎉",
    content: "You can see we're following emeth.",
    placement: 'bottom',
    disableBeacon: true,
  },
  { // 11
    target: "[id='searched_user_1']",
    content: "Let's take a look at merlin's home.",
    placement: 'bottom',
    disableBeacon: true,
    locale: {
      next: "go",
    },
  },
  { // 12
    target: "[id='profile-post-0']",
    content: "Let's see this post.",
    placement: 'right',
    disableBeacon: true,
  },
  { // 13
    target: "[id='post-detail-actions']",
    title: "Add a like",
    content: "Insert an Edge to add a like to this post. Click the 'done' button when you're done.",
    placement: 'bottom',
    disableBeacon: true,
    locale: {
      next: "done",
    },
  },
  { // 14
    target: "[id='post-detail-actions']",
    title: "Yeah 🎉",
    content: "You can see the like data has changed.",
    placement: 'bottom',
    disableBeacon: true,
  },
  { // 15
    target: "[id='nav-btn-profile']",
    content: "And then, let's go to your home!",
    placement: 'left',
    disableBeacon: true,
  },
  { // 16
    target: "[id='profile-stats-bottom']",
    content: "You can see these values are the same as the results.",
    title: "See counts of status",
    placement: 'bottom',
    disableBeacon: true,
  },
  { // 17
    target: "[id='profile-follows']",
    content: "Let's see whom you follow.",
    placement: 'bottom',
    disableBeacon: true,
    locale: {
      next: 'go',
    },
  },
  { // 18
    target: "[id='followers-list']",
    title: "See followers list",
    content: "You can see these values are the same as the results.",
    placement: 'top',
    disableBeacon: true,
  },
  { // 19
    target: "[id='nav-btn-feed']",
    content: "Let's see the feed.",
    placement: 'top',
    disableBeacon: true,
  },
  { // 20
    target: "[class='mobile-frame']",
    title: "See following's posts",
    content: "You can see your following's posts!",
    placement: 'top',
    disableBeacon: true,
  },
  { // 21
    target: 'body',
    title: "End",
    content: "The tutorial has ended. Thanks for joining the tour!",
    placement: 'center',
    disableBeacon: true,
    locale: {
      next: 'Bye 👋🏻',
    },
  },
];

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
