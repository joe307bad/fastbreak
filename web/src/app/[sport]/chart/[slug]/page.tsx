import { fetchChartRegistry, fetchChartData, slugToKey, keyToSlug } from '@/lib/api';
import { ChartWithTable } from '@/components/charts/ChartWithTable';

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
  const { slug } = await params;
  const data = await fetchChartData(slugToKey(slug));

  return (
    <>
      <header className="max-w-[2000px] mx-auto px-2 md:px-4 mb-4">
        <div className="flex items-center gap-2 text-xs text-[var(--muted)] uppercase tracking-wider">
          <span>{data.sport}</span>
          <span>·</span>
          <span>{data.visualizationType.replace('_', ' ')}</span>
        </div>
        <h1 className="text-xl font-bold mt-1">{data.title}</h1>
        {data.subtitle && (
          <p className="text-sm text-[var(--muted)] mt-1">{data.subtitle}</p>
        )}
      </header>

      <main className="max-w-[2000px] mx-auto px-2 md:px-4 lg:h-[calc(100vh-8.5rem)] lg:overflow-hidden">
        <ChartWithTable data={data} />
      </main>
    </>
  );
}
