import React, {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useStepAutoAdvance} from "../../modules/useStepAutoAdvance";

import '../../styles/followings.css';
import {scan} from "../../api/scan";
import {User, users} from "../../modules/dummy";
import NotFound from "./NotFound";
import Spinner from "../common/Spinner";
import {useFollowingToggle} from "../../modules/useFollowingToggle";

const Followings: React.FC = () => {
  const {id} = useParams()
  const owner = users.find(x => x.id === id)!!
  if (!owner) {
    return <NotFound/>;
  }

  const navigate = useNavigate();
  const [followings, setFollowings] = useState<User[]>([])
  const [suggestedFollowings, setSuggestedFollowings] = useState<User[]>([])
  const [isLoading, setIsLoading] = useState(true);
  const [followingStates, setFollowingStates] = useState<Record<string, boolean>>({});

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const ownerFollowingsPayload = await scan(
        "social",
        "user_follows",
        "created_at_desc",
        owner.id,
        "OUT",
        25,
        undefined
      )

      const ownerFollowings =
        ownerFollowingsPayload.edges
          .map(edge => (users.find(u => u.id === edge.target)))
          .filter((user): user is User => user !== undefined);
      setFollowings(ownerFollowings)

      const myFollowingsPayload = await scan(
        "social",
        "user_follows",
        "created_at_desc",
        "doki",
        "OUT",
        25,
        undefined
      )
      const edgesByTarget = Object.fromEntries(myFollowingsPayload.edges.map(e => [e.target, false]))
      const states: Record<string, boolean> = {};
      users.forEach(user => {
        if (edgesByTarget[user.id] !== undefined) {
          states[user.id] = true;
        }
      })
      setFollowingStates(states);

      const followingUsersSet = new Set(ownerFollowings);
      const suggested = users
        .filter(x => !followingUsersSet.has(x))
        .filter(x => !x.isMe)
        .filter(x => !states[x.id]);
      setSuggestedFollowings(suggested)
    } catch (err) {
    } finally {
      setIsLoading(false);
    }
  };

  const {handleFollowingToggle} = useFollowingToggle(
    "doki",
    {
      onSuccess: async (isFollowing, followersCount, userId) => {
        if (owner.isMe) {
          fetchData()
          return
        }
        setFollowingStates(prev => ({
          ...prev,
          [userId]: isFollowing
        }))
      },
      onError: (error) => {
        console.error("Failed to toggle follow:", error);
      }
    }
  );

  const handleFollowToggle = async (userId: string) => {
    const currentIsFollowing = followingStates[userId] ?? false;
    await handleFollowingToggle(userId, currentIsFollowing);
  };

  useStepAutoAdvance([17], [followings]);

  useEffect(() => {
    fetchData();
  }, [id]);

  return (
    <div className="app" style={{position: 'relative', height: '100%'}}>
      {isLoading && <Spinner/>}
      {/* Header */}
      <header className="followers-header">
        <button className="back-btn" onClick={() => navigate(-1)}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
        <h1 className="header-title">Following</h1>
      </header>

      {/* Followers List */}
      {!isLoading && (
        <>
          {followings.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-search">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8"/>
                  <path d="m21 21-4.35-4.35"/>
                </svg>
                <span>Search</span>
              </div>
              <div className="empty-state-content">
                <div className="empty-state-icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="14" cy="9" r="3"/>
                    <path d="M8 19c0-3.314 2.686-6 6-6s6 2.686 6 6"/>
                    <line x1="3" y1="12" x2="7" y2="12" strokeWidth="1"/>
                    <line x1="5" y1="10" x2="5" y2="14" strokeWidth="1"/>
                  </svg>
                </div>
                <h2 className="empty-state-title">Following</h2>
                <p className="empty-state-description">All people you follow will be displayed here.</p>
              </div>
            </div>
          ) : (
            <div className="followers-list" id="followers-list">
              {/* Actual Followers */}
              {followings.map((following) => (
                <div key={following.id} className="follower-item">
                  <div className="follower-info" onClick={() => navigate("/profile/" + following.id)}>
                    <div className="follower-avatar" style={{background: following.gradient}}>{following.icon}</div>
                    <div className="follower-details">
                      <div className="follower-username">{following.id}</div>
                      <div className="follower-name">{following.name}</div>
                    </div>
                  </div>
                  <button
                    className={`follow-action-btn ${followingStates[following.id] ? 'following' : 'follow'}`}
                    onClick={() => handleFollowToggle(following.id)}
                  >
                    {followingStates[following.id] ? 'Following' : 'Follow'}
                  </button>
                </div>
              ))}
            </div>
          )}
          <div>
            {/* Suggested Section */}
            {suggestedFollowings.length > 0 && (
              <>
                <div className="section-divider">
                  <h3 className="section-title">Suggested for you</h3>
                </div>

                {suggestedFollowings.map((suggested) =>
                  <div key={suggested.id} className="follower-item">
                    <div className="follower-info" onClick={() => navigate("/profile/" + suggested.id)}>
                      <div className="follower-avatar" style={{background: suggested.gradient}}>{suggested.icon}</div>
                      <div className="follower-details">
                        <div className="follower-username">{suggested.id}</div>
                        <div className="follower-name">{suggested.name}</div>
                        <div className="follower-subtitle">Suggested for you</div>
                      </div>
                    </div>
                    <button
                      className={`follow-action-btn ${followingStates[suggested.id] ? 'following' : 'follow'}`}
                      onClick={() => handleFollowToggle(suggested.id)}
                    >
                      {followingStates[suggested.id] ? 'Following' : 'Follow'}
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default Followings;


