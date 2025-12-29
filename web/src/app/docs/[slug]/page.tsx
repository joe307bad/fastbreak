import type { Metadata } from 'next';
import fs from 'fs';
import path from 'path';
import matter from 'gray-matter';
import { MDXRemote } from 'next-mdx-remote/rsc';
import { notFound } from 'next/navigation';
import type { MDXComponents } from 'mdx/types';
import remarkGfm from 'remark-gfm';
import { BetaLinks } from '@/components/ui/BetaLinks';
import { PlatformSupport } from '@/components/ui/PlatformSupport';
import { PinchToZoomDemo } from '@/components/ui/PinchToZoomDemo';
import { ManageTeamsDemo } from '@/components/ui/ManageTeamsDemo';
import { HighlightingDataPointsDemo } from '@/components/ui/HighlightingDataPointsDemo';

interface DocMetadata {
  title: string;
  description: string;
  order: number;
}

const components: MDXComponents = {
  table: (props) => <table {...props} />,
  thead: (props) => <thead {...props} />,
  tbody: (props) => <tbody {...props} />,
  tr: (props) => <tr {...props} />,
  th: (props) => <th {...props} />,
  td: (props) => <td {...props} />,
  BetaLinks,
  PlatformSupport,
  PinchToZoomDemo,
  ManageTeamsDemo,
  HighlightingDataPointsDemo
};

export async function generateStaticParams() {
  const docsDirectory = path.join(process.cwd(), 'content/docs');
  const filenames = fs.readdirSync(docsDirectory);

  return filenames
    .filter((filename) => filename.endsWith('.mdx') && filename !== 'overview.mdx')
    .map((filename) => ({
      slug: filename.replace('.mdx', ''),
    }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  const filePath = path.join(process.cwd(), 'content/docs', `${slug}.mdx`);

  if (!fs.existsSync(filePath)) {
    return {
      title: 'Not Found',
    };
  }

  const fileContents = fs.readFileSync(filePath, 'utf8');
  const { data } = matter(fileContents);
  const metadata = data as DocMetadata;

  return {
    title: `${metadata.title} - fastbreak`,
    description: metadata.description,
  };
}

export default async function DocPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const filePath = path.join(process.cwd(), 'content/docs', `${slug}.mdx`);

  if (!fs.existsSync(filePath)) {
    notFound();
  }

  const fileContents = fs.readFileSync(filePath, 'utf8');
  const { content } = matter(fileContents);

  return (
    <article className="docs-content">
      <MDXRemote
        source={content}
        components={components}
        options={{
          mdxOptions: {
            remarkPlugins: [remarkGfm],
          },
        }}
      />
    </article>
  );
}
