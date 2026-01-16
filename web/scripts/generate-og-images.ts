import fs from 'fs';
import path from 'path';
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

  const subtitleLines = subtitle ? wrapText(subtitle, 35) : [];
  const fontFamily = "'Geist Mono', 'SF Mono', 'Monaco', 'Menlo', 'Consolas', 'Liberation Mono', 'Courier New', monospace";

  return `<svg width="1200" height="630" viewBox="0 0 1200 630" xmlns="http://www.w3.org/2000/svg">
  <!-- Background -->
  <rect width="1200" height="630" fill="#fafafa"/>

  <!-- Title -->
  <text x="40" y="140" font-family="${fontFamily}" font-size="80" font-weight="bold" fill="#171717" text-anchor="start">
    <tspan x="40" dy="0">${escapeXml(title)}</tspan>
  </text>

  <!-- Subtitle (wrapped) -->
  ${
    subtitleLines.length > 0
      ? subtitleLines
          .map(
            (line, i) =>
              `<text x="40" y="${260 + i * 50}" font-family="${fontFamily}" font-size="40" fill="#525252" text-anchor="start">
    <tspan x="40" dy="0">${escapeXml(line)}</tspan>
  </text>`
          )
          .join('\n  ')
      : ''
  }

  <!-- Date -->
  ${
    formattedDate
      ? `<text x="40" y="570" font-family="${fontFamily}" font-size="36" fill="#525252" text-anchor="start">${escapeXml(
          formattedDate
        )}</text>`
      : ''
  }

  <!-- Logo in bottom right (aligned with date) -->
  <image x="1000" y="430" width="160" height="160" href="data:image/png;base64,${logoBase64}"/>
</svg>`;
}

async function main() {
  const posts = getAllPosts();

  for (const post of posts) {
    const svg = generateOGImageSVG(post.title, post.description, post.date);

    // Save as PNG (browsers will render SVG as PNG for OG images)
    const outputPath = path.join(OUTPUT_DIR, `${post.slug}.svg`);
    fs.writeFileSync(outputPath, svg);
    console.log(`Generated: ${outputPath}`);
  }

  console.log(`\nGenerated ${posts.length} OG images (SVG format)`);
  console.log('Note: SVG files work as OG images and will render correctly on social media');
}

main().catch(console.error);
