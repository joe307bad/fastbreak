import { fetchRegistry, fetchChartData } from '@/lib/api';
import { ChartRenderer } from '@/components/charts';
import { Matchup } from '@/components/charts/Matchup';
import { ChartData, MatchupData } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { notFound } from 'next/navigation';

const VALID_SPORTS = ['nfl', 'nba', 'nhl'];

interface Props {
  params: Promise<{ sport: string }>;
}

export async function generateStaticParams() {
  return VALID_SPORTS.map(sport => ({ sport }));
}

// Revalidate this page once per day (86400 seconds = 24 hours)
export const revalidate = 86400;

export async function generateMetadata({ params }: Props) {
  const { sport } = await params;
  return {
    title: `${sport.toUpperCase()}`,
  };
}

export default async function SportPage({ params }: Props) {
  const { sport } = await params;

  if (!VALID_SPORTS.includes(sport.toLowerCase())) {
    notFound();
  }

  const registry = await fetchRegistry();
  const keys = Object.keys(registry);

  const allCharts: { key: string; data: ChartData }[] = await Promise.all(
    keys.map(async key => ({
      key,
      data: await fetchChartData(key),
    }))
  );

  const sportCharts = allCharts.filter(
    chart => chart.data.sport.toLowerCase() === sport.toLowerCase()
  );

  // List of supported visualization types on web
  const SUPPORTED_TYPES = ['SCATTER_PLOT', 'LINE_CHART', 'BAR_CHART', 'BAR_GRAPH', 'TABLE'];

  const charts = sportCharts.filter(
    chart => SUPPORTED_TYPES.includes(chart.data.visualizationType)
  );

  const matchups = sportCharts.filter(
    chart => chart.data.visualizationType === 'MATCHUP'
  ) as { key: string; data: MatchupData }[];

  return (
    <main className="max-w-7xl mx-auto px-0 md:px-4">
      <SportTabs />

      <div className="grid gap-2 md:gap-6 grid-cols-1 lg:grid-cols-2">
        {charts.map(({ key, data }) => (
          <article
            key={key}
            className="border border-[var(--border)] bg-[var(--card)] rounded-none md:rounded p-2 md:p-4"
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
        ))}
      </div>

      {matchups.length > 0 && (
        <section className="mt-10">
          {matchups.map(({ key, data }) => (
            <div key={key}>
              <h2 className="text-sm font-bold mb-4">{data.title}</h2>
              <Matchup data={data} />
              <div className="mt-4 text-xs text-[var(--muted)]">
                {data.source} · {new Date(data.lastUpdated).toLocaleDateString()}
              </div>
            </div>
          ))}
        </section>
      )}
    </main>
  );
}
