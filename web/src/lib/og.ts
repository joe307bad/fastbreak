import type { Metadata } from 'next';

// The card is rendered by the fastbreak-og-image Lambda behind CloudFront
// (pipeline/04-fastbreak-charts/aws/lambda/og-image). It takes ?title= and
// ?subtitle= and returns a 2400x1260 PNG; CloudFront caches on the query string.
const OG_ENDPOINT = process.env.NEXT_PUBLIC_OG_IMAGE_URL || 'https://d2jyizt5xogu23.cloudfront.net/og';
const OG_WIDTH = 2400;
const OG_HEIGHT = 1260;
// Bump when the card design changes: CloudFront and Twitter both cache by URL.
const OG_VERSION = '2';

export function ogImageUrl(title: string, subtitle?: string): string {
  const params = new URLSearchParams({ title });
  if (subtitle) params.set('subtitle', subtitle);
  params.set('v', OG_VERSION);
  return `${OG_ENDPOINT}?${params.toString()}`;
}

export function pageMetadata({
  title,
  description,
}: {
  title: string;
  description?: string;
}): Metadata {
  const imageUrl = ogImageUrl(title, description);

  return {
    title,
    ...(description ? { description } : {}),
    openGraph: {
      title,
      ...(description ? { description } : {}),
      images: [{ url: imageUrl, width: OG_WIDTH, height: OG_HEIGHT, alt: title }],
    },
    twitter: {
      card: 'summary_large_image',
      title,
      ...(description ? { description } : {}),
      images: [imageUrl],
    },
  };
}
