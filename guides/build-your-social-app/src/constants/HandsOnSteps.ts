import {Step} from "react-joyride";

export const steps: Step[] = [
  { // 0
    target: 'body',
    title: "Welcome to Actionbase 🙌🏼",
    content: "Let's start building a social media app using Actionbase.",
    placement: 'center',
    disableBeacon: true,
    locale: {
      next: "start",
    },
  },
  { // 1
    target: "[id='cli-commands']",
    title: "Prepare tutoral ",
    content: "You have to create Database. Click.",
    placement: 'right',
    disableBeacon: true,
    locale: {
      next: "done",
    },
  },
  { // 2
    target: "[id='cli-commands']",
    title: "Load prepared data",
    content: "Let's load user_posts and user_likes data.",
    placement: 'right',
    disableBeacon: true,
    locale: {
      next: "done",
    },
  },
  { // 3
    target: "[id='nav-btn-search']",
    content: "Let's see how many users there are(prepared).",
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
    title: "Add a like feature",
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
    placement: 'bottom',
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
    placement: 'right',
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
