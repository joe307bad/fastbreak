'use client';

import { useState } from 'react';
import Image from 'next/image';

interface CaptionedImage {
  src: string;
  caption: string;
}

interface CaptionedGalleryProps {
  images: CaptionedImage[];
}

export function CaptionedGallery({ images }: CaptionedGalleryProps) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  return (
    <>
      <div className="flex gap-4 overflow-x-auto pb-4 my-6 snap-x snap-mandatory">
        {images.map((image, index) => (
          <div
            key={image.src}
            className="flex-shrink-0 snap-start cursor-pointer flex flex-col items-start justify-start"
            onClick={() => setLightboxIndex(index)}
          >
            <div className="mb-2 text-xs text-[var(--muted)] max-w-[240px] text-left border-l-2 border-[var(--border)] pl-3 line-clamp-2">{image.caption}</div>
            <div className="relative h-[280px] w-[240px]">
              <Image
                src={image.src}
                alt={image.caption}
                fill
                className="rounded-lg border border-[var(--border)] hover:opacity-80 transition-opacity object-cover"
              />
            </div>
          </div>
        ))}
      </div>

      {lightboxIndex !== null && (
        <div
          className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-2 md:p-4"
          onClick={() => setLightboxIndex(null)}
        >
          <div className="w-full h-full flex items-center justify-center gap-2 md:gap-4">
            {/* Left arrow */}
            <div className="flex-shrink-0 w-8 md:w-12 flex items-center justify-center">
              {lightboxIndex > 0 && (
                <button
                  className="text-white text-3xl md:text-4xl hover:opacity-70 transition-opacity"
                  onClick={(e) => {
                    e.stopPropagation();
                    setLightboxIndex(lightboxIndex - 1);
                  }}
                  aria-label="Previous"
                >
                  ‹
                </button>
              )}
            </div>

            {/* Main content */}
            <div className="flex-1 h-full max-w-5xl flex flex-col items-center justify-center min-w-0 relative" onClick={(e) => e.stopPropagation()}>
              <button
                className="absolute top-0 right-0 text-white text-3xl md:text-4xl hover:opacity-70 transition-opacity leading-none z-10"
                onClick={() => setLightboxIndex(null)}
                aria-label="Close"
              >
                ×
              </button>
              <div className="flex flex-col items-start">
                <Image
                  src={images[lightboxIndex].src}
                  alt={images[lightboxIndex].caption}
                  width={1200}
                  height={2400}
                  className="rounded-lg max-h-[65vh] md:max-h-[calc(100vh-12rem)] w-auto object-contain"
                />
                <div className="max-h-[20vh] md:max-h-32 mt-2 overflow-y-auto">
                  <p className="text-white text-sm md:text-base text-left">{images[lightboxIndex].caption}</p>
                </div>
              </div>
            </div>

            {/* Right arrow */}
            <div className="flex-shrink-0 w-8 md:w-12 flex items-center justify-center">
              {lightboxIndex < images.length - 1 && (
                <button
                  className="text-white text-3xl md:text-4xl hover:opacity-70 transition-opacity"
                  onClick={(e) => {
                    e.stopPropagation();
                    setLightboxIndex(lightboxIndex + 1);
                  }}
                  aria-label="Next"
                >
                  ›
                </button>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
