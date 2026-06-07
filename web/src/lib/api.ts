import { ChartData, Registry } from '@/types/chart';
import { chartIdToFileKey, fileKeyToChartId } from '@/lib/registry';
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
  return fileKeyToChartId(key);
}

export function slugToKey(slug: string): string {
  return chartIdToFileKey(slug);
}
