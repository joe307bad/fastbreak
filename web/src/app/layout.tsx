import type { Metadata } from "next";
import { Geist_Mono } from "next/font/google";
import "./globals.css";
import { Header } from "@/components/ui";
import { ogImageUrl } from "@/lib/og";

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || "https://fastbreak.joebad.com";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: "fastbreak",
  description: "Fast sports analytics dashboard",
  openGraph: {
    title: "fastbreak",
    description: "Fast sports analytics dashboard",
    images: [ogImageUrl("fastbreak")],
  },
  twitter: {
    card: "summary_large_image",
    title: "fastbreak",
    description: "Fast sports analytics dashboard",
    images: [ogImageUrl("fastbreak")],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  var stored = localStorage.getItem('theme');
                  var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                  var isDark = stored === 'dark' || (!stored && prefersDark);
                  document.documentElement.classList.toggle('dark', isDark);
                } catch (_) {}
              })();
              (function() {
                try {
                  var pinnedTeams = localStorage.getItem('pinnedTeams');
                  if (pinnedTeams) {
                    document.cookie = 'pinnedTeams=' + encodeURIComponent(pinnedTeams) + '; path=/; max-age=31536000; SameSite=Lax';
                  }
                } catch (_) {}
              })();
            `,
          }}
        />
      </head>
      <body className={`${geistMono.variable} antialiased`}>
        <Header />
        {children}
      </body>
    </html>
  );
}
