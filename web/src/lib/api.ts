import { ChartData, Registry } from '@/types/chart';
import { getRegistry, getChartRegistry, getChartData } from './data';

export async function fetchRegistry(): Promise<Registry> {
  return getRegistry();
}

export async function fetchChartRegistry(): Promise<Registry> {
  return getChartRegistry();
}

export async function fetchChartData(key: string): Promise<ChartData> {
  const chartId = keyToSlug(key);
  return getChartData(chartId);
}

export function keyToSlug(key: string): string {
  return key.replace('dev/', '').replace('.json', '');
}

export function slugToKey(slug: string): string {
  return `dev/${slug}.json`;
}
