// Light / Dark / System theme preference, applied as the `dark` class on <html>.

export type ThemePreference = "system" | "light" | "dark";

export const THEME_STORAGE_KEY = "tap8583_theme";

/**
 * Runs inline in <head> before first paint so the page never flashes the
 * wrong theme. Kept in sync with applyTheme() below.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var p=localStorage.getItem("${THEME_STORAGE_KEY}");var d=p==="dark"||(p!=="light"&&window.matchMedia("(prefers-color-scheme: dark)").matches);document.documentElement.classList.toggle("dark",d);}catch(e){document.documentElement.classList.toggle("dark",window.matchMedia("(prefers-color-scheme: dark)").matches);}})();`;

export function loadThemePreference(): ThemePreference {
  try {
    const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === "light" || saved === "dark") return saved;
  } catch {
    // Storage unavailable: fall back to the system setting.
  }
  return "system";
}

export function saveThemePreference(preference: ThemePreference) {
  try {
    if (preference === "system") window.localStorage.removeItem(THEME_STORAGE_KEY);
    else window.localStorage.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // Storage unavailable: the choice lasts for this tab only.
  }
}

export function systemPrefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function applyTheme(preference: ThemePreference) {
  const dark = preference === "dark" || (preference === "system" && systemPrefersDark());
  document.documentElement.classList.toggle("dark", dark);
}
