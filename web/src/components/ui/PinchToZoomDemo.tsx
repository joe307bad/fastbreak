'use client';

import { DemoGif } from './DemoGif';

export function PinchToZoomDemo() {
  return (
    <div className="flex flex-col md:flex-row gap-8 my-8 items-start">
      {/* Left side - Demo GIF */}
      <div className="flex-shrink-0">
        <DemoGif
          src="/02-manual-screenshots/01-pinch-to-zoom.gif"
          alt="Pinch to zoom and pan demonstration on NFL Team Tiers chart"
        />
      </div>

      {/* Right side - Information sections */}
      <div className="flex flex-col gap-6 flex-1">
        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">When was this released?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            December 2024 - Available in the initial release of fastbreak on iOS and Android.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">What does this do?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Pinch-to-zoom allows you to zoom in and out on any chart by pinching with two fingers.
            Once zoomed in, you can drag to pan around and explore different areas of the chart in detail.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">Why is this useful?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Charts with many data points can be hard to read on mobile devices. Pinch-to-zoom lets you
            focus on specific areas of interest, making it easier to analyze crowded scatter plots and
            identify individual teams or players.
          </p>
        </div>
      </div>
    </div>
  );
}
