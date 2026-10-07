"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getStoredAuth } from "@/lib/api";
import { formatPhone, formatDate } from "@/lib/format";
import { useLanguage } from "@/lib/i18n";
import { EmptyState } from "@/components/EmptyState";
import {
  Users,
  Search,
  PlusCircle,
  KeyRound,
  CheckCircle2,
  AlertCircle,
  Loader2,
  X,
  Phone,
  User,
  CreditCard,
  MessageSquare,
} from "lucide-react";

type MemberItem = Awaited<ReturnType<typeof api.getMembers>>[number];

export default function MembersDirectoryPage() {
  const router = useRouter();
  const { language, t } = useLanguage();
  const [members, setMembers] = useState<MemberItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  // Modals
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [selectedMember, setSelectedMember] = useState<MemberItem | null>(null);

  // Create form
  const [newFullName, setNewFullName] = useState("");
  const [newPhone, setNewPhone] = useState("");
  const [newCccd, setNewCccd] = useState("");
  const [newZalo, setNewZalo] = useState("");
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Password form
  const [password, setPassword] = useState("");
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const auth = getStoredAuth();
    if (!auth || !auth.roles.includes("HOST")) {
      router.push("/login");
      return;
    }

    let ignore = false;
    api.getMembers(submittedQuery || undefined)
      .then((data) => {
        if (!ignore) {
          setMembers(data);
          setError(null);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError(err instanceof Error ? err.message : t.membersDirectory.defaultError);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [router, refreshKey, submittedQuery, t.membersDirectory.defaultError]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSubmittedQuery(query.trim());
  };

  const handleCreateMember = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);
    setCreateLoading(true);
    try {
      await api.createMember({
        fullName: newFullName.trim(),
        phone: newPhone.trim(),
        cccd: newCccd.trim() || undefined,
        zalo: newZalo.trim() || undefined,
      });
      setShowCreateModal(false);
      setNewFullName("");
      setNewPhone("");
      setNewCccd("");
      setNewZalo("");
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setCreateError(err instanceof Error ? err.message : t.membersDirectory.createError);
    } finally {
      setCreateLoading(false);
    }
  };

  const handleSetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedMember) return;
    if (password.length < 8) {
      setPasswordError(t.membersDirectory.errPasswordMin);
      return;
    }
    setPasswordError(null);
    setPasswordLoading(true);
    try {
      await api.setMemberLogin(selectedMember.id, password);
      setPasswordSuccess(t.membersDirectory.passwordSuccess(selectedMember.fullName));
      setTimeout(() => {
        setShowPasswordModal(false);
        setPasswordSuccess(null);
        setPassword("");
        setRefreshKey((k) => k + 1);
      }, 1500);
    } catch (err: unknown) {
      setPasswordError(err instanceof Error ? err.message : t.membersDirectory.passwordError);
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-8 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
            {t.membersDirectory.title}
          </h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            {t.membersDirectory.subtitle}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            href="/host"
            className="rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
          >
            {t.membersDirectory.backToDashboard}
          </Link>
          <button
            onClick={() => setShowCreateModal(true)}
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-md shadow-emerald-600/20 hover:bg-emerald-500 transition-colors cursor-pointer"
          >
            <PlusCircle className="h-4 w-4" />
            {t.membersDirectory.addMemberBtn}
          </button>
        </div>
      </div>

      {/* Search Bar */}
      <div className="mt-8 flex items-center justify-between gap-4">
        <form onSubmit={handleSearch} className="flex-1 max-w-md relative">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t.membersDirectory.searchPlaceholder}
            className="w-full rounded-xl border border-zinc-200 bg-white py-2 pl-10 pr-4 text-sm text-zinc-900 outline-none transition-all focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
          />
        </form>
        <span className="text-xs text-zinc-500 dark:text-zinc-400">
          {t.membersDirectory.totalCountPrefix}{" "}
          <strong className="text-zinc-900 dark:text-zinc-100">{members.length}</strong>{" "}
          {t.membersDirectory.totalCountSuffix}
        </span>
      </div>

      {error && (
        <div className="mt-4 rounded-xl bg-red-50 p-4 text-xs font-medium text-red-700 dark:bg-red-950/40 dark:text-red-400">
          {error}
        </div>
      )}

      {/* Main Table */}
      <div className="mt-6">
        {loading ? (
          <div className="flex min-h-[300px] items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-emerald-600" />
          </div>
        ) : members.length === 0 ? (
          <EmptyState
            icon={Users}
            title={query ? t.membersDirectory.emptySearchTitle : t.membersDirectory.emptyTitle}
            description={
              query
                ? t.membersDirectory.emptySearchDesc(query)
                : t.membersDirectory.emptyDesc
            }
            actionText={query ? undefined : t.membersDirectory.emptyAction}
            onAction={query ? undefined : () => setShowCreateModal(true)}
          />
        ) : (
          <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                <tr>
                  <th className="py-3.5 pl-6 pr-3">{t.membersDirectory.colFullName}</th>
                  <th className="px-3 py-3.5">{t.membersDirectory.colPhone}</th>
                  <th className="px-3 py-3.5">{t.membersDirectory.colIdCard}</th>
                  <th className="px-3 py-3.5">{t.membersDirectory.colZalo}</th>
                  <th className="px-3 py-3.5">{t.membersDirectory.colLoginStatus}</th>
                  <th className="px-3 py-3.5">{t.membersDirectory.colCreatedAt}</th>
                  <th className="py-3.5 pl-3 pr-6 text-right">{t.membersDirectory.colActions}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                {members.map((m) => (
                  <tr key={m.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30 transition-colors">
                    <td className="py-4 pl-6 pr-3 font-semibold text-zinc-900 dark:text-zinc-50">
                      {m.fullName}
                    </td>
                    <td className="px-3 py-4 font-mono text-xs">
                      {formatPhone(m.phone)}
                    </td>
                    <td className="px-3 py-4 font-mono text-xs text-zinc-500">
                      {m.cccd || "—"}
                    </td>
                    <td className="px-3 py-4 text-xs text-zinc-500">
                      {m.zalo || "—"}
                    </td>
                    <td className="px-3 py-4">
                      {m.loginEnabled ? (
                        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300">
                          <CheckCircle2 className="h-3 w-3" />
                          {t.membersDirectory.statusActivated}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 rounded-full border border-zinc-200 bg-zinc-50 px-2.5 py-0.5 text-xs font-medium text-zinc-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
                          {t.membersDirectory.statusNoPassword}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-4 text-xs text-zinc-500">
                      {formatDate(m.createdAt, language)}
                    </td>
                    <td className="py-4 pl-3 pr-6 text-right">
                      <button
                        onClick={() => {
                          setSelectedMember(m);
                          setPassword("");
                          setPasswordError(null);
                          setPasswordSuccess(null);
                          setShowPasswordModal(true);
                        }}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
                      >
                        <KeyRound className="h-3.5 w-3.5 text-emerald-600" />
                        {m.loginEnabled
                          ? t.membersDirectory.changePasswordBtn
                          : t.membersDirectory.setPasswordBtn}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal: Create Member */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50">
                {t.membersDirectory.createModalTitle}
              </h3>
              <button
                onClick={() => setShowCreateModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {createError && (
              <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{createError}</span>
              </div>
            )}

            <form onSubmit={handleCreateMember} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.membersDirectory.fullNameLabel}
                </label>
                <div className="relative mt-1">
                  <User className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
                  <input
                    type="text"
                    required
                    value={newFullName}
                    onChange={(e) => setNewFullName(e.target.value)}
                    placeholder={t.membersDirectory.fullNamePlaceholder}
                    className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2 pl-9 pr-3 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.membersDirectory.phoneLabel}
                </label>
                <div className="relative mt-1">
                  <Phone className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
                  <input
                    type="tel"
                    required
                    value={newPhone}
                    onChange={(e) => setNewPhone(e.target.value)}
                    placeholder={t.membersDirectory.phonePlaceholder}
                    className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2 pl-9 pr-3 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.membersDirectory.idCardLabel}
                </label>
                <div className="relative mt-1">
                  <CreditCard className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
                  <input
                    type="text"
                    value={newCccd}
                    onChange={(e) => setNewCccd(e.target.value)}
                    placeholder={t.membersDirectory.idCardPlaceholder}
                    className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2 pl-9 pr-3 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.membersDirectory.zaloLabel}
                </label>
                <div className="relative mt-1">
                  <MessageSquare className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
                  <input
                    type="text"
                    value={newZalo}
                    onChange={(e) => setNewZalo(e.target.value)}
                    placeholder={t.membersDirectory.zaloPlaceholder}
                    className="w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2 pl-9 pr-3 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                >
                  {t.membersDirectory.cancelBtn}
                </button>
                <button
                  type="submit"
                  disabled={createLoading}
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 disabled:opacity-50"
                >
                  {createLoading ? t.membersDirectory.savingBtn : t.membersDirectory.saveMemberBtn}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Set Login Password */}
      {showPasswordModal && selectedMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
                <KeyRound className="h-4 w-4 text-emerald-600" />
                {t.membersDirectory.passwordModalTitle}
              </h3>
              <button
                onClick={() => setShowPasswordModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <p className="mt-3 text-xs text-zinc-600 dark:text-zinc-400">
              {t.membersDirectory.passwordModalDesc(
                selectedMember.fullName,
                formatPhone(selectedMember.phone)
              )}
            </p>

            {passwordError && (
              <div className="mt-3 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{passwordError}</span>
              </div>
            )}

            {passwordSuccess && (
              <div className="mt-3 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>{passwordSuccess}</span>
              </div>
            )}

            <form onSubmit={handleSetPassword} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
                  {t.membersDirectory.newPasswordLabel}
                </label>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={t.membersDirectory.newPasswordPlaceholder}
                  className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowPasswordModal(false)}
                  className="rounded-xl px-4 py-2 text-xs font-semibold text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                >
                  {t.membersDirectory.cancelBtn}
                </button>
                <button
                  type="submit"
                  disabled={passwordLoading}
                  className="rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 disabled:opacity-50"
                >
                  {passwordLoading
                    ? t.membersDirectory.processingBtn
                    : t.membersDirectory.confirmPasswordBtn}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
