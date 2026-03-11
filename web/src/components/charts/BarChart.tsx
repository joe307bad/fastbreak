'use client';

import { ResponsiveBar, BarLayer } from '@nivo/bar';
import { BarChartData, ReferenceLine } from '@/types/chart';

interface Props {
  data: BarChartData;
  highlightedLabels?: string[] | null;
  selectedLabel?: string | null;
  onSelect?: (label: string | null) => void;
}

// Custom layer to add clipPath for the chart area
const ClipLayer: BarLayer<{ label: string; value: number }> = ({ innerWidth, innerHeight }) => (
  <defs>
    <clipPath id="bar-clip">
      <rect x={0} y={0} width={innerWidth} height={innerHeight} />
    </clipPath>
  </defs>
);

// Factory function to create clipped bars layer with selection support
function createClippedBarsLayer(
  selectedLabel?: string | null,
  onSelect?: (label: string | null) => void
): BarLayer<{ label: string; value: number }> {
  return (props) => {
    const { bars } = props;
    return (
      <g clipPath="url(#bar-clip)">
        {bars.map((bar) => {
          const isSelected = selectedLabel === bar.data.data.label;
          return (
            <g key={bar.key}>
              {/* Selection highlight - marching ants */}
              {isSelected && (
                <>
                  <rect
                    x={bar.x - 2}
                    y={bar.y - 2}
                    width={bar.width + 4}
                    height={bar.height + 4}
                    fill="none"
                    stroke="#000"
                    strokeWidth={3}
                    strokeDasharray="6,6"
                  />
                  <rect
                    x={bar.x - 2}
                    y={bar.y - 2}
                    width={bar.width + 4}
                    height={bar.height + 4}
                    fill="none"
                    stroke={bar.color}
                    strokeWidth={3}
                    strokeDasharray="6,6"
                    strokeDashoffset={6}
                    style={{ animation: 'marching-ants 1s linear infinite' }}
                  />
                </>
              )}
              <rect
                x={bar.x}
                y={bar.y}
                width={bar.width}
                height={bar.height}
                fill={bar.color}
                style={{ cursor: 'pointer' }}
                onClick={() => onSelect?.(bar.data.data.label)}
              />
            </g>
          );
        })}
      </g>
    );
  };
}

// Layer to draw connecting lines from bars to the labels
const BarConnectorLinesLayer: BarLayer<{ label: string; value: number }> = (props) => {
  const { bars, innerHeight } = props;
  return (
    <g>
      {bars.map((bar, index) => {
        // Line from bottom of bar to the label position
        const barBottom = Math.min(bar.y + bar.height, innerHeight);
        // Match the staggered label positions from renderTick
        const isEven = index % 2 === 0;
        const labelOffset = 16 + (isEven ? 0 : 18) - 4; // yOffset - 4 from renderTick
        return (
          <line
            key={`connector-${bar.key}`}
            x1={bar.x + bar.width / 2}
            y1={barBottom}
            x2={bar.x + bar.width / 2}
            y2={innerHeight + labelOffset}
            stroke={bar.color}
            strokeWidth={1}
          />
        );
      })}
    </g>
  );
};

// Factory function to create reference line layers
function createReferenceLinesLayer(
  topRef?: ReferenceLine,
  bottomRef?: ReferenceLine
): BarLayer<{ label: string; value: number }> {
  return (props) => {
    const { innerWidth, yScale } = props;
    const lines: JSX.Element[] = [];

    if (topRef) {
      const y = yScale(topRef.value);
      if (typeof y === 'number') {
        lines.push(
          <g key="top-ref">
            <line
              x1={0}
              y1={y}
              x2={innerWidth}
              y2={y}
              stroke={topRef.color}
              strokeWidth={2}
              strokeDasharray="6,4"
            />
            <text
              x={innerWidth - 4}
              y={y - 6}
              textAnchor="end"
              style={{
                fontFamily: 'var(--font-geist-mono), monospace',
                fontSize: 10,
                fontWeight: 600,
                fill: topRef.color,
              }}
            >
              {topRef.label}
            </text>
          </g>
        );
      }
    }

    if (bottomRef) {
      const y = yScale(bottomRef.value);
      if (typeof y === 'number') {
        lines.push(
          <g key="bottom-ref">
            <line
              x1={0}
              y1={y}
              x2={innerWidth}
              y2={y}
              stroke={bottomRef.color}
              strokeWidth={2}
              strokeDasharray="6,4"
            />
            <text
              x={innerWidth - 4}
              y={y - 6}
              textAnchor="end"
              style={{
                fontFamily: 'var(--font-geist-mono), monospace',
                fontSize: 10,
                fontWeight: 600,
                fill: bottomRef.color,
              }}
            >
              {bottomRef.label}
            </text>
          </g>
        );
      }
    }

    return <>{lines}</>;
  };
}

