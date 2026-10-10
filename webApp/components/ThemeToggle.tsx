"use client";

import React, { useEffect, useState } from "react";
import { Monitor, Moon, Sun } from "lucide-react";
import {
  ThemePreference,
  applyTheme,
  loadThemePreference,
  saveThemePreference,
} from "@/lib/theme";

const OPTIONS: { value: ThemePreference; label: string; Icon: typeof Sun }[] = [
  { value: "system", label: "System theme", Icon: Monitor },
  { value: "light", label: "Light theme", Icon: Sun },
  { value: "dark", label: "Dark theme", Icon: Moon },
];

export const ThemeToggle: React.FC = () => {
  const [preference, setPreference] = useState<ThemePreference>("system");

  useEffect(() => {
    setPreference(loadThemePreference());
  }, []);

  // Follow the OS setting live while on "system".
  useEffect(() => {
    applyTheme(preference);
    if (preference !== "system") return;
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const onChange = () => applyTheme("system");
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [preference]);

  const choose = (value: ThemePreference) => {
    setPreference(value);
    saveThemePreference(value);
  };

  return (
    <div
      role="radiogroup"
      aria-label="Theme"
      className="flex items-center p-0.5 rounded-lg bg-slate-800/80 border border-white/10"
    >
      {OPTIONS.map(({ value, label, Icon }) => {
        const active = preference === value;
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={active}
            aria-label={label}
            title={label}
            onClick={() => choose(value)}
            className={`p-1 rounded-md transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-500/60 ${
              active ? "bg-sky-500/20 text-sky-300" : "text-slate-400 hover:text-slate-200"
            }`}
          >
            <Icon className="w-3.5 h-3.5" />
          </button>
        );
      })}
    </div>
  );
};
