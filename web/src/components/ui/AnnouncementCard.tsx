import { ReactNode } from "react";

interface AnnouncementCardProps {
  date: string;
  title: string;
  children: ReactNode;
}

export function AnnouncementCard({ date, title, children }: AnnouncementCardProps) {
  return (
    <div className="my-6 pt-4 px-4 pb-1 rounded-lg border-[4px] border-green-500">
      <div className="flex items-center gap-2 mb-2">
        <span className="text-xs font-medium text-[var(--muted)] bg-[var(--border)] px-2 py-0.5 rounded">
          {date}
        </span>
      </div>
      <h3 className="text-lg font-bold text-[var(--foreground)] mb-2">{title}</h3>
      <div className="text-sm text-[var(--muted)]">{children}</div>
    </div>
  );
}
