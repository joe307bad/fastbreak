'use client';

import { ResponsiveLine } from '@nivo/line';
import { LineChartData } from '@/types/chart';

interface Props {
  data: LineChartData;
}

export function LineChart({ data }: Props) {
  const chartData = data.series.map(series => ({
    id: series.label,
    color: series.color,
    data: series.dataPoints.map(p => ({ x: p.x, y: p.y })),
  }));

  return (
    <div className="w-full h-full">
      <ResponsiveLine
        data={chartData}
        margin={{ top: 20, right: 90, bottom: 50, left: 60 }}
        xScale={{ type: 'linear', min: 'auto', max: 'auto' }}
        yScale={{ type: 'linear', min: 'auto', max: 'auto', stacked: false }}
        axisBottom={{
          tickSize: 0,
          tickPadding: 8,
          tickRotation: 0,
          legend: data.xAxisLabel || 'Week',
          legendOffset: 40,
          legendPosition: 'middle',
        }}
        axisLeft={{
          tickSize: 0,
          tickPadding: 8,
          tickRotation: 0,
          legend: data.yAxisLabel || 'Value',
          legendOffset: -50,
          legendPosition: 'middle',
        }}
        theme={{
          text: {
            fontFamily: 'var(--font-geist-mono), monospace',
            fill: 'var(--foreground)',
          },
          axis: {
            ticks: {
              text: {
                fontSize: 11,
                fontWeight: 600,
                fill: 'var(--foreground)',
              },
            },
            legend: {
              text: {
                fontSize: 12,
                fontWeight: 700,
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
          crosshair: {
            line: {
              stroke: 'var(--foreground)',
              strokeOpacity: 0.5,
            },
          },
        }}
        colors={chartData.map(d => d.color)}
        lineWidth={2}
        pointSize={6}
        pointColor={{ theme: 'background' }}
        pointBorderWidth={2}
        pointBorderColor={{ from: 'serieColor' }}
        useMesh={true}
        legends={[
          {
            anchor: 'right',
            direction: 'column',
            translateX: 80,
            itemWidth: 70,
            itemHeight: 18,
            symbolSize: 10,
            symbolShape: 'circle',
            itemTextColor: 'var(--foreground)',
          },
        ]}
        enableSlices={false}
        tooltip={({ point }) => (
          <div className="bg-[var(--card)] border border-[var(--border)] px-3 py-2 rounded shadow-lg text-xs font-mono">
            <div className="flex items-center gap-2">
              <span
                className="w-2 h-2 rounded-full"
                style={{ backgroundColor: point.seriesColor }}
              />
              <span className="font-bold">{point.seriesId}</span>
            </div>
            <div className="text-[var(--muted)] mt-1">
              Week {point.data.x}: {point.data.yFormatted}
            </div>
          </div>
        )}
      />
    </div>
  );
}
