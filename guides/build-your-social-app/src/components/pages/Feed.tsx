import React, {useEffect, useState} from 'react';
import {useStepAutoAdvance} from "../../modules/useStepAutoAdvance";

import '../../styles/feed.css';
import {postDetails, User, users} from "../../modules/dummy";
import {scan} from "../../api/scan";
import {useNavigate} from "react-router-dom";
import {count} from "../../api/count";
import {get} from "../../api/get";
import {useLikeToggle} from "../../modules/useLikeToggle";
import Spinner from "../common/Spinner";

interface UserPost {
  owner: User,
  id: number,
  images: string[],
  content: string,
  likes: number,
  createdAt: number,
  isLiked: boolean
}

const Feed: React.FC = () => {
  const navigate = useNavigate()

  const [followings, setFollowings] = useState<User[]>([])
  const [userPosts, setUserPosts] = useState<UserPost[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentImageIndices, setCurrentImageIndices] = useState<Record<string, number>>({});
  const [touchStart, setTouchStart] = useState<{ postId: number, x: number } | null>(null);
  const [touchEnd, setTouchEnd] = useState<{ postId: number, x: number } | null>(null);

  const {handleLikeToggle} = useLikeToggle(
    "doki",
    {
      onSuccess: (newIsLiked, newLikes, postId) => {
        setUserPosts(prevPosts => {
          return prevPosts.map(post => {
            if (post.id === postId) {
              return {
                ...post,
                isLiked: newIsLiked,
                likes: newLikes
              };
            }
            return post;
          });
        });
      },
      onError: (error) => {
        console.error("Failed to toggle like:", error);
      }
    });

  const handleLikeToggleWrapper = async (postId: number) => {
    const currentPost = userPosts.find(p => p.id === postId);
    if (!currentPost) return;

    await handleLikeToggle(postId, currentPost.isLiked);
  };

  useStepAutoAdvance([19]);

  useEffect(() => {
    const fetchData = async () => {
      setIsLoading(true);
      try {
        const myFollowingsPayload = await scan(
          "social",
          "user_follows",
          "created_at_desc",
          "doki",
          "OUT",
          25,
          undefined
        )
        const myFollowingEdges = myFollowingsPayload.edges

        const followingUsers = myFollowingEdges
          .map(edge => {
            return users.find(u => u.id === edge.target);
          })
          .filter((user): user is User => user !== undefined)
        setFollowings(followingUsers)

        const userPostsEdges = await Promise.all(
          myFollowingEdges.map(following => {
              return scan(
                "social",
                "user_posts",
                "created_at_desc",
                following.target,
                "OUT",
                25,
                undefined
              )
            }
          )
        )

        const userPostPayloads = await Promise.all(
          userPostsEdges.flatMap(p =>
            p.edges.map(edge =>
              count(
                "social",
                "user_likes",
                edge.target,
                "IN"
              )
            )
          )
        )

        const userPostLikesEdgeCounts = userPostPayloads.flatMap(p => p.counts)
        const LikesCountByPostId = Object.fromEntries(userPostLikesEdgeCounts.map(p => [p.start, p.count]))

        const userPostDetails = userPostsEdges
          .flatMap(dataPayload =>
            dataPayload.edges
              .map(edge => {
                const post = postDetails.find(p => p.id === edge.target)
                return {
                  owner: users.find(u => u.id === edge.source) || users[0],
                  id: edge.target,
                  images: post?.imageUrls,
                  content: post?.content,
                  likes: LikesCountByPostId[post?.id!!] || 0,
                  createdAt: edge.properties["createdAt"],
                  isLiked: false
                }
              })
          )

        const myPostLikePayloads = await Promise.all(
          userPostDetails.map(p =>
            get(
              "social",
              "user_likes",
              "doki",
              p.id
            )
          )
        )

        const myPostLikeByPostId = Object.fromEntries(
          myPostLikePayloads.flatMap(p => p.edges).map(t => [t.target, t])
        )

        const userPosts = userPostDetails.map(p => {
            const postId = p.id;
            const isLiked = postId ? myPostLikeByPostId[postId] !== undefined : false;
            return {
              owner: p.owner,
              id: p.id,
              images: p.images,
              content: p.content,
              likes: p.likes,
              createdAt: p.createdAt,
              isLiked: isLiked
            }
          }
        ).sort((a, b) =>
          a.createdAt - b.createdAt
        )
        setUserPosts(userPosts)
      } catch (err) {
      } finally {
        setIsLoading(false);
      }
    }
    fetchData()
  }, []);

  return (
    <div className="app feed-page" style={{position: 'relative'}}>
      {isLoading && <Spinner/>}
      {/* Header - fixed within mobile frame */}
      <header className="app-header">
        <div className="logo-wrapper">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M6 9l6 6 6-6"/>
          </svg>
        </div>
        <div className="header-icons">
          <button className="icon-btn">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 4v16m8-8H4"/>
            </svg>
          </button>
          <button className="icon-btn notification-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            <span className="notification-dot"></span>
          </button>
        </div>
      </header>

      {/* Scrollable content (stories + feed) */}
      {!isLoading && (
        <div className="feed-scroll">
          {/* Stories */}
          <div className="stories-container">
            {/* Your Story */}
            <div className="story">
              <div className="story-avatar-wrapper your-story" onClick={() => navigate("/profile/doki")}>
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

            {/* Other Stories */}
            {followings
              .filter(x => !x.isMe)
              .map((user, idx) => (
                <div key={idx} className="story">
                  <div className="story-avatar-wrapper">
                    <div className="story-avatar" onClick={() => navigate("/profile/" + user.id)}>
                      <div className="story-avatar-inner" style={{background: user.gradient}}>
                        <div className="avatar-placeholder">{user.icon}</div>
                      </div>
                    </div>
                  </div>
                  <span className="story-username">{user.name}</span>
                </div>
              ))}
          </div>

          {/* Feed Posts */}
          <div className="feed">
            {userPosts.map((post, index) => (
              <article key={post.id || `${post.owner.id}-${index}`} className="post" id="feed-post">
                <div className="post-header">
                  <div className="post-profile" onClick={() => navigate("/profile/" + post.owner.id)}>
                    <div className="post-avatar" style={{background: post.owner.gradient}}>{post.owner.icon}</div>
                    <div className="post-user-info">
                      <span className="post-username">{post.owner.id}</span>
                      <span className="post-location">{post.owner.name}</span>
                    </div>
                  </div>
                  <button className="post-options">
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
                    onTouchStart={(e) => {
                      const postId = post.id || -1;
                      setTouchStart({postId, x: e.targetTouches[0].clientX});
                    }}
                    onTouchMove={(e) => {
                      const postId = post.id || -1;
                      setTouchEnd({postId, x: e.targetTouches[0].clientX});
                    }}
                    onTouchEnd={() => {
                      if (!touchStart || !touchEnd || touchStart.postId !== post.id) return;

                      const distance = touchStart.x - touchEnd.x;
                      const isLeftSwipe = distance > 50;
                      const isRightSwipe = distance < -50;

                      const images = post.images || [];
                      const currentIndex = currentImageIndices[post.id || ''] || 0;

                      if (isLeftSwipe && currentIndex < images.length - 1) {
                        setCurrentImageIndices(prev => ({
                          ...prev,
                          [post.id || '']: currentIndex + 1
                        }));
                      }
                      if (isRightSwipe && currentIndex > 0) {
                        setCurrentImageIndices(prev => ({
                          ...prev,
                          [post.id || '']: currentIndex - 1
                        }));
                      }

                      setTouchStart(null);
                      setTouchEnd(null);
                    }}
                  >
                    <div
                      className="image-carousel-track"
                      style={{
                        transform: `translateX(-${(currentImageIndices[post.id || ''] || 0) * 100}%)`,
                        transition: touchStart?.postId === post.id && touchEnd?.postId === post.id ? 'none' : 'transform 0.3s ease-out'
                      }}
                    >
                      {(post.images || []).map((image, index) => (
                        <div key={index} className="image-placeholder">
                          <span><img src={image}/></span>
                        </div>
                      ))}
                    </div>

                    {/* Navigation Arrows */}
                    {(post.images && post.images.length > 1) && (
                      <>
                        {(currentImageIndices[post.id || ''] || 0) > 0 && (
                          <button
                            className="carousel-arrow carousel-arrow-left"
                            onClick={(e) => {
                              e.stopPropagation();
                              const currentIndex = currentImageIndices[post.id || ''] || 0;
                              setCurrentImageIndices(prev => ({
                                ...prev,
                                [post.id || '']: currentIndex - 1
                              }));
                            }}
                          >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M15 18l-6-6 6-6"/>
                            </svg>
                          </button>
                        )}
                        {(currentImageIndices[post.id || ''] || 0) < ((post.images?.length || 1) - 1) && (
                          <button
                            className="carousel-arrow carousel-arrow-right"
                            onClick={(e) => {
                              e.stopPropagation();
                              const currentIndex = currentImageIndices[post.id || ''] || 0;
                              setCurrentImageIndices(prev => ({
                                ...prev,
                                [post.id || '']: currentIndex + 1
                              }));
                            }}
                          >
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                              <path d="M9 18l6-6-6-6"/>
                            </svg>
                          </button>
                        )}
                      </>
                    )}

                    {/* Indicators */}
                    {(post.images && post.images.length > 1) && (
                      <div className="carousel-indicators">
                        {(post.images || []).map((_, index) => (
                          <button
                            key={index}
                            className={`indicator ${index === (currentImageIndices[post.id || ''] || 0) ? 'active' : ''}`}
                            onClick={(e) => {
                              e.stopPropagation();
                              setCurrentImageIndices(prev => ({
                                ...prev,
                                [post.id || '']: index
                              }));
                            }}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                </div>

                <div className="post-actions">
                  <div className="post-actions-left">
                    <button className={`action-btn ${post.isLiked ? 'liked' : ''}`} onClick={() => handleLikeToggleWrapper(post.id!!)}>
                      <svg viewBox="0 0 24 24" fill={post.isLiked ? '#ff3040' : 'none'} stroke="currentColor" strokeWidth="2">
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                      </svg>
                    </button>
                    <button className="action-btn">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
                      </svg>
                    </button>
                    <button className="action-btn">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="22" y1="2" x2="11" y2="13"/>
                        <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                      </svg>
                    </button>
                  </div>
                  <button className="action-btn">
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
                  {post.content !== undefined && (
                    <span className="caption-username-hyphen">-</span>
                  )}
                  <span className="caption-text">{post.content}</span>
                </div>

                <div className="post-timestamp">
                  <span>{new Date(post.createdAt).toUTCString().split(' ').slice(0, 4).join(' ')}</span>
                </div>
              </article>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default Feed;

