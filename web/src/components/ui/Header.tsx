'use client';

import { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { ThemeToggle } from './ThemeToggle';
import { InfoModal } from './InfoModal';

export function Header() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="sticky top-0 z-50 bg-[var(--background)] border-b border-[var(--border)]">
      <div className="max-w-7xl mx-auto px-2 md:px-4 h-10 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2">
          <Image src="/logo.png" alt="Fastbreak" width={28} height={28} />
          <span className="font-bold text-lg">fastbreak</span>
        </Link>

        {/* Desktop nav */}
        <div className="hidden md:flex items-center gap-1">
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
          <Link
            href="/settings"
            className="p-2 hover:bg-[var(--border)] rounded transition-colors"
            aria-label="Settings"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
          </Link>
        </div>

        {/* Mobile nav */}
        <div className="flex md:hidden items-center gap-1">
          <InfoModal />
          <button
            onClick={() => setMenuOpen(true)}
            className="p-2 hover:bg-[var(--border)] rounded transition-colors"
            aria-label="Open menu"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        </div>
      </div>

      {/* Mobile slide-out menu */}
      {menuOpen && (
        <>
          <div
            className="fixed inset-0 z-50 bg-black/50 md:hidden"
            onClick={() => setMenuOpen(false)}
          />
          <div className="fixed top-0 right-0 z-50 h-full w-64 bg-[var(--background)] border-l border-[var(--border)] md:hidden">
            <div className="flex items-center justify-between p-4">
              <ThemeToggle size="lg" />
              <button
                onClick={() => setMenuOpen(false)}
                className="p-2 hover:bg-[var(--border)] rounded transition-colors"
                aria-label="Close menu"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <nav className="p-4 space-y-1">
              <Link
                href="/docs"
                onClick={() => setMenuOpen(false)}
                className="block px-3 py-2 text-sm hover:bg-[var(--border)] rounded transition-colors"
              >
                Docs
              </Link>
              <Link
                href="/blog"
                onClick={() => setMenuOpen(false)}
                className="block px-3 py-2 text-sm hover:bg-[var(--border)] rounded transition-colors"
              >
                Blog
              </Link>
              <Link
                href="/settings"
                onClick={() => setMenuOpen(false)}
                className="block px-3 py-2 text-sm hover:bg-[var(--border)] rounded transition-colors"
              >
                Settings
              </Link>
            </nav>
          </div>
        </>
      )}
    </header>
  );
}
