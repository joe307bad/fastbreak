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
          className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4"
          onClick={() => setLightboxIndex(null)}
        >
          {lightboxIndex > 0 && (
            <button
              className="absolute left-4 text-white text-4xl hover:opacity-70 transition-opacity z-10"
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
              className="absolute right-4 text-white text-4xl hover:opacity-70 transition-opacity z-10"
              onClick={(e) => {
                e.stopPropagation();
                setLightboxIndex(lightboxIndex + 1);
              }}
              aria-label="Next"
            >
              ›
            </button>
          )}

          <div className="max-w-5xl w-full h-full relative flex flex-col items-center" onClick={(e) => e.stopPropagation()}>
            <button
              className="self-end mb-2 text-white text-4xl hover:opacity-70 transition-opacity leading-none"
              onClick={() => setLightboxIndex(null)}
              aria-label="Close"
            >
              ×
            </button>
            <Image
              src={images[lightboxIndex].src}
              alt={images[lightboxIndex].caption}
              width={1200}
              height={2400}
              className="rounded-lg max-h-[calc(100vh-8rem)] w-auto"
            />
            <div className="mt-2 text-white text-sm px-4">{images[lightboxIndex].caption}</div>
          </div>
        </div>
      )}
    </>
  );
}
