import { fetchAllCharts, fetchChartData, fetchOrderedSportsWithCharts, slugToKey, keyToSlug } from '@/lib/api';
import { ChartWithTable } from '@/components/charts/ChartWithTable';
import { SportTabs } from '@/components/ui/SportTabs';

interface Props {
  params: Promise<{ sport: string; slug: string }>;
}

export async function generateStaticParams() {
  const registry = await fetchAllCharts();
  const params: { sport: string; slug: string }[] = [];

  for (const { key, data } of registry) {
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
  const { slug } = await params;
  const data = await fetchChartData(slugToKey(slug));
  const orderedSports = await fetchOrderedSportsWithCharts();
  const isReportCard = data.visualizationType === 'MLB_TEAM_REPORT_CARD';

  return (
    <main
      className={`max-w-[2000px] mx-auto px-0 md:px-4 ${
        isReportCard ? 'lg:h-[calc(100vh-2.5rem)] lg:flex lg:flex-col lg:min-h-0' : ''
      }`}
    >
      <SportTabs orderedSports={orderedSports} />

      <div className={`px-2 md:px-0 ${isReportCard ? 'flex-1 min-h-0 lg:overflow-hidden' : ''}`}>
        <div className={isReportCard ? 'h-full min-h-0 overflow-hidden' : 'lg:h-[calc(100vh-10rem)] lg:overflow-hidden'}>
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
