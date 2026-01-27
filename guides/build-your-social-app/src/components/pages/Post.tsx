import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { TouchPosition, UserPost } from '../../types';
import { formatDate } from '../../utils/date';
import { count, get, scanUserPosts } from '../../api/actionbase';
import { DATABASE, DIRECTION, ROUTES, TABLE, UI } from '../../constants';
import { calculateImageIndex, shouldTriggerSwipe } from '../../utils/image';
import NotFound from './NotFound';
import '../../styles/post.css';
import { useToggleLike } from '../../hooks/useToggleMutate';
import Spinner from '../layout/Spinner';
import { me, postDetails, users } from '../../constants/dummy';
import { useToast } from '../../contexts/ToastContext';
import {
  BackArrowIcon,
  MenuDotsIcon,
  HeartIcon,
  CommentIcon,
  ShareIcon,
  BookmarkIcon,
  ChevronRightIcon,
} from '../icons';

const Post: React.FC = () => {
  const { id } = useParams();
  const post = postDetails.find((p) => String(p.id) === id);
  const { showToast } = useToast();
  if (!post) {
    return <NotFound />;
  }

  const navigate = useNavigate();
  const [userPost, setUserPost] = useState<UserPost | null>(null);
  const [isLiked, setIsLiked] = useState<boolean>(false);
  const [likesCount, setLikesCount] = useState<number>(0);
  const [currentImageIndex, setCurrentImageIndex] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const touchRef = useRef<TouchPosition>({ start: null, end: null });
  const [hasError, setHasError] = useState<boolean>(false);

  const { toggleLike: toggleLike } = useToggleLike(me.id, {
    onSuccess: (newIsLiked, newLikes) => {
      setIsLiked(newIsLiked);
      setLikesCount(newLikes);
    },
    onError: console.error,
  });

  const handleLikeToggle = useCallback(async () => {
    if (userPost?.id) await toggleLike(userPost.id, isLiked);
  }, [userPost?.id, isLiked, toggleLike]);

  const fetchPost = async () => {
    try {
      const userPostPayload = await scanUserPosts(String(post.id), DIRECTION.IN);
      const posts = userPostPayload.edges.map((edge) => ({
        owner: users.find((u) => u.id === edge.source) || users[0],
        id: edge.target,
        images: post.imageUrls || [],
        content: post.content || '',
        likes: 0,
        createdAt: formatDate(edge.properties['createdAt']),
      }));

      if (posts.length > 0) {
        const postData = posts[0];
        setUserPost(postData);

        const [likeCountPayload, userLikePayload] = await Promise.all([
          count(DATABASE.SOCIAL, TABLE.USER_LIKES, postData.id, DIRECTION.IN),
          get(DATABASE.SOCIAL, TABLE.USER_LIKES, me.id, postData.id),
        ]);
        const likesCount = likeCountPayload.counts[0]?.count ?? 0;
        const isLiked = userLikePayload.count > 0;

        setLikesCount(likesCount);
        setIsLiked(isLiked);
      }
    } catch (err) {
      console.error('Failed to fetch post:', err);
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
    return <NotFound />;
  }

  return (
    <div className="app" style={{ position: 'relative', height: '100%' }}>
      {isLoading && <Spinner />}
      {/* Header */}
      <header className="detail-header">
        <button className="back-btn" onClick={() => navigate(-1)}>
          <BackArrowIcon />
        </button>
        <h1 className="page-title">Posts</h1>
        <button className="menu-btn" onClick={() => showToast('Unsupported')}>
          <MenuDotsIcon />
        </button>
      </header>

      {/* Post Detail Content */}
      {!isLoading && (
        <div className="post-detail-container">
          <div className="post-detail-header">
            <div
              className="author-info"
              onClick={() => navigate(ROUTES.PROFILE(userPost?.owner.id || ''))}
            >
              <div className="author-avatar" style={{ background: userPost?.owner.gradient }}>
                <img src={userPost?.owner.avatar} alt={userPost?.owner.name} />
              </div>
              <span className="author-name">{userPost?.owner.name}</span>
            </div>
          </div>
          <div className="post-detail-image">
            <div
              className="image-carousel"
              onTouchStart={(e) => (touchRef.current.start = e.targetTouches[0].clientX)}
              onTouchMove={(e) => (touchRef.current.end = e.targetTouches[0].clientX)}
              onTouchEnd={() => {
                const { start, end } = touchRef.current;
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
                touchRef.current = { start: null, end: null };
              }}
            >
              <div
                className="image-carousel-track"
                style={{
                  transform: `translateX(-${currentImageIndex * 100}%)`,
                  transition:
                    touchRef.current.start && touchRef.current.end
                      ? 'none'
                      : `transform ${UI.CAROUSEL_TRANSITION_DURATION}ms ease-out`,
                }}
              >
                {(userPost?.images || []).map((image, index) => (
                  <div
                    key={index}
                    className="image-content"
                    style={{ background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}
                  >
                    <span className="main-icon">
                      <img src={image} />
                    </span>
                  </div>
                ))}
              </div>

              {userPost?.images && userPost.images.length > 1 && (
                <>
                  {currentImageIndex > 0 && (
                    <button
                      className="carousel-arrow carousel-arrow-left"
                      onClick={() => setCurrentImageIndex(currentImageIndex - 1)}
                    >
                      <ChevronRightIcon style={{ transform: 'rotate(180deg)' }} />
                    </button>
                  )}
                  {currentImageIndex < userPost.images.length - 1 && (
                    <button
                      className="carousel-arrow carousel-arrow-right"
                      onClick={() => setCurrentImageIndex(currentImageIndex + 1)}
                    >
                      <ChevronRightIcon />
                    </button>
                  )}
                  <div className="carousel-indicators">
                    {userPost.images.map((_, index) => (
                      <button
                        key={index}
                        className={`indicator ${index === currentImageIndex ? 'active' : ''}`}
                        onClick={() => setCurrentImageIndex(index)}
                      />
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>
          <div className="post-detail-actions" id="post-detail-actions">
            <div className="action-buttons-wrapper">
              <div className="actions-left">
                <button
                  id="btn-likes"
                  className={`action-icon ${isLiked ? 'liked' : ''}`}
                  onClick={handleLikeToggle}
                >
                  <HeartIcon filled={isLiked} />
                </button>
                <button className="action-icon" onClick={() => showToast('Unsupported')}>
                  <CommentIcon />
                </button>
                <button className="action-icon" onClick={() => showToast('Unsupported')}>
                  <ShareIcon />
                </button>
              </div>
              <button
                className="action-icon action-bookmark"
                onClick={() => showToast('Unsupported')}
              >
                <BookmarkIcon />
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
