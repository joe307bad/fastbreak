import { fetchAllCharts, fetchOrderedSportsWithCharts } from '@/lib/api';
import { AnyMatchupData, filterChartsForSport } from '@/lib/charts';
import { SportTabs } from '@/components/ui/SportTabs';
import { MatchupsWithNav } from '@/components/charts/MatchupsWithNav';
import { notFound } from 'next/navigation';
import { pageMetadata } from '@/lib/og';

interface Props {
  params: Promise<{ sport: string }>;
}

export async function generateStaticParams() {
  const orderedSports = await fetchOrderedSportsWithCharts();
  return orderedSports.map(sport => ({ sport }));
}

export async function generateMetadata({ params }: Props) {
  const { sport } = await params;
  return pageMetadata({ title: `${sport.toUpperCase()} Matchups` });
}

export default async function MatchupsPage({ params }: Props) {
  const { sport } = await params;
  const orderedSports = await fetchOrderedSportsWithCharts();

  if (!orderedSports.includes(sport.toLowerCase())) {
    notFound();
  }

  const allCharts = await fetchAllCharts();
  const { matchups } = filterChartsForSport(allCharts, sport);
  const matchupData = matchups[0]?.data as AnyMatchupData | undefined;

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
