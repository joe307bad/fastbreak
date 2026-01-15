'use client';

import { useState, useEffect } from 'react';
import { DemoGif } from './DemoGif';
import { PlatformSupport } from './PlatformSupport';
import manualFeatures from '@/data/manual-features.json';

type SortMode = 'all' | 'latest';

interface ManualFeature {
  id: string;
  title: string;
  sortOrder: number;
  demoGif: string;
  demoAlt: string;
  platforms: {
    ios: boolean;
    android: boolean;
    web: boolean;
  };
  dateReleased: string;
  version: string;
  sections: {
    howToUse: string;
    whatItDoes: string;
    whyUseful: string;
  };
}

export function ManualPage() {
  const [sortMode, setSortMode] = useState<SortMode>('all');
  const [mounted, setMounted] = useState(false);

  // Sync with hash immediately on mount to avoid flicker
  useEffect(() => {
    const hash = window.location.hash;
    if (hash === '#latest-changes') {
      setSortMode('latest');
    }
    // Use requestAnimationFrame to ensure the sort happens before paint
    requestAnimationFrame(() => {
      setMounted(true);
    });
  }, []);

  // Update hash when sort mode changes
  const handleSortModeChange = (mode: SortMode) => {
    setSortMode(mode);
    if (mode === 'latest') {
      window.history.pushState(null, '', '#latest-changes');
    } else {
      window.history.pushState(null, '', window.location.pathname);
    }
  };

  const sortedFeatures = [...(manualFeatures as ManualFeature[])].sort((a, b) => {
    if (sortMode === 'latest') {
      return new Date(b.dateReleased).getTime() - new Date(a.dateReleased).getTime();
    }
    // For 'all', sort by sortOrder
    return a.sortOrder - b.sortOrder;
  });

  return (
    <div>
      <p className="text-[var(--muted)] leading-relaxed mb-6">
        Learn how to use fastbreak with this guide.
      </p>

      <div className="flex gap-3 mb-8" style={{ opacity: mounted ? 1 : 0, transition: 'opacity 0.15s ease-in' }}>
        <button
          onClick={() => handleSortModeChange('all')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors border ${
            sortMode === 'all'
              ? 'border-blue-600 text-blue-600 bg-blue-600/10'
              : 'border-[var(--border)] text-[var(--muted)] hover:border-blue-600 hover:text-blue-600'
          }`}
        >
          All features
        </button>
        <button
          onClick={() => handleSortModeChange('latest')}
          className={`px-4 py-2 rounded-md text-sm font-medium transition-colors border ${
            sortMode === 'latest'
              ? 'border-blue-600 text-blue-600 bg-blue-600/10'
              : 'border-[var(--border)] text-[var(--muted)] hover:border-blue-600 hover:text-blue-600'
          }`}
        >
          Latest updates
        </button>
      </div>

      <div style={{ opacity: mounted ? 1 : 0, transition: 'opacity 0.15s ease-in' }}>
        {sortedFeatures.map((feature) => {
        const headingId = feature.id;

        return (
        <div key={feature.id}>
          <h2 id={headingId} className="text-2xl font-bold mb-4 group scroll-mt-20">
            <a href={`#${headingId}`} className="flex items-center gap-2 no-underline hover:underline">
              {feature.sortOrder}. {feature.title}
              <svg
                className="w-5 h-5 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
              </svg>
            </a>
          </h2>

          <PlatformSupport
            ios={feature.platforms.ios}
            android={feature.platforms.android}
            web={feature.platforms.web}
            dateReleased={feature.dateReleased}
          />

          <div className="flex flex-col md:flex-row gap-8 my-8 items-start">
            <div className="flex-shrink-0">
              <DemoGif src={feature.demoGif} alt={feature.demoAlt} />
            </div>

            <div className="flex flex-col gap-6 flex-1">
              <div>
                <h3 className="text-lg font-bold mb-2 mt-0">How do I use this?</h3>
                <p className="text-[var(--muted)] leading-relaxed">
                  {feature.sections.howToUse}
                </p>
              </div>

              <div>
                <h3 className="text-lg font-bold mb-2 mt-0">What does this do?</h3>
                <p className="text-[var(--muted)] leading-relaxed">
                  {feature.sections.whatItDoes}
                </p>
              </div>

              <div>
                <h3 className="text-lg font-bold mb-2 mt-0">Why is this useful?</h3>
                <p className="text-[var(--muted)] leading-relaxed">
                  {feature.sections.whyUseful}
                </p>
              </div>
            </div>
          </div>
        </div>
        );
      })}
      </div>
    </div>
  );
}
