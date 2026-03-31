import { fetchChartRegistry, fetchChartData, slugToKey, keyToSlug } from '@/lib/api';
import { ChartRenderer } from '@/components/charts';
import { SportTabs } from '@/components/ui/SportTabs';
import { getOrderedLeagues } from '@/lib/leagues';

interface Props {
  params: Promise<{ slug: string }>;
}

export async function generateStaticParams() {
  const registry = await fetchChartRegistry();
  return Object.keys(registry).map(key => ({
    slug: keyToSlug(key),
  }));
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const key = slugToKey(slug);
  const data = await fetchChartData(key);
  return {
    title: `${data.title}`,
    description: data.subtitle || data.description,
  };
}

export default async function ChartPage({ params }: Props) {
  const { slug } = await params;
  const key = slugToKey(slug);
  const data = await fetchChartData(key);
  const orderedSports = getOrderedLeagues();

  return (
    <main className="max-w-[2000px] mx-auto px-0 md:px-4">
      <SportTabs orderedSports={orderedSports} />

      <div className="px-2 md:px-0">
        <div className="border border-[var(--border)] bg-[var(--card)] rounded-none md:rounded p-2 md:p-4">
          <div className="h-[400px] md:h-[500px]">
            <ChartRenderer data={data} />
          </div>
        </div>

        <div className="mt-4">
          <h1 className="text-lg font-bold">{data.title}</h1>
          {data.subtitle && (
            <p className="text-sm text-[var(--muted)] mt-1">{data.subtitle}</p>
          )}
          {data.description && (
            <p className="text-sm text-[var(--muted)] mt-2 max-w-2xl">
              {data.description}
            </p>
          )}
          <div className="mt-2 text-xs text-[var(--muted)]">
            {data.source} · {new Date(data.lastUpdated).toLocaleDateString()}
          </div>
        </div>
      </div>
    </main>
  );
}
