import { useCallback } from 'react';
import { count, mutate } from '../api/actionbase';
import { DATABASE } from '../constants';

const VALID_STATUSES = ['CREATED', 'DELETED', 'UPDATED'] as const;

interface ToggleOptions<T> {
  onSuccess?: (isActive: boolean, count: number, target: T) => void;
  onError?: (error: Error) => void;
}

interface UserToggleFollowingOptions {
  onSuccess?: (isFollowing: boolean, FollowersCount: number, userId: string) => void;
  onError?: (error: Error) => void;
}

interface UseToggleLikeOptions {
  onSuccess?: (isLiked: boolean, likesCount: number, postId: number) => void;
  onError?: (error: Error) => void;
}

export function useToggleFollowing(source: string, options?: UserToggleFollowingOptions) {
  const { handleToggle } = useToggleBase<string>('user_follows', source, options);
  return { ToggleFollowing: handleToggle };
}

export function useToggleLike(source: string, options?: UseToggleLikeOptions) {
  const { handleToggle } = useToggleBase<number>('user_likes', source, options);
  return { toggleLike: handleToggle };
}

function useToggleBase<T extends string | number>(
  table: string,
  source: string,
  options?: ToggleOptions<T>
) {
  const handleToggle = useCallback(
    async (target: T, currentIsActive: boolean) => {
      const mutationType = currentIsActive ? 'DELETE' : 'INSERT';
      const now = Date.now();

      try {
        const result = await mutate(DATABASE.SOCIAL, table, {
          mutations: [
            {
              type: mutationType,
              edge: { version: now, source, target, properties: { createdAt: now } },
            },
          ],
        });

        const status = result.results[0]?.status;
        if (status === undefined || !VALID_STATUSES.includes(status)) {
          options?.onError?.(new Error(`Mutation failed with status: ${status}`));
          return;
        }

        try {
          const countResult = await count(DATABASE.SOCIAL, table, target, 'IN');
          options?.onSuccess?.(
            mutationType === 'INSERT',
            countResult.counts?.[0]?.count ?? 0,
            target
          );
        } catch (error) {
          options?.onError?.(error as Error);
        }
      } catch (error) {
        options?.onError?.(error as Error);
      }
    },
    [source, options, table]
  );

  return { handleToggle };
}
