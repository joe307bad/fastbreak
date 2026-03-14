import fs from 'fs';
import path from 'path';

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

  // Save registry
  const registryPath = path.join(DATA_DIR, 'registry.json');
  fs.writeFileSync(registryPath, JSON.stringify(registry, null, 2));
  console.log(`Saved registry (${Object.keys(registry).length} entries)\n`);

  // Filter to chart entries only (exclude topics)
  const chartEntries = Object.entries(registry).filter(
    ([, entry]) => entry.type !== 'topics'
  );

  // Download each chart
  let successCount = 0;
  let failCount = 0;

  for (const [key, entry] of chartEntries) {
    const chartId = key.replace('dev/', '').replace('.json', '');
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
