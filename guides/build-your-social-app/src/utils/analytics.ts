const ANALYTICS_STORAGE_KEY = 'analytics-choice';
const UMAMI_SCRIPT_SRC = 'https://cloud.umami.is/script.js';
const UMAMI_WEBSITE_ID = 'ab2dbebd-9f39-4ad5-97c0-155a3b37593f';

export type AnalyticsChoice = 'yes' | 'no' | null;

export const getAnalyticsChoice = (): AnalyticsChoice => {
  try {
    const choice = localStorage.getItem(ANALYTICS_STORAGE_KEY);
    if (choice === 'yes' || choice === 'no') {
      return choice;
    }
  } catch (error) {
    console.error('Failed to get analytics choice:', error);
  }
  return null;
};

export const setAnalyticsChoice = (choice: 'yes' | 'no'): void => {
  try {
    localStorage.setItem(ANALYTICS_STORAGE_KEY, choice);
  } catch (error) {
    console.error('Failed to save analytics choice:', error);
  }
};

export const loadUmamiScript = (): void => {
  if (document.querySelector(`script[src="${UMAMI_SCRIPT_SRC}"]`)) {
    return;
  }

  const script = document.createElement('script');
  script.defer = true;
  script.src = UMAMI_SCRIPT_SRC;
  script.dataset.websiteId = UMAMI_WEBSITE_ID;
  document.head.appendChild(script);
};

export const initAnalytics = (): void => {
  const choice = getAnalyticsChoice();
  if (choice === 'yes') {
    loadUmamiScript();
  }
};

export const clearAnalyticsChoice = (): void => {
  try {
    localStorage.removeItem(ANALYTICS_STORAGE_KEY);
  } catch (error) {
    console.error('Failed to clear analytics choice:', error);
  }
};
