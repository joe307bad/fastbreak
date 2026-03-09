import fs from 'fs';
import path from 'path';
import sharp from 'sharp';
import { getAllPosts } from '../src/lib/blog';

const OUTPUT_DIR = path.join(process.cwd(), 'public', 'og-images');
const LOGO_PATH = path.join(process.cwd(), 'public', 'logo.png');

// Create output directory if it doesn't exist
if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

// Load logo and convert to base64 once
const logoBuffer = fs.readFileSync(LOGO_PATH);
const logoBase64 = logoBuffer.toString('base64');

function generateOGImageSVG(
  title: string,
  subtitle: string,
  date: string
): string {
  const formattedDate = date
    ? new Date(date).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : '';

  // Helper to escape XML
  const escapeXml = (str: string) =>
    str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&apos;');

  // Helper to wrap text into multiple lines
  const wrapText = (text: string, maxCharsPerLine: number): string[] => {
    const words = text.split(' ');
    const lines: string[] = [];
    let currentLine = '';

    for (const word of words) {
      const testLine = currentLine ? `${currentLine} ${word}` : word;
      if (testLine.length <= maxCharsPerLine) {
        currentLine = testLine;
      } else {
        if (currentLine) lines.push(currentLine);
        currentLine = word;
      }
    }
    if (currentLine) lines.push(currentLine);
    return lines;
  };

  const titleLines = wrapText(title, 28);
  const subtitleLines = subtitle ? wrapText(subtitle, 55) : [];
  const fontFamily = "'Geist Mono', 'SF Mono', 'Monaco', 'Menlo', 'Consolas', 'Liberation Mono', 'Courier New', monospace";

  // Calculate title height for positioning subtitle
  const titleStartY = 120;
  const titleLineHeight = 85;
  const titleEndY = titleStartY + (titleLines.length - 1) * titleLineHeight;
  const subtitleStartY = titleEndY + 80;

  return `<svg width="1200" height="630" viewBox="0 0 1200 630" xmlns="http://www.w3.org/2000/svg">
  <!-- Background -->
  <rect width="1200" height="630" fill="#fafafa"/>

  <!-- Title (wrapped) -->
  ${titleLines
    .map(
      (line, i) =>
        `<text x="48" y="${titleStartY + i * titleLineHeight}" font-family="${fontFamily}" font-size="72" font-weight="bold" fill="#171717" text-anchor="start">${escapeXml(line)}</text>`
    )
    .join('\n  ')}

  <!-- Subtitle (wrapped) -->
  ${
    subtitleLines.length > 0
      ? subtitleLines
          .map(
            (line, i) =>
              `<text x="48" y="${subtitleStartY + i * 44}" font-family="${fontFamily}" font-size="34" fill="#525252" text-anchor="start">${escapeXml(line)}</text>`
          )
          .join('\n  ')
      : ''
  }

  <!-- Date -->
  ${
    formattedDate
      ? `<text x="48" y="580" font-family="${fontFamily}" font-size="28" fill="#525252" text-anchor="start">${escapeXml(
          formattedDate
        )}</text>`
      : ''
  }

  <!-- Logo in bottom right -->
  <image x="1040" y="470" width="120" height="120" href="data:image/png;base64,${logoBase64}"/>
</svg>`;
}

async function main() {
  const posts = getAllPosts();

  for (const post of posts) {
    const svg = generateOGImageSVG(post.title, post.description, post.date);

    // Convert SVG to PNG
    const outputPath = path.join(OUTPUT_DIR, `${post.slug}.png`);
    await sharp(Buffer.from(svg))
      .png()
      .toFile(outputPath);
    console.log(`Generated: ${outputPath}`);
  }

  console.log(`\nGenerated ${posts.length} OG images (PNG format)`);
}

main().catch(console.error);
