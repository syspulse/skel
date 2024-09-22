import React, { useEffect } from 'react';
import { FaInfoCircle, FaExclamationTriangle, FaTimesCircle } from 'react-icons/fa';
import './Popup.css';

export type PopupLevel = 'info' | 'warning' | 'error';

interface PopupProps {
  title: string;
  message: string;
  level: PopupLevel;
  onClose: () => void;
}

const Popup: React.FC<PopupProps> = ({ title, message, level, onClose }) => {
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose();
    }, 3000);

    return () => clearTimeout(timer);
  }, [onClose]);

  const getIcon = () => {
    switch (level) {
      case 'info':
        return <FaInfoCircle className="popup-icon info" />;
      case 'warning':
        return <FaExclamationTriangle className="popup-icon warning" />;
      case 'error':
        return <FaTimesCircle className="popup-icon error" />;
    }
  };

  return (
    <div className={`popup ${level}`}>
      <div className="popup-header">
        {getIcon()}
        <h3>{title}</h3>
      </div>
      <p>{message}</p>
    </div>
  );
};

export default Popup;