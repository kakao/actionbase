import React, {useCallback, useEffect, useRef, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {TouchPosition, UserPost} from '../../types';
import {formatDate} from '../../utils/date';
import {count, get, scanUserPosts} from '../../api/actionbase';
import {DATABASE, DIRECTION, ROUTES, TABLE, UI} from '../../constants';
import {calculateImageIndex, shouldTriggerSwipe} from '../../utils/image';
import NotFound from "./NotFound";
import '../../styles/post.css';
import {useToggleLike} from "../../hooks/useToggleMutate";
import Spinner from "../layout/Spinner";
import {me, postDetails, users} from "../../constants/dummy";
import {useToast} from "../../contexts/ToastContext";

const Post: React.FC = () => {
  const {id} = useParams();
  const post = postDetails.find(p => String(p.id) === id);
  const {showToast} = useToast();
  if (!post) {
    return <NotFound/>;
  }

  const navigate = useNavigate();
  const [userPost, setUserPost] = useState<UserPost | null>(null);
  const [isLiked, setIsLiked] = useState<boolean>(false);
  const [likesCount, setLikesCount] = useState<number>(0);
  const [currentImageIndex, setCurrentImageIndex] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const touchRef = useRef<TouchPosition>({start: null, end: null});
  const [hasError, setHasError] = useState<boolean>(false);

  const {toggleLike: toggleLike} = useToggleLike(
    me.id,
    {
      onSuccess: (newIsLiked, newLikes) => {
        setIsLiked(newIsLiked);
        setLikesCount(newLikes);
      },
      onError: console.error
    });

  const handleLikeToggle = useCallback(async () => {
    if (userPost?.id) await toggleLike(userPost.id, isLiked);
  }, [userPost?.id, isLiked, toggleLike]);

  const fetchPost = async () => {
    try {
      const userPostPayload = await scanUserPosts(String(post.id), DIRECTION.IN);
      const posts = userPostPayload.edges.map(edge => ({
        owner: users.find(u => u.id === edge.source) || users[0],
        id: edge.target,
        images: post.imageUrls || [],
        content: post.content || '',
        likes: 0,
        createdAt: formatDate(edge.properties["createdAt"]),
      }));

      if (posts.length > 0) {
        const postData = posts[0];
        setUserPost(postData);

        const [likeCountPayload, userLikePayload] = await Promise.all([
          count(DATABASE.SOCIAL, TABLE.USER_LIKES, postData.id, DIRECTION.IN),
          get(DATABASE.SOCIAL, TABLE.USER_LIKES, me.id, postData.id)
        ]);
        const likesCount = likeCountPayload.counts[0]?.count ?? 0;
        const isLiked = userLikePayload.count > 0;

        setLikesCount(likesCount);
        setIsLiked(isLiked);
      }
    } catch (err) {
      console.error("Failed to fetch post:", err);
      setHasError(true);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!post) {
      setIsLoading(false);
      return;
    }
    setCurrentImageIndex(0);

    fetchPost();
  }, [id, post?.id, post?.imageUrls, post?.content]);

  useEffect(() => {
    window.addEventListener('reload', fetchPost as EventListener);
    return () => {
      window.removeEventListener('reload', fetchPost as EventListener);
    };
  }, []);

  if (hasError) {
    return <NotFound/>;
  }

  return (
    <div className="app" style={{position: 'relative', height: '100%'}}>
      {isLoading && <Spinner/>}
      {/* Header */}
      <header className="detail-header">
        <button className="back-btn" onClick={() => navigate(-1)}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
        <h1 className="page-title">Posts</h1>
        <button className="menu-btn" onClick={() => showToast('Unsupported')}>
          <svg viewBox="0 0 24 24" fill="currentColor">
            <circle cx="12" cy="5" r="1.5"/>
            <circle cx="12" cy="12" r="1.5"/>
            <circle cx="12" cy="19" r="1.5"/>
          </svg>
        </button>
      </header>

      {/* Post Detail Content */}
      {!isLoading && (
        <div className="post-detail-container">
          <div className="post-detail-header">
            <div className="author-info" onClick={() => navigate(ROUTES.PROFILE(userPost?.owner.id || ''))}>
              <div className="author-avatar" style={{background: userPost?.owner.gradient}}>{userPost?.owner.icon}</div>
              <span className="author-name">{userPost?.owner.name}</span>
            </div>
          </div>
          <div className="post-detail-image">
            <div
              className="image-carousel"
              onTouchStart={(e) => touchRef.current.start = e.targetTouches[0].clientX}
              onTouchMove={(e) => touchRef.current.end = e.targetTouches[0].clientX}
              onTouchEnd={() => {
                const {start, end} = touchRef.current;
                if (!start || !end) return;
                const distance = start - end;
                const images = userPost?.images || [];
                if (shouldTriggerSwipe(distance, UI.SWIPE_THRESHOLD)) {
                  const maxIndex = images.length - 1;
                  if (distance > UI.SWIPE_THRESHOLD && currentImageIndex < maxIndex) {
                    setCurrentImageIndex(calculateImageIndex(currentImageIndex, 1, maxIndex));
                  } else if (distance < -UI.SWIPE_THRESHOLD && currentImageIndex > 0) {
                    setCurrentImageIndex(calculateImageIndex(currentImageIndex, -1, maxIndex));
                  }
                }
                touchRef.current = {start: null, end: null};
              }}
            >
              <div
                className="image-carousel-track"
                style={{
                  transform: `translateX(-${currentImageIndex * 100}%)`,
                  transition: touchRef.current.start && touchRef.current.end ? 'none' : `transform ${UI.CAROUSEL_TRANSITION_DURATION}ms ease-out`
                }}
              >
                {(userPost?.images || []).map((image, index) => (
                  <div key={index} className="image-content" style={{background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'}}>
                  <span className="main-icon">
                    <img src={image}/>
                  </span>
                  </div>
                ))}
              </div>

              {userPost?.images && userPost.images.length > 1 && (
                <>
                  {currentImageIndex > 0 && (
                    <button className="carousel-arrow carousel-arrow-left" onClick={() => setCurrentImageIndex(currentImageIndex - 1)}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M15 18l-6-6 6-6"/>
                      </svg>
                    </button>
                  )}
                  {currentImageIndex < userPost.images.length - 1 && (
                    <button className="carousel-arrow carousel-arrow-right" onClick={() => setCurrentImageIndex(currentImageIndex + 1)}>
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M9 18l6-6-6-6"/>
                      </svg>
                    </button>
                  )}
                  <div className="carousel-indicators">
                    {userPost.images.map((_, index) => (
                      <button key={index} className={`indicator ${index === currentImageIndex ? 'active' : ''}`} onClick={() => setCurrentImageIndex(index)}/>
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>
          <div className="post-detail-actions" id="post-detail-actions">
            <div className="action-buttons-wrapper">
              <div className="actions-left">
                <button id="btn-likes" className={`action-icon ${isLiked ? 'liked' : ''}`} onClick={handleLikeToggle}>
                  <svg viewBox="0 0 24 24" fill={isLiked ? '#ff3040' : 'none'} stroke="currentColor" strokeWidth="2">
                    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                  </svg>
                </button>
                <button className="action-icon" onClick={() => showToast('Unsupported')}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
                    <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
                  </svg>
                </button>
                <button className="action-icon" onClick={() => showToast('Unsupported')}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"/>
                    <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                  </svg>
                </button>
              </div>
              <button className="action-icon action-bookmark" onClick={() => showToast('Unsupported')}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
            </div>
          </div>

          <div className="likes-count-section">
            <span className="likes-text">{likesCount.toLocaleString()} likes</span>
          </div>

          <div className="post-info">
            <div className="caption-section">
              <p className="caption-content">
                <span className="caption-author">
                  {userPost?.owner?.id}
                  {userPost?.content && <span className="caption-author-hyphen">-</span>}
                </span>
                <span>{userPost?.content}</span>
              </p>
            </div>
          </div>
          <div className="post-timestamp">{userPost?.createdAt}</div>
        </div>
      )}
    </div>
  );
};

export default Post;

