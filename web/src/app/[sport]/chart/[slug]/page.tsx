import { fetchChartRegistry, fetchChartData, slugToKey, keyToSlug } from '@/lib/api';
import { ChartWithTable } from '@/components/charts/ChartWithTable';
import { SportTabs } from '@/components/ui/SportTabs';
import { getOrderedLeagues } from '@/lib/leagues';

interface Props {
  params: Promise<{ sport: string; slug: string }>;
}

export async function generateStaticParams() {
  const registry = await fetchChartRegistry();
  const keys = Object.keys(registry);
  const params: { sport: string; slug: string }[] = [];

  for (const key of keys) {
    const data = await fetchChartData(key);
    params.push({
      sport: data.sport.toLowerCase(),
      slug: keyToSlug(key),
    });
  }

  return params;
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const data = await fetchChartData(slugToKey(slug));
  return {
    title: data.title,
    description: data.description || data.subtitle,
  };
}

export default async function ChartPage({ params }: Props) {
  const { sport, slug } = await params;
  const data = await fetchChartData(slugToKey(slug));
  const orderedSports = getOrderedLeagues();

  return (
    <main className="max-w-[2000px] mx-auto px-0 md:px-4">
      <SportTabs orderedSports={orderedSports} />

      <div className="px-2 md:px-0">
        <div className="lg:h-[calc(100vh-10rem)] lg:overflow-hidden">
          <ChartWithTable
            data={data}
            title={data.title}
            subtitle={data.subtitle}
            source={data.source}
            lastUpdated={data.lastUpdated}
          />
        </div>
      </div>
    </main>
  );
}
