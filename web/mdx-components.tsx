import type { MDXComponents } from "mdx/types";
import { PlatformLinks } from "./src/components/ui/PlatformLinks";
import { IntroScreenshots } from "./src/components/ui/IntroScreenshots";
import { PlatformSupport } from "./src/components/ui/PlatformSupport";
import { BetaLinks } from "./src/components/ui/BetaLinks";
import { PinchToZoomDemo } from "./src/components/ui/PinchToZoomDemo";
import { ManageTeamsDemo } from "./src/components/ui/ManageTeamsDemo";
import { HighlightingDataPointsDemo } from "./src/components/ui/HighlightingDataPointsDemo";
import { NavigateChartsDemo } from "./src/components/ui/NavigateChartsDemo";
import { HeadingWithAnchor } from "./src/components/ui/HeadingWithAnchor";
import { ManualPage } from "./src/components/ui/ManualPage";
import { SportsTalkGallery } from "./src/components/ui/SportsTalkGallery";
import { WorksheetLightbox } from "./src/components/ui/WorksheetLightbox";
import { SportsTalkExamplesGallery } from "./src/components/ui/SportsTalkExamplesGallery";

export function useMDXComponents(components: MDXComponents): MDXComponents {
  return {
    PlatformLinks,
    IntroScreenshots,
    PlatformSupport,
    BetaLinks,
    PinchToZoomDemo,
    ManageTeamsDemo,
    HighlightingDataPointsDemo,
    NavigateChartsDemo,
    ManualPage,
    SportsTalkGallery,
    WorksheetLightbox,
    SportsTalkExamplesGallery,
    h1: ({ children }) => (
      <h1 className="text-2xl font-bold mb-4">{children}</h1>
    ),
    h2: (props) => <HeadingWithAnchor {...props} />,
    h3: ({ children }) => (
      <h3 className="text-lg font-bold mt-4 mb-2">{children}</h3>
    ),
    p: ({ children }) => (
      <p className="text-[var(--muted)] mb-4 leading-relaxed">{children}</p>
    ),
    ul: ({ children }) => (
      <ul className="list-disc ml-6 mb-4 text-[var(--muted)] space-y-2">{children}</ul>
    ),
    ol: ({ children }) => (
      <ol className="list-decimal ml-6 mb-4 text-[var(--muted)] space-y-2">{children}</ol>
    ),
    li: ({ children }) => <li className="leading-relaxed">{children}</li>,
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
