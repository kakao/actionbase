import {count, get, scan} from '../api/actionbase';
import {DATABASE, TABLE, INDEX, DIRECTION, DEFAULT_LIMIT} from '../constants';

export const getPostLikeData = async (postId: number, userId: string) => {
  const [likeCountPayload, userLikePayload] = await Promise.all([
    count(DATABASE.SOCIAL, TABLE.USER_LIKES, postId, DIRECTION.IN),
    get(DATABASE.SOCIAL, TABLE.USER_LIKES, userId, postId)
  ]);
  return {
    likesCount: likeCountPayload.counts[0]?.count ?? 0,
    isLiked: userLikePayload.count > 0
  };
};

export const getUserFollowData = async (userId: string, targetUserId: string) => {
  const [isFollowingPayload, followersPayload, followingsPayload] = await Promise.all([
    get(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, userId, targetUserId),
    count(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, targetUserId, DIRECTION.IN),
    count(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, targetUserId, DIRECTION.OUT)
  ]);
  return {
    isFollowing: isFollowingPayload.count > 0,
    followers: followersPayload.counts[0]?.count ?? 0,
    followings: followingsPayload.counts[0]?.count ?? 0
  };
};

export const scanUserPosts = async (userId: string, direction: string = DIRECTION.OUT) => {
  return scan(
    DATABASE.SOCIAL,
    TABLE.USER_POSTS,
    INDEX.CREATED_AT_DESC,
    userId,
    direction,
    DEFAULT_LIMIT,
    undefined
  );
};

export const scanUserFollows = async (userId: string, direction: string = DIRECTION.OUT) => {
  return scan(
    DATABASE.SOCIAL,
    TABLE.USER_FOLLOWS,
    INDEX.CREATED_AT_DESC,
    userId,
    direction,
    DEFAULT_LIMIT,
    undefined
  );
};

