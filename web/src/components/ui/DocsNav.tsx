'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

interface DocItem {
  slug: string;
  title: string;
  order: number;
}

interface DocsNavProps {
  docs: DocItem[];
}

export function DocsNav({ docs }: DocsNavProps) {
  const pathname = usePathname();
  const [isOpen, setIsOpen] = useState(false);

  const sortedDocs = [...docs].sort((a, b) => a.order - b.order);

  return (
    <nav className="space-y-1">
      {/* Mobile toggle button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="md:hidden flex items-center justify-between w-full px-3 py-2 text-sm font-medium bg-[var(--border)] rounded mb-2"
        aria-label="Toggle documentation menu"
      >
        <span>Documentation</span>
        <svg
          className={`w-5 h-5 transition-transform ${isOpen ? 'rotate-180' : ''}`}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Desktop header */}
      <h2 className="hidden md:block text-xs font-bold uppercase tracking-wider text-[var(--muted)] mb-3">
        Documentation
      </h2>

      {/* Navigation links - collapsible on mobile, always visible on desktop */}
      <div className={`${isOpen ? 'block' : 'hidden'} md:block space-y-1`}>
        {sortedDocs.map((doc) => {
          const href = doc.slug === 'overview' ? '/docs' : `/docs/${doc.slug}`;
          const isActive = pathname === href;

          return (
            <Link
              key={doc.slug}
              href={href}
              className={`
                block px-3 py-2 text-sm rounded transition-colors
                ${
                  isActive
                    ? 'bg-[var(--border)] text-[var(--foreground)] font-medium'
                    : 'text-[var(--muted)] hover:text-[var(--foreground)] hover:bg-[var(--border)]'
                }
              `}
              onClick={() => setIsOpen(false)}
            >
              {doc.title}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
