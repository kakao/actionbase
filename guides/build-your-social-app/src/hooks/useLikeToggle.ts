import {createToggleHook} from "./useToggleMutation";

interface UseLikeToggleOptions {
  onSuccess?: (isLiked: boolean, likesCount: number, postId: number) => void;
  onError?: (error: Error) => void;
}

const useLikeToggleBase = createToggleHook<number>("user_likes");

export const useLikeToggle = (source: string, options?: UseLikeToggleOptions) => {
  const {handleToggle} = useLikeToggleBase(source, options);
  return {handleLikeToggle: handleToggle};
};

