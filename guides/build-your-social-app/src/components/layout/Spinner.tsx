import React from 'react';
import '../../styles/spinner.css';

const Spinner: React.FC = () => {
  return (
    <div className="spinner-container">
      <div className="spinner">
        <div className="spinner-circle"></div>
      </div>
    </div>
  );
};

export default Spinner;
