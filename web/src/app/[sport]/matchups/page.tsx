import { fetchChartRegistry, fetchChartData } from '@/lib/api';
import { ChartData, MatchupData, MatchupV2Data, NBAMatchupData, NHLMatchupData } from '@/types/chart';
import { SportTabs } from '@/components/ui/SportTabs';
import { MatchupsWithNav } from '@/components/charts/MatchupsWithNav';
import { getOrderedLeagues } from '@/lib/leagues';
import { notFound } from 'next/navigation';

type AnyMatchupData = MatchupData | MatchupV2Data | NBAMatchupData | NHLMatchupData;

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
    title: `${sport.toUpperCase()} Matchups`,
  };
}

export default async function MatchupsPage({ params }: Props) {
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

  const MATCHUP_TYPES = ['MATCHUP', 'MATCHUP_V2', 'NBA_MATCHUP', 'NHL_MATCHUP'];
  const matchups = sportCharts.filter(
    chart => MATCHUP_TYPES.includes(chart.data.visualizationType)
  ) as { key: string; data: AnyMatchupData }[];

  const matchupData = matchups[0]?.data;

  if (!matchupData) {
    return (
      <main className="max-w-[2000px] mx-auto px-0 md:px-4">
        <SportTabs orderedSports={orderedSports} />
        <div className="p-4">
          <p className="text-[var(--muted)]">No matchups available for {sport.toUpperCase()}</p>
        </div>
      </main>
    );
  }

  return (
    <main className="max-w-[2000px] mx-auto px-0 md:px-4">
      <SportTabs orderedSports={orderedSports} />

      <div className="px-2 md:px-0">
        <MatchupsWithNav data={matchupData} />

        <div className="mt-4 py-4 border-t border-[var(--border)] text-xs text-[var(--muted)]">
          {matchupData.source} · {new Date(matchupData.lastUpdated).toLocaleDateString()}
        </div>
      </div>
    </main>
  );
}
