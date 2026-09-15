import { fetchAllCharts, fetchChartData, slugToKey, keyToSlug } from '@/lib/api';
import { pageMetadata } from '@/lib/og';
import { ChartPageBody } from './ChartPageBody';

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
  return pageMetadata({
    title: data.title,
    description: data.description || data.subtitle,
    subtitle: data.subtitle || data.title,
  });
}

export default async function ChartPage({ params }: Props) {
  const { slug } = await params;
  const data = await fetchChartData(slugToKey(slug));
  return <ChartPageBody data={data} />;
}
