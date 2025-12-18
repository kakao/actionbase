import {createToggleHook} from "./useToggleMutation";

interface UseFollowingToggleOptions {
  onSuccess?: (isFollowing: boolean, FollowersCount: number, userId: string) => void;
  onError?: (error: Error) => void;
}

const useFollowingToggleBase = createToggleHook<string>("user_follows");

export const useFollowingToggle = (source: string, options?: UseFollowingToggleOptions) => {
  const {handleToggle} = useFollowingToggleBase(source, options);
  return {handleFollowingToggle: handleToggle};
};

