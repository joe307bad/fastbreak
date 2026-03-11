import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { ChartData, MatchupData } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { ChartGrid } from '@/components/ui/ChartGrid';
import { getOrderedLeagues } from '@/lib/leagues';
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
    title: `${sport.toUpperCase()}`,
  };
}

export default async function SportPage({ params }: Props) {
  const { sport } = await params;

  if (!VALID_SPORTS.includes(sport.toLowerCase())) {
    notFound();
  }

  const orderedSports = getOrderedLeagues();
  const registry = await fetchChartRegistry();
  const keys = Object.keys(registry);

  const allCharts: { key: string; data: ChartData }[] = await Promise.all(
    keys.map(async key => ({
      key,
      data: await fetchChartData(key),
    }))
  );

  const sportCharts = allCharts.filter(
    chart => chart.data.sport?.toLowerCase() === sport.toLowerCase()
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
    <main className="max-w-[2000px] mx-auto px-0 md:px-4">
      <SportTabs orderedSports={orderedSports} />
      <ChartGrid charts={charts} matchups={matchups} />
    </main>
  );
}
