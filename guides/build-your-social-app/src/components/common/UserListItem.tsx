import React, {memo} from 'react';
import {User} from '../../types';

interface UserListItemProps {
  user: User;
  isFollowing?: boolean;
  showFollowButton?: boolean;
  subtitle?: string;
  onUserClick?: (userId: string) => void;
  onFollowClick?: (userId: string) => void;
}

const UserListItem: React.FC<UserListItemProps> = ({
  user,
  isFollowing = false,
  showFollowButton = true,
  subtitle,
  onUserClick,
  onFollowClick,
}) => {
  return (
    <div className="follower-item">
      <div
        className="follower-info"
        onClick={() => onUserClick?.(user.id)}
        style={{cursor: onUserClick ? 'pointer' : 'default'}}
      >
        <div className="follower-avatar" style={{background: user.gradient}}>
          <img src={user.avatar} alt={user.name} />
        </div>
        <div className="follower-details">
          <div className="follower-username">{user.id}</div>
          <div className="follower-name">{user.name}</div>
          {subtitle && <div className="follower-subtitle">{subtitle}</div>}
        </div>
      </div>
      {showFollowButton && (
        <button
          className={`follow-action-btn ${isFollowing ? 'following' : 'follow'}`}
          onClick={() => onFollowClick?.(user.id)}
        >
          {isFollowing ? 'Following' : 'Follow'}
        </button>
      )}
    </div>
  );
};

export default memo(UserListItem);
