'use client';

import Image from 'next/image';
import Link from 'next/link';
import { ThemeToggle } from './ThemeToggle';
import { InfoModal } from './InfoModal';

export function Header() {
  return (
    <header className="sticky top-0 z-40 bg-[var(--background)]">
      <div className="max-w-7xl mx-auto px-2 md:px-4 h-10 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2">
          <Image src="/logo.png" alt="Fastbreak" width={28} height={28} />
          <span className="font-bold text-lg">fastbreak</span>
        </Link>
        <div className="flex items-center gap-1">
          <Link
            href="/docs"
            className="px-2 py-1 text-sm hover:bg-[var(--border)] rounded transition-colors"
          >
            Docs
          </Link>
          <Link
            href="/blog"
            className="px-2 py-1 text-sm hover:bg-[var(--border)] rounded transition-colors"
          >
            Blog
          </Link>
          <InfoModal />
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
