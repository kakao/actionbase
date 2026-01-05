import {mutate} from "../api/mutate";
import {count} from "../api/count";

interface UseLikeToggleOptions {
  onSuccess?: (isLiked: boolean, likesCount: number, postId: number) => void;
  onError?: (error: Error) => void;
}

export const useLikeToggle = (source: string, options?: UseLikeToggleOptions) => {
  const handleLikeToggle = async (postId: number, currentIsLiked: boolean) => {
    const mutationType = currentIsLiked ? "DELETE" : "INSERT";

    try {
      const result = await mutate(
        "social",
        "user_likes",
        {
          mutations: [
            {
              type: mutationType,
              edge: {
                version: Date.now(),
                source: source,
                target: postId,
                properties: {
                  "createdAt": Date.now()
                }
              }
            }
          ]
        }
      );

      const status = result.results[0].status;
      if (!(status === "CREATED" || status === "DELETED")) {
        console.error("Failed to toggle like:", status);
        if (options?.onError) {
          options.onError(new Error(`Mutation failed with status: ${status}`));
        }
        return;
      }

      try {
        const countResult = await count(
          "social",
          "user_likes",
          postId,
          "IN"
        );

        const newLikes = countResult.counts && countResult.counts.length > 0
          ? countResult.counts[0].count
          : 0;

        const newIsLiked = mutationType === "INSERT";

        if (options?.onSuccess) {
          options.onSuccess(newIsLiked, newLikes, postId);
        }
      } catch (error) {
        console.error("Failed to fetch updated likes count:", error);
        if (options?.onError) {
          options.onError(error as Error);
        }
      }
    } catch (error) {
      console.error("Failed to toggle like:", error);
      if (options?.onError) {
        options.onError(error as Error);
      }
    }
  };

  return {handleLikeToggle};
};

