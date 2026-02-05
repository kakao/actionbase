export const DATABASE = {
  SOCIAL: 'social',
} as const;

export const TABLE = {
  USER_POSTS: 'user_posts',
  USER_LIKES: 'user_likes',
  USER_FOLLOWS: 'user_follows',
} as const;

export const DIRECTION = {
  IN: 'IN',
  OUT: 'OUT',
} as const;

export const ROUTES = {
  HOME: '/',
  PROFILE: (id: string) => `/profile/${id}`,
  POST: (id: string | number) => `/post/${id}`,
  SEARCH: '/search',
  FOLLOWERS: (id: string) => `/followers/${id}`,
  FOLLOWINGS: (id: string) => `/followings/${id}`,
} as const;

export const UI = {
  TOGGLE_LIKE: 'toggleLike',
  SWIPE_THRESHOLD: 50,
  CAROUSEL_TRANSITION_DURATION: 300,
  REFRESH_DELAY: 100,
} as const;
