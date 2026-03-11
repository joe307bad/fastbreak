'use client';

import { useEffect, useState } from 'react';

interface ThemeToggleProps {
  size?: 'default' | 'lg';
}

export function ThemeToggle({ size = 'default' }: ThemeToggleProps) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  const toggle = () => {
    const isDark = document.documentElement.classList.contains('dark');
    document.documentElement.classList.toggle('dark', !isDark);
    localStorage.setItem('theme', !isDark ? 'dark' : 'light');
  };

  const iconClass = size === 'lg' ? 'w-6 h-6' : 'w-5 h-5';

  // Prevent hydration mismatch by not rendering icon until mounted
  // Use CSS to show correct icon based on dark class
  return (
    <button
      onClick={toggle}
      className="p-2 hover:bg-[var(--border)] rounded transition-colors"
      aria-label="Toggle theme"
    >
      {mounted ? (
        <>
          {/* Sun icon - shown in dark mode (click to go light) */}
          <svg className={`${iconClass} hidden dark:block`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
          </svg>
          {/* Moon icon - shown in light mode (click to go dark) */}
          <svg className={`${iconClass} block dark:hidden`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
          </svg>
        </>
      ) : (
        // Placeholder with same dimensions to prevent layout shift
        <div className={iconClass} />
      )}
    </button>
  );
}
