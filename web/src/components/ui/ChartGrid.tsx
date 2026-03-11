'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { ChartRenderer } from '@/components/charts';
import { UpcomingMatchups } from '@/components/charts/UpcomingMatchups';
import { TopMatchupsWidget } from '@/components/charts/TopMatchupsWidget';
import { ChartData, MatchupData, MatchupV2Data, NBAMatchupData, NHLMatchupData } from '@/types/chart';
import { ChartNav } from './ChartNav';

type AnyMatchupData = MatchupData | MatchupV2Data | NBAMatchupData | NHLMatchupData;

interface ChartGridProps {
  charts: { key: string; data: ChartData }[];
  matchups: { key: string; data: AnyMatchupData }[];
}

function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

function keyToSlug(key: string): string {
  return key.replace('dev/', '').replace('.json', '');
}

export function ChartGrid({ charts, matchups }: ChartGridProps) {
  const [activeChart, setActiveChart] = useState<string | null>(null);

  // Find NBA or NHL matchup for the top matchups widget
  const topMatchupData = matchups.find(m =>
    m.data.visualizationType === 'NBA_MATCHUP' || m.data.visualizationType === 'NHL_MATCHUP'
  ) as { key: string; data: NBAMatchupData | NHLMatchupData } | undefined;

  // Find NFL/MATCHUP_V2 matchup for NFL homepage
  const nflMatchupData = matchups.find(m =>
    m.data.visualizationType === 'MATCHUP_V2'
  ) as { key: string; data: MatchupV2Data } | undefined;

  const allChartItems = [
    ...matchups.map(m => ({
      key: m.key,
      title: m.data.title,
      // Use simple "matchups" slug for NBA/NHL/NFL matchup widgets
      slug: m.data.visualizationType === 'NBA_MATCHUP' || m.data.visualizationType === 'NHL_MATCHUP' || m.data.visualizationType === 'MATCHUP_V2'
        ? 'matchups'
        : slugify(m.data.title),
    })),
    ...charts.map(c => ({ key: c.key, title: c.data.title, slug: slugify(c.data.title) })),
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
        {/* Top Matchups Widget - First position */}
        {topMatchupData && (
          <article
            id="chart-matchups"
            className={`border bg-[var(--card)] rounded-none md:rounded p-2 md:p-4 transition-all duration-300 scroll-mt-24 md:scroll-mt-4 ${
              activeChart === topMatchupData.key
                ? 'border-[var(--foreground)] ring-2 ring-[var(--foreground)]/20'
                : 'border-[var(--border)]'
            }`}
          >
            <header className="mb-3">
              <div className="text-xs text-[var(--muted)] uppercase tracking-wider">
                Top Matchups
              </div>
              <div className="flex items-center justify-between mt-1">
                <h3 className="text-sm font-bold">
                  Best Games by {topMatchupData.data.visualizationType === 'NHL_MATCHUP' ? 'Goal Diff' : 'Net Rating'}
                </h3>
                <Link
                  href={`/${topMatchupData.data.sport.toLowerCase()}/matchups`}
                  className="text-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
                  aria-label="View all matchups"
                >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5v-4m0 4h-4m4 0l-5-5"
                    />
                  </svg>
                </Link>
              </div>
            </header>

            <TopMatchupsWidget data={topMatchupData.data} />

            <footer className="mt-3 pt-3 border-t border-[var(--border)] text-xs text-[var(--muted)] flex justify-between">
              <span>{topMatchupData.data.source}</span>
              <span>{new Date(topMatchupData.data.lastUpdated).toLocaleDateString()}</span>
            </footer>
          </article>
        )}

        {/* NFL Matchups Widget - First position for NFL */}
        {nflMatchupData && !topMatchupData && (
          <article
            id="chart-matchups"
            className={`border bg-[var(--card)] rounded-none md:rounded p-2 md:p-4 transition-all duration-300 scroll-mt-24 md:scroll-mt-4 lg:col-span-2 ${
              activeChart === nflMatchupData.key
                ? 'border-[var(--foreground)] ring-2 ring-[var(--foreground)]/20'
                : 'border-[var(--border)]'
            }`}
          >
            <header className="mb-3">
              <div className="text-xs text-[var(--muted)] uppercase tracking-wider">
                Upcoming Games
              </div>
              <div className="flex items-center justify-between mt-1">
                <h3 className="text-sm font-bold">{nflMatchupData.data.title}</h3>
                <Link
                  href={`/${nflMatchupData.data.sport.toLowerCase()}/matchups`}
                  className="text-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
                  aria-label="View all matchups"
                >
                  <svg
                    className="w-4 h-4"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5v-4m0 4h-4m4 0l-5-5"
                    />
                  </svg>
                </Link>
              </div>
            </header>

            <UpcomingMatchups data={nflMatchupData.data} />

            <footer className="mt-3 pt-3 border-t border-[var(--border)] text-xs text-[var(--muted)] flex justify-between">
              <span>{nflMatchupData.data.source}</span>
              <span>{new Date(nflMatchupData.data.lastUpdated).toLocaleDateString()}</span>
            </footer>
          </article>
        )}

        {/* Regular Charts */}
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
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold">{data.title}</h3>
                  <Link
                    href={`/${data.sport.toLowerCase()}/chart/${keyToSlug(key)}`}
                    className="text-[var(--muted)] hover:text-[var(--foreground)] transition-colors"
                    aria-label="Expand chart"
                  >
                    <svg
                      className="w-4 h-4"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5v-4m0 4h-4m4 0l-5-5"
                      />
                    </svg>
                  </Link>
                </div>
              </header>

              <div className="h-[300px] md:h-[450px]">
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

      {/* Full Matchups Section - exclude NBA/NHL/NFL matchups since they're shown in widgets above */}
      {matchups.filter(m => m.data.visualizationType !== 'NBA_MATCHUP' && m.data.visualizationType !== 'NHL_MATCHUP' && m.data.visualizationType !== 'MATCHUP_V2').length > 0 && (
        <section className="mt-10">
          {matchups.filter(m => m.data.visualizationType !== 'NBA_MATCHUP' && m.data.visualizationType !== 'NHL_MATCHUP' && m.data.visualizationType !== 'MATCHUP_V2').map(({ key, data }) => {
            const slug = slugify(data.title);
            const isActive = activeChart === key;
            return (
              <div
                key={key}
                id={`chart-${slug}-full`}
                className={`p-2 md:p-4 rounded-none md:rounded transition-all duration-300 scroll-mt-24 md:scroll-mt-4 ${
                  isActive
                    ? 'ring-2 ring-[var(--foreground)]/20 bg-[var(--foreground)]/5'
                    : ''
                }`}
              >
                <h2 className="text-sm font-bold mb-4">{data.title}</h2>
                <UpcomingMatchups data={data} />
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
