import type { DiagnosticsSnapshot } from '@/types/diagnostics';

/** When a chart's pipeline script last finished and when cron will run it next, read from diagnostics.json at build time. */
export interface ChartUpdateSchedule {
  /** finishedAt of the script's last successful run; absent when the last run failed */
  lastUpdatedAt?: string;
  /** Next cron firing for the script's schedule (daily / weekly); absent for startup-only scripts */
  nextUpdateAt?: string;
  /** startup | daily | weekly | topics */
  schedule: string;
}

/** Charts are written by the pipeline script of the same name: nfl__team_report_card.json ← nfl__team_report_card.R */
export function getChartUpdateSchedule(
  snapshot: DiagnosticsSnapshot,
  chartId: string
): ChartUpdateSchedule | null {
  const run = snapshot.runs.find(r => r.script === `${chartId}.R`);
  if (!run) return null;

  const nextUpdateAt =
    run.schedule === 'daily'
      ? snapshot.schedule?.nextDailyRunAt
      : run.schedule === 'weekly'
        ? snapshot.schedule?.nextWeeklyRunAt
        : undefined;

  return {
    lastUpdatedAt: run.status === 'success' ? run.finishedAt : undefined,
    nextUpdateAt,
    schedule: run.schedule,
  };
}
