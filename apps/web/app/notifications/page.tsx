"use client";

import React, { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { EmptyState } from "@/components/EmptyState";
import { Bell, CheckCheck, Check, Loader2 } from "lucide-react";

type NotificationItem = Awaited<ReturnType<typeof api.getNotifications>>["notifications"][number];

export default function NotificationsPage() {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    api.getNotifications()
      .then((res) => {
        if (!ignore) {
          setItems(res.notifications);
          setUnreadCount(res.unreadCount);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, []);

  const handleMarkRead = async (id: number) => {
    try {
      await api.markNotificationRead(id);
      setItems((prev) =>
        prev.map((item) => (item.id === id ? { ...item, readAt: new Date().toISOString() } : item))
      );
      setUnreadCount((c) => Math.max(0, c - 1));
    } catch {
      // ignore
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await api.markAllNotificationsRead();
      setItems((prev) =>
        prev.map((item) => ({ ...item, readAt: item.readAt || new Date().toISOString() }))
      );
      setUnreadCount(0);
    } catch {
      // ignore
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-emerald-600" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between pb-6 border-b border-zinc-200 dark:border-zinc-800">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-900 dark:text-zinc-50 flex items-center gap-2.5">
            Thông Báo Hệ Thống
            {unreadCount > 0 && (
              <span className="rounded-full bg-rose-500 px-2.5 py-0.5 text-xs font-bold text-white">
                {unreadCount} mới
              </span>
            )}
          </h1>
          <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
            Cập nhật diễn biến mở kỳ, công bố người hốt hụi và xác nhận tiền đóng
          </p>
        </div>

        {unreadCount > 0 && (
          <button
            onClick={handleMarkAllRead}
            className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-3.5 py-2 text-xs font-semibold text-zinc-700 shadow-sm hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300 dark:hover:bg-zinc-800 cursor-pointer"
          >
            <CheckCheck className="h-4 w-4 text-emerald-600" />
            Đánh dấu tất cả đã đọc
          </button>
        )}
      </div>

      {items.length === 0 ? (
        <EmptyState
          icon={Bell}
          title="Không có thông báo nào"
          description="Các thông báo mới về kỳ hụi và thanh toán sẽ hiển thị tại đây khi có diễn biến mới."
        />
      ) : (
        <div className="space-y-3">
          {items.map((item) => {
            const isUnread = !item.readAt;
            return (
              <div
                key={item.id}
                className={`flex items-start justify-between gap-4 rounded-2xl border p-4.5 transition-all ${
                  isUnread
                    ? "border-emerald-500/30 bg-emerald-50/40 dark:border-emerald-900/50 dark:bg-emerald-950/20 shadow-sm"
                    : "border-zinc-200/80 bg-white dark:border-zinc-800 dark:bg-zinc-900/60"
                }`}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-zinc-900 dark:text-zinc-50">
                      {item.title}
                    </span>
                    {isUnread && (
                      <span className="h-2 w-2 rounded-full bg-emerald-500" />
                    )}
                  </div>
                  <p className="text-xs text-zinc-600 dark:text-zinc-300 leading-relaxed">
                    {item.body}
                  </p>
                  <span className="text-[11px] text-zinc-400">
                    {formatDateTime(item.createdAt)}
                  </span>
                </div>

                {isUnread && (
                  <button
                    onClick={() => handleMarkRead(item.id)}
                    className="shrink-0 rounded-lg p-1.5 text-zinc-400 hover:bg-emerald-100 hover:text-emerald-700 dark:hover:bg-emerald-900/40 dark:hover:text-emerald-300 transition-colors"
                    title="Đánh dấu đã đọc"
                  >
                    <Check className="h-4 w-4" />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
