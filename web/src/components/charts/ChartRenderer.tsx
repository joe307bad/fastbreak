'use client';

import { ChartData } from '@/types/chart';
import { ScatterPlot } from './ScatterPlot';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { Table } from './Table';
import { Matchup } from './Matchup';

interface Props {
  data: ChartData;
  highlightedLabels?: string[] | null;
  selectedLabel?: string | null;
  onSelect?: (label: string | null) => void;
}

export function ChartRenderer({ data, highlightedLabels, selectedLabel, onSelect }: Props) {
  switch (data.visualizationType) {
    case 'SCATTER_PLOT':
      return <ScatterPlot data={data} highlightedLabels={highlightedLabels} selectedLabel={selectedLabel} onSelect={onSelect} />;
    case 'LINE_CHART':
      return <LineChart data={data} highlightedLabels={highlightedLabels} selectedLabel={selectedLabel} onSelect={onSelect} />;
    case 'BAR_CHART':
    case 'BAR_GRAPH':
      return <BarChart data={data} highlightedLabels={highlightedLabels} selectedLabel={selectedLabel} onSelect={onSelect} />;
    case 'TABLE':
      return <Table data={data} />;
    case 'MATCHUP':
      return <Matchup data={data} />;
    default:
      return null;
  }
}
