import React, {useCallback, useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {User} from '../../types';
import {DIRECTION, ROUTES} from '../../constants';
import NotFound from "./NotFound";
import '../../styles/followings.css';
import Spinner from "../layout/Spinner";
import {me, users} from "../../constants/dummy";
import {useToggleFollowing} from "../../hooks/useToggleMutate";
import {scanUserFollows} from "../../api/actionbase";
import {BackArrowIcon, UserPlusIcon} from '../icons';

const Followers: React.FC = () => {
  const {id} = useParams()
  const owner = users.find(x => x.id === id);
  if (!owner) return <NotFound/>;

  const navigate = useNavigate();
  const [followings, setFollowings] = useState<User[]>([])
  const [suggestedFollowings, setSuggestedFollowings] = useState<User[]>([])
  const [isLoading, setIsLoading] = useState(true);
  const [followingStates, setFollowingStates] = useState<Record<string, boolean>>({});

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [ownerFolloweesPayload, myFollowingsPayload] = await Promise.all([
        scanUserFollows(owner.id, DIRECTION.IN),
        scanUserFollows(me.id, DIRECTION.OUT)
      ]);

      const ownerFollowees = ownerFolloweesPayload.edges
        .map(edge => users.find(u => u.id === edge.source))
        .filter((user): user is User => user !== undefined);
      setFollowings(ownerFollowees);

      const edgesByTarget = new Set(myFollowingsPayload.edges.map(e => e.target));
      const newFollowingStates = Object.fromEntries(users.filter(u => edgesByTarget.has(u.id)).map(u => [u.id, true]));
      setFollowingStates(newFollowingStates);

      const followingUsersSet = new Set(ownerFollowees.map(u => u.id));
      const suggested = users.filter(x => !followingUsersSet.has(x.id) && !x.isMe && !newFollowingStates[x.id]);
      setSuggestedFollowings(suggested);
    } catch (err) {
    } finally {
      setIsLoading(false);
    }
  }, [owner.id]);

  const {ToggleFollowing} = useToggleFollowing(
    me.id,
    {
      onSuccess: (isFollowing, followersCount, userId) => {
        setFollowingStates(prev => ({...prev, [userId]: isFollowing}));
      },
      onError: (error) => {
        console.error("Failed to toggle follow:", error);
      }
    });

  const handleFollowToggle = useCallback(async (userId: string) => {
    await ToggleFollowing(userId, followingStates[userId] ?? false);
  }, [followingStates, ToggleFollowing]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <div className="app mobile-content-inner" style={{position: 'relative'}}>
      {isLoading && <Spinner/>}
      <header className="followers-header">
        <button className="back-btn" onClick={() => navigate(-1)}>
          <BackArrowIcon />
        </button>
        <h1 className="header-title">Follower</h1>
      </header>

      {!isLoading && (
        <div className="mobile-content-inner-scroll">
          {followings.length === 0 ? (
              <div className="empty-state">
                <div className="empty-state-content">
                  <div className="empty-state-icon">
                    <UserPlusIcon />
                  </div>
                  <h2 className="empty-state-title">Followers</h2>
                  <p className="empty-state-description">All people who follow you will be displayed here.</p>
                </div>
              </div>
            ) : (
              <div className="followers-list" id="followers-list">
                {followings.map((following) => (
                  <div key={following.id} className="follower-item">
                    <div className="follower-info" onClick={() => navigate(ROUTES.PROFILE(following.id))}>
                      <div className="follower-avatar" style={{background: following.gradient}}>
                        <img src={following.avatar} alt={following.name} />
                      </div>
                      <div className="follower-details">
                        <div className="follower-username">{following.id}</div>
                        <div className="follower-name">{following.name}</div>
                      </div>
                    </div>
                    {following.id !== me.id && (
                      <button
                        className={`follow-action-btn ${followingStates[following.id] ? 'following' : 'follow'}`}
                        onClick={() => handleFollowToggle(following.id)}
                      >
                        {followingStates[following.id] ? 'Following' : 'Follow'}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
            <div>
              {suggestedFollowings.length > 0 && (
                <>
                  <div className="section-divider">
                    <h3 className="section-title">Suggested for you</h3>
                  </div>

                  {suggestedFollowings.map((suggested) =>
                    <div key={suggested.id} className="follower-item">
                      <div className="follower-info" onClick={() => navigate(ROUTES.PROFILE(suggested.id))}>
                        <div className="follower-avatar" style={{background: suggested.gradient}}>
                          <img src={suggested.avatar} alt={suggested.name} />
                        </div>
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
        </div>
      )}
    </div>
  );
};

export default Followers;
