import React, { memo, useCallback, useRef, useState } from 'react';
import { UI } from '../../constants';
import { calculateImageIndex, shouldTriggerSwipe } from '../../utils/image';
import { ChevronRightIcon } from '../icons';

interface ImageCarouselProps {
  images: string[];
  currentIndex?: number;
  onIndexChange?: (index: number) => void;
  className?: string;
  imageClassName?: string;
}

const ImageCarousel: React.FC<ImageCarouselProps> = ({
  images,
  currentIndex: controlledIndex,
  onIndexChange,
  className = '',
  imageClassName = 'image-placeholder',
}) => {
  const [internalIndex, setInternalIndex] = useState(0);
  const touchRef = useRef<{ start: number | null; end: number | null }>({ start: null, end: null });

  const currentIndex = controlledIndex ?? internalIndex;
  const hasMultipleImages = images.length > 1;
  const maxIndex = images.length - 1;

  const setIndex = useCallback(
    (newIndex: number) => {
      if (onIndexChange) {
        onIndexChange(newIndex);
      } else {
        setInternalIndex(newIndex);
      }
    },
    [onIndexChange]
  );

  const changeIndex = useCallback(
    (delta: number) => {
      const newIndex = calculateImageIndex(currentIndex, delta, maxIndex);
      if (newIndex !== currentIndex) {
        setIndex(newIndex);
      }
    },
    [currentIndex, maxIndex, setIndex]
  );

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    touchRef.current.start = e.targetTouches[0].clientX;
  }, []);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    touchRef.current.end = e.targetTouches[0].clientX;
  }, []);

  const handleTouchEnd = useCallback(() => {
    const { start, end } = touchRef.current;
    if (start === null || end === null) return;

    const distance = start - end;
    if (shouldTriggerSwipe(distance, UI.SWIPE_THRESHOLD)) {
      changeIndex(distance > 0 ? 1 : -1);
    }
    touchRef.current = { start: null, end: null };
  }, [changeIndex]);

  const isTouching = touchRef.current.start !== null && touchRef.current.end !== null;

  return (
    <div
      className={`image-carousel ${className}`}
      onTouchStart={handleTouchStart}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
    >
      <div
        className="image-carousel-track"
        style={{
          transform: `translateX(-${currentIndex * 100}%)`,
          transition: isTouching
            ? 'none'
            : `transform ${UI.CAROUSEL_TRANSITION_DURATION || 300}ms ease-out`,
        }}
      >
        {images.map((image, idx) => (
          <div key={idx} className={imageClassName}>
            <span>
              <img src={image} alt="" />
            </span>
          </div>
        ))}
      </div>

      {hasMultipleImages && (
        <>
          {currentIndex > 0 && (
            <button
              className="carousel-arrow carousel-arrow-left"
              onClick={(e) => {
                e.stopPropagation();
                changeIndex(-1);
              }}
            >
              <ChevronRightIcon style={{ transform: 'rotate(180deg)' }} />
            </button>
          )}
          {currentIndex < maxIndex && (
            <button
              className="carousel-arrow carousel-arrow-right"
              onClick={(e) => {
                e.stopPropagation();
                changeIndex(1);
              }}
            >
              <ChevronRightIcon />
            </button>
          )}
          <div className="carousel-indicators">
            {images.map((_, idx) => (
              <button
                key={idx}
                className={`indicator ${idx === currentIndex ? 'active' : ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  setIndex(idx);
                }}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
};

export default memo(ImageCarousel);
