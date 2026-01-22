export const STORAGE_KEYS = {
  STEP_INDEX: 'active-step-index',
  COMMAND_HISTORY: 'command-history',
  TERMINAL_CONTEXT: 'terminal-context',
  RESTART_NOTICE: 'show-restart-notice',
} as const;

export function getStorageItem<T>(key: string, defaultValue: T): T {
  try {
    const stored = localStorage.getItem(key);
    if (stored === null) {
      return defaultValue;
    }
    return JSON.parse(stored) as T;
  } catch (error) {
    console.error(`Failed to get ${key} from localStorage:`, error);
    return defaultValue;
  }
}

export function setStorageItem<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (error) {
    console.error(`Failed to set ${key} in localStorage:`, error);
  }
}

export function removeStorageItem(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch (error) {
    console.error(`Failed to remove ${key} from localStorage:`, error);
  }
}

export function getStorageNumber(key: string, defaultValue: number): number {
  try {
    const stored = localStorage.getItem(key);
    if (stored === null) {
      return defaultValue;
    }
    const parsed = parseInt(stored, 10);
    return isNaN(parsed) ? defaultValue : parsed;
  } catch (error) {
    console.error(`Failed to get ${key} from localStorage:`, error);
    return defaultValue;
  }
}

export function setStorageNumber(key: string, value: number): void {
  try {
    localStorage.setItem(key, value.toString());
  } catch (error) {
    console.error(`Failed to set ${key} in localStorage:`, error);
  }
}