export function BarChart({ data, highlightedLabels, selectedLabel, onSelect }: Props) {
  const chartData = data.dataPoints.map(p => ({
    label: p.label,
    value: p.value,
  }));

  const minValue = Math.min(...data.dataPoints.map(p => p.value));
  const maxValue = Math.max(...data.dataPoints.map(p => p.value));

  // Include reference line values in scale calculations
  const refValues = [
    data.topReferenceLine?.value,
    data.bottomReferenceLine?.value,
  ].filter((v): v is number => v !== undefined);

  const allMax = Math.max(maxValue, ...refValues);
  const allMin = Math.min(minValue, ...refValues);

  // Start scale below the minimum value for visual padding
  // Bars extend from 0, so they'll be clipped by the clipPath
  const scaleMin = allMin - 10;
  const scaleMax = allMax;

  // Create reference lines layer
  const ReferenceLinesLayer = createReferenceLinesLayer(
    data.topReferenceLine,
    data.bottomReferenceLine
  );

  // Create clipped bars layer with selection support
  const ClippedBarsLayer = createClippedBarsLayer(selectedLabel, onSelect);

  // Helper to darken a hex color
  const darkenColor = (hex: string, factor: number = 0.3): string => {
    const r = Math.floor(parseInt(hex.slice(1, 3), 16) * (1 - factor));
    const g = Math.floor(parseInt(hex.slice(3, 5), 16) * (1 - factor));
    const b = Math.floor(parseInt(hex.slice(5, 7), 16) * (1 - factor));
    return `rgb(${r}, ${g}, ${b})`;
  };

  const getBarColor = (barData: { label: string; value: number }) => {
    const { topReferenceLine, bottomReferenceLine } = data;
    let baseColor: string;

    // Determine color based on reference lines
    if (topReferenceLine && bottomReferenceLine) {
      if (barData.value >= topReferenceLine.value) {
        // Above top reference line - darker version of top color
        baseColor = darkenColor(topReferenceLine.color, 0.2);
      } else if (barData.value <= bottomReferenceLine.value) {
        // Below bottom reference line - darker version of bottom color
        baseColor = darkenColor(bottomReferenceLine.color, 0.2);
      } else {
        // Between reference lines - orange
        baseColor = '#FF9800';
      }
    } else if (topReferenceLine) {
      baseColor = barData.value >= topReferenceLine.value
        ? darkenColor(topReferenceLine.color, 0.2)
        : '#FF9800';
    } else if (bottomReferenceLine) {
      baseColor = barData.value <= bottomReferenceLine.value
        ? darkenColor(bottomReferenceLine.color, 0.2)
        : '#FF9800';
    } else {
      // No reference lines - use default green/red
      baseColor = barData.value >= 0 ? '#4CAF50' : '#F44336';
    }

    if (highlightedLabels && !highlightedLabels.includes(barData.label)) {
      // Return dimmed color with alpha
      return baseColor.startsWith('rgb')
        ? baseColor.replace('rgb', 'rgba').replace(')', ', 0.15)')
        : `${baseColor}26`; // 26 is hex for ~15% opacity
    }
    return baseColor;
  };

  return (
    <div className="w-full h-full overflow-hidden">
      <ResponsiveBar
        data={chartData}
        keys={['value']}
        indexBy="label"
        margin={{ top: 20, right: 20, bottom: 80, left: 50 }}
        padding={0.3}
        valueScale={{ type: 'linear', min: scaleMin, max: scaleMax }}
        indexScale={{ type: 'band', round: true }}
        colors={({ data }) => getBarColor(data as { label: string; value: number })}
        layers={['grid', ClipLayer, ClippedBarsLayer, BarConnectorLinesLayer, ReferenceLinesLayer, 'axes', 'markers', 'legends', 'annotations']}
        axisBottom={{
          tickSize: 0,
          tickPadding: 24,
          tickRotation: -45,
          renderTick: (tick) => {
            const isEven = tick.tickIndex % 2 === 0;
            const yOffset = 16 + (isEven ? 0 : 18);
            return (
              <g transform={`translate(${tick.x},${tick.y})`}>
                <text
                  x={0}
                  y={yOffset}
                  textAnchor="end"
                  dominantBaseline="central"
                  transform={`rotate(-45, 0, ${yOffset})`}
                  style={{
                    fontFamily: 'var(--font-geist-mono), monospace',
                    fontSize: 11,
                    fontWeight: 600,
                    fill: 'var(--foreground)',
                  }}
                >
                  {tick.value}
                </text>
              </g>
            );
          },
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
