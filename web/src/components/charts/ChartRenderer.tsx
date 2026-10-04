'use client';

import { ChartData } from '@/types/chart';
import { ScatterPlot } from './ScatterPlot';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { Table } from './Table';
import { Matchup } from './Matchup';
import { TeamReportCard, TeamReportCardSummary } from './TeamReportCard';
import type { ChartUpdateSchedule } from '@/lib/diagnostics';

interface Props {
  data: ChartData;
  highlightedLabels?: string[] | null;
  selectedLabel?: string | null;
  onSelect?: (label: string | null) => void;
  compact?: boolean;
  /** Report cards only: team to open with */
  initialTeamCode?: string;
  /** Report cards only: when the pipeline last wrote this chart and when it runs next */
  updateSchedule?: ChartUpdateSchedule | null;
}

export function ChartRenderer({ data, highlightedLabels, selectedLabel, onSelect, compact, initialTeamCode, updateSchedule }: Props) {
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
    case 'NFL_TEAM_REPORT_CARD':
      return (
        <div className="h-full min-h-0">
          {compact
            ? <TeamReportCardSummary data={data} />
            : <TeamReportCard data={data} initialTeamCode={initialTeamCode} updateSchedule={updateSchedule} />}
        </div>
      );
    default:
      return null;
  }
}
