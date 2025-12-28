'use client';

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

  const sortedDocs = [...docs].sort((a, b) => a.order - b.order);

  return (
    <nav className="space-y-1">
      <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--muted)] mb-3">
        Documentation
      </h2>
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
          >
            {doc.title}
          </Link>
        );
      })}
    </nav>
  );
}
