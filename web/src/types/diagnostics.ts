/** One row per pipeline script per env, written by pipeline/04-fastbreak-charts/run-script.sh */
export interface SchedulerRun {
  file_key: string;
  namespace: 'scheduler-o11y';
  script: string;
  /** startup | daily | weekly | topics */
  schedule: string;
  env: string;
  status: 'success' | 'failed';
  exitCode: number;
  startedAt: string;
  finishedAt: string;
  durationSeconds: number;
  /** Tail of the script output; only present when status is failed */
  error?: string;
}

/** Shape of data/diagnostics.json, written at build time by scripts/download-charts.ts */
export interface DiagnosticsSnapshot {
  fetchedAt: string;
  /** Set when the scheduler-o11y endpoint could not be reached at build time */
  fetchError?: string;
  runs: SchedulerRun[];
}
