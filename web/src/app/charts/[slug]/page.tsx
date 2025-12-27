import { fetchRegistry, fetchChartData, slugToKey, keyToSlug } from '@/lib/api';
import { ChartRenderer } from '@/components/charts';

interface Props {
  params: Promise<{ slug: string }>;
}

export async function generateStaticParams() {
  const registry = await fetchRegistry();
  return Object.keys(registry).map(key => ({
    slug: keyToSlug(key),
  }));
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const key = slugToKey(slug);
  const data = await fetchChartData(key);
  return {
    title: `${data.title} | fastbreak`,
    description: data.subtitle || data.description,
  };
}

export default async function ChartPage({ params }: Props) {
  const { slug } = await params;
  const key = slugToKey(slug);
  const data = await fetchChartData(key);

  return (
    <main className="max-w-5xl mx-auto px-0 md:px-4">
      <header className="mb-4 md:mb-6 px-2 md:px-0">
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

      <div className="border border-[var(--border)] bg-[var(--card)] rounded-none md:rounded p-2 md:p-4">
        <div className="h-[400px] md:h-[500px]">
          <ChartRenderer data={data} />
        </div>
      </div>

      {data.description && (
        <p className="text-sm text-[var(--muted)] mt-4 max-w-2xl px-2 md:px-0">
          {data.description}
        </p>
      )}

      <footer className="mt-6 pt-4 border-t border-[var(--border)] text-xs text-[var(--muted)] flex gap-4 px-2 md:px-0">
        <span>Source: {data.source}</span>
        <span>Updated: {new Date(data.lastUpdated).toLocaleString()}</span>
      </footer>
    </main>
  );
}
