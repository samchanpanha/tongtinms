"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";
import { Shield, Lock, Phone, ArrowRight, AlertCircle, Sparkles } from "lucide-react";

export default function LoginPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await api.login(phone.trim(), password);
      if (res.roles.includes("ADMIN")) {
        router.push("/admin");
      } else if (res.roles.includes("HOST")) {
        router.push("/host");
      } else {
        router.push("/app");
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t.login.defaultError);
    } finally {
      setLoading(false);
    }
  };

  const fillDemoHost = () => {
    setPhone("0900111001");
    setPassword("demo1234");
    setError(null);
  };

  const fillDemoMember = () => {
    setPhone("0900100001");
    setPassword("demo1234");
    setError(null);
  };

  const fillDemoAdmin = () => {
    setPhone("0900999999");
    setPassword("admin1234");
    setError(null);
  };

  return (
    <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md space-y-8 rounded-3xl border border-zinc-200/80 bg-white p-8 shadow-xl shadow-zinc-200/40 dark:border-zinc-800 dark:bg-zinc-900 dark:shadow-none sm:p-10">
        <div className="text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-tr from-emerald-600 to-teal-400 text-white shadow-md shadow-emerald-500/20">
            <Shield className="h-6 w-6" />
          </div>
          <h2 className="mt-4 text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.login.title}
          </h2>
          <p className="mt-1.5 text-sm text-zinc-500 dark:text-zinc-400">
            {t.login.subtitle}
          </p>
        </div>

        {/* Demo Fast Login Helpers */}
        <div className="rounded-2xl border border-emerald-500/20 bg-emerald-50/50 p-3.5 dark:bg-emerald-950/20">
          <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-800 dark:text-emerald-300 mb-2">
            <Sparkles className="h-3.5 w-3.5" />
            {t.login.demoFastLogin}
          </div>
          <div className="grid grid-cols-3 gap-2">
            <button
              type="button"
              onClick={fillDemoHost}
              className="rounded-lg border border-emerald-300/80 bg-white px-2 py-1.5 text-xs font-medium text-emerald-800 hover:bg-emerald-50 dark:border-emerald-800 dark:bg-zinc-800 dark:text-emerald-200 transition-colors text-center cursor-pointer"
            >
              {t.login.demoHost}
            </button>
            <button
              type="button"
              onClick={fillDemoMember}
              className="rounded-lg border border-emerald-300/80 bg-white px-2 py-1.5 text-xs font-medium text-emerald-800 hover:bg-emerald-50 dark:border-emerald-800 dark:bg-zinc-800 dark:text-emerald-200 transition-colors text-center cursor-pointer"
            >
              {t.login.demoMember}
            </button>
            <button
              type="button"
              onClick={fillDemoAdmin}
              className="rounded-lg border border-indigo-300/80 bg-white px-2 py-1.5 text-xs font-semibold text-indigo-700 hover:bg-indigo-50 dark:border-indigo-800 dark:bg-zinc-800 dark:text-indigo-300 transition-colors text-center cursor-pointer"
            >
              {t.login.demoAdmin}
            </button>
          </div>
        </div>

        {error && (
          <div className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              {t.login.phoneLabel}
            </label>
            <div className="relative mt-1.5">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-zinc-400">
                <Phone className="h-4 w-4" />
              </div>
              <input
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder={t.login.phonePlaceholder}
                className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-3.5 text-sm text-zinc-900 outline-none transition-all focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-500/20 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100 dark:focus:border-emerald-500 dark:focus:bg-zinc-800"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              {t.login.passwordLabel}
            </label>
            <div className="relative mt-1.5">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-zinc-400">
                <Lock className="h-4 w-4" />
              </div>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t.login.passwordPlaceholder}
                className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 pl-10 pr-3.5 text-sm text-zinc-900 outline-none transition-all focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-500/20 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100 dark:focus:border-emerald-500 dark:focus:bg-zinc-800"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 text-sm font-semibold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-500 disabled:opacity-50 transition-all cursor-pointer"
          >
            {loading ? t.login.submitting : t.login.submit}
            <ArrowRight className="h-4 w-4" />
          </button>
        </form>

        <div className="text-center text-xs text-zinc-500 dark:text-zinc-400">
          {t.login.newHostPrompt}{" "}
          <Link href="/register/owner" className="font-semibold text-emerald-600 hover:text-emerald-500">
            {t.login.registerHostLink}
          </Link>
        </div>
      </div>
    </div>
  );
}
