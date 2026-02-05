import React from 'react';
import { useNavigate } from 'react-router-dom';
import '../../styles/not-found.css';

const NotFound: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="not-found-page">
      <div className="not-found-content">
        <h1 className="not-found-title">Sorry. The page cannot be used.</h1>
        <p className="not-found-description">
          The link you clicked was incorrect or the page has been deleted.
        </p>
        <button className="not-found-link" onClick={() => navigate(-1)}>
          Go back
        </button>
      </div>
    </div>
  );
};

export default NotFound;
