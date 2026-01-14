import { ChartData, Registry } from '@/types/chart';

const BASE_URL = 'https://d2jyizt5xogu23.cloudfront.net';

// Use shorter revalidation in development (60s), longer in production (1 hour)
const REVALIDATE_TIME = process.env.NODE_ENV === 'development' ? 60 : 3600;

export async function fetchRegistry(): Promise<Registry> {
  const res = await fetch(`${BASE_URL}/registry`, {
    next: { revalidate: REVALIDATE_TIME }
  });
  if (!res.ok) throw new Error('Failed to fetch registry');
  return res.json();
}

export async function fetchChartData(key: string): Promise<ChartData> {
  const res = await fetch(`${BASE_URL}/${key}`, {
    next: { revalidate: REVALIDATE_TIME }
  });
  if (!res.ok) throw new Error(`Failed to fetch chart: ${key}`);
  return res.json();
}

export function keyToSlug(key: string): string {
  return key.replace('dev/', '').replace('.json', '');
}

export function slugToKey(slug: string): string {
  return `dev/${slug}.json`;
}
