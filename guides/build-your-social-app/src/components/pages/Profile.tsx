import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { count, get, scanUserPosts } from '../../api/actionbase';
import { DATABASE, DIRECTION, ROUTES, TABLE } from '../../constants';
import NotFound from './NotFound';
import '../../styles/profile.css';
import Spinner from '../layout/Spinner';
import { me, postDetails, users } from '../../constants/dummy';
import { useToggleFollowing } from '../../hooks/useToggleMutate';
import { useToast } from '../../contexts/ToastContext';

interface UserPost {
  id: number;
  image: string[];
}

const Profile: React.FC = () => {
  const { id } = useParams();
  const owner = users.find((x) => x.id === id);
  const { showToast } = useToast();
  if (!owner) return <NotFound />;

  const [followers, setFollowers] = useState<number>(0);
  const [followings, setFollowings] = useState<number>(0);
  const [isFollowing, setIsFollowing] = useState<boolean>(false);
  const [posts, setUserPosts] = useState<UserPost[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState<boolean>(false);

  const navigate = useNavigate();
  const { ToggleFollowing } = useToggleFollowing(me.id, {
    onSuccess: (isFollowing, followersCount) => {
      setIsFollowing(isFollowing);
      setFollowers(followersCount);
    },
    onError: (error) => {
      console.error('Failed to toggle follow:', error);
    },
  });

  const handleFollowingToggleWrapper = useCallback(async () => {
    await ToggleFollowing(owner.id, isFollowing);
  }, [owner.id, isFollowing, ToggleFollowing]);

  const fetchData = useCallback(async () => {
    try {
      const [isFollowingPayload, followersPayload, followingsPayload, postsPayload] =
        await Promise.all([
          get(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, me.id, owner.id),
          count(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, owner.id, DIRECTION.IN),
          count(DATABASE.SOCIAL, TABLE.USER_FOLLOWS, owner.id, DIRECTION.OUT),
          scanUserPosts(owner.id, DIRECTION.OUT),
        ]);

      setIsFollowing(isFollowingPayload.count > 0);
      setFollowers(followersPayload.counts[0]?.count ?? 0);
      setFollowings(followingsPayload.counts[0]?.count ?? 0);

      const userPostEdges = postsPayload.edges
        .map((edge) => {
          const post = postDetails.find((p) => p.id == edge.target);
          return post ? { id: edge.target, image: post.imageUrls } : null;
        })
        .filter((post): post is UserPost => post !== null);
      setUserPosts(userPostEdges);
    } catch (err) {
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  }, [owner.id, isFollowing, followers, followings]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  useEffect(() => {
    window.addEventListener('reload', fetchData);
    return () => {
      window.removeEventListener('reload', fetchData);
    };
  }, [fetchData]);

  if (hasError) {
    return <NotFound />;
  }

  return (
    <div style={{ position: 'relative', height: '100%' }}>
      {isLoading && <Spinner />}
      <header className="profile-header">
        <button className="icon-btn" onClick={() => showToast('Unsupported')}>
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C11.172 2 10.5 2.672 10.5 3.5V4.341C9.672 4.541 8.891 4.875 8.184 5.325L7.525 4.666C6.947 4.088 6.009 4.088 5.431 4.666L4.666 5.431C4.088 6.009 4.088 6.947 4.666 7.525L5.325 8.184C4.875 8.891 4.541 9.672 4.341 10.5H3.5C2.672 10.5 2 11.172 2 12C2 12.828 2.672 13.5 3.5 13.5H4.341C4.541 14.328 4.875 15.109 5.325 15.816L4.666 16.475C4.088 17.053 4.088 17.991 4.666 18.569L5.431 19.334C6.009 19.912 6.947 19.912 7.525 19.334L8.184 18.675C8.891 19.125 9.672 19.459 10.5 19.659V20.5C10.5 21.328 11.172 22 12 22C12.828 22 13.5 21.328 13.5 20.5V19.659C14.328 19.459 15.109 19.125 15.816 18.675L16.475 19.334C17.053 19.912 17.991 19.912 18.569 19.334L19.334 18.569C19.912 17.991 19.912 17.053 19.334 16.475L18.675 15.816C19.125 15.109 19.459 14.328 19.659 13.5H20.5C21.328 13.5 22 12.828 22 12C22 11.172 21.328 10.5 20.5 10.5H19.659C19.459 9.672 19.125 8.891 18.675 8.184L19.334 7.525C19.912 6.947 19.912 6.009 19.334 5.431L18.569 4.666C17.991 4.088 17.053 4.088 16.475 4.666L15.816 5.325C15.109 4.875 14.328 4.541 13.5 4.341V3.5C13.5 2.672 12.828 2 12 2ZM12 8C14.209 8 16 9.791 16 12C16 14.209 14.209 16 12 16C9.791 16 8 14.209 8 12C8 9.791 9.791 8 12 8Z" />
          </svg>
        </button>
        <div className="username-dropdown">
          <h1 className="username">{owner?.id}</h1>
          <svg
            viewBox="0 0 24 24"
            width="16"
            height="16"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        </div>
        <button className="icon-btn" onClick={() => showToast('Unsupported')}>
          <svg viewBox="0 0 24 24" fill="currentColor">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
            <path d="M12 8v8M8 12h8" stroke="white" strokeWidth="2" />
          </svg>
        </button>
      </header>
      {!isLoading && (
        <div className="profile-scroll">
          <div className="app">
            <div className="profile-info">
              <div className="profile-header-section">
                <div className="profile-avatar-container">
                  <div className="profile-avatar-large" style={{ background: owner?.gradient }}>
                    <span className="profile-icon ">
                      <img src={owner?.avatar} alt={owner?.name} />
                    </span>
                  </div>
                </div>

                <div className="profile-right-section">
                  <div className="profile-username-row">
                    <h2 className="profile-username">{owner?.name}</h2>
                    <button className="icon-btn-menu" onClick={() => showToast('Unsupported')}>
                      <svg viewBox="0 0 24 24" fill="currentColor">
                        <circle cx="12" cy="5" r="1.5" />
                        <circle cx="12" cy="12" r="1.5" />
                        <circle cx="12" cy="19" r="1.5" />
                      </svg>
                    </button>
                  </div>

                  <div className="profile-actions">
                    {owner?.isMe ? (
                      <>
                        <button
                          className="action-button-primary"
                          onClick={() => showToast('Unsupported')}
                        >
                          Edit profile
                        </button>
                        <button
                          className="action-button-primary"
                          onClick={() => showToast('Unsupported')}
                        >
                          Share profile
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          id="btn-profile-following"
                          className={`action-button-following-default ${isFollowing ? 'action-button-following' : 'action-button-follow'}`}
                          onClick={handleFollowingToggleWrapper}
                        >
                          {isFollowing ? (
                            <>
                              Following
                              <svg
                                viewBox="0 0 24 24"
                                width="12"
                                height="12"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2.5"
                              >
                                <path d="M6 9l6 6 6-6" />
                              </svg>
                            </>
                          ) : (
                            'Follow'
                          )}
                        </button>
                        <button
                          className="action-button-primary"
                          onClick={() => showToast('Unsupported')}
                        >
                          Message
                        </button>
                      </>
                    )}
                  </div>
                </div>
              </div>

              <div className="profile-bio-section">
                <p className="profile-bio-text">{owner?.message}</p>
              </div>
            </div>

            <div className="profile-stats-bottom" id="profile-stats-bottom">
              <div className="stat-item">
                <span className="stat-label">Posts</span>
                <span className="stat-count">{posts.length}</span>
              </div>
              <div
                className="stat-item"
                id="profile-followers"
                onClick={() => navigate(ROUTES.FOLLOWERS(owner.id))}
              >
                <span className="stat-label">Followers</span>
                <span className="stat-count">{followers}</span>
              </div>
              <div className="stat-item" onClick={() => navigate(ROUTES.FOLLOWINGS(owner.id))}>
                <span className="stat-label">Follows</span>
                <span className="stat-count">{followings}</span>
              </div>
            </div>

            <div className="tab-navigation">
              <button className="tab-btn active">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <rect x="3" y="3" width="7" height="7" />
                  <rect x="14" y="3" width="7" height="7" />
                  <rect x="3" y="14" width="7" height="7" />
                  <rect x="14" y="14" width="7" height="7" />
                </svg>
              </button>
              <button className="tab-btn" onClick={() => showToast('Unsupported')}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <line x1="9" y1="3" x2="9" y2="21" />
                  <line x1="15" y1="3" x2="15" y2="21" />
                  <line x1="3" y1="9" x2="21" y2="9" />
                  <line x1="3" y1="15" x2="21" y2="15" />
                </svg>
              </button>
              <button className="tab-btn" onClick={() => showToast('Unsupported')}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
                </svg>
              </button>
              <button className="tab-btn" onClick={() => showToast('Unsupported')}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="8" r="4" />
                  <path d="M3 21v-2a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v2" />
                </svg>
              </button>
            </div>
            {posts.length === 0 ? (
              <div className="empty-posts">
                <div className="empty-posts-content">
                  <div className="empty-posts-icon">
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                      <circle cx="12" cy="13" r="4" />
                    </svg>
                  </div>
                  <div className="empty-posts-text">
                    <div className="empty-posts-title">No posts</div>
                    <div className="empty-posts-description">
                      Photos and videos posted by {owner?.name} will be displayed here
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              <div className="posts-grid">
                {posts.map((post, idx) => (
                  <div
                    id={'profile-post-' + idx}
                    key={idx}
                    className="grid-item"
                    style={{ background: post.image[0] || '' }}
                    onClick={() => navigate(ROUTES.POST(post.id))}
                  >
                    <span className="grid-icon">
                      <img src={post.image[0]} />
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default Profile;
