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

export async function generateMetadata({ params }: Props) {
  const { sport } = await params;
  return {
    title: `${sport.toUpperCase()} | fastbreak`,
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

  const charts = sportCharts.filter(
    chart => chart.data.visualizationType !== 'MATCHUP'
  );

  const matchups = sportCharts.filter(
    chart => chart.data.visualizationType === 'MATCHUP'
  ) as { key: string; data: MatchupData }[];

  return (
    <main className="max-w-7xl mx-auto px-4">
      <SportTabs />

      <div className="grid gap-4 md:gap-6 grid-cols-1 lg:grid-cols-2">
        {charts.map(({ key, data }) => (
          <article
            key={key}
            className="border border-[var(--border)] bg-[var(--card)] rounded p-4"
          >
            <header className="mb-3">
              <div className="text-xs text-[var(--muted)] uppercase tracking-wider">
                {data.visualizationType.replace('_', ' ')}
              </div>
              <h3 className="text-sm font-bold mt-1">{data.title}</h3>
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
