'use client';

import { useState } from 'react';
import Image from 'next/image';

interface FullWidthImageProps {
  src: string;
  alt: string;
}

export function FullWidthImage({ src, alt }: FullWidthImageProps) {
  const [lightboxOpen, setLightboxOpen] = useState(false);

  return (
    <>
      <div
        className="my-6 cursor-pointer"
        onClick={() => setLightboxOpen(true)}
      >
        <Image
          src={src}
          alt={alt}
          width={1200}
          height={800}
          className="w-full rounded-lg border border-[var(--border)] hover:opacity-80 transition-opacity"
        />
      </div>

      {lightboxOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/90 flex items-center justify-center p-4"
          onClick={() => setLightboxOpen(false)}
        >
          <button
            className="absolute top-4 right-4 text-white text-4xl hover:opacity-70 transition-opacity"
            onClick={() => setLightboxOpen(false)}
            aria-label="Close"
          >
            ×
          </button>

          <div className="max-w-5xl max-h-[90vh] relative" onClick={(e) => e.stopPropagation()}>
            <Image
              src={src}
              alt={alt}
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
