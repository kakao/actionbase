import React from 'react';

interface IconProps {
  className?: string;
  size?: number;
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  style?: React.CSSProperties;
}

const defaultProps: IconProps = {
  size: 24,
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
};

export const BackArrowIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} {...props}>
    <path d="M19 12H5M12 19l-7-7 7-7"/>
  </svg>
);

export const HeartIcon: React.FC<IconProps & { filled?: boolean }> = ({
  size = defaultProps.size,
  filled = false,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill={filled ? '#ff3040' : 'none'} stroke={stroke} strokeWidth={strokeWidth} {...props}>
    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
  </svg>
);

export const CommentIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinejoin="round" {...props}>
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
  </svg>
);

export const ShareIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <line x1="22" y1="2" x2="11" y2="13"/>
    <polygon points="22 2 15 22 11 13 2 9 22 2"/>
  </svg>
);

export const BookmarkIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
  </svg>
);

export const ChevronDownIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} {...props}>
    <path d="M6 9l6 6 6-6"/>
  </svg>
);

export const ChevronRightIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} {...props}>
    <path d="M9 18l6-6-6-6"/>
  </svg>
);

export const SearchIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <circle cx="11" cy="11" r="8"/>
    <path d="m21 21-4.35-4.35"/>
  </svg>
);

export const SettingsIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  fill = 'currentColor',
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill={fill} {...props}>
    <path d="M12 2C11.172 2 10.5 2.672 10.5 3.5V4.341C9.672 4.541 8.891 4.875 8.184 5.325L7.525 4.666C6.947 4.088 6.009 4.088 5.431 4.666L4.666 5.431C4.088 6.009 4.088 6.947 4.666 7.525L5.325 8.184C4.875 8.891 4.541 9.672 4.341 10.5H3.5C2.672 10.5 2 11.172 2 12C2 12.828 2.672 13.5 3.5 13.5H4.341C4.541 14.328 4.875 15.109 5.325 15.816L4.666 16.475C4.088 17.053 4.088 17.991 4.666 18.569L5.431 19.334C6.009 19.912 6.947 19.912 7.525 19.334L8.184 18.675C8.891 19.125 9.672 19.459 10.5 19.659V20.5C10.5 21.328 11.172 22 12 22C12.828 22 13.5 21.328 13.5 20.5V19.659C14.328 19.459 15.109 19.125 15.816 18.675L16.475 19.334C17.053 19.912 17.991 19.912 18.569 19.334L19.334 18.569C19.912 17.991 19.912 17.053 19.334 16.475L18.675 15.816C19.125 15.109 19.459 14.328 19.659 13.5H20.5C21.328 13.5 22 12.828 22 12C22 11.172 21.328 10.5 20.5 10.5H19.659C19.459 9.672 19.125 8.891 18.675 8.184L19.334 7.525C19.912 6.947 19.912 6.009 19.334 5.431L18.569 4.666C17.991 4.088 17.053 4.088 16.475 4.666L15.816 5.325C15.109 4.875 14.328 4.541 13.5 4.341V3.5C13.5 2.672 12.828 2 12 2ZM12 8C14.209 8 16 9.791 16 12C16 14.209 14.209 16 12 16C9.791 16 8 14.209 8 12C8 9.791 9.791 8 12 8Z"/>
  </svg>
);

export const MenuDotsIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  fill = 'currentColor',
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill={fill} {...props}>
    <circle cx="12" cy="5" r="1.5"/>
    <circle cx="12" cy="12" r="1.5"/>
    <circle cx="12" cy="19" r="1.5"/>
  </svg>
);

export const GridIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  fill = 'currentColor',
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill={fill} {...props}>
    <rect x="3" y="3" width="7" height="7"/>
    <rect x="14" y="3" width="7" height="7"/>
    <rect x="3" y="14" width="7" height="7"/>
    <rect x="14" y="14" width="7" height="7"/>
  </svg>
);

export const CameraIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = 1.5,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
    <circle cx="12" cy="13" r="4"/>
  </svg>
);

export const UserIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = defaultProps.strokeWidth,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} {...props}>
    <circle cx="12" cy="8" r="4"/>
    <path d="M3 21v-2a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v2"/>
  </svg>
);

export const UserPlusIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <circle cx="14" cy="9" r="3"/>
    <path d="M8 19c0-3.314 2.686-6 6-6s6 2.686 6 6"/>
    <line x1="3" y1="12" x2="7" y2="12" strokeWidth="1"/>
    <line x1="5" y1="10" x2="5" y2="14" strokeWidth="1"/>
  </svg>
);

export const PlusCircleIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  fill = 'currentColor',
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill={fill} {...props}>
    <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
    <path d="M12 8v8M8 12h8" stroke="white" strokeWidth="2"/>
  </svg>
);

export const HomeIcon: React.FC<IconProps & { filled?: boolean }> = ({
  size = defaultProps.size,
  filled = false,
  stroke = defaultProps.stroke,
  strokeWidth = 1.5,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill={filled ? 'currentColor' : 'none'} stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="butt" strokeLinejoin="miter" {...props}>
    <path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z"/>
  </svg>
);

export const CheckIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = 2.5,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);

export const XIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = 2.5,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <line x1="18" y1="6" x2="6" y2="18"/>
    <line x1="6" y1="6" x2="18" y2="18"/>
  </svg>
);

export const InfoIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = 2.5,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <circle cx="12" cy="12" r="10"/>
    <line x1="12" y1="16" x2="12" y2="12"/>
    <line x1="12" y1="8" x2="12.01" y2="8"/>
  </svg>
);

export const AlertIcon: React.FC<IconProps> = ({
  size = defaultProps.size,
  stroke = defaultProps.stroke,
  strokeWidth = 2.5,
  ...props
}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
    <line x1="12" y1="9" x2="12" y2="13"/>
    <line x1="12" y1="17" x2="12.01" y2="17"/>
  </svg>
);
