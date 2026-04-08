'use client';

import { useRef, useCallback, useState, ReactNode } from 'react';
import { downloadChartAsPng, copyChartAsPng, QuadrantLegendItem } from '@/lib/chartExport';

interface Props {
  children: ReactNode;
  title?: string;
  quadrantLegend?: QuadrantLegendItem[];
  source?: string;
}

export function ChartDownloadWrapper({ children, title, quadrantLegend, source }: Props) {
  const chartRef = useRef<HTMLDivElement>(null);
  const [copied, setCopied] = useState(false);

  const getFilterLabel = useCallback(() => {
    if (!chartRef.current) return undefined;
    const activeFilterEl = chartRef.current.querySelector('[data-active-filter]');
    return activeFilterEl?.getAttribute('data-active-filter') || undefined;
  }, []);

  const handleDownload = useCallback(() => {
    if (!chartRef.current) return;
    downloadChartAsPng(chartRef.current, title, quadrantLegend, getFilterLabel(), source);
  }, [title, quadrantLegend, getFilterLabel, source]);

  const handleCopy = useCallback(async () => {
    if (!chartRef.current) return;
    const ok = await copyChartAsPng(chartRef.current, title, quadrantLegend, getFilterLabel(), source);
    if (ok) {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  }, [title, quadrantLegend, getFilterLabel, source]);

  const btnClass = "p-1 rounded hover:bg-[var(--border)] text-[var(--muted)] hover:text-[var(--foreground)] transition-colors z-10 opacity-50 hover:opacity-100";

  return (
    <div className="relative h-full">
      <div className="absolute top-[5px] right-[5px] flex gap-0.5 z-10">
        <button
          onClick={handleCopy}
          className={btnClass}
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
        <button
          onClick={handleDownload}
          className={btnClass}
          title="Download as PNG"
          aria-label="Download chart as PNG"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M8 2v8M5 7l3 3 3-3" />
            <path d="M2 11v2a1 1 0 001 1h10a1 1 0 001-1v-2" />
          </svg>
        </button>
      </div>
      <div ref={chartRef} className="h-full">
        {children}
      </div>
    </div>
  );
}
