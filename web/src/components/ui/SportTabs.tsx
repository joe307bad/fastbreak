'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

interface SportTabsProps {
  orderedSports: string[];
}

export function SportTabs({ orderedSports }: SportTabsProps) {
  const pathname = usePathname();
  const currentSport = pathname.split('/')[1]?.toLowerCase() || orderedSports[0];

  return (
    <nav className="mb-2 md:mb-4">
      <div className="flex gap-0">
        {orderedSports.map(sport => {
          const isActive = currentSport === sport;
          return (
            <Link
              key={sport}
              href={`/${sport}`}
              className={`px-3 md:px-6 py-2 md:py-3 text-sm font-bold uppercase tracking-wider border-b-2 transition-colors ${
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
