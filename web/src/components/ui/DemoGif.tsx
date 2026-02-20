'use client';

import { useState } from 'react';
import Image from 'next/image';

interface DemoGifProps {
  src: string;
  alt: string;
  caption?: string;
}

export function DemoGif({ src, alt, caption }: DemoGifProps) {
  const [lightboxOpen, setLightboxOpen] = useState(false);

  return (
    <>
      <div className="inline-block w-[280px] lg:w-[240px] xl:w-[320px] 2xl:w-[400px]">
        <div
          className="cursor-pointer rounded-lg border border-[var(--border)] overflow-hidden hover:opacity-90 transition-opacity"
          onClick={() => setLightboxOpen(true)}
        >
          <Image
            src={src}
            alt={alt}
            width={400}
            height={900}
            className="w-full h-auto"
            unoptimized // GIFs should not be optimized by Next.js
          />
        </div>
        {caption && (
          <p className="text-sm text-[var(--text-secondary)] mt-2 italic">
            {caption}
          </p>
        )}
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
              height={1600}
              className="rounded-lg max-h-[90vh] w-auto"
              unoptimized
            />
          </div>
        </div>
      )}
    </>
  );
}
