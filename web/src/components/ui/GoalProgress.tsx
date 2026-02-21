interface GoalProgressProps {
  total: number;
  completed: number;
  inProgress?: number;
  label?: string;
}

export function GoalProgress({ total, completed, inProgress = 0, label = "Roadmap Progress" }: GoalProgressProps) {
  const completedPercent = Math.round((completed / total) * 100);
  const inProgressPercent = Math.round((inProgress / total) * 100);

  return (
    <div className="my-6 p-4 rounded-lg border border-[var(--border)] bg-[var(--card-bg)]">
      <div className="flex justify-between items-center mb-2">
        <span className="text-sm font-medium text-[var(--foreground)]">{label}</span>
        <span className="text-sm text-[var(--muted)]">
          {completed} / {total} goals ({Math.round((completed / total) * 100)}%)
        </span>
      </div>
      <div className="w-full h-3 bg-[var(--border)] rounded-full overflow-hidden flex">
        <div
          className="h-full bg-green-500 transition-all duration-500"
          style={{ width: `${completedPercent}%` }}
        />
        <div
          className="h-full bg-blue-500 transition-all duration-500"
          style={{ width: `${inProgressPercent}%` }}
        />
      </div>
      <div className="mt-2 flex gap-4 text-xs text-[var(--muted)]">
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-green-500" /> {completed} done
        </span>
        {inProgress > 0 && (
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-blue-500" /> {inProgress} in progress
          </span>
        )}
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-full bg-[var(--border)]" /> {total - completed - inProgress} not started
        </span>
      </div>
    </div>
  );
}
