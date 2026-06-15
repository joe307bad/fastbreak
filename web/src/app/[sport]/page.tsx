import { fetchAllCharts, fetchOrderedSportsWithCharts } from '@/lib/api';
import { AnyMatchupData, TOP_MATCHUP_TYPES, filterChartsForSport } from '@/lib/charts';
import { SportTabs } from '@/components/ui/SportTabs';
import { ChartGrid } from '@/components/ui/ChartGrid';
import { selectTopMatchups } from '@/lib/topMatchups';
import { notFound } from 'next/navigation';
import { MLBMatchupData, NBAMatchupData, NHLMatchupData } from '@/types/chart';

interface Props {
  params: Promise<{ sport: string }>;
}

export async function generateStaticParams() {
  const orderedSports = await fetchOrderedSportsWithCharts();
  return orderedSports.map(sport => ({ sport }));
}

export async function generateMetadata({ params }: Props) {
  const { sport } = await params;
  return {
    title: `${sport.toUpperCase()}`,
  };
}

export default async function SportPage({ params }: Props) {
  const { sport } = await params;
  const orderedSports = await fetchOrderedSportsWithCharts();

  if (!orderedSports.includes(sport.toLowerCase())) {
    notFound();
  }

  const allCharts = await fetchAllCharts();
  const { charts, matchups } = filterChartsForSport(allCharts, sport);

  const topMatchupEntry = matchups.find(m =>
    TOP_MATCHUP_TYPES.includes(m.data.visualizationType)
  ) as { key: string; data: NBAMatchupData | NHLMatchupData | MLBMatchupData } | undefined;
  const topMatchupGameIds = topMatchupEntry ? selectTopMatchups(topMatchupEntry.data) : [];

  return (
    <main className="max-w-[2000px] mx-auto px-0 md:px-4">
      <SportTabs orderedSports={orderedSports} />
      <ChartGrid charts={charts} matchups={matchups as { key: string; data: AnyMatchupData }[]} topMatchupGameIds={topMatchupGameIds} />
    </main>
  );
}
