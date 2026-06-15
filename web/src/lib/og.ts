import type { Metadata } from 'next';

const MAX_CHARS_PER_LINE = 32;

function truncateWithEllipsis(text: string, maxLen: number): string {
  if (text.length <= maxLen) return text;
  if (maxLen <= 1) return '…';
  return `${text.slice(0, maxLen - 1).trimEnd()}…`;
}

export function splitOgDescription(text: string): string[] {
  const words = text.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return ['fastbreak'];

  const lines: string[] = [];
  let current = '';

  for (let i = 0; i < words.length; i++) {
    const word = words[i];
    const candidate = current ? `${current} ${word}` : word;

    if (candidate.length <= MAX_CHARS_PER_LINE) {
      current = candidate;
      continue;
    }

    if (current) {
      lines.push(current);
      if (lines.length === 2) {
        const remainder = [word, ...words.slice(i + 1)].join(' ');
        lines[1] = truncateWithEllipsis(`${lines[1]} ${remainder}`.trim(), MAX_CHARS_PER_LINE);
        return lines;
      }
      current = word.length > MAX_CHARS_PER_LINE
        ? truncateWithEllipsis(word, MAX_CHARS_PER_LINE)
        : word;
      continue;
    }

    current = truncateWithEllipsis(word, MAX_CHARS_PER_LINE);
  }

  if (current) lines.push(current);
  return lines.slice(0, 2);
}

export function ogImageUrl(title: string): string {
  return `/og?${new URLSearchParams({ description: title }).toString()}`;
}

export function pageMetadata({
  title,
  description,
}: {
  title: string;
  description?: string;
}): Metadata {
  const imageUrl = ogImageUrl(title);

  return {
    title,
    ...(description ? { description } : {}),
    openGraph: {
      title,
      ...(description ? { description } : {}),
      images: [{ url: imageUrl, width: 1200, height: 630, alt: title }],
    },
    twitter: {
      card: 'summary_large_image',
      title,
      ...(description ? { description } : {}),
      images: [imageUrl],
    },
  };
}
