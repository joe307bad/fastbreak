'use client';

import { useState, useCallback, useMemo, useRef } from 'react';
import { downloadChartAsPng, copyChartAsPng, QuadrantLegendItem } from '@/lib/chartExport';
import { ChartRenderer } from './ChartRenderer';
import { ChartDataTable } from './ChartDataTable';
import { ChartData, ScatterPlotData } from '@/types/chart';

interface Props {
  data: ChartData;
  title?: string;
  subtitle?: string;
  source?: string;
  lastUpdated?: string;
}

// Helper to darken a hex color
function darkenColor(hex: string, factor: number = 0.2): string {
  const r = Math.floor(parseInt(hex.slice(1, 3), 16) * (1 - factor));
  const g = Math.floor(parseInt(hex.slice(3, 5), 16) * (1 - factor));
  const b = Math.floor(parseInt(hex.slice(5, 7), 16) * (1 - factor));
  return `rgb(${r}, ${g}, ${b})`;
}

type QuadrantKey = 'topRight' | 'topLeft' | 'bottomLeft' | 'bottomRight';

// Get quadrant for a scatter plot point
function getPointQuadrant(data: ScatterPlotData, label: string): QuadrantKey | null {
  const point = data.dataPoints.find(p => p.label === label);
  if (!point) return null;

  const xValues = data.dataPoints.map(p => p.x);
  const yValues = data.dataPoints.map(p => p.y);
  const xMean = xValues.reduce((sum, val) => sum + val, 0) / xValues.length;
  const yMean = yValues.reduce((sum, val) => sum + val, 0) / yValues.length;

  const isHighX = point.x >= xMean;
  const isHighY = data.invertYAxis ? point.y <= yMean : point.y >= yMean;

  if (isHighX && isHighY) return 'topRight';
  if (!isHighX && isHighY) return 'topLeft';
  if (!isHighX && !isHighY) return 'bottomLeft';
  return 'bottomRight';
}

// Get all labels in a specific quadrant
function getLabelsInQuadrant(data: ScatterPlotData, quadrant: QuadrantKey): string[] {
  return data.dataPoints
    .filter(p => getPointQuadrant(data, p.label) === quadrant)
    .map(p => p.label);
}

// Get color for a specific item based on chart type
function getItemColor(data: ChartData, label: string): string | null {
  if (data.visualizationType === 'SCATTER_PLOT') {
    const quadrant = getPointQuadrant(data, label);
    if (!quadrant) return null;

    const quadrantMap = {
      topRight: data.quadrantTopRight,
      topLeft: data.quadrantTopLeft,
      bottomLeft: data.quadrantBottomLeft,
      bottomRight: data.quadrantBottomRight,
    };

    return quadrantMap[quadrant]?.color || '#888';
  }

  if (data.visualizationType === 'BAR_CHART' || data.visualizationType === 'BAR_GRAPH') {
    const point = data.dataPoints.find(p => p.label === label);
    if (!point) return null;

    const { topReferenceLine, bottomReferenceLine } = data;

    if (topReferenceLine && bottomReferenceLine) {
      if (point.value >= topReferenceLine.value) {
        return darkenColor(topReferenceLine.color, 0.2);
      } else if (point.value <= bottomReferenceLine.value) {
        return darkenColor(bottomReferenceLine.color, 0.2);
      } else {
        return '#FF9800';
      }
    } else if (topReferenceLine) {
      return point.value >= topReferenceLine.value
        ? darkenColor(topReferenceLine.color, 0.2)
        : '#FF9800';
    } else if (bottomReferenceLine) {
      return point.value <= bottomReferenceLine.value
        ? darkenColor(bottomReferenceLine.color, 0.2)
        : '#FF9800';
    } else {
      return point.value >= 0 ? '#4CAF50' : '#F44336';
    }
  }

  if (data.visualizationType === 'LINE_CHART') {
    const series = data.series.find(s => s.label === label);
    return series?.color || null;
  }

  return null;
}

