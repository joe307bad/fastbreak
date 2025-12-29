import type { MDXComponents } from "mdx/types";
import { PlatformLinks } from "./src/components/ui/PlatformLinks";
import { IntroScreenshots } from "./src/components/ui/IntroScreenshots";
import { PlatformSupport } from "./src/components/ui/PlatformSupport";
import { BetaLinks } from "./src/components/ui/BetaLinks";
import { PinchToZoomDemo } from "./src/components/ui/PinchToZoomDemo";

export function useMDXComponents(components: MDXComponents): MDXComponents {
  return {
    PlatformLinks,
    IntroScreenshots,
    PlatformSupport,
    BetaLinks,
    PinchToZoomDemo,
    h1: ({ children }) => (
      <h1 className="text-2xl font-bold mb-4">{children}</h1>
    ),
    h2: ({ children }) => (
      <h2 className="text-xl font-bold mt-6 mb-3">{children}</h2>
    ),
    h3: ({ children }) => (
      <h3 className="text-lg font-bold mt-4 mb-2">{children}</h3>
    ),
    p: ({ children }) => (
      <p className="text-[var(--muted)] mb-4 leading-relaxed">{children}</p>
    ),
    ul: ({ children }) => (
      <ul className="list-disc list-inside mb-4 text-[var(--muted)]">{children}</ul>
    ),
    ol: ({ children }) => (
      <ol className="list-decimal list-inside mb-4 text-[var(--muted)]">{children}</ol>
    ),
    li: ({ children }) => <li className="mb-1">{children}</li>,
    a: ({ href, children }) => (
      <a href={href} className="text-blue-500 hover:underline">
        {children}
      </a>
    ),
    code: ({ children }) => (
      <code className="bg-[var(--border)] px-1.5 py-0.5 rounded text-sm">
        {children}
      </code>
    ),
    pre: ({ children }) => (
      <pre className="bg-[var(--border)] p-4 rounded mb-4 overflow-x-auto text-sm">
        {children}
      </pre>
    ),
    blockquote: ({ children }) => (
      <blockquote className="border-l-4 border-[var(--border)] pl-4 italic text-[var(--muted)] mb-4">
        {children}
      </blockquote>
    ),
    ...components,
  };
}
