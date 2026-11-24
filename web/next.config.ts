import type { NextConfig } from "next";
import createMDX from "@next/mdx";
import path from "path";

/**
 * The site, built once a day and served as files.
 *
 * `output: "export"` is the whole arrangement: every page here is a function of
 * the chart JSON that `scripts/download-charts.ts` pulls out of S3 before the
 * build (see `src/lib/data.ts` — it is all `fs.readFileSync`), so there is
 * nothing for a server to decide at request time. `next build` writes a
 * directory of HTML and the daily GitHub Actions run publishes it to GitHub
 * Pages at fastbreak.joebad.com, which costs nothing to host.
 *
 * `trailingSlash` makes every route `<route>/index.html`, the shape a plain
 * file server answers without a rewrite rule, so the export is not tied to
 * Pages in particular.
 *
 * Two things follow from having no server, and both are load-bearing:
 *   - No request-time redirects. The matchup index pages pick a game in the
 *     browser instead (`MatchupsClientRedirect`), which is also what keeps a
 *     build from yesterday landing on today's game.
 *   - No `cookies()`. Pinned teams are read from localStorage on the client.
 */
const nextConfig: NextConfig = {
  pageExtensions: ["js", "jsx", "md", "mdx", "ts", "tsx"],
  output: "export",
  trailingSlash: true,
  // There is no image optimizer behind a static export; the art here is
  // screenshots and logos committed under public/, already sized.
  images: { unoptimized: true },
  turbopack: {
    root: path.join(__dirname),
  },
  outputFileTracingRoot: path.join(__dirname),
};

const withMDX = createMDX({});

export default withMDX(nextConfig);
