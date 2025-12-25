import Link from "next/link";
import { getAllPosts } from "@/lib/blog";

export const dynamic = "force-static";

export const metadata = {
  title: "Blog | fastbreak",
  description: "Updates and insights from the fastbreak team",
};

export default function BlogPage() {
  const posts = getAllPosts();

  return (
    <main className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-8">Blog</h1>

      {posts.length === 0 ? (
        <p className="text-[var(--muted)]">No posts yet.</p>
      ) : (
        <ul className="space-y-6">
          {posts.map((post) => (
            <li key={post.slug}>
              <Link
                href={`/blog/${post.slug}`}
                className="block group"
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
        </ul>
      )}
    </main>
  );
}
