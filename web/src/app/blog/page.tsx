import Link from "next/link";
import { getAllPosts } from "@/lib/blog";

export const dynamic = "force-static";

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || "https://fastbreak.joebad.com";

export const metadata = {
  title: "Blog",
  description: "Updates and insights from the fastbreak team",
  openGraph: {
    title: "Blog",
    description: "Updates and insights from the fastbreak team",
    images: [`${siteUrl}/og-image.png`],
  },
  twitter: {
    card: "summary_large_image",
    title: "Blog",
    description: "Updates and insights from the fastbreak team",
    images: [`${siteUrl}/og-image.png`],
  },
};

export default function BlogPage() {
  const posts = getAllPosts();

  return (
    <main className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-8">Blog</h1>

      {posts.length === 0 ? (
        <p className="text-[var(--muted)]">No posts yet.</p>
      ) : (
        <ol className="space-y-6 list-none">
          {posts.map((post, index) => (
            <li key={post.slug} className="pl-2 flex gap-3 items-end">
              <span className="text-[var(--foreground)] shrink-0 translate-y-[1px]">
                {posts.length - index}.
              </span>
              <Link
                href={`/blog/${post.slug}`}
                className="block group flex-1"
              >
                <article>
                  <time className="text-xs text-[var(--muted)]">
                    {new Date(post.date).toLocaleDateString("en-US", {
                      year: "numeric",
                      month: "long",
                      day: "numeric",
                    })}
                  </time>
                  <h2 className="text-lg font-bold mt-1 group-hover:underline">
                    {post.title}
                  </h2>
                  {post.description && (
                    <p className="text-sm text-[var(--muted)] mt-1">
                      {post.description}
                    </p>
                  )}
                </article>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </main>
  );
}
