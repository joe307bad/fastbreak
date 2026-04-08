const CSS_VARS = ['--foreground', '--background', '--border', '--muted', '--card'] as const;

function resolveVars(svgString: string, container: HTMLElement): { svg: string; vars: Record<string, string> } {
  const computed = getComputedStyle(container);
  const vars: Record<string, string> = {};

  for (const v of CSS_VARS) {
    const resolved = computed.getPropertyValue(v).trim();
    vars[v] = resolved;
    svgString = svgString.replaceAll(`var(${v})`, resolved);
  }

  // Replace font variable with system monospace fallback
  svgString = svgString.replaceAll('var(--font-geist-mono), monospace', 'monospace');
  svgString = svgString.replaceAll('var(--font-geist-mono)', 'monospace');

  return { svg: svgString, vars };
}

export interface QuadrantLegendItem {
  label: string;
  color: string;
  count?: number;
}

async function renderChartToCanvas(
  chartContainer: HTMLElement,
  title?: string,
  quadrantLegend?: QuadrantLegendItem[],
  filterLabel?: string,
): Promise<HTMLCanvasElement | null> {
  const svgEl = chartContainer.querySelector('svg');
  if (!svgEl) return null;

  const rect = svgEl.getBoundingClientRect();
  if (!rect.width || !rect.height) return null;

  // Clone so we can modify attributes without affecting the live DOM
  const clonedSvg = svgEl.cloneNode(true) as SVGSVGElement;

  // Set explicit dimensions for export
  const DPI_SCALE = 2;
  const exportWidth = 900;
  const scale = exportWidth / rect.width;
  const exportHeight = Math.round(rect.height * scale);

  // Ensure viewBox is set from original dimensions, then set export size
  if (!clonedSvg.getAttribute('viewBox')) {
    clonedSvg.setAttribute('viewBox', `0 0 ${rect.width} ${rect.height}`);
  }
  clonedSvg.setAttribute('width', String(exportWidth));
  clonedSvg.setAttribute('height', String(exportHeight));

  // Ensure xmlns is set (required for rendering as image)
  clonedSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');

  // Serialize and resolve CSS custom properties
  let svgString = new XMLSerializer().serializeToString(clonedSvg);
  const { svg: resolvedSvg, vars } = resolveVars(svgString, chartContainer);
  svgString = resolvedSvg;

  const titleHeight = title ? 48 : 0;
  const legendHeight = quadrantLegend?.length ? 36 : 0;
  const filterHeight = filterLabel ? 28 : 0;
  const padding = 16;
  const canvasWidth = exportWidth + padding * 2;
  const canvasHeight = exportHeight + titleHeight + filterHeight + legendHeight + padding * 2;

  const canvas = document.createElement('canvas');
  canvas.width = canvasWidth * DPI_SCALE;
  canvas.height = canvasHeight * DPI_SCALE;
  canvas.style.width = `${canvasWidth}px`;
  canvas.style.height = `${canvasHeight}px`;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  ctx.scale(DPI_SCALE, DPI_SCALE);

  // Fill background
  ctx.fillStyle = vars['--card'] || vars['--background'] || '#ffffff';
  ctx.fillRect(0, 0, canvasWidth, canvasHeight);

  // Draw title
  if (title) {
    ctx.fillStyle = vars['--foreground'] || '#000000';
    ctx.font = 'bold 18px monospace';
    ctx.fillText(title, padding, padding + 28);
  }

  // Draw filter label below title
  if (filterLabel) {
    ctx.fillStyle = vars['--muted'] || '#888888';
    ctx.font = '13px monospace';
    ctx.fillText(`Filter: ${filterLabel}`, padding, titleHeight + padding + 14);
  }

  // Render SVG to canvas via blob URL
  const blob = new Blob([svgString], { type: 'image/svg+xml;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const img = new Image();

  return new Promise<HTMLCanvasElement | null>((resolve) => {
    img.onload = () => {
      ctx.drawImage(img, padding, titleHeight + filterHeight + padding, exportWidth, exportHeight);
      URL.revokeObjectURL(url);

      // Draw quadrant legend below chart
      if (quadrantLegend?.length) {
        const legendY = titleHeight + filterHeight + padding + exportHeight + 20;
        let legendX = padding;
        ctx.font = '13px monospace';
        for (const item of quadrantLegend) {
          // Colored circle
          ctx.beginPath();
          ctx.arc(legendX + 6, legendY, 6, 0, Math.PI * 2);
          ctx.fillStyle = item.color;
          ctx.fill();
          // Label + count
          ctx.fillStyle = vars['--foreground'] || '#000000';
          const text = item.count != null ? `${item.label} (${item.count})` : item.label;
          ctx.fillText(text, legendX + 18, legendY + 4);
          legendX += ctx.measureText(text).width + 34;
        }
      }

      resolve(canvas);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    img.src = url;
  });
}

export async function downloadChartAsPng(
  chartContainer: HTMLElement,
  title?: string,
  quadrantLegend?: QuadrantLegendItem[],
  filterLabel?: string,
): Promise<void> {
  const canvas = await renderChartToCanvas(chartContainer, title, quadrantLegend, filterLabel);
  if (!canvas) return;

  const filename = title
    ? title.replace(/[^a-zA-Z0-9\-_ ]/g, '').replace(/\s+/g, '-') + '.png'
    : 'chart.png';

  const link = document.createElement('a');
  link.download = filename;
  link.href = canvas.toDataURL('image/png');
  link.click();
}

export async function copyChartAsPng(
  chartContainer: HTMLElement,
  title?: string,
  quadrantLegend?: QuadrantLegendItem[],
  filterLabel?: string,
): Promise<boolean> {
  const canvas = await renderChartToCanvas(chartContainer, title, quadrantLegend, filterLabel);
  if (!canvas) return false;

  return new Promise<boolean>((resolve) => {
    canvas.toBlob(async (blob) => {
      if (!blob) { resolve(false); return; }
      try {
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
        resolve(true);
      } catch {
        resolve(false);
      }
    }, 'image/png');
  });
}
