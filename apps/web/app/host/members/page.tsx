"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, getStoredAuth } from "@/lib/api";
import { formatPhone, formatDate, formatBytes } from "@/lib/format";
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
  Paperclip,
  FileText,
  Download,
  Upload,
  Trash2,
  ShieldOff,
  ShieldCheck,
  Ban,
} from "lucide-react";

type MemberItem = Awaited<ReturnType<typeof api.getMembers>>[number];
type BlacklistItem = Awaited<ReturnType<typeof api.getBlacklist>>[number];

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

  // Files modal
  const [showFilesModal, setShowFilesModal] = useState(false);
  const [filesMember, setFilesMember] = useState<MemberItem | null>(null);
  const [uploadingFile, setUploadingFile] = useState(false);
  const [filesError, setFilesError] = useState<string | null>(null);
  const [filesSuccess, setFilesSuccess] = useState<string | null>(null);

  // Member status
  const [statusSavingId, setStatusSavingId] = useState<number | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);

  // Blacklist manager
  const [blacklist, setBlacklist] = useState<BlacklistItem[]>([]);
  const [blPhone, setBlPhone] = useState("");
  const [blReason, setBlReason] = useState("");
  const [blAdding, setBlAdding] = useState(false);
  const [blError, setBlError] = useState<string | null>(null);
  const [blSuccess, setBlSuccess] = useState<string | null>(null);
  const [blBusyId, setBlBusyId] = useState<number | null>(null);

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

  useEffect(() => {
    const auth = getStoredAuth();
    if (!auth || !auth.roles.includes("HOST")) {
      return;
    }
    let ignore = false;
    api.getBlacklist()
      .then((data) => {
        if (!ignore) setBlacklist(data);
      })
      .catch(() => {
        if (!ignore) setBlacklist([]);
      });
    return () => {
      ignore = true;
    };
  }, [refreshKey]);

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

  const handleStatusChange = async (member: MemberItem, status: string) => {
    if (status === member.status) return;
    setStatusError(null);
    setStatusSavingId(member.id);
    try {
      await api.updateMember(member.id, { status });
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setStatusError(err instanceof Error ? err.message : t.membersDirectory.statusChangeError);
    } finally {
      setStatusSavingId(null);
    }
  };

  const handleAddBlacklist = async (e: React.FormEvent) => {
    e.preventDefault();
    const phone = blPhone.trim();
    if (!phone) {
      setBlError(t.membersDirectory.blacklistErrRequired);
      return;
    }
    setBlError(null);
    setBlSuccess(null);
    setBlAdding(true);
    try {
      await api.addBlacklist({ phone, reason: blReason.trim() || undefined });
      setBlPhone("");
      setBlReason("");
      setBlSuccess(t.membersDirectory.blacklistAddedOk);
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setBlError(err instanceof Error ? err.message : t.membersDirectory.blacklistAddError);
    } finally {
      setBlAdding(false);
    }
  };

  const handleUnlist = async (id: number) => {
    setBlError(null);
    setBlSuccess(null);
    setBlBusyId(id);
    try {
      await api.unlistBlacklist(id);
      setBlSuccess(t.membersDirectory.blacklistRemovedOk);
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setBlError(err instanceof Error ? err.message : t.membersDirectory.blacklistUnlistError);
    } finally {
      setBlBusyId(null);
    }
  };

  const openFilesModal = (m: MemberItem) => {
    setFilesMember(m);
    setFilesError(null);
    setFilesSuccess(null);
    setShowFilesModal(true);
  };

  const handleFileUpload = async (file: File | null) => {
    if (!filesMember || !file) {
      setFilesError(t.membersDirectory.errFileRequired);
      return;
    }
    setFilesError(null);
    setFilesSuccess(null);
    setUploadingFile(true);
    try {
      await api.uploadMemberAttachment(filesMember.id, file);
      setFilesSuccess(t.membersDirectory.fileUploadedOk(file.name));
      setRefreshKey((k) => k + 1);
    } catch (err: unknown) {
      setFilesError(err instanceof Error ? err.message : t.membersDirectory.fileUploadError);
    } finally {
      setUploadingFile(false);
    }
  };

  const handleFileDownload = async (attachmentId: number, name: string) => {
    setFilesError(null);
    try {
      await api.downloadAttachment(attachmentId, name);
    } catch (err: unknown) {
      setFilesError(err instanceof Error ? err.message : t.membersDirectory.fileUploadError);
    }
  };

  const handleFileDelete = async (attachmentId: number) => {
    if (!filesMember) return;
    setFilesError(null);
    setFilesSuccess(null);
    try {
      await api.deleteAttachment(attachmentId);
      setRefreshKey((k) => k + 1);
      const updated = members.find((m) => m.id === filesMember.id);
      if (updated) setFilesMember(updated);
    } catch (err: unknown) {
      setFilesError(err instanceof Error ? err.message : t.membersDirectory.fileDeleteError);
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

      {statusError && (
        <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{statusError}</span>
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
                  <th className="px-3 py-3.5">{t.membersDirectory.colStatus}</th>
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
                    <td className="px-3 py-4">
                      <select
                        value={m.status}
                        disabled={statusSavingId === m.id}
                        onChange={(e) => handleStatusChange(m, e.target.value)}
                        className="rounded-lg border border-zinc-200 bg-white px-2 py-1 text-xs font-medium text-zinc-700 outline-none focus:border-emerald-500 disabled:opacity-50 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-200"
                      >
                        <option value="ACTIVE">{t.membersDirectory.statusActive}</option>
                        <option value="INACTIVE">{t.membersDirectory.statusInactive}</option>
                        <option value="BLOCKED">{t.membersDirectory.statusBlocked}</option>
                      </select>
                    </td>
                    <td className="px-3 py-4 text-xs text-zinc-500">
                      {formatDate(m.createdAt, language)}
                    </td>
                    <td className="py-4 pl-3 pr-6 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => openFilesModal(m)}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
                        >
                          <Paperclip className="h-3.5 w-3.5 text-zinc-400" />
                          {t.membersDirectory.filesBtn}
                        </button>
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
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Blacklist manager */}
      <div className="mt-12">
        <div className="flex items-center gap-3 pb-4 border-b border-zinc-200 dark:border-zinc-800">
          <ShieldOff className="h-5 w-5 text-rose-500" />
          <div>
            <h2 className="text-lg font-bold tracking-tight text-zinc-900 dark:text-zinc-50">
              {t.membersDirectory.blacklistTitle}
            </h2>
            <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">
              {t.membersDirectory.blacklistSubtitle}
            </p>
          </div>
        </div>

        <form
          onSubmit={handleAddBlacklist}
          className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-end"
        >
          <div className="flex-1">
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              {t.membersDirectory.blacklistPhoneLabel}
            </label>
            <input
              type="tel"
              value={blPhone}
              onChange={(e) => setBlPhone(e.target.value)}
              placeholder={t.membersDirectory.blacklistPhonePlaceholder}
              className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
            />
          </div>
          <div className="flex-[2]">
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
              {t.membersDirectory.blacklistReasonLabel}
            </label>
            <input
              type="text"
              value={blReason}
              onChange={(e) => setBlReason(e.target.value)}
              placeholder={t.membersDirectory.blacklistReasonPlaceholder}
              className="mt-1 w-full rounded-xl border border-zinc-200 bg-zinc-50/50 py-2.5 px-3.5 text-sm text-zinc-900 outline-none focus:border-emerald-500 focus:bg-white dark:border-zinc-800 dark:bg-zinc-800/50 dark:text-zinc-100"
            />
          </div>
          <button
            type="submit"
            disabled={blAdding}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-rose-600 px-4 py-2.5 text-xs font-semibold text-white shadow-md shadow-rose-600/20 hover:bg-rose-500 disabled:opacity-50 cursor-pointer"
          >
            {blAdding ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Ban className="h-3.5 w-3.5" />}
            {blAdding ? t.membersDirectory.blacklistAdding : t.membersDirectory.blacklistAddBtn}
          </button>
        </form>

        {blError && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{blError}</span>
          </div>
        )}

        {blSuccess && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300">
            <CheckCircle2 className="h-4 w-4 shrink-0" />
            <span>{blSuccess}</span>
          </div>
        )}

        <div className="mt-6">
          {blacklist.length === 0 ? (
            <p className="rounded-2xl border border-zinc-200/80 bg-white p-6 text-center text-xs text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/60 dark:text-zinc-400">
              {t.membersDirectory.blacklistEmpty}
            </p>
          ) : (
            <div className="overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-900/60">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                  <tr>
                    <th className="py-3.5 pl-6 pr-3">{t.membersDirectory.blacklistColPhone}</th>
                    <th className="px-3 py-3.5">{t.membersDirectory.blacklistColReason}</th>
                    <th className="px-3 py-3.5">{t.membersDirectory.blacklistColStatus}</th>
                    <th className="px-3 py-3.5">{t.membersDirectory.blacklistColCreated}</th>
                    <th className="py-3.5 pl-3 pr-6 text-right">{t.membersDirectory.colActions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                  {blacklist.map((entry) => (
                    <tr key={entry.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30 transition-colors">
                      <td className="py-4 pl-6 pr-3 font-mono text-xs">{formatPhone(entry.phone)}</td>
                      <td className="px-3 py-4 text-xs text-zinc-500">{entry.reason || "—"}</td>
                      <td className="px-3 py-4">
                        {entry.active ? (
                          <span className="inline-flex items-center gap-1.5 rounded-full border border-rose-200 bg-rose-50 px-2.5 py-0.5 text-xs font-medium text-rose-700 dark:border-rose-800 dark:bg-rose-950/60 dark:text-rose-300">
                            <ShieldOff className="h-3 w-3" />
                            {t.membersDirectory.blacklistActive}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 rounded-full border border-zinc-200 bg-zinc-50 px-2.5 py-0.5 text-xs font-medium text-zinc-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
                            <ShieldCheck className="h-3 w-3" />
                            {t.membersDirectory.blacklistUnlisted}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-4 text-xs text-zinc-500">
                        {formatDate(entry.createdAt, language)}
                      </td>
                      <td className="py-4 pl-3 pr-6 text-right">
                        {entry.active && (
                          <button
                            onClick={() => handleUnlist(entry.id)}
                            disabled={blBusyId === entry.id}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-zinc-200 px-3 py-1.5 text-xs font-semibold text-zinc-700 hover:bg-zinc-100 disabled:opacity-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
                          >
                            {blBusyId === entry.id ? (
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" />
                            )}
                            {t.membersDirectory.blacklistUnlistBtn}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
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

      {/* Modal: Member Files */}
      {showFilesModal && filesMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-3xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800">
              <h3 className="text-base font-bold text-zinc-900 dark:text-zinc-50 flex items-center gap-2">
                <Paperclip className="h-4 w-4 text-emerald-600" />
                {t.membersDirectory.filesModalTitle(filesMember.fullName)}
              </h3>
              <button
                onClick={() => setShowFilesModal(false)}
                className="rounded-lg p-1 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {filesError && (
              <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-300">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{filesError}</span>
              </div>
            )}

            {filesSuccess && (
              <div className="mt-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-300">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>{filesSuccess}</span>
              </div>
            )}

            <div className="mt-4">
              <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white hover:bg-emerald-500 disabled:opacity-50">
                {uploadingFile ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Upload className="h-3.5 w-3.5" />
                )}
                {uploadingFile ? t.membersDirectory.uploading : t.membersDirectory.uploadBtn}
                <input
                  type="file"
                  className="hidden"
                  disabled={uploadingFile}
                  onChange={(e) => {
                    handleFileUpload(e.target.files?.[0] ?? null);
                    e.target.value = "";
                  }}
                />
              </label>
            </div>

            <div className="mt-4">
              {filesMember.attachments.length === 0 ? (
                <p className="rounded-xl bg-zinc-50 p-4 text-center text-xs text-zinc-500 dark:bg-zinc-800/40 dark:text-zinc-400">
                  {t.membersDirectory.filesModalEmpty}
                </p>
              ) : (
                <div className="overflow-hidden rounded-2xl border border-zinc-200/80 dark:border-zinc-800">
                  <table className="w-full text-left text-sm">
                    <thead className="border-b border-zinc-200/80 bg-zinc-50/70 text-xs font-semibold text-zinc-600 dark:border-zinc-800 dark:bg-zinc-800/40 dark:text-zinc-300">
                      <tr>
                        <th className="py-2.5 pl-4 pr-3">{t.membersDirectory.fileColName}</th>
                        <th className="px-3 py-2.5">{t.membersDirectory.fileColSize}</th>
                        <th className="px-3 py-2.5">{t.membersDirectory.fileColUploaded}</th>
                        <th className="py-2.5 pl-3 pr-4 text-right">{t.membersDirectory.colActions}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-200/60 dark:divide-zinc-800/60 text-zinc-700 dark:text-zinc-300">
                      {filesMember.attachments.map((att) => (
                        <tr key={att.id} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30">
                          <td className="max-w-44 truncate py-3 pl-4 pr-3 font-medium text-zinc-900 dark:text-zinc-50">
                            <span className="flex items-center gap-2">
                              <FileText className="h-4 w-4 shrink-0 text-zinc-400" />
                              <span className="truncate">{att.originalName}</span>
                            </span>
                          </td>
                          <td className="px-3 py-3 text-xs text-zinc-500">
                            {formatBytes(att.sizeBytes)}
                          </td>
                          <td className="px-3 py-3 text-xs text-zinc-500">
                            {formatDate(att.uploadedAt, language)}
                          </td>
                          <td className="py-3 pl-3 pr-4 text-right">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                onClick={() => handleFileDownload(att.id, att.originalName)}
                                className="rounded-md p-1.5 text-emerald-600 hover:bg-emerald-100 dark:hover:bg-emerald-950/40"
                                title={t.membersDirectory.downloadFileBtn}
                              >
                                <Download className="h-3.5 w-3.5" />
                              </button>
                              <button
                                onClick={() => handleFileDelete(att.id)}
                                className="rounded-md p-1.5 text-rose-500 hover:bg-rose-100 dark:hover:bg-rose-950/40"
                                title={t.membersDirectory.deleteFileBtn}
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
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
