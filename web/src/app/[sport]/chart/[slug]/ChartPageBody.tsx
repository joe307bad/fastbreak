import { fetchOrderedSportsWithCharts } from '@/lib/api';
import { ChartWithTable } from '@/components/charts/ChartWithTable';
import { SportTabs } from '@/components/ui/SportTabs';
import { REPORT_CARD_TYPES } from '@/lib/charts';
import type { ChartData } from '@/types/chart';

/** Shared by /[sport]/chart/[slug] and its /[team] child, which differ only in metadata and the opening team. */
export async function ChartPageBody({ data, initialTeamCode }: { data: ChartData; initialTeamCode?: string }) {
  const orderedSports = await fetchOrderedSportsWithCharts();
  const isReportCard = REPORT_CARD_TYPES.includes(data.visualizationType);

  return (
    <main
      className={`max-w-[2000px] mx-auto px-0 md:px-4 ${
        isReportCard ? 'lg:h-[calc(100vh-2.5rem)] lg:flex lg:flex-col lg:min-h-0' : ''
      }`}
    >
      <SportTabs orderedSports={orderedSports} />

      <div className={`px-2 md:px-0 ${isReportCard ? 'flex-1 min-h-0 lg:overflow-hidden' : ''}`}>
        <div className={isReportCard ? 'h-full min-h-0 overflow-hidden' : 'lg:h-[calc(100vh-10rem)] lg:overflow-hidden'}>
          <ChartWithTable
            data={data}
            title={data.title}
            subtitle={data.subtitle}
            source={data.source}
            lastUpdated={data.lastUpdated}
            initialTeamCode={initialTeamCode}
          />
        </div>
      </div>
    </main>
  );
}
