// SVG icon constants for reuse across the guide
// Each icon uses viewBox="0 0 24 24" with stroke-based rendering

export const ICONS = {
  // Heart icon for likes
  heart: `<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>`,

  // Users icon for follows
  users: `<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>`,

  // User with plus icon for adding follows
  userPlus: `<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/>`,

  // Table/grid icon for tables
  table: `<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18"/><path d="M9 21V9"/>`,

  // Search icon
  search: `<circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>`,

  // Lightning bolt icon for precomputing
  lightning: `<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>`,

  // Home icon for feed
  home: `<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>`,

  // Package/database icon
  package: `<path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>`,

  // Checkmark icon
  check: `<polyline points="20 6 9 17 4 12"/>`,

  // Rocket icon for instant/fast operations
  rocket: `<path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"/><path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"/><path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"/><path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"/>`,

  // Bar chart icon for counts
  barChart: `<path d="M3 3v18h18"/><path d="M18 17V9"/><path d="M13 17V5"/><path d="M8 17v-3"/>`,

  // List icon
  list: `<path d="M8 6h13"/><path d="M8 12h13"/><path d="M8 18h13"/><path d="M3 6h.01"/><path d="M3 12h.01"/><path d="M3 18h.01"/>`,
} as const;

// Helper function to create a full SVG element string
export const createSvgIcon = (iconPath: string, className?: string): string => {
  const classAttr = className ? ` class="${className}"` : '';
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"${classAttr}>${iconPath}</svg>`;
};

// Pre-built SVG strings for common use cases
export const SVG_ICONS = {
  heart: createSvgIcon(ICONS.heart),
  users: createSvgIcon(ICONS.users),
  userPlus: createSvgIcon(ICONS.userPlus),
  table: createSvgIcon(ICONS.table),
  search: createSvgIcon(ICONS.search),
  lightning: createSvgIcon(ICONS.lightning),
  home: createSvgIcon(ICONS.home),
  package: createSvgIcon(ICONS.package),
  check: createSvgIcon(ICONS.check),
  rocket: createSvgIcon(ICONS.rocket),
  barChart: createSvgIcon(ICONS.barChart),
  list: createSvgIcon(ICONS.list),
} as const;
