'use client';

import { DemoGif } from './DemoGif';

export function NavigateChartsDemo() {
  return (
    <div className="flex flex-col md:flex-row gap-8 my-8 items-start">
      {/* Left side - Demo GIF */}
      <div className="flex-shrink-0">
        <DemoGif
          src="/02-manual-screenshots/04-navigate-charts-and-show-info.gif"
          alt="Navigating through charts and showing info demo"
        />
      </div>

      {/* Right side - Information sections */}
      <div className="flex flex-col gap-6 flex-1">
        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">How do I use this?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Browse the main screen to see all available charts for your selected sport.
            Tap any chart to view the full interactive version. On the chart screen, tap the
            info icon (ℹ) in the top-right corner to see a description of what the chart displays.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">What does this do?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Fastbreak's core functionality is viewing different types of statistical visualizations.
            Each chart includes an info button that provides context about the metrics being
            displayed, helping you understand what the data represents and how to interpret it.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">Why is this useful?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            With dozens of different charts covering all aspects of each sport,
            fastbreak helps you quickly find the exact data you're looking for. The info descriptions ensure
            you understand each metric, making it easier to discover insights and compare stats meaningfully.
          </p>
        </div>
      </div>
    </div>
  );
}
