import {get, getTable} from "../api/actionbase";
import {DATABASE, TABLE} from "./index";

export interface ButtonEvent {
  type: string | undefined;
}

export interface StepEvent {
  to?: string;
  target?: string[];
}

export const stepNextEvent = new Map<number, StepEvent>([
  [1, {target: ["[id='run-command-btn']"]}],
  [2, {target: ["[id='run-command-btn']"]}],
  [3, {to: '/search', target: ["[id='search-results-list']"]}],
  [5, {target: ["[id='run-command-btn']"]}],
  [6, {to: '/profile/merlin', target: ["[id='btn-profile-following']", "[id='run-command-btn']"]}],
  [7, {target: ["[id='btn-profile-following']"]}],
  [8, {target: ["[id='run-command-btn']"]}],
  [9, {target: ["[id='run-command-btn']"]}],
  [10, {target: ["[id='profile-followers']"]}],
  [11, {target: ["[id='run-command-btn']"]}],
  [12, {to: '/followers/merlin', target: ["[id='followers-list']"]}],
  [14, {to: '/post/1', target: ["[id='run-command-btn']"]}],
  [15, {target: ["[id='btn-likes']"]}],
  [16, {target: ["[id='run-command-btn']"]}],
  [18, {to: '/'}],
]);

export const stepPrevEvent = new Map<number, StepEvent>([
  [3, {target: ["[id='run-command-btn']"]}],
  [4, {to: '/search', target: ["[id='cli-commands']", "[id='run-command-btn']"]}],
  [7, {to: '/search', target: ["[id='run-command-btn']"]}],
  [8, {target: ["[id='run-command-btn']"]}],
  [10, {target: ["[id='run-command-btn']"]}],
  [11, {target: ["[id='run-command-btn']"]}],
  [13, {to: '/profile/merlin', target: ["[id='run-command-btn']"]}],
  [15, {to: '/followers/merlin'}],
  [16, {target: ["[id='run-command-btn']"]}],
  [18, {target: ["[id='run-command-btn']"]}],
  [19, {to: '/post/1'}],
]);

export const stepVerifiers = new Map<number, () => Promise<boolean>>();

const setDelegatingVerifiers = (targetSteps: number[], sourceStep: number) => {
  targetSteps.forEach(step => {
    stepVerifiers.set(step, async () => {
      return await stepVerifiers.get(sourceStep)!()
    });
  });
};

stepVerifiers.set(2, async () => {
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

stepVerifiers.set(6, async () => {
  const userFollows = await getTable(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, false)
  return !(!userFollows || userFollows?.active === false);
});

stepVerifiers.set(7, async () => {
  const edgeState = await get(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, 'doki', 'merlin', false)
  return !(!edgeState || edgeState?.count < 1);
});

stepVerifiers.set(15, async () => {
  const edgeState = await get(DATABASE.SOCIAL, TABLE.USER_LIKES, 'doki', 1, false)
  return !(!edgeState || edgeState?.count < 1);
});

stepVerifiers.set(19, async () => {
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

setDelegatingVerifiers([3, 4, 5], 2);
setDelegatingVerifiers([8, 9, 10, 11, 12, 13, 14], 7);
setDelegatingVerifiers([16, 17], 15);
