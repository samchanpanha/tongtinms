"use client";

import React, { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useLanguage } from "@/lib/i18n";
import { formatMoney } from "@/lib/format";
import {
  Shield,
  Settings,
  CreditCard,
  Users,
  Receipt,
  Plus,
  Edit2,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  RefreshCw,
  Save,
  X,
} from "lucide-react";

interface PayWaySettings {
  merchantId: string;
  apiKey: string;
  apiUrl: string;
  checkUrl: string;
  sandboxMode: boolean;
  enabled: boolean;
  freeTrialDays: number;
  gracePeriodDays: number;
  enforceSubscription: boolean;
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

interface HostAdminView {
  ownerId: number;
  userId: number;
  fullName: string;
  phone: string;
  email: string | null;
  displayName: string;
  subscriptionStatus: string;
  trialEndsAt: string;
  subscriptionEndsAt: string;
  daysRemaining: number;
  currentPlanId: number | null;
  currentPlanName: string;
  groupsCount: number;
  membersCount: number;
  registeredAt: string;
}

interface Order {
  id: number;
  ownerId: number;
  planId: number;
  tranId: string;
  amountMinor: number;
  currency: string;
  status: string;
  paymentGateway: string;
  gatewayTranId: string | null;
  reqTime: string;
  paidAt: string | null;
  createdAt: string;
}

export default function AdminPage() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<"settings" | "plans" | "hosts" | "orders">("settings");

  const [settings, setSettings] = useState<PayWaySettings | null>(null);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [hosts, setHosts] = useState<HostAdminView[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);

  const [loading, setLoading] = useState(true);
  const [savingSettings, setSavingSettings] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  // Plan Edit/Create Modal
  const [editingPlan, setEditingPlan] = useState<Partial<Plan> | null>(null);
  const [isPlanModalOpen, setIsPlanModalOpen] = useState(false);

