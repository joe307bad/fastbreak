import fs from 'fs';
import path from 'path';
import { ChartData, Registry, isChartEntry } from '@/types/chart';

const DATA_DIR = path.join(process.cwd(), 'data');
const CHARTS_DIR = path.join(DATA_DIR, 'charts');

export function getRegistry(): Registry {
  const registryPath = path.join(DATA_DIR, 'registry.json');
  if (!fs.existsSync(registryPath)) {
    throw new Error('Registry not found. Run npm run download-charts first.');
  }
  const content = fs.readFileSync(registryPath, 'utf-8');
  return JSON.parse(content);
}

export function getChartRegistry(): Registry {
  const registry = getRegistry();
  const chartRegistry: Registry = {};
  for (const [key, entry] of Object.entries(registry)) {
    if (isChartEntry(entry)) {
      chartRegistry[key] = entry;
    }
  }
  return chartRegistry;
}

export function getChartData(chartId: string): ChartData {
  const chartPath = path.join(CHARTS_DIR, `${chartId}.json`);
  if (!fs.existsSync(chartPath)) {
    throw new Error(`Chart not found: ${chartId}`);
  }
  const content = fs.readFileSync(chartPath, 'utf-8');
  return JSON.parse(content);
}

export function getAllChartIds(): string[] {
  if (!fs.existsSync(CHARTS_DIR)) {
    return [];
  }
  return fs
    .readdirSync(CHARTS_DIR)
    .filter((f) => f.endsWith('.json'))
    .map((f) => f.replace('.json', ''));
}

export interface Manifest {
  downloadedAt: string;
  chartCount: number;
}

export function getManifest(): Manifest | null {
  const manifestPath = path.join(DATA_DIR, 'manifest.json');
  if (!fs.existsSync(manifestPath)) {
    return null;
  }
  const content = fs.readFileSync(manifestPath, 'utf-8');
  return JSON.parse(content);
}
