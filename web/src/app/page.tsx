import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { ChartData, MatchupData, MatchupV2Data, NBAMatchupData, NHLMatchupData } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { ChartGrid } from '@/components/ui/ChartGrid';
import { getOrderedLeagues } from '@/lib/leagues';
import type { Metadata } from 'next';

type AnyMatchupData = MatchupData | MatchupV2Data | NBAMatchupData | NHLMatchupData;

export const metadata: Metadata = {
  title: 'fastbreak',
  description: 'Fast sports analytics dashboard',
};

export default async function Home() {
  const orderedSports = getOrderedLeagues();
  const sport = orderedSports[0];

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

  const MATCHUP_TYPES = ['MATCHUP', 'MATCHUP_V2', 'NBA_MATCHUP', 'NHL_MATCHUP'];
  const matchups = sportCharts.filter(
    chart => MATCHUP_TYPES.includes(chart.data.visualizationType)
  ) as { key: string; data: AnyMatchupData }[];

  return (
    <main className="max-w-[2000px] mx-auto px-0 md:px-4">
      <SportTabs orderedSports={orderedSports} />
      <ChartGrid charts={charts} matchups={matchups} />
    </main>
  );
}
