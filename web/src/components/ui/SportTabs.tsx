'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const SPORTS = ['nfl', 'nba', 'nhl'] as const;

export function SportTabs() {
  const pathname = usePathname();
  const currentSport = pathname.split('/')[1]?.toLowerCase() || 'nfl';

  return (
    <nav className="mb-4">
      <div className="flex gap-0">
        {SPORTS.map(sport => {
          const isActive = currentSport === sport;
          return (
            <Link
              key={sport}
              href={`/${sport}`}
              className={`px-6 py-3 text-sm font-bold uppercase tracking-wider border-b-2 transition-colors ${
                isActive
                  ? 'border-[var(--foreground)] text-[var(--foreground)]'
                  : 'border-transparent text-[var(--muted)] hover:text-[var(--foreground)]'
              }`}
            >
              {sport}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
