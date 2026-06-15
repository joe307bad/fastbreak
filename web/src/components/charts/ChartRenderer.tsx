'use client';

import { ChartData } from '@/types/chart';
import { ScatterPlot } from './ScatterPlot';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { Table } from './Table';
import { Matchup } from './Matchup';
import { MLBTeamReportCard, MLBTeamReportCardSummary } from './MLBTeamReportCard';

interface Props {
  data: ChartData;
  highlightedLabels?: string[] | null;
  selectedLabel?: string | null;
  onSelect?: (label: string | null) => void;
  compact?: boolean;
}

export function ChartRenderer({ data, highlightedLabels, selectedLabel, onSelect, compact }: Props) {
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
    case 'MLB_TEAM_REPORT_CARD':
      return (
        <div className="h-full min-h-0">
          {compact
            ? <MLBTeamReportCardSummary data={data} />
            : <MLBTeamReportCard data={data} />}
        </div>
      );
    default:
      return null;
  }
}
