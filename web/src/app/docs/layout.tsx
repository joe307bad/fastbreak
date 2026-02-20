import { DocsNav } from '@/components/ui/DocsNav';
import fs from 'fs';
import path from 'path';
import matter from 'gray-matter';

interface DocMetadata {
  title: string;
  description: string;
  order: number;
}

function getDocsMetadata() {
  const docsDirectory = path.join(process.cwd(), 'content/docs');
  const filenames = fs.readdirSync(docsDirectory);

  const docs = filenames
    .filter((filename) => filename.endsWith('.mdx'))
    .map((filename) => {
      const filePath = path.join(docsDirectory, filename);
      const fileContents = fs.readFileSync(filePath, 'utf8');
      const { data } = matter(fileContents);
      const metadata = data as DocMetadata;

      return {
        slug: filename.replace('.mdx', ''),
        title: metadata.title,
        order: metadata.order,
      };
    });

  return docs;
}

export default function DocsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const docs = getDocsMetadata();

  return (
    <div className="max-w-[1920px] mx-auto px-4 md:px-8 py-8">
      <div className="grid grid-cols-1 md:grid-cols-[240px_1fr] gap-8">
        <aside className="md:sticky md:top-20 md:self-start">
          <DocsNav docs={docs} />
        </aside>
        <main className="max-w-none">
          {children}
        </main>
      </div>
    </div>
  );
}
