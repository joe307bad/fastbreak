'use client';

import { useState } from 'react';
import { BottomSheet } from '@/components/ui/BottomSheet';
import { MLB_MATCHUP_STAT_INFO, StatInfoSection } from '@/lib/mlbMatchupStatInfo';

function StatInfoSectionBlock({ section }: { section: StatInfoSection }) {
  return (
    <section>
      <h3 className="text-xs font-bold uppercase tracking-wide text-[var(--muted)] mb-2">
        {section.title}
      </h3>
      <dl className="space-y-2">
        {section.items.map(item => (
          <div key={item.label}>
            <dt className="text-sm font-medium">{item.label}</dt>
            <dd className="text-xs text-[var(--muted)] leading-relaxed">{item.text}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

interface Props {
  title?: string;
  source?: string;
}

export function MLBMatchupInfoButton({ title = 'Stat Guide', source }: Props) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="p-1 rounded hover:bg-[var(--border)] text-[var(--muted)] hover:text-[var(--foreground)] transition-colors shrink-0 mt-1"
        aria-label="Stat explanations"
      >
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      </button>

      <BottomSheet open={open} onClose={() => setOpen(false)} title={title} source={source}>
        <div className="space-y-4 pb-6">
          <p className="text-sm text-[var(--muted)] leading-relaxed">{MLB_MATCHUP_STAT_INFO.intro}</p>

          <section>
            <h3 className="text-xs font-bold uppercase tracking-wide text-[var(--muted)] mb-2">
              Reading the page
            </h3>
            <dl className="space-y-2">
              {MLB_MATCHUP_STAT_INFO.general.map(item => (
                <div key={item.label}>
                  <dt className="text-sm font-medium">{item.label}</dt>
                  <dd className="text-xs text-[var(--muted)] leading-relaxed">{item.text}</dd>
                </div>
              ))}
            </dl>
          </section>

          {MLB_MATCHUP_STAT_INFO.sections.map(section => (
            <StatInfoSectionBlock key={section.title} section={section} />
          ))}
        </div>
      </BottomSheet>
    </>
  );
}