  // Host Extend Modal
  const [selectedHost, setSelectedHost] = useState<HostAdminView | null>(null);
  const [extendDays, setExtendDays] = useState(30);
  const [extendReason, setExtendReason] = useState("");
  const [setLifetime, setSetLifetime] = useState(false);
  const [isExtending, setIsExtending] = useState(false);

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api.getAdminPayWaySettings(),
      api.getAdminPlans(),
      api.getAdminHosts(),
      api.getAdminOrders(),
    ])
      .then(([settingsRes, plansRes, hostsRes, ordersRes]) => {
        if (!ignore) {
          setSettings(settingsRes);
          setPlans(plansRes);
          setHosts(hostsRes);
          setOrders(ordersRes);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : "Access denied. Admin role required.");
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [refreshKey]);

  const loadAll = () => {
    setLoading(true);
    setRefreshKey((k) => k + 1);
  };

  const handleSaveSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!settings) return;
    setSavingSettings(true);
    setError(null);
    setSuccessMsg(null);
    try {
      const updated = await api.updateAdminPayWaySettings(settings as unknown as Record<string, unknown>);
      setSettings(updated as unknown as PayWaySettings);
      setSuccessMsg(t.admin.settingsSavedSuccess);
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to save settings");
    } finally {
      setSavingSettings(false);
    }
  };

  const handleSavePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingPlan) return;
    try {
      if (editingPlan.id) {
        await api.updateAdminPlan(editingPlan.id, editingPlan);
      } else {
        await api.createAdminPlan(editingPlan);
      }
      setIsPlanModalOpen(false);
      setEditingPlan(null);
      loadAll();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Error saving plan");
    }
  };

  const handleDeletePlan = async (id: number) => {
    if (!confirm("Are you sure you want to delete this subscription plan?")) return;
    try {
      await api.deleteAdminPlan(id);
      loadAll();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Error deleting plan");
    }
  };

  const handleConfirmExtend = async () => {
    if (!selectedHost) return;
    setIsExtending(true);
    try {
      await api.extendHostSubscription(selectedHost.ownerId, {
        extendDays,
        reason: extendReason,
        setLifetime,
      });
      setSelectedHost(null);
      loadAll();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Error extending subscription");
    } finally {
      setIsExtending(false);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-200 pb-6 dark:border-zinc-800">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-indigo-600 dark:text-indigo-400">
            <Shield className="h-4 w-4" />
            <span>Platform Administration & Gateway Control</span>
          </div>
          <h1 className="mt-1 text-2xl sm:text-3xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.admin.title}
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.admin.subtitle}
          </p>
        </div>

        <button
          onClick={loadAll}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 bg-white px-4 py-2 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 cursor-pointer"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
          <span>Reload</span>
        </button>
      </div>

      {error && (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300 flex items-center gap-3">
          <AlertTriangle className="h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {successMsg && (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300 flex items-center gap-3">
          <CheckCircle2 className="h-5 w-5 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {/* Admin Tabs */}
      <div className="flex flex-wrap gap-2 border-b border-zinc-200 pb-3 dark:border-zinc-800">
        <button
          onClick={() => setActiveTab("settings")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-semibold transition-all cursor-pointer ${
            activeTab === "settings"
              ? "bg-indigo-600 text-white shadow-sm"
              : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300"
          }`}
        >
          <Settings className="h-4 w-4" />
          <span>{t.admin.tabSettings}</span>
        </button>

        <button
          onClick={() => setActiveTab("plans")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-semibold transition-all cursor-pointer ${
            activeTab === "plans"
              ? "bg-indigo-600 text-white shadow-sm"
              : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300"
          }`}
        >
          <CreditCard className="h-4 w-4" />
          <span>{t.admin.tabPlans}</span>
        </button>

        <button
          onClick={() => setActiveTab("hosts")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-semibold transition-all cursor-pointer ${
            activeTab === "hosts"
              ? "bg-indigo-600 text-white shadow-sm"
              : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300"
          }`}
        >
          <Users className="h-4 w-4" />
          <span>{t.admin.tabHosts} ({hosts.length})</span>
        </button>

        <button
          onClick={() => setActiveTab("orders")}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-semibold transition-all cursor-pointer ${
            activeTab === "orders"
              ? "bg-indigo-600 text-white shadow-sm"
              : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300"
          }`}
        >
          <Receipt className="h-4 w-4" />
          <span>{t.admin.tabOrders} ({orders.length})</span>
        </button>
      </div>

      {/* TAB 1: ABA PayWay & System Settings */}
      {activeTab === "settings" && settings && (
        <form onSubmit={handleSaveSettings} className="space-y-6">
          <div className="rounded-3xl border border-zinc-200 bg-white p-6 sm:p-8 shadow-sm dark:border-zinc-800 dark:bg-zinc-900 space-y-6">
            <div>
              <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
                <Settings className="h-5 w-5 text-indigo-600" />
                {t.admin.paywayConfigTitle}
              </h2>
              <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
                {t.admin.paywayConfigSubtitle}
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.admin.merchantIdLabel}
                </label>
                <input
                  type="text"
                  required
                  value={settings.merchantId}
                  onChange={(e) => setSettings({ ...settings, merchantId: e.target.value })}
                  className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 p-2.5 text-xs font-mono text-zinc-900 outline-none focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.admin.apiKeyLabel}
                </label>
                <input
                  type="text"
                  required
                  value={settings.apiKey}
                  onChange={(e) => setSettings({ ...settings, apiKey: e.target.value })}
                  className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 p-2.5 text-xs font-mono text-zinc-900 outline-none focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div className="md:col-span-2">
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.admin.apiUrlLabel}
                </label>
                <input
                  type="url"
                  required
                  value={settings.apiUrl}
                  onChange={(e) => setSettings({ ...settings, apiUrl: e.target.value })}
                  className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 p-2.5 text-xs font-mono text-zinc-900 outline-none focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div className="md:col-span-2">
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.admin.checkUrlLabel}
                </label>
                <input
                  type="url"
                  required
                  value={settings.checkUrl}
                  onChange={(e) => setSettings({ ...settings, checkUrl: e.target.value })}
                  className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 p-2.5 text-xs font-mono text-zinc-900 outline-none focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.admin.trialDaysLabel}
                </label>
                <input
                  type="number"
                  min="1"
                  max="365"
                  value={settings.freeTrialDays}
                  onChange={(e) => setSettings({ ...settings, freeTrialDays: Number(e.target.value) })}
                  className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 p-2.5 text-xs text-zinc-900 outline-none focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.admin.graceDaysLabel}
                </label>
                <input
                  type="number"
                  min="0"
                  max="30"
                  value={settings.gracePeriodDays}
                  onChange={(e) => setSettings({ ...settings, gracePeriodDays: Number(e.target.value) })}
                  className="mt-1.5 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 p-2.5 text-xs text-zinc-900 outline-none focus:border-indigo-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>
            </div>

            <div className="flex flex-wrap gap-6 border-t border-zinc-100 pt-4 dark:border-zinc-800">
              <label className="flex items-center gap-2 text-xs font-medium text-zinc-700 dark:text-zinc-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={settings.sandboxMode}
                  onChange={(e) => setSettings({ ...settings, sandboxMode: e.target.checked })}
                  className="h-4 w-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span>{t.admin.sandboxModeLabel}</span>
              </label>

              <label className="flex items-center gap-2 text-xs font-medium text-zinc-700 dark:text-zinc-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={settings.enabled}
                  onChange={(e) => setSettings({ ...settings, enabled: e.target.checked })}
                  className="h-4 w-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span>{t.admin.enabledLabel}</span>
              </label>

              <label className="flex items-center gap-2 text-xs font-medium text-zinc-700 dark:text-zinc-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={settings.enforceSubscription}
                  onChange={(e) => setSettings({ ...settings, enforceSubscription: e.target.checked })}
                  className="h-4 w-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                />
                <span>{t.admin.enforceSubLabel}</span>
              </label>
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="submit"
                disabled={savingSettings}
                className="flex items-center gap-2 rounded-xl bg-indigo-600 px-6 py-2.5 text-xs font-semibold text-white shadow-md hover:bg-indigo-500 disabled:opacity-50 transition-all cursor-pointer"
              >
                <Save className="h-4 w-4" />
                <span>{savingSettings ? "Saving..." : t.admin.saveSettingsBtn}</span>
              </button>
            </div>
          </div>
        </form>
      )}

      {/* TAB 2: Subscription Plans Manager */}
      {activeTab === "plans" && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
              <CreditCard className="h-5 w-5 text-indigo-600" />
              {t.admin.plansTitle}
            </h2>
            <button
              onClick={() => {
                setEditingPlan({
                  code: "",
                  name: "",
                  description: "",
                  priceMinor: 1000,
                  currency: "USD",
                  durationMonths: 1,
                  maxGroups: -1,
                  maxMembers: -1,
                  featuresJson: "[\"Full Access\"]",
                  badge: "",
                  sortOrder: plans.length + 1,
                  isActive: true,
                });
                setIsPlanModalOpen(true);
              }}
              className="flex items-center gap-1.5 rounded-xl bg-indigo-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 cursor-pointer"
            >
              <Plus className="h-4 w-4" />
              <span>{t.admin.addPlanBtn}</span>
            </button>
          </div>

          <div className="overflow-x-auto rounded-2xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-200 bg-zinc-50 font-semibold text-zinc-700 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-300">
                <tr>
                  <th className="px-4 py-3">Code</th>
                  <th className="px-4 py-3">Tên Gói</th>
                  <th className="px-4 py-3">Giá tiền</th>
                  <th className="px-4 py-3">Thời hạn</th>
                  <th className="px-4 py-3">Giới hạn (Dây / Hội viên)</th>
                  <th className="px-4 py-3">Huy hiệu</th>
                  <th className="px-4 py-3">Trạng thái</th>
                  <th className="px-4 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {plans.map((p) => (
                  <tr key={p.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 font-mono font-bold text-zinc-900 dark:text-zinc-100">{p.code}</td>
                    <td className="px-4 py-3 font-semibold text-zinc-900 dark:text-zinc-100">{p.name}</td>
                    <td className="px-4 py-3 font-bold text-emerald-600 dark:text-emerald-400">
                      {formatMoney(p.priceMinor, p.currency)}
                    </td>
                    <td className="px-4 py-3">{p.durationMonths} tháng</td>
                    <td className="px-4 py-3">
                      {p.maxGroups < 0 ? "Vô hạn" : p.maxGroups} / {p.maxMembers < 0 ? "Vô hạn" : p.maxMembers}
                    </td>
                    <td className="px-4 py-3">
                      {p.badge && (
                        <span className="rounded bg-zinc-100 px-2 py-0.5 text-[10px] font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                          {p.badge}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          p.isActive
                            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300"
                            : "bg-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
                        }`}
                      >
                        {p.isActive ? "ACTIVE" : "INACTIVE"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => {
                            setEditingPlan(p);
                            setIsPlanModalOpen(true);
                          }}
                          className="rounded-lg p-1.5 text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800 cursor-pointer"
                          title={t.admin.editPlanBtn}
                        >
                          <Edit2 className="h-3.5 w-3.5" />
                        </button>
                        <button
                          onClick={() => handleDeletePlan(p.id)}
                          className="rounded-lg p-1.5 text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-950/40 cursor-pointer"
                          title={t.admin.deletePlanBtn}
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: Hosts Directory & Subscription Overrides */}
      {activeTab === "hosts" && (
        <div className="space-y-6">
          <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
            <Users className="h-5 w-5 text-indigo-600" />
            {t.admin.hostsTitle}
          </h2>

          <div className="overflow-x-auto rounded-2xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-200 bg-zinc-50 font-semibold text-zinc-700 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-300">
                <tr>
                  <th className="px-4 py-3">{t.admin.colHostName}</th>
                  <th className="px-4 py-3">{t.admin.colPhone}</th>
                  <th className="px-4 py-3">{t.admin.colStatus}</th>
                  <th className="px-4 py-3">{t.admin.colDaysLeft}</th>
                  <th className="px-4 py-3">{t.admin.colPlanName}</th>
                  <th className="px-4 py-3">{t.admin.colGroups}</th>
                  <th className="px-4 py-3 text-right">{t.admin.colActions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {hosts.map((h) => (
                  <tr key={h.ownerId} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 font-semibold text-zinc-900 dark:text-zinc-100">
                      {h.fullName} ({h.displayName})
                    </td>
                    <td className="px-4 py-3 font-mono">{h.phone}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          h.subscriptionStatus === "TRIAL"
                            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300"
                            : h.subscriptionStatus === "ACTIVE"
                            ? "bg-blue-100 text-blue-800 dark:bg-blue-950/80 dark:text-blue-300"
                            : h.subscriptionStatus === "LIFETIME"
                            ? "bg-purple-100 text-purple-800 dark:bg-purple-950/80 dark:text-purple-300"
                            : "bg-rose-100 text-rose-800 dark:bg-rose-950/80 dark:text-rose-300"
                        }`}
                      >
                        {h.subscriptionStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-semibold">
                      {h.subscriptionStatus === "LIFETIME" ? "Vĩnh viễn" : `${h.daysRemaining} ngày`}
                    </td>
                    <td className="px-4 py-3 text-zinc-600 dark:text-zinc-300">{h.currentPlanName}</td>
                    <td className="px-4 py-3">{h.groupsCount} dây</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => {
                          setSelectedHost(h);
                          setExtendDays(30);
                          setExtendReason("");
                          setSetLifetime(false);
                        }}
                        className="rounded-lg bg-indigo-50 px-2.5 py-1 text-[11px] font-semibold text-indigo-600 hover:bg-indigo-100 dark:bg-indigo-950/50 dark:text-indigo-300 dark:hover:bg-indigo-950 cursor-pointer"
                      >
                        {t.admin.extendBtn}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 4: PayWay Orders & Transaction Ledger */}
      {activeTab === "orders" && (
        <div className="space-y-6">
          <h2 className="text-lg font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
            <Receipt className="h-5 w-5 text-indigo-600" />
            {t.admin.ordersTitle}
          </h2>

          <div className="overflow-x-auto rounded-2xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-200 bg-zinc-50 font-semibold text-zinc-700 dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-300">
                <tr>
                  <th className="px-4 py-3">{t.subscription.colTranId}</th>
                  <th className="px-4 py-3">Owner ID</th>
                  <th className="px-4 py-3">{t.subscription.colAmount}</th>
                  <th className="px-4 py-3">{t.admin.colGateway}</th>
                  <th className="px-4 py-3">{t.admin.colGatewayTranId}</th>
                  <th className="px-4 py-3">{t.subscription.colStatus}</th>
                  <th className="px-4 py-3">{t.subscription.colDate}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {orders.map((o) => (
                  <tr key={o.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50">
                    <td className="px-4 py-3 font-mono font-bold text-zinc-900 dark:text-zinc-100">{o.tranId}</td>
                    <td className="px-4 py-3 font-mono text-zinc-500">#{o.ownerId}</td>
                    <td className="px-4 py-3 font-bold text-zinc-900 dark:text-zinc-100">
                      {formatMoney(o.amountMinor, o.currency)}
                    </td>
                    <td className="px-4 py-3">{o.paymentGateway}</td>
                    <td className="px-4 py-3 font-mono text-[10px] text-zinc-500">{o.gatewayTranId || "—"}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          o.status === "PAID"
                            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950/80 dark:text-emerald-300"
                            : "bg-amber-100 text-amber-800 dark:bg-amber-950/80 dark:text-amber-300"
                        }`}
                      >
                        {o.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-zinc-500">
                      {o.paidAt ? new Date(o.paidAt).toLocaleString() : new Date(o.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Plan Edit / Create Modal */}
      {isPlanModalOpen && editingPlan && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-lg overflow-hidden rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between border-b border-zinc-200 pb-4 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                {editingPlan.id ? t.admin.editPlanBtn : t.admin.addPlanBtn}
              </h3>
              <button onClick={() => setIsPlanModalOpen(false)} className="text-zinc-400 hover:text-zinc-600 cursor-pointer">
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleSavePlan} className="mt-4 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold">{t.admin.planCodeLabel}</label>
                  <input
                    type="text"
                    required
                    value={editingPlan.code || ""}
                    onChange={(e) => setEditingPlan({ ...editingPlan, code: e.target.value })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold">{t.admin.planNameLabel}</label>
                  <input
                    type="text"
                    required
                    value={editingPlan.name || ""}
                    onChange={(e) => setEditingPlan({ ...editingPlan, name: e.target.value })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold">{t.admin.planDescLabel}</label>
                <input
                  type="text"
                  value={editingPlan.description || ""}
                  onChange={(e) => setEditingPlan({ ...editingPlan, description: e.target.value })}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                />
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-semibold">{t.admin.priceMinorLabel}</label>
                  <input
                    type="number"
                    required
                    value={editingPlan.priceMinor || 0}
                    onChange={(e) => setEditingPlan({ ...editingPlan, priceMinor: Number(e.target.value) })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold">{t.admin.currencyLabel}</label>
                  <select
                    value={editingPlan.currency || "USD"}
                    onChange={(e) => setEditingPlan({ ...editingPlan, currency: e.target.value })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  >
                    <option value="USD">USD ($)</option>
                    <option value="KHR">KHR (៛)</option>
                    <option value="VND">VND (₫)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold">{t.admin.durationMonthsLabel}</label>
                  <input
                    type="number"
                    min="1"
                    required
                    value={editingPlan.durationMonths || 1}
                    onChange={(e) => setEditingPlan({ ...editingPlan, durationMonths: Number(e.target.value) })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold">{t.admin.maxGroupsLabel}</label>
                  <input
                    type="number"
                    value={editingPlan.maxGroups ?? -1}
                    onChange={(e) => setEditingPlan({ ...editingPlan, maxGroups: Number(e.target.value) })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold">{t.admin.maxMembersLabel}</label>
                  <input
                    type="number"
                    value={editingPlan.maxMembers ?? -1}
                    onChange={(e) => setEditingPlan({ ...editingPlan, maxMembers: Number(e.target.value) })}
                    className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold">{t.admin.featuresLabel}</label>
                <textarea
                  rows={3}
                  value={editingPlan.featuresJson || ""}
                  onChange={(e) => setEditingPlan({ ...editingPlan, featuresJson: e.target.value })}
                  placeholder='["5 Groups", "50 Members", "Support"]'
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2 text-xs font-mono dark:border-zinc-800 dark:bg-zinc-800"
                />
              </div>

              <div className="flex items-center gap-6 pt-2">
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input
                    type="checkbox"
                    checked={editingPlan.isActive ?? true}
                    onChange={(e) => setEditingPlan({ ...editingPlan, isActive: e.target.checked })}
                    className="h-4 w-4 rounded border-zinc-300 text-indigo-600"
                  />
                  <span>{t.admin.activeLabel}</span>
                </label>
              </div>

              <div className="flex justify-end gap-2 pt-4">
                <button
                  type="button"
                  onClick={() => setIsPlanModalOpen(false)}
                  className="rounded-xl border border-zinc-200 px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:border-zinc-800 dark:text-zinc-400"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-indigo-600 px-5 py-2 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500"
                >
                  {t.admin.savePlanBtn}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Host Extend Modal */}
      {selectedHost && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-md overflow-hidden rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between border-b border-zinc-200 pb-4 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                {t.admin.extendModalTitle}
              </h3>
              <button onClick={() => setSelectedHost(null)} className="text-zinc-400 hover:text-zinc-600 cursor-pointer">
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              <div className="rounded-xl bg-zinc-50 p-3 text-xs dark:bg-zinc-800">
                <div>Chủ Hụi: <span className="font-bold">{selectedHost.fullName}</span></div>
                <div>SĐT: <span className="font-mono">{selectedHost.phone}</span></div>
                <div>Hiện tại còn: <span className="font-bold text-emerald-600">{selectedHost.daysRemaining} ngày</span></div>
              </div>

              <div>
                <label className="block text-xs font-semibold">{t.admin.extendDaysLabel}</label>
                <div className="mt-2 flex gap-2">
                  {[30, 90, 180, 365].map((d) => (
                    <button
                      key={d}
                      type="button"
                      onClick={() => {
                        setExtendDays(d);
                        setSetLifetime(false);
                      }}
                      className={`rounded-lg px-3 py-1.5 text-xs font-semibold ${
                        extendDays === d && !setLifetime
                          ? "bg-indigo-600 text-white"
                          : "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300"
                      }`}
                    >
                      +{d} ngày
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold">{t.admin.extendReasonLabel}</label>
                <input
                  type="text"
                  value={extendReason}
                  onChange={(e) => setExtendReason(e.target.value)}
                  placeholder="Gia hạn khuyến mãi / hỗ trợ"
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50 p-2.5 text-xs dark:border-zinc-800 dark:bg-zinc-800"
                />
              </div>

              <div className="pt-2">
                <label className="flex items-center gap-2 text-xs font-semibold text-purple-600 dark:text-purple-400 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={setLifetime}
                    onChange={(e) => setSetLifetime(e.target.checked)}
                    className="h-4 w-4 rounded border-zinc-300 text-purple-600"
                  />
                  <span>{t.admin.setLifetimeLabel}</span>
                </label>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t border-zinc-100 dark:border-zinc-800">
                <button
                  type="button"
                  onClick={() => setSelectedHost(null)}
                  className="rounded-xl border border-zinc-200 px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:border-zinc-800 dark:text-zinc-400"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={isExtending}
                  onClick={handleConfirmExtend}
                  className="rounded-xl bg-indigo-600 px-5 py-2 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-50 cursor-pointer"
                >
                  {isExtending ? "Đang xử lý..." : t.admin.confirmExtendBtn}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
