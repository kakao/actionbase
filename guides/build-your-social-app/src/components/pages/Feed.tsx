import React, {useCallback, useEffect, useMemo, useState} from 'react'
import {count, get, scanUserFollows, scanUserPosts} from "../../api/actionbase";
import {User, UserPost} from '../../types';
import {DATABASE, DIRECTION, ROUTES, TABLE, UI} from '../../constants';
import {formatDate} from '../../utils/date';
import {calculateImageIndex, shouldTriggerSwipe} from '../../utils/image';
import '../../styles/feed.css';
import {useToggleLike} from "../../hooks/useToggleMutate";
import Spinner from "../layout/Spinner";
import {me, postDetails, users} from "../../constants/dummy";
import {useNavigate} from "react-router-dom";
import {useToast} from "../../contexts/ToastContext";

const Feed: React.FC = () => {
  const navigate = useNavigate();
  const {showToast} = useToast();

  const [followings, setFollowings] = useState<User[]>([]);
  const [userPosts, setUserPosts] = useState<UserPost[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentImageIndices, setCurrentImageIndices] = useState<Record<string, number>>({});
  const [touchStart, setTouchStart] = useState<{ postId: number, x: number } | null>(null);
  const [touchEnd, setTouchEnd] = useState<{ postId: number, x: number } | null>(null);
  const nonMeFollowings = useMemo(() => followings.filter(x => !x.isMe), [followings]);

  const {toggleLike} = useToggleLike(
    me.id,
    {
      onSuccess: (newIsLiked, newLikes, postId) => {
        setUserPosts(prevPosts => prevPosts.map(post =>
          post.id === postId ? {...post, isLiked: newIsLiked, likes: newLikes} : post
        ));
      },
      onError: console.error
    });

  const toggleLikeCallback = useCallback(async (postId: number) => {
    const currentPost = userPosts.find(p => p.id === postId);
    if (currentPost) await toggleLike(postId, currentPost.isLiked ?? false);
  }, [userPosts, toggleLike]);

  const changeImageIndex = useCallback((postId: number | string, delta: number) => {
    setCurrentImageIndices(prev => {
      const current = prev[postId] || 0;
      const post = userPosts.find(p => String(p.id) === String(postId));
      const maxIndex = (post?.images?.length || 1) - 1;
      const newIndex = calculateImageIndex(current, delta, maxIndex);
      return newIndex !== current ? {...prev, [postId]: newIndex} : prev;
    });
  }, [userPosts]);

  const handleTouchSwipe = useCallback((postId: number) => {
    if (!touchStart || !touchEnd || touchStart.postId !== postId) return;
    const distance = touchStart.x - touchEnd.x;
    if (shouldTriggerSwipe(distance, UI.SWIPE_THRESHOLD)) {
      changeImageIndex(postId, distance > 0 ? 1 : -1);
    }
    setTouchStart(null);
    setTouchEnd(null);
  }, [touchStart, touchEnd, changeImageIndex]);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const {edges: myFollowingEdges} = await scanUserFollows(me.id, DIRECTION.OUT);
        const followingUsers = myFollowingEdges
          .map(edge => users.find(u => u.id === edge.target))
          .filter((user): user is User => user !== undefined);
        setFollowings(followingUsers);

        const userPostsEdges = await Promise.all(
          myFollowingEdges.map(following => scanUserPosts(following.target, DIRECTION.OUT))
        );

        const LikesCountByPostId = Object.fromEntries(
          (await Promise.all(userPostsEdges.flatMap(p => p.edges.map(edge => count(
            DATABASE.SOCIAL,
            TABLE.USER_LIKES,
            edge.target,
            DIRECTION.IN
          )))))
            .flatMap(p => p.counts).map(p => [p.start, p.count])
        );

        const userPostDetails = userPostsEdges.flatMap(dataPayload =>
          dataPayload.edges.map(edge => {
            const post = postDetails.find(p => p.id === edge.target);
            return {
              owner: users.find(u => u.id === edge.source) || users[0],
              id: edge.target,
              images: post?.imageUrls || [],
              content: post?.content || '',
              likes: LikesCountByPostId[post?.id!!] || 0,
              createdAt: edge.properties["createdAt"],
              isLiked: false
            };
          })
        );

        const myPostLikeByPostId = Object.fromEntries(
          (await Promise.all(userPostDetails.map(p => get(
            DATABASE.SOCIAL,
            TABLE.USER_LIKES,
            me.id,
            String(p.id)
          ))))
            .flatMap(p => p.edges).map(t => [String(t.target), true])
        );

        setUserPosts(userPostDetails.map(p => ({
          ...p,
          isLiked: Boolean(myPostLikeByPostId[String(p.id)])
        })).sort((a, b) => a.createdAt - b.createdAt));
      } catch (err) {
        console.error("Failed to fetch feed data:", err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchData();
  }, []);

  useEffect(() => {
    const feedScrollElement = document.querySelector('.feed-scroll') as HTMLElement;
    if (!feedScrollElement) return;

    const enableScrolling = () => {
      Object.assign(feedScrollElement.style, {
        pointerEvents: 'auto',
        touchAction: 'pan-y',
        overflowY: 'auto'
      });
    };

    enableScrolling();
    const observer = new MutationObserver(enableScrolling);
    observer.observe(document.body, {attributes: true, attributeFilter: ['class']});

    const handleEvent = (e: WheelEvent | TouchEvent) => {
      if (feedScrollElement.contains(e.target as Node)) e.stopPropagation();
    };

    document.addEventListener('wheel', handleEvent as EventListener, {passive: true, capture: true});
    document.addEventListener('touchmove', handleEvent as EventListener, {passive: true, capture: true});

    return () => {
      observer.disconnect();
      document.removeEventListener('wheel', handleEvent as EventListener, {capture: true});
      document.removeEventListener('touchmove', handleEvent as EventListener, {capture: true});
    };
  }, []);

  return (
    <div className="app feed-page" style={{position: 'relative'}}>
      {isLoading && <Spinner/>}
      <header className="app-header">
        <div className="logo-wrapper">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M6 9l6 6 6-6"/>
          </svg>
        </div>
        <div className="header-icons">
          <button className="icon-btn" onClick={() => showToast('Unsupported')}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 4v16m8-8H4"/>
            </svg>
          </button>
          <button className="icon-btn notification-icon" onClick={() => showToast('Unsupported')}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            <span className="notification-dot"></span>
          </button>
        </div>
      </header>

      {!isLoading && (
        <div className="feed-scroll">
          <div className="stories-container">
            <div className="story">
              <div className="story-avatar-wrapper your-story" onClick={() => navigate(ROUTES.PROFILE(me.id))}>
                <div className="story-avatar-gray">
                  <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"/>
                    <circle cx="12" cy="10" r="3"/>
                    <path d="M6.5 19a6 6 0 0 1 11 0"/>
                  </svg>
                </div>
                <div className="story-plus-btn">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <circle cx="12" cy="12" r="10" fill="#0095f6"/>
                    <path d="M12 7v10M7 12h10" stroke="white" strokeWidth="2"/>
                  </svg>
                </div>
              </div>
              <span className="story-username">My story</span>
            </div>

            {nonMeFollowings.map((user, idx) => (
              <div key={idx} className="story">
                <div className="story-avatar-wrapper">
                  <div className="story-avatar" onClick={() => navigate(ROUTES.PROFILE(user.id))}>
                    <div className="story-avatar-inner" style={{background: user.gradient}}>
                      <div className="avatar-placeholder">{user.icon}</div>
                    </div>
                  </div>
                </div>
                <span className="story-username">{user.name}</span>
              </div>
            ))}
          </div>

          <div className="feed">
            {userPosts.map((post, index) => {
              const postId = String(post.id);
              const currentIndex = currentImageIndices[postId] || 0;
              const images = post.images || [];
              const hasMultipleImages = images.length > 1;
              const isTouching = touchStart?.postId === post.id && touchEnd?.postId === post.id;

              return (
                <article key={post.id || `${post.owner.id}-${index}`} className="post" id="feed-post">
                  <div className="post-header">
                    <div className="post-profile" onClick={() => navigate(ROUTES.PROFILE(post.owner.id))}>
                      <div className="post-avatar" style={{background: post.owner.gradient}}>{post.owner.icon}</div>
                      <div className="post-user-info">
                        <span className="post-username">{post.owner.id}</span>
                        <span className="post-location">{post.owner.name}</span>
                      </div>
                    </div>
                    <button className="post-options" onClick={() => showToast('Unsupported')}>
                      <svg viewBox="0 0 24 24" fill="currentColor">
                        <circle cx="12" cy="5" r="1.5"/>
                        <circle cx="12" cy="12" r="1.5"/>
                        <circle cx="12" cy="19" r="1.5"/>
                      </svg>
                    </button>
                  </div>

                  <div className="post-image">
                    <div
                      className="image-carousel"
                      onTouchStart={(e) => setTouchStart({postId: post.id, x: e.targetTouches[0].clientX})}
                      onTouchMove={(e) => setTouchEnd({postId: post.id, x: e.targetTouches[0].clientX})}
                      onTouchEnd={() => handleTouchSwipe(post.id)}
                    >
                      <div
                        className="image-carousel-track"
                        style={{
                          transform: `translateX(-${currentIndex * 100}%)`,
                          transition: isTouching ? 'none' : 'transform 0.3s ease-out'
                        }}
                      >
                        {images.map((image, idx) => (
                          <div key={idx} className="image-placeholder">
                            <span><img src={image}/></span>
                          </div>
                        ))}
                      </div>

                      {hasMultipleImages && (
                        <>
                          {currentIndex > 0 && (
                            <button className="carousel-arrow carousel-arrow-left" onClick={(e) => {
                              e.stopPropagation();
                              changeImageIndex(postId, -1);
                            }}>
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M15 18l-6-6 6-6"/>
                              </svg>
                            </button>
                          )}
                          {currentIndex < images.length - 1 && (
                            <button className="carousel-arrow carousel-arrow-right" onClick={(e) => {
                              e.stopPropagation();
                              changeImageIndex(postId, 1);
                            }}>
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M9 18l6-6-6-6"/>
                              </svg>
                            </button>
                          )}
                          <div className="carousel-indicators">
                            {images.map((_, idx) => (
                              <button
                                key={idx}
                                className={`indicator ${idx === currentIndex ? 'active' : ''}`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setCurrentImageIndices(prev => ({...prev, [postId]: idx}));
                                }}
                              />
                            ))}
                          </div>
                        </>
                      )}
                    </div>
                  </div>

                  <div className="post-actions">
                    <div className="post-actions-left">
                      <button className={`action-btn ${post.isLiked ? 'liked' : ''}`} onClick={() => toggleLikeCallback(post.id!!)}>
                        <svg viewBox="0 0 24 24" fill={post.isLiked ? '#ff3040' : 'none'} stroke="currentColor" strokeWidth="2">
                          <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                        </svg>
                      </button>
                      <button className="action-btn" onClick={() => showToast('Unsupported')}>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
                          <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
                        </svg>
                      </button>
                      <button className="action-btn" onClick={() => showToast('Unsupported')}>
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <line x1="22" y1="2" x2="11" y2="13"/>
                          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                        </svg>
                      </button>
                    </div>
                    <button className="action-btn" onClick={() => showToast('Unsupported')}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                      </svg>
                    </button>
                  </div>

                  <div className="post-likes">
                    <span className="likes-count">{post.likes.toLocaleString()} Likes</span>
                  </div>

                  <div className="post-caption">
                    <span className="caption-username">{post.owner.id}</span>
                    {post.content !== undefined && <span className="caption-username-hyphen">-</span>}
                    <span className="caption-text">{post.content}</span>
                  </div>

                  <div className="post-timestamp">
                    <span>{formatDate(post.createdAt)}</span>
                  </div>
                </article>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default Feed;
