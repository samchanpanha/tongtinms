"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import {
  DEFAULT_LANGUAGE,
  Language,
  LANGUAGE_OPTIONS,
  LanguageOption,
  Translations,
} from "./types";
import { km } from "./locales/km";
import { en } from "./locales/en";
import { zh } from "./locales/zh";
import { vi } from "./locales/vi";

export * from "./types";

export const TRANSLATIONS: Record<Language, Translations> = {
  km,
  en,
  zh,
  vi,
};

const STORAGE_KEY = "tongtin_language";

export function isValidLanguage(val: unknown): val is Language {
  return val === "km" || val === "en" || val === "zh" || val === "vi";
}

export function getStoredLanguage(): Language {
  if (typeof window === "undefined") return DEFAULT_LANGUAGE;
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (isValidLanguage(saved)) {
      return saved;
    }
  } catch {
    // ignore storage errors
  }
  return DEFAULT_LANGUAGE;
}

export function getTranslations(lang: Language = DEFAULT_LANGUAGE): Translations {
  return TRANSLATIONS[lang] || TRANSLATIONS[DEFAULT_LANGUAGE];
}

interface LanguageContextValue {
  language: Language;
  setLanguage: (lang: Language) => void;
  t: Translations;
  currentOption: LanguageOption;
  options: LanguageOption[];
}

const LanguageContext = createContext<LanguageContextValue>({
  language: DEFAULT_LANGUAGE,
  setLanguage: () => {},
  t: TRANSLATIONS[DEFAULT_LANGUAGE],
  currentOption: LANGUAGE_OPTIONS[0],
  options: LANGUAGE_OPTIONS,
});

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguageState] = useState<Language>(DEFAULT_LANGUAGE);

  useEffect(() => {
    const stored = getStoredLanguage();
    if (stored !== DEFAULT_LANGUAGE) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setLanguageState(stored);
    }
  }, []);

  useEffect(() => {
    if (typeof document === "undefined") return;
    const opt = LANGUAGE_OPTIONS.find((o) => o.code === language) || LANGUAGE_OPTIONS[0];
    const title = TRANSLATIONS[language].meta.title;
    document.documentElement.lang = opt.htmlLang;
    document.title = title;

    // Next.js re-applies the static root metadata title on hydration and route
    // changes; re-assert the language-specific title whenever that happens.
    const observer = new MutationObserver(() => {
      if (document.title !== title) {
        document.title = title;
      }
    });
    observer.observe(document.head, { childList: true, subtree: true, characterData: true });
    return () => observer.disconnect();
  }, [language]);

  const setLanguage = useCallback((lang: Language) => {
    if (!isValidLanguage(lang)) return;
    setLanguageState(lang);
    if (typeof window !== "undefined") {
      try {
        localStorage.setItem(STORAGE_KEY, lang);
      } catch {
        // ignore storage errors
      }
    }
  }, []);

  const currentOption =
    LANGUAGE_OPTIONS.find((o) => o.code === language) || LANGUAGE_OPTIONS[0];

  return (
    <LanguageContext.Provider
      value={{
        language,
        setLanguage,
        t: TRANSLATIONS[language],
        currentOption,
        options: LANGUAGE_OPTIONS,
      }}
    >
      {children}
    </LanguageContext.Provider>
  );
}

export function useLanguage(): LanguageContextValue {
  return useContext(LanguageContext);
}
