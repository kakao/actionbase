import {mutate} from "../api/mutate";
import {count} from "../api/count";

interface UseFollowingToggleOptions {
  onSuccess?: (isFollowing: boolean, FollowersCount: number, userId: string) => void;
  onError?: (error: Error) => void;
}

export const useFollowingToggle = (source: string, options?: UseFollowingToggleOptions) => {
  const handleFollowingToggle = async (userId: string, currentIsFollowing: boolean) => {
    const mutationType = currentIsFollowing ? "DELETE" : "INSERT";

    try {
      const result = await mutate(
        "social",
        "user_follows",
        {
          mutations: [
            {
              type: mutationType,
              edge: {
                version: Date.now(),
                source: source,
                target: userId,
                properties: {
                  "createdAt": Date.now()
                }
              }
            }
          ]
        }
      );

      const status = result.results[0].status;
      if (!(status === "CREATED" || status === "DELETED" || status === "UPDATED")) {
        console.error("Failed to toggle follow:", status);
        if (options?.onError) {
          options.onError(new Error(`Mutation failed with status: ${status}`));
        }
        return;
      }

      try {
        const countResult = await count(
          "social",
          "user_follows",
          userId,
          "IN"
        );

        const followersCount = countResult.counts && countResult.counts.length > 0
          ? countResult.counts[0].count
          : 0;

        const isFollowing = mutationType === "INSERT";

        if (options?.onSuccess) {
          options.onSuccess(isFollowing, followersCount, userId);
        }
      } catch (error) {
        console.error("Failed to fetch updated follows count:", error);
        if (options?.onError) {
          options.onError(error as Error);
        }
      }
    } catch (error) {
      console.error("Failed to toggle follow:", error);
      if (options?.onError) {
        options.onError(error as Error);
      }
    }
  };

  return {handleFollowingToggle};
};

