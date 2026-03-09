'use client';

import { useState } from 'react';
import Image from 'next/image';

type ImageItem = string | { src: string; caption: string };

interface ScreenshotGalleryProps {
  images: ImageItem[];
}

function getImageSrc(item: ImageItem): string {
  return typeof item === 'string' ? item : item.src;
}

function getImageCaption(item: ImageItem): string | undefined {
  return typeof item === 'string' ? undefined : item.caption;
}

export function ScreenshotGallery({ images }: ScreenshotGalleryProps) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  return (
    <>
      <div className="flex gap-4 overflow-x-auto pb-4 my-6 snap-x snap-mandatory">
        {images.map((image, index) => {
          const src = getImageSrc(image);
          const caption = getImageCaption(image);
          return (
            <div
              key={src}
              className="flex-shrink-0 snap-start cursor-pointer"
              onClick={() => setLightboxIndex(index)}
            >
              <Image
                src={src}
                alt={caption || `Screenshot ${index + 1}`}
                width={300}
                height={600}
                className="rounded-lg border border-[var(--border)] hover:opacity-80 transition-opacity"
              />
              {caption && (
                <p className="text-sm text-[var(--muted)] mt-2 text-center max-w-[300px]">{caption}</p>
              )}
            </div>
          );
        })}
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

          <div className="max-w-5xl max-h-[90vh] relative flex flex-col items-center" onClick={(e) => e.stopPropagation()}>
            <Image
              src={getImageSrc(images[lightboxIndex])}
              alt={getImageCaption(images[lightboxIndex]) || `Screenshot ${lightboxIndex + 1}`}
              width={1200}
              height={2400}
              className="rounded-lg max-h-[85vh] w-auto"
            />
            {getImageCaption(images[lightboxIndex]) && (
              <p className="text-white text-center mt-4 text-lg">{getImageCaption(images[lightboxIndex])}</p>
            )}
          </div>
        </div>
      )}
    </>
  );
}
