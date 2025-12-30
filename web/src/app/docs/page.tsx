import type { Metadata } from 'next';
import fs from 'fs';
import path from 'path';
import matter from 'gray-matter';
import { MDXRemote } from 'next-mdx-remote/rsc';
import type { MDXComponents } from 'mdx/types';
import remarkGfm from 'remark-gfm';
import { BetaLinks } from '@/components/ui/BetaLinks';
import { PlatformSupport } from '@/components/ui/PlatformSupport';

export const metadata: Metadata = {
  title: 'Documentation - fastbreak',
  description: 'Documentation for the fastbreak sports analytics dashboard',
};

const components: MDXComponents = {
  table: (props) => <table {...props} />,
  thead: (props) => <thead {...props} />,
  tbody: (props) => <tbody {...props} />,
  tr: (props) => <tr {...props} />,
  th: (props) => <th {...props} />,
  td: (props) => <td {...props} />,
  BetaLinks,
  PlatformSupport,
  
};

export default function DocsPage() {
  const filePath = path.join(process.cwd(), 'content/docs/overview.mdx');
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
