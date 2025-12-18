export const calculateImageIndex = (
  currentIndex: number,
  delta: number,
  maxIndex: number
): number => {
  return Math.max(0, Math.min(maxIndex, currentIndex + delta));
};

export const shouldTriggerSwipe = (distance: number, threshold: number = 50): boolean => {
  return Math.abs(distance) > threshold;
};

