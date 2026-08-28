'use client';
import Link from "next/link";
import { useRouter } from "next/navigation";
import { IconStethoscope, IconCalendar, IconReceipt, IconPill, IconFlask, IconLogout } from "@tabler/icons-react";
import api from "@/lib/api";

const navItems = [
  { label: "Reception", href: "/dashboard/reception", icon: IconCalendar },
  { label: "Doctor", href: "/dashboard/doctor", icon: IconStethoscope },
  { label: "Billing", href: "/dashboard/billing", icon: IconReceipt },
  { label: "Pharmacy", href: "/dashboard/pharmacy", icon: IconPill },
  { label: "Lab", href: "/dashboard/lab", icon: IconFlask },
];

export default function Sidebar() {
  const router = useRouter();

  const handleLogout = async () => {
    try {
      await api.post("/auth/logout", { refreshToken: localStorage.getItem("refreshToken") });
    } catch (_) {}
    localStorage.clear();
    router.push("/login");
  };

  return (
    <aside className="w-64 bg-white border-r border-gray-200 p-6 flex flex-col">
      <div className="mb-10">
        <h1 className="text-2xl font-bold text-clinic-600">ClinIQ</h1>
        <p className="text-sm text-gray-500">Smart Clinic</p>
      </div>
      <nav className="flex-1 space-y-2">
        {navItems.map(({ label, href, icon: Icon }) => (
          <Link key={href} href={href} className="flex items-center gap-3 px-3 py-2 rounded-lg text-gray-700 hover:bg-clinic-50 hover:text-clinic-600 transition-colors">
            <Icon size={20} />
            <span>{label}</span>
          </Link>
        ))}
      </nav>
      <button onClick={handleLogout} className="flex items-center gap-3 px-3 py-2 mt-auto text-gray-500 hover:text-red-600 transition-colors">
        <IconLogout size={20} />
        <span>Logout</span>
      </button>
    </aside>
  );
}
