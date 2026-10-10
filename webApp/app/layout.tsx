import type { Metadata } from "next";
import "./globals.css";
import { THEME_INIT_SCRIPT } from "@/lib/theme";

export const metadata: Metadata = {
  title: "Tap8583 — Real-Time ISO 8583 Packet Visualizer",
  description: "Next-generation real-time ISO 8583 payment log visualizer, transaction matcher, and EMV decoder for POS terminals.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    // The theme class is set before paint by THEME_INIT_SCRIPT, so the server-rendered
    // className legitimately differs from the client's.
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:ital,wght@0,400;0,500;0,600;1,400&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="antialiased min-h-screen bg-app text-slate-100 font-sans select-none">
        {children}
      </body>
    </html>
  );
}
