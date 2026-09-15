// Renders an Open Graph image (title + subtitle, dark, fastbreak logo top left).
//   GET /og?title=...&subtitle=...
// satori lays the card out as SVG; resvg rasterises it to PNG at 2x (2400x1260)
// so the text stays crisp wherever Twitter/Slack/iMessage downscale it.
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import satori from "satori";

const require = createRequire(import.meta.url);
const { initWasm, Resvg } = require("@resvg/resvg-wasm");

const here = dirname(fileURLToPath(import.meta.url));
const asset = (name) => readFileSync(join(here, "assets", name));

const WIDTH = 1200;
const HEIGHT = 630;
const SCALE = 2;
const MAX_TITLE = 110;
const MAX_SUBTITLE = 150;

const fontRegular = asset("GeistMono-Regular.ttf");
const fontBold = asset("GeistMono-Bold.ttf");
const logoDataUrl = `data:image/png;base64,${asset("logo.png").toString("base64")}`;

let wasmReady;
function ensureWasm() {
  wasmReady ??= initWasm(readFileSync(require.resolve("@resvg/resvg-wasm/index_bg.wasm")));
  return wasmReady;
}

function clean(value, max, fallback) {
  const text = (value ?? "").toString().replace(/\s+/g, " ").trim();
  if (!text) return fallback;
  return text.length > max ? `${text.slice(0, max - 1).trimEnd()}…` : text;
}

// The logo sits top left, above the bottom-anchored text; the text column is
// capped so the tallest title + subtitle never climbs into it. Geist Mono
// glyphs are ~0.6em wide, so a size of N fits about 1540/N characters per line
// of the 924px column. Step down so short titles stay on one line and the
// longest fit in three.
const TEXT_WIDTH = 924;
function titleSize(title) {
  if (title.length <= 19) return 80;
  if (title.length <= 27) return 56;
  if (title.length <= 64) return 48;
  return 40;
}

// satori takes React-like element objects; no JSX so the file needs no build step.
// A leaf must not get `children: []`: satori treats that as a multi-child box.
const h = (type, props, ...children) => ({
  type,
  props: children.length === 0 ? { ...props } : { ...props, children: children.length === 1 ? children[0] : children },
});

function card(title, subtitle) {
  return h(
    "div",
    {
      style: {
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "flex-end",
        backgroundColor: "#0a0a0a",
        backgroundImage: "linear-gradient(135deg, #0a0a0a 0%, #171717 100%)",
        padding: "64px 72px",
        fontFamily: "Geist Mono",
        color: "#fafafa",
        position: "relative",
      },
    },
    h("img", {
      src: logoDataUrl,
      width: 132,
      height: 134,
      style: { position: "absolute", top: 56, left: 72 },
    }),
    h(
      "div",
      { style: { display: "flex", flexDirection: "column", width: TEXT_WIDTH } },
      h(
        "div",
        {
          style: {
            fontSize: titleSize(title),
            fontWeight: 700,
            lineHeight: 1.15,
            letterSpacing: "-0.02em",
            display: "flex",
            flexWrap: "wrap",
          },
        },
        title
      ),
      subtitle
        ? h(
            "div",
            {
              style: {
                marginTop: 28,
                fontSize: 28,
                lineHeight: 1.4,
                color: "#a3a3a3",
                display: "flex",
                flexWrap: "wrap",
              },
            },
            subtitle
          )
        : null,
      h(
        "div",
        {
          style: {
            marginTop: 44,
            display: "flex",
            alignItems: "center",
            fontSize: 22,
            color: "#737373",
            letterSpacing: "0.04em",
          },
        },
        h("div", { style: { width: 10, height: 10, borderRadius: 5, backgroundColor: "#4CAF50", marginRight: 14 } }),
        "fastbreak.joebad.com"
      )
    )
  );
}

export async function render(title, subtitle) {
  await ensureWasm();
  const svg = await satori(card(title, subtitle), {
    width: WIDTH,
    height: HEIGHT,
    fonts: [
      { name: "Geist Mono", data: fontRegular, weight: 400, style: "normal" },
      { name: "Geist Mono", data: fontBold, weight: 700, style: "normal" },
    ],
  });
  const png = new Resvg(svg, { fitTo: { mode: "width", value: WIDTH * SCALE } }).render().asPng();
  return Buffer.from(png);
}

export const handler = async (event) => {
  const params = event?.queryStringParameters ?? {};
  const title = clean(params.title, MAX_TITLE, "fastbreak");
  const subtitle = clean(params.subtitle, MAX_SUBTITLE, "");

  try {
    const png = await render(title, subtitle);
    return {
      statusCode: 200,
      headers: {
        "Content-Type": "image/png",
        "Cache-Control": "public, max-age=86400, s-maxage=604800",
        "Access-Control-Allow-Origin": "*",
      },
      isBase64Encoded: true,
      body: png.toString("base64"),
    };
  } catch (error) {
    console.error("Error rendering OG image:", error);
    return {
      statusCode: 500,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ error: "Failed to render OG image" }),
    };
  }
};
