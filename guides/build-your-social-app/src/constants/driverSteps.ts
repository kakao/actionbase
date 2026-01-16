import {get, getTable} from "../api/actionbase";
import {DATABASE, TABLE} from "./index";
import {me} from "./dummy";

export interface ButtonEvent {
  type: string | undefined;
}

export interface StepEvent {
  to?: string;
  target?: string[];
}

export const stepNextEvent = new Map<number, StepEvent>([
  [2, {target: ["[id='run-command-btn']"]}],
  [3, {target: ["[id='run-command-btn']"]}],
  [4, {to: '/search', target: ["[id='search-results-list']"]}],
  [6, {target: ["[id='run-command-btn']"]}],
  [7, {to: '/profile/j4rami', target: ["[id='btn-profile-following']", "[id='run-command-btn']"]}],
  [8, {target: ["[id='btn-profile-following']"]}],
  [9, {target: ["[id='run-command-btn']"]}],
  [10, {target: ["[id='run-command-btn']"]}],
  [11, {target: ["[id='profile-followers']"]}],
  [12, {target: ["[id='run-command-btn']"]}],
  [13, {to: '/followers/j4rami', target: ["[id='followers-list']"]}],
  [15, {to: '/post/1', target: ["[id='run-command-btn']"]}],
  [16, {target: ["[id='btn-likes']"]}],
  [17, {target: ["[id='run-command-btn']"]}],
  [19, {to: '/'}],
]);

export const stepPrevEvent = new Map<number, StepEvent>([
  [4, {target: ["[id='run-command-btn']"]}],
  [5, {to: '/search', target: ["[id='cli-commands']", "[id='run-command-btn']"]}],
  [8, {to: '/search', target: ["[id='run-command-btn']"]}],
  [9, {target: ["[id='run-command-btn']"]}],
  [11, {target: ["[id='run-command-btn']"]}],
  [12, {target: ["[id='run-command-btn']"]}],
  [14, {to: '/profile/j4rami', target: ["[id='run-command-btn']"]}],
  [16, {to: '/followers/j4rami'}],
  [17, {target: ["[id='run-command-btn']"]}],
  [19, {target: ["[id='run-command-btn']"]}],
  [20, {to: '/post/1'}],
]);

export const stepVerifiers = new Map<number, () => Promise<boolean>>();

const setDelegatingVerifiers = (targetSteps: number[], sourceStep: number) => {
  targetSteps.forEach(step => {
    stepVerifiers.set(step, async () => {
      return await stepVerifiers.get(sourceStep)!()
    });
  });
};

stepVerifiers.set(3, async () => {
  const [userPosts, userLikes] = await Promise.all([
    getTable(DATABASE.SOCIAL, TABLE.USER_POSTS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_LIKES, false),
  ])

  if (!userPosts || userPosts?.active === false) {
    return false;
  } else if (!userLikes || userLikes?.active === false) {
    return false;
  }
  return true;
});

stepVerifiers.set(7, async () => {
  const userFollows = await getTable(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, false)
  return !(!userFollows || userFollows?.active === false);
});

stepVerifiers.set(8, async () => {
  const edgeState = await get(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, me.id, 'j4rami', false)
  return !(!edgeState || edgeState?.count < 1);
});

stepVerifiers.set(16, async () => {
  const edgeState = await get(DATABASE.SOCIAL, TABLE.USER_LIKES, me.id, 1, false)
  return !(!edgeState || edgeState?.count < 1);
});

stepVerifiers.set(20, async () => {
  const [userFollows, userPosts, userLikes] = await Promise.all([
    getTable(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_POSTS, false),
    getTable(DATABASE.SOCIAL, TABLE.USER_LIKES, false),
  ])
  if (!userFollows || userFollows?.active === false) {
    return false;
  } else if (!userPosts || userPosts?.active === false) {
    return false;
  } else if (!userLikes || userLikes?.active === false) {
    return false;
  }
  return true;
});

setDelegatingVerifiers([4, 5, 6], 3);
setDelegatingVerifiers([9, 10, 11, 12, 13, 14, 15], 8);
setDelegatingVerifiers([17, 18], 16);