"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { api, InvoiceResponse } from "@/lib/api";
import { formatMoney, formatDateTime } from "@/lib/format";
import {
  Printer,
  ArrowLeft,
  Receipt,
  Building,
  User,
  CreditCard,
  Calendar,
  CheckCircle2,
  FileText,
} from "lucide-react";

export default function PaymentInvoicePage() {
  const params = useParams();
  const router = useRouter();
  const groupId = Number(params.id);
  const paymentId = Number(params.pid);

  const [invoice, setInvoice] = useState<InvoiceResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!groupId || !paymentId) return;

    api
      .getPaymentInvoice(groupId, paymentId)
      .then((data) => setInvoice(data))
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Failed to load invoice");
      })
      .finally(() => setLoading(false));
  }, [groupId, paymentId]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-zinc-50 dark:bg-zinc-950 p-6">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent" />
          <p className="text-sm text-zinc-500">Đang tải hoá đơn...</p>
        </div>
      </div>
    );
  }

  if (error || !invoice) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-zinc-50 dark:bg-zinc-950 p-6">
        <div className="max-w-md w-full rounded-2xl border border-red-200 bg-white p-6 shadow-sm dark:border-red-900/50 dark:bg-zinc-900 text-center">
          <p className="text-sm font-semibold text-red-600 dark:text-red-400 mb-4">
            {error || "Không tìm thấy hoá đơn"}
          </p>
          <Link
            href={`/host/groups/${groupId}`}
            className="inline-flex items-center gap-2 rounded-xl bg-zinc-100 px-4 py-2 text-sm font-medium text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-200"
          >
            <ArrowLeft className="h-4 w-4" />
            Quay lại nhóm
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-zinc-100 py-8 px-4 dark:bg-zinc-950 sm:px-6 lg:px-8 print:bg-white print:p-0">
      {/* Top action bar - hidden during print */}
      <div className="mx-auto max-w-3xl mb-6 flex items-center justify-between print:hidden">
        <Link
          href={`/host/groups/${groupId}`}
          className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm font-medium text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:bg-zinc-800/80 transition-all"
        >
          <ArrowLeft className="h-4 w-4" />
          Quay lại nhóm
        </Link>
        <button
          onClick={() => window.print()}
          className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-emerald-500 transition-all hover:scale-[1.02]"
        >
          <Printer className="h-4 w-4" />
          In / Lưu PDF
        </button>
      </div>

      {/* Invoice Document Card */}
      <div className="mx-auto max-w-3xl overflow-hidden rounded-3xl border border-zinc-200 bg-white shadow-xl dark:border-zinc-800 dark:bg-zinc-900 print:rounded-none print:border-none print:shadow-none print:p-0">
        {/* Header Ribbon */}
        <div className="border-b border-zinc-200 bg-gradient-to-r from-emerald-600 via-teal-600 to-cyan-600 px-8 py-6 text-white print:border-b-2 print:border-zinc-900 print:bg-none print:text-zinc-900 print:px-0">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <Receipt className="h-6 w-6" />
                <h1 className="text-2xl font-bold tracking-tight">HOÁ ĐƠN THANH TOÁN</h1>
              </div>
              <p className="mt-1 text-sm text-emerald-100 print:text-zinc-600">
                Mã hoá đơn: <span className="font-mono font-bold text-white print:text-zinc-900">{invoice.invoiceNo}</span>
              </p>
            </div>
            <div className="sm:text-right">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/20 px-3 py-1 text-xs font-semibold backdrop-blur-sm print:border print:border-zinc-400 print:bg-white print:text-zinc-900">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-300 print:text-zinc-900" />
                ĐÃ THANH TOÁN
              </span>
              <p className="mt-1 text-xs text-emerald-100 print:text-zinc-600">
                Ngày lập: {formatDateTime(invoice.issuedAt)}
              </p>
            </div>
          </div>
        </div>

        <div className="p-8 space-y-8 print:p-4 print:space-y-6">
          {/* Group and Parties Info */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pb-6 border-b border-zinc-200 dark:border-zinc-800 print:border-zinc-300">
            {/* Host / Issuer */}
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400 print:text-zinc-700">
                <Building className="h-4 w-4" />
                Bên nhận (Chủ hội)
              </div>
              <div className="rounded-2xl border border-zinc-100 bg-zinc-50/70 p-4 dark:border-zinc-800/80 dark:bg-zinc-800/40 print:border-none print:bg-transparent print:p-0">
                <p className="font-bold text-base text-zinc-900 dark:text-zinc-100">
                  {invoice.host.displayName}
                </p>
                {invoice.host.phone && (
                  <p className="text-xs text-zinc-600 dark:text-zinc-400 mt-1">
                    SĐT: {invoice.host.phone}
                  </p>
                )}
                {invoice.host.bankName && (
                  <div className="mt-2 pt-2 border-t border-zinc-200/60 dark:border-zinc-700/60 text-xs text-zinc-600 dark:text-zinc-400 space-y-0.5">
                    <p>
                      <span className="font-medium text-zinc-700 dark:text-zinc-300">Ngân hàng:</span> {invoice.host.bankName}
                    </p>
                    {invoice.host.bankAccount && (
                      <p>
                        <span className="font-medium text-zinc-700 dark:text-zinc-300">Số tài khoản:</span>{" "}
                        <span className="font-mono font-semibold text-zinc-800 dark:text-zinc-200">{invoice.host.bankAccount}</span>
                      </p>
                    )}
                    {invoice.host.accountHolder && (
                      <p>
                        <span className="font-medium text-zinc-700 dark:text-zinc-300">Chủ TK:</span> {invoice.host.accountHolder}
                      </p>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Payer / Member */}
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-teal-600 dark:text-teal-400 print:text-zinc-700">
                <User className="h-4 w-4" />
                Bên thanh toán (Hội viên)
              </div>
              <div className="rounded-2xl border border-zinc-100 bg-zinc-50/70 p-4 dark:border-zinc-800/80 dark:bg-zinc-800/40 print:border-none print:bg-transparent print:p-0">
                <p className="font-bold text-base text-zinc-900 dark:text-zinc-100">
                  {invoice.payer.memberName}
                </p>
                {invoice.payer.phone && (
                  <p className="text-xs text-zinc-600 dark:text-zinc-400 mt-1">
                    SĐT: {invoice.payer.phone}
                  </p>
                )}
                <div className="mt-2 pt-2 border-t border-zinc-200/60 dark:border-zinc-700/60 text-xs text-zinc-600 dark:text-zinc-400">
                  <p>
                    <span className="font-medium text-zinc-700 dark:text-zinc-300">Dây hụi:</span> {invoice.group.name}
                  </p>
                  <p>
                    <span className="font-medium text-zinc-700 dark:text-zinc-300">Mã dây:</span>{" "}
                    <span className="font-mono font-semibold">{invoice.group.code}</span>
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Payment Summary Meta */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 p-4 rounded-2xl bg-zinc-50 dark:bg-zinc-800/30 border border-zinc-100 dark:border-zinc-800 print:border print:border-zinc-300">
            <div>
              <p className="text-xs text-zinc-500 flex items-center gap-1">
                <Calendar className="h-3 w-3" />
                Ngày thanh toán
              </p>
              <p className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 mt-1">
                {formatDateTime(invoice.payment.paidAt)}
              </p>
            </div>
            <div>
              <p className="text-xs text-zinc-500 flex items-center gap-1">
                <CreditCard className="h-3 w-3" />
                Phương thức
              </p>
              <p className="text-xs font-semibold text-zinc-800 dark:text-zinc-200 mt-1">
                {invoice.payment.method}
              </p>
            </div>
            <div>
              <p className="text-xs text-zinc-500">Số tiền thanh toán</p>
              <p className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1">
                {formatMoney(invoice.payment.amountMinor, invoice.currency)}
              </p>
            </div>
            <div>
              <p className="text-xs text-zinc-500">Ghi chú</p>
              <p className="text-xs text-zinc-700 dark:text-zinc-300 mt-1 truncate">
                {invoice.payment.note || "—"}
              </p>
            </div>
          </div>

          {/* Allocation Items Table */}
          <div>
            <div className="flex items-center gap-2 mb-3">
              <FileText className="h-4 w-4 text-zinc-500" />
              <h2 className="text-xs font-bold uppercase tracking-wider text-zinc-700 dark:text-zinc-300">
                Chi tiết khoản thanh toán (Allocations)
              </h2>
            </div>
            <div className="overflow-x-auto rounded-2xl border border-zinc-200 dark:border-zinc-800 print:border-zinc-300">
              <table className="min-w-full divide-y divide-zinc-200 dark:divide-zinc-800 text-left text-xs">
                <thead className="bg-zinc-50 dark:bg-zinc-800/60 text-zinc-600 dark:text-zinc-400">
                  <tr>
                    <th className="px-4 py-3 font-semibold">Khoản mục</th>
                    <th className="px-4 py-3 font-semibold">Kỳ</th>
                    <th className="px-4 py-3 font-semibold">Loại</th>
                    <th className="px-4 py-3 font-semibold text-right">Tổng nghĩa vụ</th>
                    <th className="px-4 py-3 font-semibold text-right">Đã phân bổ</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800 bg-white dark:bg-zinc-900">
                  {invoice.lines.map((line, idx) => (
                    <tr key={idx}>
                      <td className="px-4 py-3 font-medium text-zinc-900 dark:text-zinc-100">
                        {line.description}
                      </td>
                      <td className="px-4 py-3 text-zinc-600 dark:text-zinc-400">
                        {line.cycleNo != null ? `Kỳ ${line.cycleNo}` : "—"}
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center rounded-md bg-zinc-100 px-2 py-0.5 text-[10px] font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
                          {line.type}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-zinc-600 dark:text-zinc-400">
                        {formatMoney(line.amountMinor, invoice.currency)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono font-bold text-emerald-600 dark:text-emerald-400">
                        {formatMoney(line.allocatedMinor, invoice.currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
                <tfoot className="bg-zinc-50/70 dark:bg-zinc-800/40 font-bold border-t border-zinc-200 dark:border-zinc-800">
                  <tr>
                    <td colSpan={4} className="px-4 py-3 text-right text-zinc-700 dark:text-zinc-300">
                      Tổng tiền đã phân bổ:
                    </td>
                    <td className="px-4 py-3 text-right font-mono text-base text-emerald-600 dark:text-emerald-400">
                      {formatMoney(invoice.totalAllocatedMinor, invoice.currency)}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          {/* Signatures & Stamp area for printing */}
          <div className="pt-8 grid grid-cols-2 text-center text-xs text-zinc-500 border-t border-zinc-200 dark:border-zinc-800 print:border-zinc-400 print:pt-12">
            <div>
              <p className="font-semibold text-zinc-700 dark:text-zinc-300">Người nộp tiền</p>
              <p className="text-[10px] italic text-zinc-400 mt-0.5">(Ký và ghi rõ họ tên)</p>
              <div className="h-20" />
              <p className="font-medium text-zinc-800 dark:text-zinc-200">{invoice.payer.memberName}</p>
            </div>
            <div>
              <p className="font-semibold text-zinc-700 dark:text-zinc-300">Người lập phiếu (Chủ hội)</p>
              <p className="text-[10px] italic text-zinc-400 mt-0.5">(Ký và xác nhận)</p>
              <div className="h-20" />
              <p className="font-medium text-zinc-800 dark:text-zinc-200">{invoice.host.displayName}</p>
            </div>
          </div>

          <div className="pt-4 text-center text-[10px] text-zinc-400 border-t border-zinc-100 dark:border-zinc-800/50">
            Hệ thống Quản lý Hụi / Tong Tin • Biên lai xác nhận thanh toán điện tử
          </div>
        </div>
      </div>
    </div>
  );
}
