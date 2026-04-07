'use client';

import { useRef, useCallback, ReactNode } from 'react';
import { downloadChartAsPng, QuadrantLegendItem } from '@/lib/chartExport';

interface Props {
  children: ReactNode;
  title?: string;
  quadrantLegend?: QuadrantLegendItem[];
}

export function ChartDownloadWrapper({ children, title, quadrantLegend }: Props) {
  const chartRef = useRef<HTMLDivElement>(null);

  const handleDownload = useCallback(() => {
    if (!chartRef.current) return;
    // Read active week filter from data attribute if present
    const activeFilterEl = chartRef.current.querySelector('[data-active-filter]');
    const filterLabel = activeFilterEl?.getAttribute('data-active-filter') || undefined;
    downloadChartAsPng(chartRef.current, title, quadrantLegend, filterLabel);
  }, [title, quadrantLegend]);

  return (
    <div className="relative h-full">
      <button
        onClick={handleDownload}
        className="absolute top-[5px] right-[5px] p-1 rounded hover:bg-[var(--border)] text-[var(--muted)] hover:text-[var(--foreground)] transition-colors z-10 opacity-50 hover:opacity-100"
        title="Download as PNG"
        aria-label="Download chart as PNG"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M8 2v8M5 7l3 3 3-3" />
          <path d="M2 11v2a1 1 0 001 1h10a1 1 0 001-1v-2" />
        </svg>
      </button>
      <div ref={chartRef} className="h-full">
        {children}
      </div>
    </div>
  );
}