// Quadrant Legend Component
function QuadrantLegend({
  data,
  selectedQuadrant,
  onSelectQuadrant,
}: {
  data: ScatterPlotData;
  selectedQuadrant: QuadrantKey | null;
  onSelectQuadrant: (quadrant: QuadrantKey | null) => void;
}) {
  const quadrants = ([
    { key: 'topRight' as const, config: data.quadrantTopRight },
    { key: 'topLeft' as const, config: data.quadrantTopLeft },
    { key: 'bottomLeft' as const, config: data.quadrantBottomLeft },
    { key: 'bottomRight' as const, config: data.quadrantBottomRight },
  ] as const).filter(q => q.config);

  if (quadrants.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-3 mt-3">
      {quadrants.map(({ key, config }) => {
        const isSelected = selectedQuadrant === key;
        const count = getLabelsInQuadrant(data, key).length;

        return (
          <button
            key={key}
            onClick={() => onSelectQuadrant(isSelected ? null : key)}
            className={`flex items-center gap-2 px-2 py-1 rounded text-xs transition-all ${
              isSelected
                ? 'ring-1 ring-[var(--foreground)]'
                : 'hover:bg-[var(--border)]'
            } ${selectedQuadrant && !isSelected ? 'opacity-40' : ''}`}
          >
            <span
              className="w-3 h-3 rounded-full shrink-0"
              style={{ backgroundColor: config?.color }}
            />
            <span className="font-medium">{config?.label}</span>
            <span className="text-[var(--muted)]">({count})</span>
          </button>
        );
      })}
      {selectedQuadrant && (
        <button
          onClick={() => onSelectQuadrant(null)}
          className="text-xs text-[var(--muted)] hover:text-[var(--foreground)] underline"
        >
          Clear
        </button>
      )}
    </div>
  );
}

const DOWNLOADABLE_TYPES = ['SCATTER_PLOT', 'LINE_CHART', 'BAR_CHART', 'BAR_GRAPH'];

const chartBtnClass = "p-1 rounded hover:bg-[var(--border)] text-[var(--muted)] hover:text-[var(--foreground)] transition-colors opacity-50 hover:opacity-100";

function CopyButton({ onClick }: { onClick: () => void }) {
  const [copied, setCopied] = useState(false);

  const handleClick = useCallback(() => {
    onClick();
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }, [onClick]);

  return (
    <button
      onClick={handleClick}
      className={chartBtnClass}
      title="Copy as PNG"
      aria-label="Copy chart as PNG"
    >
      {copied ? (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 8.5l3 3 7-7" />
        </svg>
      ) : (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="5" y="5" width="8" height="8" rx="1" />
          <path d="M3 11V3a1 1 0 011-1h8" />
        </svg>
      )}
    </button>
  );
}

function DownloadButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={chartBtnClass}
      title="Download as PNG"
      aria-label="Download chart as PNG"
    >
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M8 2v8M5 7l3 3 3-3" />
        <path d="M2 11v2a1 1 0 001 1h10a1 1 0 001-1v-2" />
      </svg>
    </button>
  );
}

