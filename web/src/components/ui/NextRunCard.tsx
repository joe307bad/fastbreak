'use client';

import { useSyncExternalStore } from 'react';
import type { SchedulerSchedule } from '@/types/diagnostics';

function describeRelative(iso: string, now: number): string {
  const ms = new Date(iso).getTime() - now;
  if (Number.isNaN(ms)) return '';
  if (ms <= 0) return 'due now';
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `in ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `in ${hours}h ${(minutes % 60).toString().padStart(2, '0')}m`;
  const days = Math.floor(hours / 24);
  return `in ${days}d ${hours % 24}h`;
}

function formatUtc(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toISOString().replace('T', ' ').replace(/:\d\d\.\d+Z$/, 'Z');
}

// "Now", ticking every 30s. The server snapshot is null so the prerendered
// HTML carries only the UTC time and the relative/local parts render on the client.
const TICK_MS = 30000;
function subscribeToClock(onChange: () => void) {
  const timer = setInterval(onChange, TICK_MS);
  return () => clearInterval(timer);
}
const getClientNow = () => Math.floor(Date.now() / TICK_MS) * TICK_MS;
const getServerNow = () => null;

export function NextRunCard({ schedule }: { schedule: SchedulerSchedule }) {
  const now = useSyncExternalStore(subscribeToClock, getClientNow, getServerNow);

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
