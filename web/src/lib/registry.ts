export function isProdEnv(): boolean {
  return process.env.FASTBREAK_ENV === 'prod';
}

export function getRegistryPrefix(): string {
  return isProdEnv() ? 'prod/' : 'dev/';
}

export function fileKeyToChartId(fileKey: string): string {
  return fileKey
    .replace(/^dev\//, '')
    .replace(/^prod\//, '')
    .replace(/\.json$/, '');
}

export function chartIdToFileKey(chartId: string): string {
  return `${getRegistryPrefix()}${chartId}.json`;
}

export function filterRegistryKeys<T>(registry: Record<string, T>): Record<string, T> {
  const prefix = getRegistryPrefix();
  return Object.fromEntries(
    Object.entries(registry).filter(([key]) => key.startsWith(prefix))
  );
}