export function ChartWithTable({ data, title, subtitle, source, lastUpdated }: Props) {
  const [highlightedLabels, setHighlightedLabels] = useState<string[] | null>(null);
  const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
  const [selectedQuadrant, setSelectedQuadrant] = useState<QuadrantKey | null>(null);
  const chartRef = useRef<HTMLDivElement>(null);

  const showDownload = DOWNLOADABLE_TYPES.includes(data.visualizationType);

  const quadrantLegendItems = useMemo((): QuadrantLegendItem[] | undefined => {
    if (data.visualizationType !== 'SCATTER_PLOT') return undefined;
    const quadrants = [
      { config: data.quadrantTopRight, key: 'topRight' as const },
      { config: data.quadrantTopLeft, key: 'topLeft' as const },
      { config: data.quadrantBottomLeft, key: 'bottomLeft' as const },
      { config: data.quadrantBottomRight, key: 'bottomRight' as const },
    ];
    const items = quadrants
      .filter(q => q.config)
      .map(q => ({
        label: q.config!.label,
        color: q.config!.color,
        count: getLabelsInQuadrant(data, q.key).length,
      }));
    return items.length > 0 ? items : undefined;
  }, [data]);

  const handleDownload = useCallback(() => {
    if (chartRef.current) {
      downloadChartAsPng(chartRef.current, title, quadrantLegendItems, undefined, source);
    }
  }, [title, quadrantLegendItems, source]);

  const handleCopy = useCallback(() => {
    if (chartRef.current) {
      copyChartAsPng(chartRef.current, title, quadrantLegendItems, undefined, source);
    }
  }, [title, quadrantLegendItems, source]);

  // Compute effective highlighted labels based on table filters and quadrant filter
  const effectiveHighlightedLabels = useMemo(() => {
    if (data.visualizationType !== 'SCATTER_PLOT') {
      return highlightedLabels;
    }

    const quadrantLabels = selectedQuadrant
      ? getLabelsInQuadrant(data, selectedQuadrant)
      : null;

    // If both filters are active, intersect them
    if (highlightedLabels && quadrantLabels) {
      const intersection = highlightedLabels.filter(l => quadrantLabels.includes(l));
      return intersection.length > 0 ? intersection : [];
    }

    // Return whichever filter is active
    return quadrantLabels || highlightedLabels;
  }, [data, highlightedLabels, selectedQuadrant]);

  const handleHighlight = useCallback((labels: string[]) => {
    // If all items are shown (no filter), clear highlighting
    const totalItems = getTotalItems(data);
    if (labels.length === totalItems) {
      setHighlightedLabels(null);
    } else {
      setHighlightedLabels(labels);
    }
  }, [data]);

  const handleSelect = useCallback((label: string | null) => {
    // Toggle selection if clicking the same item
    setSelectedLabel(prev => prev === label ? null : label);
  }, []);

  const handleQuadrantSelect = useCallback((quadrant: QuadrantKey | null) => {
    setSelectedQuadrant(quadrant);
  }, []);

  // Create a stable color getter function
  const getColorForLabel = useCallback((label: string) => {
    return getItemColor(data, label);
  }, [data]);

  const isScatterPlot = data.visualizationType === 'SCATTER_PLOT';
  const isReportCard = data.visualizationType === 'MLB_TEAM_REPORT_CARD';

  return (
    <div className={`flex flex-col gap-4 h-full min-h-0 ${isReportCard ? '' : 'lg:flex-row'}`}>
      {/* Chart + About — left 50% (full height for report cards) */}
      <div className={isReportCard ? 'w-full h-full min-h-0 flex flex-col' : 'lg:w-1/2 lg:overflow-y-auto'}>
        <section className={`relative ${isReportCard ? 'flex-1 min-h-0 flex flex-col' : 'border border-[var(--border)] bg-[var(--card)] rounded-none md:rounded p-2 md:p-4'}`}>
          {showDownload && (
            <div className="absolute top-[5px] right-[5px] flex gap-0.5 z-10">
              <CopyButton onClick={handleCopy} />
              <DownloadButton onClick={handleDownload} />
            </div>
          )}
          <div ref={chartRef} className={isReportCard ? 'flex-1 min-h-0 h-full' : 'h-[400px] md:h-[500px]'}>
            <ChartRenderer
              data={data}
              highlightedLabels={effectiveHighlightedLabels}
              selectedLabel={selectedLabel}
              onSelect={handleSelect}
            />
          </div>
          {isScatterPlot && (
            <QuadrantLegend
              data={data}
              selectedQuadrant={selectedQuadrant}
              onSelectQuadrant={handleQuadrantSelect}
            />
          )}
        </section>
        {!isReportCard && title && (
          <div className="mt-4">
            <h1 className="text-lg font-bold">{title}</h1>
            {subtitle && (
              <p className="text-sm text-[var(--muted)] mt-1">{subtitle}</p>
            )}
            {source && lastUpdated && (
              <div className="mt-2 text-xs text-[var(--muted)]">
                {source} · {new Date(lastUpdated).toLocaleDateString('en-US', { timeZone: 'UTC' })}
              </div>
            )}
          </div>
        )}
        {!isReportCard && data.description && (
          <aside className="mt-4">
            <h2 className="text-sm font-bold mb-2">About</h2>
            <p className="text-xs text-[var(--muted)] leading-relaxed">{data.description}</p>
          </aside>
        )}
      </div>

      {/* Data table — right 50% */}
      {!isReportCard && (
      <section className="lg:w-1/2 flex flex-col min-h-0">
        <ChartDataTable
          data={data}
          onHighlight={handleHighlight}
          selectedLabel={selectedLabel}
          getItemColor={getColorForLabel}
          onSelect={handleSelect}
          quadrantFilter={selectedQuadrant}
        />
      </section>
      )}
    </div>
  );
}

function getTotalItems(data: ChartData): number {
  switch (data.visualizationType) {
    case 'SCATTER_PLOT':
    case 'BAR_CHART':
    case 'BAR_GRAPH':
    case 'TABLE':
      return data.dataPoints.length;
    case 'LINE_CHART':
      return data.series.length;
    default:
      return 0;
  }
}
