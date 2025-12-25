import { notFound } from "next/navigation";
import Link from "next/link";
import { MDXRemote } from "next-mdx-remote/rsc";
import { getAllSlugs, getPostBySlug } from "@/lib/blog";
import { useMDXComponents } from "../../../../mdx-components";

interface Props {
  params: Promise<{ slug: string }>;
}

export const dynamicParams = false;

export async function generateStaticParams() {
  const slugs = getAllSlugs();
  return slugs.map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: Props) {
  const { slug } = await params;
  const post = getPostBySlug(slug);

  if (!post) {
    return { title: "Not Found | fastbreak" };
  }

  return {
    title: `${post.title} | fastbreak`,
    description: post.description,
  };
}

export default async function BlogPostPage({ params }: Props) {
  const { slug } = await params;
  const post = getPostBySlug(slug);

  if (!post) {
    notFound();
  }

  const components = useMDXComponents({});

  return (
    <main className="max-w-2xl mx-auto px-4 py-8">
      <Link
        href="/blog"
        className="text-sm text-[var(--muted)] hover:underline"
      >
        &larr; Back to blog
      </Link>

      <article className="mt-6">
        <header className="mb-8">
          <div className="flex items-center gap-2 text-xs text-[var(--muted)]">
            <time>
              {new Date(post.date).toLocaleDateString("en-US", {
                year: "numeric",
                month: "long",
                day: "numeric",
              })}
            </time>
            {post.author && (
              <>
                <span>·</span>
                {post.authorUrl ? (
                  <a
                    href={post.authorUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:underline"
                  >
                    {post.author}
                  </a>
                ) : (
                  <span>{post.author}</span>
                )}
              </>
            )}
          </div>
          <h1 className="text-2xl font-bold mt-2">{post.title}</h1>
        </header>

        <div className="prose-sm">
          <MDXRemote source={post.content} components={components} />
        </div>
      </article>
    </main>
  );
}
