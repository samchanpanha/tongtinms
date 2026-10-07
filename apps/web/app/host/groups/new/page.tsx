"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { formatMoney } from "@/lib/format";
import { useLanguage } from "@/lib/i18n";
import { Layers, ArrowRight, AlertCircle, Sparkles, Calculator } from "lucide-react";

export default function CreateGroupPage() {
  const router = useRouter();
  const { t } = useLanguage();

  // Form State
  const [name, setName] = useState("");
  const [type, setType] = useState<"BIDDING" | "FIXED_EQUAL">("BIDDING");
  const [baseAmount, setBaseAmount] = useState<number>(1_000_000);
  const [shareCount, setShareCount] = useState<number>(10);
  const [cycleUnit, setCycleUnit] = useState<"MONTH" | "WEEK" | "DAY">("MONTH");
  const [currency, setCurrency] = useState("VND");
  const [hostFeeType, setHostFeeType] = useState<"FIXED_PER_CYCLE" | "PERCENT_OF_POT" | "NONE">("FIXED_PER_CYCLE");
  const [hostFeeMinor, setHostFeeMinor] = useState<number>(100_000);
  const [maxBid, setMaxBid] = useState<number>(500_000);
  const [bidStep, setBidStep] = useState<number>(10_000);
  const [bidCloseOffset, setBidCloseOffset] = useState<number>(7);
  const [tieBreak, setTieBreak] = useState("EARLIEST_BID");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Live pot preview estimate
  const estimatedGross = (shareCount - 1) * baseAmount;
  const estimatedNet = hostFeeType === "FIXED_PER_CYCLE" ? estimatedGross - hostFeeMinor : estimatedGross;
  const effectiveName = name || t.createGroup.defaultGroupName;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const payload: Record<string, unknown> = {
        name: effectiveName.trim(),
        type,
        baseAmount,
        shareCount,
        cycleCount: shareCount,
        cycleUnit,
        currency,
        hostFeeType,
        hostFeeMinor: hostFeeType === "FIXED_PER_CYCLE" ? hostFeeMinor : 0,
        hostFeeBps: hostFeeType === "PERCENT_OF_POT" ? 100 : 0,
        bidCloseOffset,
        tieBreak,
      };

      if (type === "BIDDING") {
        payload.minBid = 0;
        payload.maxBid = maxBid;
        payload.bidStep = bidStep;
      }

      const res = await api.createGroup(payload);
      router.push(`/host/groups/${res.id}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t.createGroup.defaultError);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="flex items-center justify-between pb-6 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.createGroup.title}
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.createGroup.subtitle}
          </p>
        </div>
        <Link
          href="/host"
          className="rounded-xl border border-zinc-200 bg-white px-4 py-2 text-xs font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
        >
          {t.createGroup.cancelBtn}
        </Link>
      </div>

      {error && (
        <div className="mt-6 flex items-start gap-2.5 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="mt-8 grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* Left 2 Cols: Form Fields */}
        <div className="space-y-6 lg:col-span-2">
          {/* Card 1: General Info */}
          <div className="rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60 space-y-4">
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Layers className="h-4 w-4 text-emerald-600" />
              {t.createGroup.section1Title}
            </h3>

            <div>
              <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                {t.createGroup.groupNameLabel}
              </label>
              <input
                type="text"
                required
                value={effectiveName}
                onChange={(e) => setName(e.target.value)}
                placeholder={t.createGroup.groupNamePlaceholder}
                className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                {t.createGroup.groupTypeLabel}
              </label>
              <div className="mt-1.5 grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setType("BIDDING")}
                  className={`rounded-xl border p-3.5 text-left transition-all cursor-pointer ${
                    type === "BIDDING"
                      ? "border-emerald-500 bg-emerald-50/50 dark:bg-emerald-950/30"
                      : "border-zinc-200 bg-white hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900"
                  }`}
                >
                  <span className="block text-sm font-bold text-zinc-900 dark:text-zinc-100">
                    {t.createGroup.biddingTitle}
                  </span>
                  <span className="block text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                    {t.createGroup.biddingDesc}
                  </span>
                </button>

                <button
                  type="button"
                  onClick={() => setType("FIXED_EQUAL")}
                  className={`rounded-xl border p-3.5 text-left transition-all cursor-pointer ${
                    type === "FIXED_EQUAL"
                      ? "border-emerald-500 bg-emerald-50/50 dark:bg-emerald-950/30"
                      : "border-zinc-200 bg-white hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900"
                  }`}
                >
                  <span className="block text-sm font-bold text-zinc-900 dark:text-zinc-100">
                    {t.createGroup.fixedTitle}
                  </span>
                  <span className="block text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
                    {t.createGroup.fixedDesc}
                  </span>
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.baseAmountLabel}
                </label>
                <div className="relative mt-1">
                  <input
                    type="number"
                    min={1000}
                    step={1000}
                    required
                    value={baseAmount}
                    onChange={(e) => setBaseAmount(Number(e.target.value))}
                    className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                  <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-xs font-bold text-zinc-400">
                    {currency}
                  </span>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.shareCountLabel}
                </label>
                <input
                  type="number"
                  min={3}
                  max={50}
                  required
                  value={shareCount}
                  onChange={(e) => setShareCount(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.cycleUnitLabel}
                </label>
                <select
                  value={cycleUnit}
                  onChange={(e) => setCycleUnit(e.target.value as "MONTH" | "WEEK" | "DAY")}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="MONTH">{t.createGroup.unitMonth}</option>
                  <option value="WEEK">{t.createGroup.unitWeek}</option>
                  <option value="DAY">{t.createGroup.unitDay}</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.bidCloseOffsetLabel}
                </label>
                <input
                  type="number"
                  min={0}
                  max={30}
                  value={bidCloseOffset}
                  onChange={(e) => setBidCloseOffset(Number(e.target.value))}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.currencyLabel}
                </label>
                <select
                  value={currency}
                  onChange={(e) => setCurrency(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="VND">{t.createGroup.currencyVND}</option>
                  <option value="USD">{t.createGroup.currencyUSD}</option>
                  <option value="KHR">{t.createGroup.currencyKHR}</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.tieBreakLabel}
                </label>
                <select
                  value={tieBreak}
                  onChange={(e) => setTieBreak(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="EARLIEST_BID">{t.createGroup.tieEarliest}</option>
                  <option value="LOTTERY">{t.createGroup.tieLottery}</option>
                </select>
              </div>
            </div>
          </div>

          {/* Card 2: Commission & Bidding */}
          <div className="rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60 space-y-4">
            <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
              <Calculator className="h-4 w-4 text-emerald-600" />
              {t.createGroup.section2Title}
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.createGroup.hostFeeTypeLabel}
                </label>
                <select
                  value={hostFeeType}
                  onChange={(e) => setHostFeeType(e.target.value as "FIXED_PER_CYCLE" | "PERCENT_OF_POT" | "NONE")}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                >
                  <option value="FIXED_PER_CYCLE">{t.createGroup.feeFixed}</option>
                  <option value="PERCENT_OF_POT">{t.createGroup.feePercent}</option>
                  <option value="NONE">{t.createGroup.feeNone}</option>
                </select>
              </div>

              {hostFeeType === "FIXED_PER_CYCLE" && (
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    {t.createGroup.hostFeeAmountLabel}
                  </label>
                  <input
                    type="number"
                    min={0}
                    step={1000}
                    value={hostFeeMinor}
                    onChange={(e) => setHostFeeMinor(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                </div>
              )}
            </div>

            {type === "BIDDING" && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 border-t border-zinc-200/60 dark:border-zinc-700/60">
                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    {t.createGroup.maxBidLabel}
                  </label>
                  <input
                    type="number"
                    min={0}
                    max={baseAmount - 1}
                    value={maxBid}
                    onChange={(e) => setMaxBid(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                  <p className="mt-1 text-[11px] text-zinc-500">
                    {t.createGroup.maxBidHint(formatMoney(baseAmount, currency))}
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                    {t.createGroup.bidStepLabel}
                  </label>
                  <input
                    type="number"
                    min={1000}
                    step={1000}
                    value={bidStep}
                    onChange={(e) => setBidStep(Number(e.target.value))}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                  <p className="mt-1 text-[11px] text-zinc-500">{t.createGroup.bidStepHint}</p>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right Col: Live Summary Card */}
        <div className="space-y-6">
          <div className="rounded-2xl border border-emerald-500/20 bg-emerald-50/40 p-6 dark:bg-emerald-950/20 space-y-4">
            <div className="flex items-center gap-2 text-sm font-bold text-emerald-900 dark:text-emerald-200">
              <Sparkles className="h-4 w-4 text-emerald-600" />
              {t.createGroup.summaryTitle}
            </div>

            <div className="space-y-3 text-xs text-zinc-600 dark:text-zinc-300">
              <div className="flex justify-between py-1 border-b border-emerald-200/50 dark:border-emerald-900/40">
                <span>{t.createGroup.summarySharesLabel}</span>
                <span className="font-bold text-zinc-900 dark:text-zinc-100">
                  {t.createGroup.summarySharesValue(shareCount)}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-emerald-200/50 dark:border-emerald-900/40">
                <span>{t.createGroup.summaryBaseAmountLabel}</span>
                <span className="font-bold text-zinc-900 dark:text-zinc-100">
                  {formatMoney(baseAmount, currency)}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-emerald-200/50 dark:border-emerald-900/40">
                <span>{t.createGroup.summaryFeePerCycleLabel}</span>
                <span className="font-bold text-emerald-700 dark:text-emerald-300">
                  {hostFeeType === "FIXED_PER_CYCLE"
                    ? formatMoney(hostFeeMinor, currency)
                    : t.createGroup.summaryFeePercentValue}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-emerald-200/50 dark:border-emerald-900/40">
                <span>{t.createGroup.summaryTotalFeeLabel(shareCount)}</span>
                <span className="font-bold text-emerald-700 dark:text-emerald-300">
                  {formatMoney(hostFeeMinor * shareCount, currency)}
                </span>
              </div>
              <div className="flex justify-between py-1">
                <span>{t.createGroup.summaryEstFirstNetLabel}</span>
                <span className="font-bold text-zinc-900 dark:text-zinc-100">
                  ~{formatMoney(estimatedNet, currency)}
                </span>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 text-sm font-semibold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-500 disabled:opacity-50 transition-all cursor-pointer"
            >
              {loading ? t.createGroup.creatingBtn : t.createGroup.submitBtn}
              <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}
