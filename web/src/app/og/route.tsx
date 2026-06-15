import { ImageResponse } from 'next/og';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { splitOgDescription } from '@/lib/og';

export const runtime = 'nodejs';

const GEIST_MONO_URL =
  'https://fonts.gstatic.com/s/geistmono/v6/or3yQ6H-1_WfwkMZI_qYPLs1a-t7PU0AbeE9KJ5T.ttf';

let logoBase64Promise: Promise<string> | null = null;
let fontDataPromise: Promise<ArrayBuffer> | null = null;

function getLogoBase64() {
  logoBase64Promise ??= readFile(join(process.cwd(), 'public', 'logo_dark.png')).then(buffer =>
    `data:image/png;base64,${buffer.toString('base64')}`
  );
  return logoBase64Promise;
}

function getFontData() {
  fontDataPromise ??= fetch(GEIST_MONO_URL).then(response => response.arrayBuffer());
  return fontDataPromise;
}

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const description = searchParams.get('description')?.trim() || 'fastbreak';
  const lines = splitOgDescription(description);
  const [logoBase64, fontData] = await Promise.all([getLogoBase64(), getFontData()]);

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#000000',
        }}
      >
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            marginTop: -48,
          }}
        >
          <img
            src={logoBase64}
            alt=""
            width={120}
            height={120}
            style={{ marginBottom: 28 }}
          />
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              maxWidth: 960,
              padding: '0 48px',
            }}
          >
            {lines.map((line, index) => (
              <div
                key={`${index}-${line}`}
                style={{
                  color: '#ffffff',
                  fontSize: 40,
                  lineHeight: 1.3,
                  fontFamily: 'Geist Mono',
                  textAlign: 'center',
                }}
              >
                {line}
              </div>
            ))}
          </div>
        </div>
      </div>
    ),
    {
      width: 1200,
      height: 630,
      fonts: [
        {
          name: 'Geist Mono',
          data: fontData,
          weight: 400,
          style: 'normal',
        },
      ],
    }
  );
}
