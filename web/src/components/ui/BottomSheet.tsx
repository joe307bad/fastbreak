'use client';

import { useEffect, type ReactNode } from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  source?: string;
  headerExtra?: ReactNode;
  children: ReactNode;
}

export function BottomSheet({
  open,
  onClose,
  title,
  subtitle,
  source,
  headerExtra,
  children,
}: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden />
      <div
        className="relative w-full max-w-lg max-h-[85vh] bg-[var(--background)] border border-[var(--border)] rounded-t-xl flex flex-col shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-labelledby="bottom-sheet-title"
      >
        <div className="flex justify-center pt-2 pb-1 shrink-0">
          <div className="w-10 h-1 rounded-full bg-[var(--border)]" />
        </div>

        <div className="px-6 pb-2 flex items-center gap-2 shrink-0">
          <h2 id="bottom-sheet-title" className="font-mono font-bold text-base truncate flex-1">
            {title}
          </h2>
          {headerExtra}
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded hover:bg-[var(--border)] text-[var(--muted)] hover:text-[var(--foreground)] transition-colors shrink-0"
            aria-label="Close"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {subtitle && (
          <p className="px-6 text-xs text-[var(--muted)] pb-2 shrink-0">{subtitle}</p>
        )}

        <div className="flex-1 overflow-y-auto px-6 pb-4 min-h-0">{children}</div>

        {source && (
          <div className="px-6 py-2 border-t border-[var(--border)] text-xs text-[var(--muted)] shrink-0">
            {source}
          </div>
        )}
      </div>
    </div>
  );
}
