'use client';

import { ResponsiveLine, LineCustomSvgLayerProps, LineSeries } from '@nivo/line';

type CustomLayerProps = LineCustomSvgLayerProps<LineSeries>;
import { LineChartData } from '@/types/chart';

interface Props {
  data: LineChartData;
  highlightedLabels?: string[] | null;
  selectedLabel?: string | null;
  onSelect?: (label: string | null) => void;
}

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// Custom layer to render marching ants on selected line
function createSelectedLineLayer(selectedLabel: string | null) {
  return function SelectedLineLayer({ series, lineGenerator }: CustomLayerProps) {
    if (!selectedLabel) return null;

    const selectedSeries = series.find(s => s.id === selectedLabel);
    if (!selectedSeries) return null;

    const path = lineGenerator(selectedSeries.data.map(d => ({
      x: d.position.x,
      y: d.position.y,
    })));

    if (!path) return null;

    return (
      <g>
        {/* Background stroke - black */}
        <path
          d={path}
          fill="none"
          stroke="#000"
          strokeWidth={4}
          strokeDasharray="6,6"
          strokeLinecap="round"
        />
        {/* Foreground stroke - colored, animated */}
        <path
          d={path}
          fill="none"
          stroke={selectedSeries.color}
          strokeWidth={4}
          strokeDasharray="6,6"
          strokeDashoffset={6}
          strokeLinecap="round"
          style={{ animation: 'marching-ants 1s linear infinite' }}
        />
      </g>
    );
  };
}

// Custom legend layer with click handling
function createClickableLegendLayer(
  seriesData: Array<{ id: string; color: string }>,
  selectedLabel: string | null,
  onSelect?: (label: string | null) => void
) {
  return function ClickableLegendLayer({ innerWidth, innerHeight }: CustomLayerProps) {
    const legendX = innerWidth + 15;
    const itemHeight = 18;
    const startY = 0;

    return (
      <g>
        {seriesData.map((series, index) => {
          const isSelected = selectedLabel === series.id;
          const y = startY + index * itemHeight;

          return (
            <g
              key={series.id}
              transform={`translate(${legendX}, ${y})`}
              style={{ cursor: 'pointer' }}
              onClick={() => onSelect?.(series.id)}
            >
              {/* Selection highlight background */}
              {isSelected && (
                <rect
                  x={-4}
                  y={-2}
                  width={74}
                  height={itemHeight}
                  fill="var(--border)"
                  rx={2}
                />
              )}
              {/* Color circle */}
              <circle
                cx={5}
                cy={itemHeight / 2 - 2}
                r={5}
                fill={series.color}
              />
              {/* Label */}
              <text
                x={15}
                y={itemHeight / 2 + 1}
                style={{
                  fontSize: 11,
                  fontFamily: 'var(--font-geist-mono), monospace',
                  fill: 'var(--foreground)',
                  fontWeight: isSelected ? 700 : 400,
                }}
              >
                {series.id}
              </text>
            </g>
          );
        })}
      </g>
    );
  };
}

export function LineChart({ data, highlightedLabels, selectedLabel, onSelect }: Props) {
  const chartData = data.series.map(series => {
    const isHighlighted = !highlightedLabels || highlightedLabels.includes(series.label);
    const color = isHighlighted ? series.color : hexToRgba(series.color, 0.15);
    return {
      id: series.label,
      color,
      data: series.dataPoints.map(p => ({ x: p.x, y: p.y })),
    };
  });

  const seriesColors = data.series.map(s => ({ id: s.label, color: s.color }));
  const SelectedLineLayer = createSelectedLineLayer(selectedLabel || null);
  const ClickableLegendLayer = createClickableLegendLayer(seriesColors, selectedLabel || null, onSelect);

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
        layers={[
          'grid',
          'markers',
          'axes',
          'areas',
          'crosshair',
          'lines',
          SelectedLineLayer,
          'points',
          'slices',
          'mesh',
          ClickableLegendLayer,
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
              Week {String(point.data.x)}: {point.data.yFormatted}
            </div>
          </div>
        )}
      />
    </div>
  );
}
