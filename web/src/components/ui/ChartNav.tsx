'use client';

import { useEffect, useState } from 'react';

interface ChartNavProps {
  charts: { key: string; title: string }[];
  activeChart: string | null;
  onChartClick: (key: string) => void;
}

export function ChartNav({ charts, activeChart, onChartClick }: ChartNavProps) {
  const [isDark, setIsDark] = useState(false);
  const [isOpen, setIsOpen] = useState(false);

  useEffect(() => {
    const checkDark = () => setIsDark(document.documentElement.classList.contains('dark'));
    checkDark();
    const observer = new MutationObserver(checkDark);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] });
    return () => observer.disconnect();
  }, []);

  if (charts.length === 0) return null;

  const activeChartTitle = charts.find(c => c.key === activeChart)?.title || 'Jump to chart';

  const handleChartClick = (key: string) => {
    onChartClick(key);
    setIsOpen(false);
  };

  return (
    <nav className="mb-4 sticky top-10 z-30 bg-[var(--background)] py-1">
      {/* Mobile dropdown */}
      <div className="md:hidden">
        <button
          onClick={() => setIsOpen(!isOpen)}
          className="flex items-center justify-between w-full px-3 py-2 text-sm font-medium bg-[var(--border)] rounded"
          aria-label="Toggle chart navigation"
        >
          <span className="truncate">{activeChartTitle}</span>
          <svg
            className={`w-5 h-5 ml-2 flex-shrink-0 transition-transform ${isOpen ? 'rotate-180' : ''}`}
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>

        {isOpen && (
          <div className="mt-2 p-2 bg-[var(--card)] border border-[var(--border)] rounded shadow-lg max-h-[60vh] overflow-y-auto">
            <div className="flex flex-wrap gap-2">
              {charts.map(chart => {
                const isActive = activeChart === chart.key;
                return (
                  <button
                    key={chart.key}
                    onClick={() => handleChartClick(chart.key)}
                    style={
                      !isActive
                        ? { backgroundColor: isDark ? 'rgba(163, 163, 163, 0.2)' : '#ffffff' }
                        : undefined
                    }
                    className={`px-3 py-1 text-xs font-medium rounded-full whitespace-nowrap transition-colors border ${
                      isActive
                        ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
                        : isDark
                          ? 'text-[var(--muted)] border-transparent hover:text-[var(--foreground)]'
                          : 'text-black border-gray-200 hover:bg-gray-100'
                    }`}
                  >
                    {chart.title}
                  </button>
                );
              })}
            </div>
          </div>
        )}
      </div>

      {/* Desktop horizontal scroll */}
      <div className="hidden md:block overflow-x-auto scrollbar-hide">
        <div className="flex gap-2 pb-2">
          {charts.map(chart => {
            const isActive = activeChart === chart.key;
            return (
              <button
                key={chart.key}
                onClick={() => onChartClick(chart.key)}
                style={
                  !isActive
                    ? { backgroundColor: isDark ? 'rgba(163, 163, 163, 0.2)' : '#ffffff' }
                    : undefined
                }
                className={`px-3 py-1 text-xs font-medium rounded-full whitespace-nowrap transition-colors border ${
                  isActive
                    ? 'bg-[var(--foreground)] text-[var(--background)] border-[var(--foreground)]'
                    : isDark
                      ? 'text-[var(--muted)] border-transparent hover:text-[var(--foreground)]'
                      : 'text-black border-gray-200 hover:bg-gray-100'
                }`}
              >
                {chart.title}
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}
