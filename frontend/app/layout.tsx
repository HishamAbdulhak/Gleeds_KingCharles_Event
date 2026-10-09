import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "King Charles Quiz",
  description: "Gleeds trivia — King Charles & UK–Saudi relations",
};

// royal-900 (globals.css): the phone's browser bar runs into the page
export const viewport: Viewport = { themeColor: "#1e0b3b" };

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
