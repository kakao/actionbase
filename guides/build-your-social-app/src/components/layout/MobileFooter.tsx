import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import '../../styles/mobile-footer.css';
import { me } from '../../constants/dummy';
import { useToast } from '../../contexts/ToastContext';

const MobileFooter: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const isActive = (path: string) => location.pathname === path;

  return (
    <div className="bottom-nav">
      <button
        id="nav-btn-feed"
        className={`nav-btn ${isActive('/') ? 'active' : ''}`}
        onClick={() => navigate('/')}
      >
        {isActive('/') ? (
          <svg
            viewBox="0 0 24 24"
            fill="currentColor"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="butt"
            strokeLinejoin="miter"
          >
            <path
              fillRule="evenodd"
              clipRule="evenodd"
              d="M12 3L3 10V19C3 19.5304 3.21071 20.0391 3.58579 20.4142C3.96086 20.7893 4.46957 21 5 21H9V12H15V21H19C19.5304 21 20.0391 20.7893 20.4142 20.4142C20.7893 20.0391 21 19.5304 21 19V10L12 3Z"
            />
          </svg>
        ) : (
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="butt"
            strokeLinejoin="miter"
          >
            <path d="M3 10L12 3L21 10V19C21 19.5304 20.7893 20.0391 20.4142 20.4142C20.0391 20.7893 19.5304 21 19 21H15V12H9V21H5C4.46957 21 3.96086 20.7893 3.58579 20.4142C3.21071 20.0391 3 19.5304 3 19V10Z" />
          </svg>
        )}
      </button>

      <button
        id="nav-btn-search"
        className={`nav-btn ${isActive('/search') ? 'active' : ''}`}
        onClick={() => navigate('/search')}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={isActive('/search') ? '3' : '2'}
        >
          <circle cx="11" cy="11" r="8" />
          <path d="m21 21-4.35-4.35" />
        </svg>
      </button>

      <button className="nav-btn" onClick={() => showToast('Unsupported')}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="2" y="2" width="20" height="20" rx="5" />
          <circle cx="12" cy="12" r="3" />
          <path d="M12 8v8M8 12h8" />
        </svg>
      </button>

      <button className="nav-btn" onClick={() => showToast('Unsupported')}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <rect x="2" y="2" width="20" height="20" rx="2" />
          <polygon points="10 8 16 12 10 16 10 8" fill="currentColor" />
        </svg>
      </button>

      <button
        id="nav-btn-profile"
        className={`nav-btn ${isActive('/profile/doki') ? 'active' : ''}`}
        onClick={() => navigate('/profile/' + me.id)}
      >
        <div className="nav-avatar" style={{ background: me.gradient }}>
          <img src={me.avatar} alt={me.name} />
        </div>
      </button>
    </div>
  );
};

export default MobileFooter;
