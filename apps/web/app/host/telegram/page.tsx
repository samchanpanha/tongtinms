"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, getStoredAuth } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";
import { Send, Link2, Unlink, Loader2, ShieldAlert, Info, CheckCircle2, XCircle } from "lucide-react";

type TelegramStatus = Awaited<ReturnType<typeof api.getTelegramStatus>>;

export default function HostTelegramPage() {
  const router = useRouter();
  const { t } = useLanguage();
  const [status, setStatus] = useState<TelegramStatus | null>(null);
  const [chatIdInput, setChatIdInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [notice, setNotice] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const auth = getStoredAuth();
    if (!auth || !auth.roles.includes("HOST")) {
      router.push("/login");
      return;
    }

    api
      .getTelegramStatus()
      .then((res) => {
        setStatus(res);
        setChatIdInput(res.chatId != null ? String(res.chatId) : "");
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : t.telegramChannel.msgError);
        setLoading(false);
      });
  }, [router, refreshKey, t.telegramChannel.msgError]);

  const saveChatId = async () => {
    setSaving(true);
    setNotice(null);
    try {
      const parsed = chatIdInput.trim() === "" ? null : Number(chatIdInput.trim());
      if (parsed != null && (!Number.isInteger(parsed) || parsed <= 0)) {
        setNotice({ kind: "err", text: t.telegramChannel.msgError });
        return;
      }
      const res = await api.setTelegramChatId(parsed);
      setStatus(res);
      setChatIdInput(res.chatId != null ? String(res.chatId) : "");
      setNotice({ kind: "ok", text: parsed == null ? t.telegramChannel.msgCleared : t.telegramChannel.msgSaved });
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t.telegramChannel.msgError;
      setNotice({ kind: "err", text: msg });
    } finally {
      setSaving(false);
    }
  };

  const clearChatId = async () => {
    if (!window.confirm(t.telegramChannel.msgClearConfirm)) return;
    setChatIdInput("");
    await saveChatId();
  };

  const testSend = async () => {
    setTesting(true);
    setNotice(null);
    try {
      await api.testTelegram();
      setNotice({ kind: "ok", text: t.telegramChannel.msgTestOk });
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : t.telegramChannel.msgTestError;
      setNotice({ kind: "err", text: msg });
    } finally {
      setTesting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-zinc-500">
          <Loader2 className="h-8 w-8 animate-spin text-sky-600" />
          <p className="text-sm">{t.hostDashboard.loading}</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <div className="flex items-center gap-3">
            <ShieldAlert className="h-6 w-6 shrink-0" />
            <div>
              <h3 className="text-base font-bold">{t.hostDashboard.errorTitle}</h3>
              <p className="text-sm mt-1">{error}</p>
            </div>
          </div>
          <button
            onClick={() => {
              setLoading(true);
              setError(null);
              setRefreshKey((k) => k + 1);
            }}
            className="mt-4 rounded-xl bg-rose-700 px-4 py-2 text-xs font-semibold text-white hover:bg-rose-600 transition-colors cursor-pointer"
          >
            {t.hostDashboard.retry}
          </button>
        </div>
      </div>
    );
  }

  const linked = status?.linked ?? false;
  const canTest = linked && (status?.eventsConfigured ?? false);

  const statusRows = [
    { label: t.telegramChannel.eventsEnabledLabel, value: status?.eventsEnabled ? t.telegramChannel.yesFlag : t.telegramChannel.noFlag },
    { label: t.telegramChannel.digestEnabledLabel, value: status?.digestEnabled ? t.telegramChannel.yesFlag : t.telegramChannel.noFlag },
    { label: t.telegramChannel.digestTimeLabel, value: status?.digestTime ?? "08:00" },
    { label: t.telegramChannel.botConfiguredLabel, value: status?.eventsConfigured ? t.telegramChannel.yesFlag : t.telegramChannel.noFlag },
  ];

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-8 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.telegramChannel.title}
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.telegramChannel.subtitle}
          </p>
        </div>
        <span
          className={`inline-flex items-center gap-1.5 self-start rounded-full px-3 py-1 text-xs font-semibold ${
            linked
              ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300"
              : "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
          }`}
        >
          {linked ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
          {linked ? t.telegramChannel.linkedBadge : t.telegramChannel.unlinkedBadge}
        </span>
      </div>

      {/* Chat id card */}
      <div className="mt-6 rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
        <h2 className="flex items-center gap-2 text-base font-semibold text-zinc-900 dark:text-zinc-50">
          <Link2 className="h-4 w-4 text-sky-600 dark:text-sky-400" />
          {t.telegramChannel.chatIdTitle}
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          {t.telegramChannel.chatIdDesc}
        </p>

        <div className="mt-4 flex flex-col sm:flex-row sm:items-center gap-3">
          <label className="sr-only" htmlFor="telegram-chat-id">
            {t.telegramChannel.chatIdLabel}
          </label>
          <input
            id="telegram-chat-id"
            type="text"
            inputMode="numeric"
            value={chatIdInput}
            onChange={(e) => setChatIdInput(e.target.value.replace(/[^0-9]/g, ""))}
            placeholder={t.telegramChannel.chatIdPlaceholder}
            className="flex-1 rounded-xl border border-zinc-300 bg-white px-4 py-2.5 text-sm text-zinc-900 placeholder-zinc-400 outline-none transition focus:ring-2 focus:ring-sky-500/40 dark:border-zinc-700 dark:bg-zinc-950 dark:text-zinc-50"
          />
          <div className="flex items-center gap-2">
            <button
              onClick={saveChatId}
              disabled={saving}
              className="inline-flex items-center gap-1.5 rounded-xl bg-sky-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-sky-500 transition-colors disabled:opacity-50 cursor-pointer"
            >
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Link2 className="h-4 w-4" />}
              {saving ? t.telegramChannel.saving : t.telegramChannel.saveBtn}
            </button>
            {linked && (
              <button
                onClick={clearChatId}
                disabled={saving}
                className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-300 px-4 py-2.5 text-sm font-medium text-zinc-700 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800 disabled:opacity-50 transition-colors cursor-pointer"
              >
                <Unlink className="h-4 w-4" />
                {t.telegramChannel.clearBtn}
              </button>
            )}
          </div>
        </div>

        <div className="mt-5 border-t border-zinc-100 pt-4 dark:border-zinc-800">
          <button
            onClick={testSend}
            disabled={!canTest || testing}
            title={canTest ? "" : t.telegramChannel.testingDisabled}
            className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-300 bg-emerald-50 px-4 py-2.5 text-sm font-semibold text-emerald-700 hover:bg-emerald-100 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-300 dark:hover:bg-emerald-950/70 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {testing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            {testing ? t.telegramChannel.testing : t.telegramChannel.testBtn}
          </button>
          {!canTest && (
            <p className="mt-2 flex items-center gap-1.5 text-xs text-zinc-500 dark:text-zinc-400">
              <Info className="h-3.5 w-3.5 shrink-0" />
              {t.telegramChannel.testingDisabled}
            </p>
          )}
        </div>
      </div>

      {/* Status card */}
      <div className="mt-6 rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
        <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-50">
          {t.telegramChannel.statusTitle}
        </h2>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          {t.telegramChannel.statusDesc}
        </p>
        <dl className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3">
          {statusRows.map((row) => (
            <div key={row.label} className="flex items-center justify-between border-b border-zinc-100 pb-2 dark:border-zinc-800">
              <dt className="text-sm text-zinc-500 dark:text-zinc-400">{row.label}</dt>
              <dd className="text-sm font-semibold text-zinc-900 dark:text-zinc-50">{row.value}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 flex items-start gap-1.5 text-xs text-zinc-500 dark:text-zinc-400">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          {t.telegramChannel.adminNote}
        </p>
      </div>

      {notice && (
        <div
          className={`mt-5 flex items-start gap-2 rounded-xl border px-4 py-3 text-sm ${
            notice.kind === "ok"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300"
              : "border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300"
          }`}
        >
          {notice.kind === "ok" ? (
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
          ) : (
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
          )}
          <span>{notice.text}</span>
        </div>
      )}
    </div>
  );
}