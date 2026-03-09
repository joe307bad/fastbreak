'use client';

import { useEffect, useState, useCallback } from 'react';
import { ChartRenderer } from '@/components/charts';
import { Matchup } from '@/components/charts/Matchup';
import { ChartData, MatchupData } from '@/types/chart';
import { ChartNav } from './ChartNav';

interface ChartGridProps {
  charts: { key: string; data: ChartData }[];
  matchups: { key: string; data: MatchupData }[];
}

function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

export function ChartGrid({ charts, matchups }: ChartGridProps) {
  const [activeChart, setActiveChart] = useState<string | null>(null);

  const allChartItems = [
    ...charts.map(c => ({ key: c.key, title: c.data.title, slug: slugify(c.data.title) })),
    ...matchups.map(m => ({ key: m.key, title: m.data.title, slug: slugify(m.data.title) })),
  ];

  const scrollToChart = useCallback((slug: string) => {
    const element = document.getElementById(`chart-${slug}`);
    if (element) {
      const isMobile = window.innerWidth < 768;
      if (isMobile) {
        // Account for fixed nav height (header 40px + nav button ~52px)
        const offset = 100;
        const elementPosition = element.getBoundingClientRect().top + window.scrollY;
        window.scrollTo({ top: elementPosition - offset, behavior: 'smooth' });
      } else {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }, []);

  const handleChartClick = useCallback((key: string) => {
    const chart = allChartItems.find(c => c.key === key);
    if (!chart) return;

    setActiveChart(key);
    window.history.pushState(null, '', `#${chart.slug}`);
    scrollToChart(chart.slug);
  }, [allChartItems, scrollToChart]);

  useEffect(() => {
    const hash = window.location.hash.slice(1);
    if (hash) {
      const chart = allChartItems.find(c => c.slug === hash);
      if (chart) {
        setActiveChart(chart.key);
        setTimeout(() => scrollToChart(hash), 100);
      }
    }

    const handleHashChange = () => {
      const newHash = window.location.hash.slice(1);
      const chart = allChartItems.find(c => c.slug === newHash);
      if (chart) {
        setActiveChart(chart.key);
        scrollToChart(newHash);
      } else {
        setActiveChart(null);
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, [allChartItems, scrollToChart]);

  return (
    <>
      <ChartNav
        charts={allChartItems}
        activeChart={activeChart}
        onChartClick={handleChartClick}
      />

      <div className="grid gap-2 md:gap-6 grid-cols-1 lg:grid-cols-2">
        {charts.map(({ key, data }) => {
          const slug = slugify(data.title);
          const isActive = activeChart === key;
          return (
            <article
              key={key}
              id={`chart-${slug}`}
              className={`border bg-[var(--card)] rounded-none md:rounded p-2 md:p-4 transition-all duration-300 scroll-mt-24 md:scroll-mt-4 ${
                isActive
                  ? 'border-[var(--foreground)] ring-2 ring-[var(--foreground)]/20'
                  : 'border-[var(--border)]'
              }`}
            >
              <header className="mb-3">
                <div className="text-xs text-[var(--muted)] uppercase tracking-wider">
                  {data.visualizationType.replace('_', ' ')}
                </div>
                <div className="flex items-center justify-between mt-1">
                  <h3 className="text-sm font-bold">{data.title}</h3>
                  {data.description && (
                    <div className="group relative">
                      <svg
                        className="w-4 h-4 text-[var(--muted)] hover:text-[var(--foreground)] transition-colors cursor-help"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                        />
                      </svg>
                      <div className="invisible group-hover:visible absolute right-0 top-6 z-10 w-64 p-3 bg-[var(--card)] border border-[var(--border)] rounded shadow-lg text-xs text-[var(--foreground)] font-normal">
                        {data.description}
                      </div>
                    </div>
                  )}
                </div>
              </header>

              <div className="h-[300px] md:h-[350px]">
                <ChartRenderer data={data} />
              </div>

              <footer className="mt-3 pt-3 border-t border-[var(--border)] text-xs text-[var(--muted)] flex justify-between">
                <span>{data.source}</span>
                <span>{new Date(data.lastUpdated).toLocaleDateString()}</span>
              </footer>
            </article>
          );
        })}
      </div>

      {matchups.length > 0 && (
        <section className="mt-10">
          {matchups.map(({ key, data }) => {
            const slug = slugify(data.title);
            const isActive = activeChart === key;
            return (
              <div
                key={key}
                id={`chart-${slug}`}
                className={`p-2 md:p-4 rounded-none md:rounded transition-all duration-300 scroll-mt-24 md:scroll-mt-4 ${
                  isActive
                    ? 'ring-2 ring-[var(--foreground)]/20 bg-[var(--foreground)]/5'
                    : ''
                }`}
              >
                <h2 className="text-sm font-bold mb-4">{data.title}</h2>
                <Matchup data={data} />
                <div className="mt-4 text-xs text-[var(--muted)]">
                  {data.source} · {new Date(data.lastUpdated).toLocaleDateString()}
                </div>
              </div>
            );
          })}
        </section>
      )}
    </>
  );
}
