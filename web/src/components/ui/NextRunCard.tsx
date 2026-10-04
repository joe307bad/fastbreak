'use client';

import type { SchedulerSchedule } from '@/types/diagnostics';
import { describeRelative, formatUtc, useClockNow } from '@/lib/schedulerTime';

export function NextRunCard({ schedule }: { schedule: SchedulerSchedule }) {
  const now = useClockNow();

  const rows = [
    { label: 'Next daily run', at: schedule.nextDailyRunAt, cron: schedule.dailyCron },
    { label: 'Next weekly run', at: schedule.nextWeeklyRunAt, cron: schedule.weeklyCron },
  ];

  return (
    <div className="mb-6 border border-[var(--border)] rounded bg-[var(--card)] px-4 py-3 text-sm">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-2">
        {rows.map((row) => (
          <div key={row.label} className="flex flex-col">
            <span className="text-xs font-medium text-[var(--muted)] uppercase tracking-wider">{row.label}</span>
            <span className="font-semibold">
              {formatUtc(row.at)}
              {now !== null && (
                <span className="ml-2 font-normal text-[var(--muted)]">{describeRelative(row.at, now)}</span>
              )}
            </span>
            <span className="text-xs text-[var(--muted)]">
              {now !== null && `${new Date(row.at).toLocaleString()} local · `}
              cron <code>{row.cron}</code> {schedule.timezone}
            </span>
          </div>
        ))}
      </div>
      <p className="mt-2 text-xs text-[var(--muted)]">
        Computed after {schedule.lastJobScript} finished at {formatUtc(schedule.lastJobFinishedAt)}. Scripts also run once
        whenever the pipeline container is redeployed.
      </p>
    </div>
  );
}
