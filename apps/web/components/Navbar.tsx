"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearAuth, getStoredAuth, LoginResponse } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";
import { LanguageSwitcher } from "@/components/LanguageSwitcher";
import { Users, LayoutDashboard, Wallet, LogOut, Bell, Shield, ArrowRight, Send } from "lucide-react";

export function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useLanguage();
  const [auth, setAuth] = useState<LoginResponse | null>(null);
  const [apiUp, setApiUp] = useState<boolean | null>(null);
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => {
      setAuth(getStoredAuth());
    }, 0);

    // Check API health
    fetch("/api/v1/health")
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data) => setApiUp(data.status === "UP"))
      .catch(() => setApiUp(false));

    // Check unread count if authenticated
    const token = typeof window !== "undefined" ? localStorage.getItem("tongtin_access_token") : null;
    if (token) {
      fetch("/api/v1/notifications/unread-count", {
        headers: { Authorization: `Bearer ${token}` },
      })
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data?.unreadCount !== undefined) setUnread(data.unreadCount);
        })
        .catch(() => {});
    }

    return () => clearTimeout(timer);
  }, [pathname]);

  const handleLogout = () => {
    clearAuth();
    setAuth(null);
    router.push("/login");
  };

  const isHost = auth?.roles.includes("HOST");
  const isMember = auth?.roles.includes("MEMBER");
  const isAdmin = auth?.roles.includes("ADMIN");

  return (
    <header className="sticky top-0 z-40 w-full border-b border-zinc-200 bg-white/80 backdrop-blur-md dark:border-zinc-800 dark:bg-zinc-950/80">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-6">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-400 text-white shadow-md shadow-emerald-500/20 group-hover:scale-105 transition-transform">
              <Shield className="h-5 w-5" />
            </div>
            <div>
              <span className="text-base font-bold tracking-tight text-zinc-900 dark:text-zinc-50 flex items-center gap-1.5">
                Tong Tin
                <span className="rounded-md bg-emerald-100 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300">
                  ROSCA
                </span>
              </span>
            </div>
          </Link>

          {auth && (
            <nav className="hidden md:flex items-center gap-1 ml-4">
              {isHost && (
                <>
                  <Link
                    href="/host"
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                      pathname === "/host"
                        ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-50"
                        : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
                    }`}
                  >
                    <LayoutDashboard className="h-4 w-4" />
                    {t.navbar.overview}
                  </Link>
                  <Link
                    href="/host/members"
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                      pathname === "/host/members"
                        ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-50"
                        : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
                    }`}
                  >
                    <Users className="h-4 w-4" />
                    {t.navbar.members}
                  </Link>
                  <Link
                    href="/host/subscription"
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                      pathname === "/host/subscription"
                        ? "bg-emerald-100 text-emerald-900 dark:bg-emerald-950/60 dark:text-emerald-200"
                        : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
                    }`}
                  >
                    <Wallet className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                    <span>{t.navbar.subscription}</span>
                  </Link>
                  <Link
                    href="/host/telegram"
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                      pathname === "/host/telegram"
                        ? "bg-sky-100 text-sky-900 dark:bg-sky-950/60 dark:text-sky-200"
                        : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
                    }`}
                  >
                    <Send className="h-4 w-4 text-sky-600 dark:text-sky-400" />
                    <span>{t.navbar.telegram}</span>
                  </Link>
                </>
              )}
              {isMember && (
                <Link
                  href="/app"
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                    pathname.startsWith("/app")
                      ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-800 dark:text-zinc-50"
                      : "text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50"
                  }`}
                >
                  <Wallet className="h-4 w-4" />
                  {t.navbar.myGroups}
                </Link>
              )}
              {isAdmin && (
                <Link
                  href="/admin"
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors ${
                    pathname.startsWith("/admin")
                      ? "bg-indigo-100 text-indigo-900 dark:bg-indigo-950/80 dark:text-indigo-200"
                      : "text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-200"
                  }`}
                >
                  <Shield className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                  <span>{t.navbar.adminPortal}</span>
                </Link>
              )}
            </nav>
          )}
        </div>

        <div className="flex items-center gap-2.5 sm:gap-3">
          {/* API Health indicator */}
          <div className="hidden lg:flex items-center gap-2 px-2.5 py-1 rounded-full border border-zinc-200 bg-zinc-50 text-xs text-zinc-600 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400">
            <span
              className={`h-2 w-2 rounded-full ${
                apiUp === true
                  ? "bg-emerald-500 animate-pulse"
                  : apiUp === false
                  ? "bg-rose-500"
                  : "bg-zinc-400"
              }`}
            />
            <span className="font-mono text-[11px]">API: {apiUp ? "UP" : apiUp === false ? "DOWN" : "..."}</span>
          </div>

          {/* Language Switcher */}
          <LanguageSwitcher />

          {auth ? (
            <div className="flex items-center gap-2.5 sm:gap-3">
              <Link
                href="/notifications"
                className="relative p-2 rounded-lg text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800 transition-colors"
                title={t.navbar.notifications}
              >
                <Bell className="h-4 w-4" />
                {unread > 0 && (
                  <span className="absolute top-1 right-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose-500 text-[10px] font-bold text-white">
                    {unread > 9 ? "9+" : unread}
                  </span>
                )}
              </Link>

              <div className="hidden sm:flex flex-col items-end text-xs">
                <span className="font-semibold text-zinc-900 dark:text-zinc-50">{auth.user.fullName}</span>
                <span className="text-[11px] text-zinc-500 dark:text-zinc-400">
                  {isAdmin ? t.navbar.roleAdmin : isHost ? t.navbar.roleHost : t.navbar.roleMember} • {auth.user.phone}
                </span>
              </div>

              <button
                onClick={handleLogout}
                className="flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-xs font-medium text-zinc-700 hover:bg-zinc-100 dark:border-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
              >
                <LogOut className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">{t.navbar.logout}</span>
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link
                href="/login"
                className="rounded-lg px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
              >
                {t.navbar.login}
              </Link>
              <Link
                href="/register/owner"
                className="flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-emerald-500 transition-colors"
              >
                <span className="hidden sm:inline">{t.navbar.registerHost}</span>
                <span className="sm:hidden">+</span>
                <ArrowRight className="h-3 w-3" />
              </Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
