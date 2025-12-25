'use client';

import { ResponsiveBar } from '@nivo/bar';
import { BarChartData } from '@/types/chart';

interface Props {
  data: BarChartData;
}

export function BarChart({ data }: Props) {
  const chartData = data.dataPoints.map(p => ({
    label: p.label,
    value: p.value,
  }));

  const minValue = Math.min(...data.dataPoints.map(p => p.value));
  const maxValue = Math.max(...data.dataPoints.map(p => p.value));

  return (
    <div className="w-full h-full">
      <ResponsiveBar
        data={chartData}
        keys={['value']}
        indexBy="label"
        margin={{ top: 20, right: 20, bottom: 40, left: 50 }}
        padding={0.3}
        valueScale={{ type: 'linear', min: minValue, max: maxValue }}
        indexScale={{ type: 'band', round: true }}
        colors={({ data }) => (data.value as number) >= 0 ? '#4CAF50' : '#F44336'}
        axisBottom={{
          tickSize: 0,
          tickPadding: 8,
          tickRotation: -45,
        }}
        axisLeft={{
          tickSize: 0,
          tickPadding: 8,
        }}
        theme={{
          text: {
            fontFamily: 'var(--font-geist-mono), monospace',
            fill: 'var(--foreground)',
          },
          axis: {
            ticks: {
              text: {
                fontSize: 10,
                fontWeight: 600,
                fill: 'var(--foreground)',
              },
            },
          },
          grid: {
            line: {
              stroke: 'var(--border)',
              strokeOpacity: 0.3,
            },
          },
        }}
        enableLabel={false}
        enableGridY={true}
        enableGridX={false}
        tooltip={({ data, value }) => (
          <div className="bg-[var(--card)] border border-[var(--border)] px-3 py-2 rounded shadow-lg text-xs font-mono">
            <span className="font-bold">{data.label}</span>
            <span className="text-[var(--muted)] ml-2">{value > 0 ? '+' : ''}{value}</span>
          </div>
        )}
      />
    </div>
  );
}
