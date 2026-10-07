import type { Metadata } from "next";
import "./globals.css";
import { Navbar } from "@/components/Navbar";
import { LanguageProvider } from "@/lib/i18n";

export const metadata: Metadata = {
  title: "Tong Tin Manager — ប្រព័ន្ធគ្រប់គ្រងតុងទីនប្រកបដោយតម្លាភាព",
  description: "ប្រព័ន្ធគ្រប់គ្រងតុងទីន (ROSCA) ទំនើប តម្លាភាព និងសុវត្ថិភាពខ្ពស់បំផុត។",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="km" className="h-full antialiased">
      <body className="min-h-full flex flex-col bg-zinc-50 font-sans text-zinc-900 dark:bg-zinc-950 dark:text-zinc-50">
        <LanguageProvider>
          <Navbar />
          <main className="flex-1">{children}</main>
        </LanguageProvider>
      </body>
    </html>
  );
}
