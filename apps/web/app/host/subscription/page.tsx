"use client";

import React, { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";
import { formatMoney, formatDate, formatDateTime } from "@/lib/format";
import {
  CreditCard,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Sparkles,
  QrCode,
  ShieldCheck,
  ArrowRight,
  RefreshCw,
  ExternalLink,
  Receipt,
  X,
  Zap,
} from "lucide-react";

interface SubscriptionStatus {
  ownerId: number;
  displayName: string;
  subscriptionStatus: string;
  trialEndsAt: string;
  subscriptionEndsAt: string;
  daysRemaining: number;
  isTrial: boolean;
  isGracePeriod: boolean;
  isExpired: boolean;
  subscriptionEnabled?: boolean;
  canCreateGroup: boolean;
  currentPlan: {
    id: number;
    code: string;
    name: string;
    description: string;
    priceMinor: number;
    currency: string;
    durationMonths: number;
    maxGroups: number;
    maxMembers: number;
    featuresJson: string;
    badge: string;
    sortOrder: number;
    isActive: boolean;
  } | null;
  groupsCount: number;
  maxGroups: number;
  membersCount: number;
  maxMembers: number;
}

interface Plan {
  id: number;
  code: string;
  name: string;
  description: string;
  priceMinor: number;
  currency: string;
  durationMonths: number;
  maxGroups: number;
  maxMembers: number;
  featuresJson: string;
  badge: string;
  sortOrder: number;
  isActive: boolean;
}

interface Invoice {
  id: number;
  tranId: string;
  planId: number;
  amountMinor: number;
  currency: string;
  status: string;
  paymentGateway: string;
  paidAt: string | null;
  createdAt: string;
}

interface PayWayCheckoutData {
  tranId: string;
  reqTime: string;
  merchantId: string;
  amount: string;
  currency: string;
  itemsBase64: string;
  hash: string;
  checkoutUrl: string;
  paymentOption: string;
  returnUrl: string;
  continueSuccessUrl: string;
  cancelUrl: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  qrString: string;
  formFields: Record<string, string>;
}

export default function HostSubscriptionPage() {
  const { language, t } = useLanguage();

  const [status, setStatus] = useState<SubscriptionStatus | null>(null);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Checkout Modal State
  const [selectedPlan, setSelectedPlan] = useState<Plan | null>(null);
  const [checkoutData, setCheckoutData] = useState<PayWayCheckoutData | null>(null);
  const [isCheckoutLoading, setIsCheckoutLoading] = useState(false);
  const [isSimulating, setIsSimulating] = useState(false);
  const [paymentSuccess, setPaymentSuccess] = useState(false);

  // ABA PayWay gateway return flow: ?status=...&tran_id=... on this page
  const [returnNotice, setReturnNotice] = useState<
    "verifying" | "success" | "cancelled" | "failed" | null
  >(null);
  const handledGatewayReturn = useRef(false);

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api.getSubscriptionStatus(),
      api.getSubscriptionPlans(),
      api.getSubscriptionInvoices(),
    ])
      .then(([statusRes, plansRes, invoicesRes]) => {
        if (!ignore) {
          setStatus(statusRes);
          setPlans(plansRes);
          setInvoices(invoicesRes);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : t.subscription.paymentError);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [refreshKey, t.subscription.paymentError]);

  const loadData = () => {
    setLoading(true);
    setRefreshKey((k) => k + 1);
  };

  // Handle return from the ABA PayWay gateway. Query params are only a
  // signal to verify - the server re-checks the transaction with the
  // gateway before activating anything.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (handledGatewayReturn.current) return;
      const params = new URLSearchParams(window.location.search);
      const tranId = params.get("tran_id");
      if (!tranId) return;
      handledGatewayReturn.current = true;

      const statusParam = params.get("status");
      params.delete("status");
      params.delete("tran_id");
      const qs = params.toString();
      window.history.replaceState(
        null,
        "",
        window.location.pathname + (qs ? `?${qs}` : "") + window.location.hash
      );

      if (statusParam === "cancelled" || statusParam === "canceled") {
        setReturnNotice("cancelled");
        return;
      }

      setReturnNotice("verifying");
      api
        .verifySubscriptionOrder(tranId)
        .then(() => {
          setReturnNotice("success");
          setRefreshKey((k) => k + 1);
        })
        .catch(() => setReturnNotice("failed"));
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const handleOpenCheckout = async (plan: Plan) => {
    setSelectedPlan(plan);
    setCheckoutData(null);
    setPaymentSuccess(false);
    setIsCheckoutLoading(true);
    try {
      const data = await api.checkoutPayWay(
        plan.id,
        "cards,abapay_khqr,abapay_deeplink",
        `${window.location.origin}/host/subscription`
      );
      setCheckoutData(data);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : t.subscription.paymentError);
      setSelectedPlan(null);
    } finally {
      setIsCheckoutLoading(false);
    }
  };

  const handleSimulatePayment = async () => {
    if (!checkoutData) return;
    setIsSimulating(true);
    try {
      await api.simulatePayWayPayment(checkoutData.tranId);
      setPaymentSuccess(true);
      setTimeout(() => {
        loadData();
      }, 1500);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : t.subscription.paymentError);
    } finally {
      setIsSimulating(false);
    }
  };

  const parseFeatures = (json: string | null): string[] => {
    if (!json) return [];
    try {
      return JSON.parse(json);
    } catch {
      return [json];
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-200 pb-6 dark:border-zinc-800">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
            <Sparkles className="h-4 w-4" />
            <span>{t.subscription.headerEyebrow}</span>
          </div>
          <h1 className="mt-1 text-2xl sm:text-3xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.subscription.title}
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.subscription.subtitle}
          </p>
        </div>

        <button
          onClick={loadData}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 cursor-pointer"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
          <span>{t.subscription.refreshBtn}</span>
        </button>
      </div>

      {error && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Subscription subsystem disabled by admin */}
      {status && status.subscriptionEnabled === false && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 shrink-0" />
          <span>{t.subscription.disabledNotice}</span>
        </div>
      )}

      {/* ABA PayWay gateway return notices */}
      {returnNotice === "verifying" && (
        <div className="rounded-2xl border border-sky-200 bg-sky-50 p-4 text-sm text-sky-800 dark:border-sky-900/50 dark:bg-sky-950/40 dark:text-sky-300 flex items-center gap-3">
          <RefreshCw className="h-5 w-5 shrink-0 animate-spin" />
          <span>{t.subscription.verifyingPayment}</span>
        </div>
      )}
      {returnNotice === "success" && (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300 flex items-center gap-3">
          <CheckCircle2 className="h-5 w-5 shrink-0" />
          <span>
            <strong>{t.subscription.paymentSuccess}</strong>{" "}
            {t.subscription.paymentSuccessDesc}
          </span>
        </div>
      )}
      {returnNotice === "cancelled" && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 shrink-0" />
          <span>{t.subscription.paymentCancelled}</span>
        </div>
      )}
      {returnNotice === "failed" && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 shrink-0" />
          <span>{t.subscription.paymentError}</span>
        </div>
      )}

      {/* Subscription Status Card */}
      {status && (
        <div className="relative overflow-hidden rounded-3xl border border-zinc-200/80 bg-gradient-to-br from-white via-zinc-50/50 to-emerald-50/30 p-6 sm:p-8 shadow-sm dark:border-zinc-800 dark:from-zinc-900 dark:via-zinc-900 dark:to-emerald-950/20">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2.5">
                <span className="text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  {t.subscription.statusCardTitle}
                </span>

                {status.isTrial && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-3 py-1 text-xs font-bold text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300">
                    <Sparkles className="h-3.5 w-3.5" />
                    {t.subscription.statusTrial}
                  </span>
                )}
                {status.subscriptionStatus === "ACTIVE" && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-100 px-3 py-1 text-xs font-bold text-blue-800 dark:bg-blue-950/80 dark:text-blue-300">
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    {t.subscription.statusActive}
                  </span>
                )}
                {status.isGracePeriod && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-1 text-xs font-bold text-amber-800 dark:bg-amber-950/80 dark:text-amber-300">
                    <Clock className="h-3.5 w-3.5" />
                    {t.subscription.statusGrace}
                  </span>
                )}
                {status.isExpired && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-rose-100 px-3 py-1 text-xs font-bold text-rose-800 dark:bg-rose-950/80 dark:text-rose-300">
                    <AlertTriangle className="h-3.5 w-3.5" />
                    {t.subscription.statusExpired}
                  </span>
                )}
                {status.subscriptionStatus === "LIFETIME" && (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-purple-100 px-3 py-1 text-xs font-bold text-purple-800 dark:bg-purple-950/80 dark:text-purple-300">
                    <Zap className="h-3.5 w-3.5" />
                    {t.subscription.statusLifetime}
                  </span>
                )}
              </div>

              <h2 className="text-xl sm:text-2xl font-bold text-zinc-900 dark:text-zinc-50">
                {status.currentPlan ? status.currentPlan.name : t.subscription.trialPlanName}
              </h2>

              <p className="text-sm text-zinc-600 dark:text-zinc-300 max-w-2xl">
                {status.isTrial && t.subscription.trialNotice}
                {status.isExpired && t.subscription.expiredWarning}
                {status.isGracePeriod && t.subscription.graceWarning}
                {status.subscriptionStatus === "ACTIVE" &&
                  t.subscription.activeUntil(formatDate(status.subscriptionEndsAt, language))}
                {status.subscriptionStatus === "LIFETIME" && t.subscription.lifetimeActiveMsg}
              </p>
            </div>

            <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4 bg-white/80 dark:bg-zinc-800/80 p-4 rounded-2xl border border-zinc-200/80 dark:border-zinc-700/80">
              <div className="space-y-1">
                <div className="text-xs text-zinc-500 dark:text-zinc-400">{t.subscription.validityLabel}</div>
                <div className="text-lg font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-1.5">
                  <Clock className="h-4 w-4 text-emerald-600" />
                  {t.subscription.daysRemaining(status.daysRemaining)}
                </div>
                <div className="text-[11px] text-zinc-400">
                  {t.subscription.expiresOn(formatDate(status.subscriptionEndsAt, language))}
                </div>
              </div>

              <div className="hidden sm:block h-10 w-px bg-zinc-200 dark:bg-zinc-700" />

              <div className="space-y-1">
                <div className="text-xs text-zinc-500 dark:text-zinc-400">{t.subscription.quotaLabel}</div>
                <div className="text-xs font-semibold text-zinc-800 dark:text-zinc-200">
                  {t.subscription.usageGroups(
                    status.groupsCount,
                    status.maxGroups < 0 ? t.subscription.unlimited : String(status.maxGroups)
                  )}
                </div>
                <div className="text-xs font-semibold text-zinc-800 dark:text-zinc-200">
                  {t.subscription.usageMembers(
                    status.membersCount,
                    status.maxMembers < 0 ? t.subscription.unlimited : String(status.maxMembers)
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Subscription Plans Selection */}
      {(!status || status.subscriptionEnabled !== false) && (
        <div className="space-y-6">
        <div>
          <h2 className="text-xl font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
            <CreditCard className="h-5 w-5 text-emerald-600" />
            {t.subscription.plansTitle}
          </h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.subscription.plansSubtitle}
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {plans.map((plan) => {
            const isCurrent = status?.currentPlan?.id === plan.id;
            const features = parseFeatures(plan.featuresJson);
            const isPopular = plan.badge && (plan.badge.includes("Phổ biến") || plan.badge.includes("Popular"));

            return (
              <div
                key={plan.id}
                className={`relative flex flex-col justify-between rounded-3xl border bg-white p-6 shadow-sm transition-all hover:shadow-md dark:bg-zinc-900 ${
                  isPopular
                    ? "border-emerald-500 ring-2 ring-emerald-500/20 dark:border-emerald-500"
                    : isCurrent
                    ? "border-blue-500 ring-1 ring-blue-500/30"
                    : "border-zinc-200 dark:border-zinc-800"
                }`}
              >
                {plan.badge && (
                  <div className="absolute -top-3 right-6">
                    <span className="inline-flex items-center rounded-full bg-emerald-600 px-3 py-1 text-[11px] font-bold text-white shadow-sm">
                      {plan.badge}
                    </span>
                  </div>
                )}

                <div className="space-y-4">
                  <div>
                    <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                      {plan.name}
                    </h3>
                    <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400 min-h-[32px]">
                      {plan.description || t.subscription.defaultPlanDesc}
                    </p>
                  </div>

                  <div className="flex items-baseline gap-1 border-b border-zinc-100 pb-4 dark:border-zinc-800">
                    <span className="text-3xl font-extrabold text-zinc-900 dark:text-zinc-50">
                      {formatMoney(plan.priceMinor, plan.currency)}
                    </span>
                    <span className="text-xs text-zinc-500 dark:text-zinc-400">
                      {plan.durationMonths === 12 ? t.subscription.perYear : t.subscription.perMonth}
                    </span>
                  </div>

                  <div className="space-y-2.5 pt-2">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-zinc-400">
                      {t.subscription.benefitsLabel}
                    </div>
                    <ul className="space-y-2">
                      {features.map((feat, idx) => (
                        <li key={idx} className="flex items-start gap-2 text-xs text-zinc-700 dark:text-zinc-300">
                          <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500 mt-0.5" />
                          <span>{feat}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                <div className="pt-6 mt-4 border-t border-zinc-100 dark:border-zinc-800">
                  <button
                    onClick={() => handleOpenCheckout(plan)}
                    className={`w-full flex items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-semibold shadow-sm transition-all cursor-pointer ${
                      isPopular
                        ? "bg-emerald-600 text-white hover:bg-emerald-500 shadow-emerald-600/20"
                        : "bg-zinc-900 text-white hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-white"
                    }`}
                  >
                    <span>{isCurrent ? t.subscription.renewBtn : t.subscription.upgradeBtn}</span>
                    <ArrowRight className="h-3.5 w-3.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
        </div>
      )}

      {/* Invoice History */}
      {(!status || status.subscriptionEnabled !== false) && (
      <div className="space-y-4 pt-4 border-t border-zinc-200 dark:border-zinc-800">
        <div className="flex items-center gap-2">
          <Receipt className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
          <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-50">
            {t.subscription.invoicesTitle}
          </h2>
        </div>

        {invoices.length === 0 ? (
          <div className="rounded-2xl border border-zinc-200 bg-zinc-50/50 p-6 text-center text-xs text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/50 dark:text-zinc-400">
            {t.subscription.invoicesEmpty}
          </div>
        ) : (
          <div className="overflow-x-auto rounded-2xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-200 bg-zinc-50 font-semibold text-zinc-700 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-300">
                <tr>
                  <th className="px-4 py-3">{t.subscription.colTranId}</th>
                  <th className="px-4 py-3">{t.subscription.colAmount}</th>
                  <th className="px-4 py-3">{t.subscription.gatewayCol}</th>
                  <th className="px-4 py-3">{t.subscription.colStatus}</th>
                  <th className="px-4 py-3">{t.subscription.colDate}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {invoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 font-mono text-[11px] font-bold text-zinc-900 dark:text-zinc-100">
                      {inv.tranId}
                    </td>
                    <td className="px-4 py-3 font-semibold text-zinc-900 dark:text-zinc-100">
                      {formatMoney(inv.amountMinor, inv.currency)}
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1 rounded bg-zinc-100 px-2 py-0.5 text-[10px] font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                        {inv.paymentGateway}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          inv.status === "PAID"
                            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300"
                            : "bg-amber-100 text-amber-800 dark:bg-amber-950/80 dark:text-amber-300"
                        }`}
                      >
                        {inv.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-zinc-500">
                      {formatDateTime(inv.paidAt ?? inv.createdAt, language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      )}

      {/* ABA PayWay Checkout Modal */}
      {selectedPlan && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-lg overflow-hidden rounded-3xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            {/* Modal Header */}
            <div className="flex items-center justify-between border-b border-zinc-200 bg-zinc-50/80 px-6 py-4 dark:border-zinc-800 dark:bg-zinc-800/50">
              <div className="flex items-center gap-2.5">
                <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-tr from-sky-600 to-blue-500 text-white font-bold text-xs shadow-md">
                  ABA
                </div>
                <div>
                  <h3 className="text-sm font-bold text-zinc-900 dark:text-zinc-50">
                    {t.subscription.modalTitle}
                  </h3>
                  <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                    ABA PayWay Payment Gateway
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedPlan(null)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-200 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200 cursor-pointer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 space-y-6">
              {isCheckoutLoading ? (
                <div className="py-12 text-center space-y-3">
                  <RefreshCw className="h-8 w-8 animate-spin text-emerald-600 mx-auto" />
                  <p className="text-xs text-zinc-500">{t.subscription.verifyingPayment}</p>
                </div>
              ) : paymentSuccess ? (
                <div className="py-8 text-center space-y-4">
                  <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 dark:bg-emerald-950/80 dark:text-emerald-400 animate-bounce">
                    <CheckCircle2 className="h-8 w-8" />
                  </div>
                  <div>
                    <h4 className="text-lg font-bold text-zinc-900 dark:text-zinc-50">
                      {t.subscription.paymentSuccess}
                    </h4>
                    <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
                      {t.subscription.paymentSuccessDesc}
                    </p>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedPlan(null);
                      loadData();
                    }}
                    className="rounded-xl bg-emerald-600 px-6 py-2.5 text-xs font-semibold text-white shadow-md hover:bg-emerald-500 cursor-pointer"
                  >
                    {t.subscription.completeAndBackBtn}
                  </button>
                </div>
              ) : (
                <>
                  {/* Order Summary */}
                  <div className="rounded-2xl border border-zinc-200/80 bg-zinc-50 p-4 dark:border-zinc-800 dark:bg-zinc-800/40 space-y-2">
                    <div className="flex justify-between text-xs">
                      <span className="text-zinc-500">{t.subscription.planLabel}</span>
                      <span className="font-bold text-zinc-900 dark:text-zinc-100">{selectedPlan.name}</span>
                    </div>
                    <div className="flex justify-between text-xs">
                      <span className="text-zinc-500">{t.subscription.tranIdLabel}</span>
                      <span className="font-mono font-semibold text-zinc-700 dark:text-zinc-300">
                        {checkoutData?.tranId}
                      </span>
                    </div>
                    <div className="flex justify-between text-xs border-t border-zinc-200/60 pt-2 dark:border-zinc-700/60">
                      <span className="font-semibold text-zinc-700 dark:text-zinc-300">{t.subscription.totalLabel}</span>
                      <span className="text-base font-extrabold text-emerald-600 dark:text-emerald-400">
                        {formatMoney(selectedPlan.priceMinor, selectedPlan.currency)}
                      </span>
                    </div>
                  </div>

                  {/* KHQR Code Section */}
                  <div className="rounded-2xl border-2 border-dashed border-emerald-500/40 bg-emerald-50/30 p-5 text-center dark:bg-emerald-950/10 space-y-3">
                    <div className="flex items-center justify-center gap-1.5 text-xs font-bold text-emerald-800 dark:text-emerald-300">
                      <QrCode className="h-4 w-4" />
                      <span>{t.subscription.scanWithAba}</span>
                    </div>

                    {/* QR Code Graphic Box */}
                    <div className="mx-auto flex h-44 w-44 items-center justify-center rounded-2xl border border-zinc-200 bg-white p-2 shadow-sm dark:border-zinc-700 dark:bg-zinc-800">
                      <div className="relative flex h-full w-full flex-col items-center justify-center rounded-xl bg-zinc-900 p-2 text-white">
                        <QrCode className="h-28 w-28 text-white" />
                        <span className="font-mono text-[9px] text-emerald-400 font-bold">
                          KHQR • ABA PAY
                        </span>
                      </div>
                    </div>

                    <p className="text-[11px] text-zinc-500 dark:text-zinc-400 px-4">
                      {t.subscription.qrInstructions}
                    </p>
                  </div>

                  {/* Actions */}
                  <div className="space-y-2.5">
                    <button
                      onClick={handleSimulatePayment}
                      disabled={isSimulating}
                      className="w-full flex items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 text-xs font-bold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-500 disabled:opacity-50 transition-all cursor-pointer"
                    >
                      <Zap className="h-4 w-4" />
                      <span>{isSimulating ? t.subscription.processing : t.subscription.simulateFastPay}</span>
                    </button>

                    {checkoutData && (
                      <form
                        method="POST"
                        action={checkoutData.checkoutUrl}
                        target="_blank"
                        className="w-full"
                      >
                        {Object.entries(checkoutData.formFields).map(([key, val]) => (
                          <input key={key} type="hidden" name={key} value={val} />
                        ))}
                        <button
                          type="submit"
                          className="w-full flex items-center justify-center gap-2 rounded-xl border border-zinc-200 bg-white py-2.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
                        >
                          <ExternalLink className="h-3.5 w-3.5" />
                          <span>{t.subscription.payWayOfficialGateway}</span>
                        </button>
                      </form>
                    )}
                  </div>
                </>
              )}
            </div>

            {/* Modal Footer */}
            <div className="border-t border-zinc-200 bg-zinc-50/50 px-6 py-3 flex items-center justify-between text-[11px] text-zinc-400 dark:border-zinc-800 dark:bg-zinc-800/30">
              <span className="flex items-center gap-1">
                <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" />
                256-bit SSL Encrypted • developer.payway.com.kh
              </span>
              <button
                onClick={() => setSelectedPlan(null)}
                className="text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-200 font-semibold cursor-pointer"
              >
                {t.subscription.closeModal}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
