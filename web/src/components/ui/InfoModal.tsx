'use client';

import { useState } from 'react';

export function InfoModal() {
  const [open, setOpen] = useState(false);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="px-4 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 transition-colors text-sm"
        aria-label="Join the beta"
      >
        <span className="hidden md:inline">Join the beta</span>
        <svg className="w-5 h-5 md:hidden" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
          <circle cx="12" cy="17" r="0.5" fill="currentColor" />
        </svg>
      </button>
    );
  }

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="px-4 py-2 bg-blue-600 text-white rounded font-medium hover:bg-blue-700 transition-colors text-sm"
        aria-label="Join the beta"
      >
        <span className="hidden md:inline">Join the beta</span>
        <svg className="w-5 h-5 md:hidden" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
          <circle cx="12" cy="17" r="0.5" fill="currentColor" />
        </svg>
      </button>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 overflow-y-auto" onClick={() => setOpen(false)}>
        <div className="bg-[var(--card)] border border-[var(--border)] rounded-lg max-w-xl w-full my-auto p-6 relative" onClick={e => e.stopPropagation()}>
          <button
            onClick={() => setOpen(false)}
            className="absolute top-4 right-4 p-1 hover:bg-[var(--border)] rounded transition-colors"
            aria-label="Close"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
          <h2 className="text-xl font-bold mb-4 pr-8">fastbreak</h2>

          <p className="text-[var(--muted)] text-sm mb-4">
            Fastbreak is a dashboard that highlights advanced sports statistics across multiple leagues. Fastbreak is built to be fast, free, and full of mobile-friendly, interactive charts.
          </p>
          <ul className="text-[var(--muted)] text-sm mb-6 space-y-2">
            <li><strong>Fast & Free</strong> - No ads, delivered via CDN</li>
            <li><strong>Interactive & Simple</strong> - Accessible to beginners</li>
            <li><strong>Cross-Platform</strong> - Web, iOS, and Android</li>
            <li><strong>Conversation starter</strong> - Talk ball with actual numbers</li>
          </ul>

          <div className="space-y-3 mb-6">
            <a
              href="https://testflight.apple.com/join/NnxqNunM"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-3 p-3 border border-[var(--border)] rounded hover:bg-[var(--border)] transition-colors"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
              </svg>
              <span>iOS (Beta - Join TestFlight)</span>
            </a>

            <a
              href="https://groups.google.com/g/fastbreak-beta-testers"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-3 p-3 border border-[var(--border)] rounded hover:bg-[var(--border)] transition-colors"
            >
              <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85a.637.637 0 00-.83.22l-1.88 3.24a11.43 11.43 0 00-8.94 0L5.65 5.67a.643.643 0 00-.87-.2c-.28.18-.37.54-.22.83L6.4 9.48C3.3 11.25 1.28 14.44 1 18h22c-.28-3.56-2.3-6.75-5.4-8.52zM7 15.25a1.25 1.25 0 110-2.5 1.25 1.25 0 010 2.5zm10 0a1.25 1.25 0 110-2.5 1.25 1.25 0 010 2.5z"/>
              </svg>
              <span>Android (Beta - Join the Google Group)</span>
            </a>
          </div>

          <div className="flex gap-4 text-sm text-[var(--muted)] mb-6">
            <a href="https://discord.gg/KtqmASc6jn" target="_blank" rel="noopener noreferrer" className="hover:text-[var(--foreground)] transition-colors flex items-center gap-2">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
                <path d="M20.317 4.37a19.791 19.791 0 00-4.885-1.515.074.074 0 00-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 00-5.487 0 12.64 12.64 0 00-.617-1.25.077.077 0 00-.079-.037A19.736 19.736 0 003.677 4.37a.07.07 0 00-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 00.031.057 19.9 19.9 0 005.993 3.03.078.078 0 00.084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 00-.041-.106 13.107 13.107 0 01-1.872-.892.077.077 0 01-.008-.128 10.2 10.2 0 00.372-.292.074.074 0 01.077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 01.078.01c.12.098.246.198.373.292a.077.077 0 01-.006.127 12.299 12.299 0 01-1.873.892.077.077 0 00-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 00.084.028 19.839 19.839 0 006.002-3.03.077.077 0 00.032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 00-.031-.03z"/>
              </svg>
              Discord
            </a>
            <a href="https://github.com/joe307bad/fastbreak" target="_blank" rel="noopener noreferrer" className="hover:text-[var(--foreground)] transition-colors flex items-center gap-2">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0C5.374 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z"/>
              </svg>
              GitHub
            </a>
          </div>

          <button
            onClick={() => setOpen(false)}
            className="w-full py-2 bg-[var(--foreground)] text-[var(--background)] rounded font-medium hover:opacity-90 transition-opacity"
          >
            Close
          </button>
        </div>
      </div>
    </>
  );
}
