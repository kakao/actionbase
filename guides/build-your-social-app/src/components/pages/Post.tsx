import React, {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useDriver} from "../../modules/DriverContext";
import {useStepAutoAdvance} from "../../modules/useStepAutoAdvance";

import '../../styles/post.css';
import {postDetails, User, users} from "../../modules/dummy";
import {scan} from "../../api/scan";
import NotFound from "./NotFound";
import {count} from "../../api/count";
import {get} from "../../api/get";
import {useLikeToggle} from "../../modules/useLikeToggle";
import Spinner from "../common/Spinner";

export const TOGGLE_LIKE = 'toggleLike'

interface UserPost {
  owner: User,
  id: number,
  images: string[],
  content: string,
  likes: number,
  createdAt: string
}

const Post: React.FC = () => {
  const navigate = useNavigate();
  const {id} = useParams()
  const post = postDetails.find(p => p.id === Number(id))
  if (!post) {
    return <NotFound/>;
  }

  const [userPost, setUserPost] = useState<UserPost>();
  const [isLiked, setIsLiked] = useState(false);
  const [likesCount, setLikesCount] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const [touchEnd, setTouchEnd] = useState<number | null>(null);

  const {handleLikeToggle: toggleLike} = useLikeToggle("doki", {
    onSuccess: async (newIsLiked, newLikes, postId) => {
      setIsLiked(newIsLiked);
      setLikesCount(newLikes);
    },
    onError: (error) => {
      console.error("Failed to toggle like:", error);
    }
  });

  const handleLikeToggle = async () => {
    if (!userPost?.id) return;
    await toggleLike(userPost.id, isLiked);
  };

  const {registerCallback} = useDriver();

  useEffect(() => {
    registerCallback(TOGGLE_LIKE, handleLikeToggle);
  }, [registerCallback, isLiked]);

  useStepAutoAdvance([12]);

  useEffect(() => {
    setCurrentImageIndex(0); // Reset image index when post changes
    const userPost = async () => {
      try {
        const userPostPayload = await scan(
          "social",
          "user_posts",
          "created_at_desc",
          post?.id!!,
          "IN",
          25,
          undefined
        )

        const posts = userPostPayload.edges
          .map(edge => {
              return {
                owner: users.find(u => u.id === edge.source),
                id: edge.target,
                images: post.imageUrls,
                content: post.content,
                likes: 0,
                createdAt: new Date(edge.properties["createdAt"]).toUTCString().split(' ').slice(0, 4).join(' '),
              }
            }
          )

        if (posts.length > 0) {
          const post = posts[0]
          setUserPost(post)

          const postLikePayload = await count(
            "social",
            "user_likes",
            post.id,
            "IN"
          )
          setLikesCount(postLikePayload.counts[0].count)

          const myLikeCountOfPost = await get(
            "social",
            "user_likes",
            "doki",
            post.id
          )
          if (myLikeCountOfPost.count > 0) {
            setIsLiked(true)
          }
        }
      } catch (err) {
        return <NotFound/>;
      } finally {
        setIsLoading(false);
      }
    };
    userPost()
  }, [id]);

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
        <button className="menu-btn">
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
          {/* Post Header */}
          <div className="post-detail-header">
            <div className="author-info" onClick={() => navigate("/profile/" + userPost?.owner.id)}>
              <div className="author-avatar" style={{background: userPost?.owner.gradient}}>{userPost?.owner.icon}</div>
              <span className="author-name">{userPost?.owner.name}</span>
            </div>
            {/*<button className="follow-btn">Follow</button>*/}
          </div>

          {/* Post Image Carousel */}
          <div className="post-detail-image">
            <div
              className="image-carousel"
              onTouchStart={(e) => setTouchStart(e.targetTouches[0].clientX)}
              onTouchMove={(e) => setTouchEnd(e.targetTouches[0].clientX)}
              onTouchEnd={() => {
                if (!touchStart || !touchEnd) return;
                const distance = touchStart - touchEnd;
                const isLeftSwipe = distance > 50;
                const isRightSwipe = distance < -50;

                const images = userPost?.images || [];

                if (isLeftSwipe && currentImageIndex < images.length - 1) {
                  setCurrentImageIndex(currentImageIndex + 1);
                }
                if (isRightSwipe && currentImageIndex > 0) {
                  setCurrentImageIndex(currentImageIndex - 1);
                }

                setTouchStart(null);
                setTouchEnd(null);
              }}
            >
              <div
                className="image-carousel-track"
                style={{
                  transform: `translateX(-${currentImageIndex * 100}%)`,
                  transition: touchStart && touchEnd ? 'none' : 'transform 0.3s ease-out'
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

              {/* Navigation Arrows */}
              {(userPost?.images && userPost.images.length > 1) && (
                <>
                  {currentImageIndex > 0 && (
                    <button
                      className="carousel-arrow carousel-arrow-left"
                      onClick={() => setCurrentImageIndex(currentImageIndex - 1)}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M15 18l-6-6 6-6"/>
                      </svg>
                    </button>
                  )}
                  {currentImageIndex < ((userPost?.images?.length || 1) - 1) && (
                    <button
                      className="carousel-arrow carousel-arrow-right"
                      onClick={() => setCurrentImageIndex(currentImageIndex + 1)}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M9 18l6-6-6-6"/>
                      </svg>
                    </button>
                  )}
                </>
              )}

              {/* Indicators */}
              {(userPost?.images && userPost.images.length > 1) && (
                <div className="carousel-indicators">
                  {(userPost?.images || []).map((_, index) => (
                    <button
                      key={index}
                      className={`indicator ${index === currentImageIndex ? 'active' : ''}`}
                      onClick={() => setCurrentImageIndex(index)}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Post Actions */}
          <div className="post-detail-actions" id="post-detail-actions">
            <div className="action-buttons-wrapper">
              <div className="actions-left">
                <button className={`action-icon ${isLiked ? 'liked' : ''}`} onClick={handleLikeToggle}>
                  <svg viewBox="0 0 24 24" fill={isLiked ? '#ff3040' : 'none'} stroke="currentColor" strokeWidth="2">
                    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
                  </svg>
                </button>

                <button className="action-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
                    <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
                  </svg>
                </button>

                <button className="action-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"/>
                    <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                  </svg>
                </button>
              </div>

              <button className="action-icon action-bookmark">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
            </div>

            <div className="likes-count-section">
              <span className="likes-text">{likesCount.toLocaleString()} likes</span>
            </div>
          </div>

          {/* Post Info */}
          <div className="post-info">
            <div className="caption-section">
              <p className="caption-content">
              <span className="caption-author">
                {userPost?.owner?.id}
                {userPost?.content !== undefined && (<span className="caption-author-hyphen">-</span>)}
              </span>
                <span>{userPost?.content}</span>
              </p>
            </div>

            {/*<div className="comments-section">*/}
            {/*  <div className="comment">*/}
            {/*    <div className="comment-content">*/}
            {/*      <span className="comment-author">mattilynonkertuna</span>*/}
            {/*      <span className="comment-text">Moo!!</span>*/}
            {/*    </div>*/}
            {/*    <button className="comment-like">*/}
            {/*      <svg viewBox="0 0 24 24" fill="#ff3040" stroke="currentColor" strokeWidth="2">*/}
            {/*        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>*/}
            {/*      </svg>*/}
            {/*    </button>*/}
            {/*  </div>*/}
            {/*</div>*/}

            <div className="post-timestamp">
              {userPost?.createdAt}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Post;

