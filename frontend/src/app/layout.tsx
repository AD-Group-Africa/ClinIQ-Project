import type { Metadata } from "next";
import Sidebar from "@/components/Sidebar";
import "@/styles/globals.css";

export const metadata: Metadata = {
  title: "ClinIQ - Clinic Management",
  description: "Smart clinic management for Kenya",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="flex min-h-screen">
          {children}
        </div>
      </body>
    </html>
  );
}
