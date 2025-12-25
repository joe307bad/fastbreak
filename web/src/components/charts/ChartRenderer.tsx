'use client';

import { ChartData } from '@/types/chart';
import { ScatterPlot } from './ScatterPlot';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { Table } from './Table';
import { Matchup } from './Matchup';

interface Props {
  data: ChartData;
}

export function ChartRenderer({ data }: Props) {
  switch (data.visualizationType) {
    case 'SCATTER_PLOT':
      return <ScatterPlot data={data} />;
    case 'LINE_CHART':
      return <LineChart data={data} />;
    case 'BAR_CHART':
    case 'BAR_GRAPH':
      return <BarChart data={data} />;
    case 'TABLE':
      return <Table data={data} />;
    case 'MATCHUP':
      return <Matchup data={data} />;
    default:
      return <div>Unsupported visualization type</div>;
  }
}
