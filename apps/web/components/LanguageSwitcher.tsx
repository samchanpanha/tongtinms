"use client";

import React, { useEffect, useRef, useState } from "react";
import { useLanguage } from "@/lib/i18n";
import { Check, ChevronDown, Globe } from "lucide-react";

export function LanguageSwitcher() {
  const { language, setLanguage, currentOption, options, t } = useLanguage();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label={t.navbar.languageLabel}
        aria-expanded={open}
        data-testid="language-switcher"
        className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-zinc-700 shadow-xs hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-200 dark:hover:bg-zinc-800 transition-colors cursor-pointer"
      >
        <Globe className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
        <span className="text-sm leading-none">{currentOption.flag}</span>
        <span className="hidden sm:inline">{currentOption.nativeName}</span>
        <span className="sm:hidden">{currentOption.shortLabel}</span>
        <ChevronDown
          className={`h-3 w-3 text-zinc-400 transition-transform duration-150 ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>

      {open && (
        <div
          role="listbox"
          aria-label={t.navbar.languageLabel}
          className="absolute right-0 mt-2 w-48 origin-top-right rounded-2xl border border-zinc-200/90 bg-white p-1.5 shadow-xl shadow-zinc-200/50 dark:border-zinc-800 dark:bg-zinc-900 dark:shadow-black/50 z-50"
        >
          <div className="px-2.5 py-1.5 text-[10px] font-bold uppercase tracking-wider text-zinc-400 dark:text-zinc-500">
            {t.navbar.languageLabel}
          </div>
          {options.map((opt) => {
            const active = opt.code === language;
            return (
              <button
                key={opt.code}
                type="button"
                role="option"
                aria-selected={active}
                data-testid={`lang-option-${opt.code}`}
                onClick={() => {
                  setLanguage(opt.code);
                  setOpen(false);
                }}
                className={`flex w-full items-center justify-between rounded-xl px-2.5 py-2 text-xs transition-colors cursor-pointer ${
                  active
                    ? "bg-emerald-50 font-semibold text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300"
                    : "text-zinc-700 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
                }`}
              >
                <span className="flex items-center gap-2.5">
                  <span className="text-base leading-none">{opt.flag}</span>
                  <span className="flex flex-col items-start">
                    <span className="leading-tight">{opt.nativeName}</span>
                    <span className="text-[10px] font-normal text-zinc-400 dark:text-zinc-500">
                      {opt.label}
                    </span>
                  </span>
                </span>
                {active && (
                  <Check className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400 shrink-0" />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
