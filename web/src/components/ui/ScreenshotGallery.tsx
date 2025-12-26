'use client';

import { useState } from 'react';
import Image from 'next/image';

interface ScreenshotGalleryProps {
  images: string[];
}

export function ScreenshotGallery({ images }: ScreenshotGalleryProps) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  return (
    <>
      <div className="flex gap-4 overflow-x-auto pb-4 my-6 snap-x snap-mandatory">
        {images.map((image, index) => (
          <div
            key={image}
            className="flex-shrink-0 snap-start cursor-pointer"
            onClick={() => setLightboxIndex(index)}
          >
            <Image
              src={image}
              alt={`Screenshot ${index + 1}`}
              width={300}
              height={600}
              className="rounded-lg border border-[var(--border)] hover:opacity-80 transition-opacity"
            />
          </div>
        ))}
      </div>

      {lightboxIndex !== null && (
        <div
          className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4"
          onClick={() => setLightboxIndex(null)}
        >
          <button
            className="absolute top-4 right-4 text-white text-4xl hover:opacity-70 transition-opacity"
            onClick={() => setLightboxIndex(null)}
            aria-label="Close"
          >
            ×
          </button>

          {lightboxIndex > 0 && (
            <button
              className="absolute left-4 text-white text-4xl hover:opacity-70 transition-opacity"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex(lightboxIndex - 1);
              }}
              aria-label="Previous"
            >
              ‹
            </button>
          )}

          {lightboxIndex < images.length - 1 && (
            <button
              className="absolute right-4 text-white text-4xl hover:opacity-70 transition-opacity"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex(lightboxIndex + 1);
              }}
              aria-label="Next"
            >
              ›
            </button>
          )}

          <div className="max-w-5xl max-h-[90vh] relative" onClick={(e) => e.stopPropagation()}>
            <Image
              src={images[lightboxIndex]}
              alt={`Screenshot ${lightboxIndex + 1}`}
              width={1200}
              height={2400}
              className="rounded-lg max-h-[90vh] w-auto"
            />
          </div>
        </div>
      )}
    </>
  );
}
