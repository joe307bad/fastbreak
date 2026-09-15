import type { Metadata } from "next";
import { Geist_Mono } from "next/font/google";
import "./globals.css";
import { Header } from "@/components/ui";
import { pageMetadata } from "@/lib/og";

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL || "https://fastbreak.joebad.com";

// Every page inherits this card unless it exports its own pageMetadata().
export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  ...pageMetadata({ title: "fastbreak", description: "Fast sports analytics dashboard" }),
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
