'use client';

import { DemoGif } from './DemoGif';

export function HighlightingDataPointsDemo() {
  return (
    <div className="flex flex-col md:flex-row gap-8 my-8 items-start">
      {/* Left side - Demo GIF */}
      <div className="flex-shrink-0">
        <DemoGif
          src="/02-manual-screenshots/03-highlighting-data-points.gif"
          alt="Highlighting data points demonstration"
        />
      </div>

      {/* Right side - Information sections */}
      <div className="flex flex-col gap-6 flex-1">
        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">How do I use this?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            In fastbreak you can highlight points on any chart using filters. The filters can be one of your pinned teams or contextual for the chart, like highlighting a given division or conference.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">What does this do?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Highlighting data points visually emphasizes specific teams or data on the chart,
            making them stand out while dimming the rest. This helps you focus on particular
            data points and compare them more easily.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">Why is this useful?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            When analyzing charts with many data points, highlighting allows you to quickly
            identify and compare specific teams or values without losing context of the overall
            data distribution.
          </p>
        </div>
      </div>
    </div>
  );
}
