'use client';

import { DemoGif } from './DemoGif';

export function ManageTeamsDemo() {
  return (
    <div className="flex flex-col md:flex-row gap-8 my-8 items-start">
      {/* Left side - Demo GIF */}
      <div className="flex-shrink-0">
        <DemoGif
          src="/02-manual-screenshots/02-manage-teams-and-filter.gif"
          alt="Manage teams and filter demonstration"
        />
      </div>

      {/* Right side - Information sections */}
      <div className="flex flex-col gap-6 flex-1">
        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">How do I use this?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Open side menu, open settings, select manage teams, select one or more teams.
            Open any charts - a badge for your pinned teams will show next to the filters.
            Select your pinned team's badge to highlight that team with your filters.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">What does this do?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            Team pinning lets you select specific teams to always keep visible on the chart.
            You can pin multiple teams at once to compare them while filtering out others,
            making it easy to focus on just the teams you want to analyze.
          </p>
        </div>

        <div>
          <h3 className="text-lg font-bold mb-2 mt-0">Why is this useful?</h3>
          <p className="text-[var(--muted)] leading-relaxed">
            When charts have many teams, it can be overwhelming to view them all at once.
            Pinning allows you to focus on the teams you care about most, making it easier
            to track your favorite teams or compare specific matchups across different metrics.
          </p>
        </div>
      </div>
    </div>
  );
}
