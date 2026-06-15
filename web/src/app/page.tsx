import { fetchAllCharts, fetchOrderedSportsWithCharts } from '@/lib/api';
import { AnyMatchupData, TOP_MATCHUP_TYPES } from '@/lib/charts';
import { SportTabs } from '@/components/ui/SportTabs';
import { ChartGrid } from '@/components/ui/ChartGrid';
import { filterChartsForSport } from '@/lib/charts';
import { selectTopMatchups } from '@/lib/topMatchups';
import type { Metadata } from 'next';
import { MLBMatchupData, NBAMatchupData, NHLMatchupData } from '@/types/chart';

export const metadata: Metadata = {
  title: 'fastbreak',
  description: 'Fast sports analytics dashboard',
};

export default async function Home() {
  const orderedSports = await fetchOrderedSportsWithCharts();
  const sport = orderedSports[0];

  if (!sport) {
    return (
      <main className="max-w-[2000px] mx-auto px-0 md:px-4">
        <p className="p-4 text-[var(--muted)]">No charts available right now.</p>
      </main>
    );
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
