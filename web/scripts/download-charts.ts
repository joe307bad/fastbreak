import fs from 'fs';
import path from 'path';
import { fileKeyToChartId, filterRegistryKeys, getRegistryPrefix } from '../src/lib/registry';
import type { DiagnosticsSnapshot, SchedulerRun, SchedulerSchedule } from '../src/types/diagnostics';

const BASE_URL = 'https://d2jyizt5xogu23.cloudfront.net';
const DATA_DIR = path.join(process.cwd(), 'data');
const CHARTS_DIR = path.join(DATA_DIR, 'charts');

interface RegistryEntry {
  interval: string;
  updatedAt: string;
  title: string;
  type?: string;
}

type Registry = Record<string, RegistryEntry>;

interface Manifest {
  downloadedAt: string;
  chartCount: number;
}

// Pull the pipeline's per-script run records for the /diagnostics page. This
// is best-effort: a missing or failing endpoint must not break the site build.
async function downloadDiagnostics() {
  const url = `${BASE_URL}/scheduler-o11y`;
  const env = getRegistryPrefix().replace(/\/$/, '');
  const snapshot: DiagnosticsSnapshot = { fetchedAt: new Date().toISOString(), runs: [] };

  console.log(`\nFetching scheduler-o11y records from ${url}`);
  try {
    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const body: { items?: Array<SchedulerRun | SchedulerSchedule> } = await res.json();
    const items = (body.items ?? []).filter((item) => item.env === env);
    snapshot.runs = items.filter((item): item is SchedulerRun => (item as SchedulerSchedule).kind !== 'schedule');
    snapshot.schedule = items.find((item): item is SchedulerSchedule => (item as SchedulerSchedule).kind === 'schedule');
    console.log(`Saved ${snapshot.runs.length} scheduler-o11y records (env=${env})`);
  } catch (error) {
    snapshot.fetchError = String(error);
    console.warn(`  Could not fetch scheduler-o11y records: ${error}`);
  }

  fs.writeFileSync(
    path.join(DATA_DIR, 'diagnostics.json'),
    JSON.stringify(snapshot, null, 2)
  );
}

async function main() {
  console.log('Downloading chart data from CDN...\n');

  // Create directories
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
  if (!fs.existsSync(CHARTS_DIR)) {
    fs.mkdirSync(CHARTS_DIR, { recursive: true });
  }

  // Fetch registry
  console.log(`Fetching registry from ${BASE_URL}/registry`);
  const registryRes = await fetch(`${BASE_URL}/registry`);
  if (!registryRes.ok) {
    throw new Error(`Failed to fetch registry: ${registryRes.status}`);
  }
  const registry: Registry = await registryRes.json();
  const envRegistry = filterRegistryKeys(registry);

  // Save registry (env-scoped so runtime only sees relevant entries)
  const registryPath = path.join(DATA_DIR, 'registry.json');
  fs.writeFileSync(registryPath, JSON.stringify(envRegistry, null, 2));
  console.log(
    `Saved registry (${Object.keys(envRegistry).length} entries, prefix=${getRegistryPrefix()})\n`
  );

  // Filter to chart entries only (exclude topics and system metadata)
  const chartEntries = Object.entries(envRegistry).filter(
    ([, entry]) => entry.type !== 'topics' && entry.type !== 'system'
  );

  // Download each chart
  let successCount = 0;
  let failCount = 0;

  for (const [key, entry] of chartEntries) {
    const chartId = fileKeyToChartId(key);
    const chartPath = path.join(CHARTS_DIR, `${chartId}.json`);

    try {
      console.log(`Downloading: ${entry.title}`);
      const chartRes = await fetch(`${BASE_URL}/${key}`);
      if (!chartRes.ok) {
        throw new Error(`HTTP ${chartRes.status}`);
      }
      const chartData = await chartRes.json();
      fs.writeFileSync(chartPath, JSON.stringify(chartData, null, 2));
      console.log(`  Saved to ${chartId}.json`);
      successCount++;
    } catch (error) {
      console.error(`  Failed: ${error}`);
      failCount++;
    }
  }

  await downloadDiagnostics();

  // Write manifest
  const manifest: Manifest = {
    downloadedAt: new Date().toISOString(),
    chartCount: successCount,
  };
  fs.writeFileSync(
    path.join(DATA_DIR, 'manifest.json'),
    JSON.stringify(manifest, null, 2)
  );

  console.log(`\nDownload complete!`);
  console.log(`  Success: ${successCount}`);
  console.log(`  Failed: ${failCount}`);
  console.log(`  Manifest: ${manifest.downloadedAt}`);
}

main().catch((err) => {
  console.error('Download failed:', err);
  process.exit(1);
});
