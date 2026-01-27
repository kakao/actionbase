import React, { useState } from 'react';
import { ROUTES } from '../../constants';
import '../../styles/search.css';
import { users } from '../../constants/dummy';
import { useNavigate } from 'react-router-dom';

const Search: React.FC = () => {
  const [searchValue, setSearchValue] = useState('');
  const navigate = useNavigate();

  return (
    <div className="app">
      <header className="search-header">
        <div className="search-input-wrapper">
          <svg
            className="search-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            type="text"
            className="search-input"
            placeholder="Search"
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
          />
          <button className="clear-btn" onClick={() => setSearchValue('')}>
            <svg viewBox="0 0 24 24" fill="currentColor">
              <circle cx="12" cy="12" r="10" fill="#c7c7c7" />
              <path d="M15 9l-6 6M9 9l6 6" stroke="#fff" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        <button className="cancel-btn" onClick={() => setSearchValue('')}>
          Cancel
        </button>
      </header>
      <div className="search-scroll">
        <div className="search-content">
          <div className="recent-section" id="search-results-list">
            <div className="search-results-list">
              {users
                .filter((x) => !x.isMe)
                .map((user, index) => (
                  <div
                    key={user.id}
                    className="search-result-item"
                    id={`searched-user-${index}`}
                    onClick={() => navigate(ROUTES.PROFILE(user.id))}
                  >
                    <div className="result-avatar" style={{ background: user.gradient }}>
                      <span className="avatar-text">
                        <img src={user.avatar} alt={user.name} />
                      </span>
                    </div>
                    <div className="result-info" id={user.id}>
                      <span className="result-name">{user.id}</span>
                      <span className="result-subtitle">{user.name}</span>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Search;
