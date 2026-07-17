import { writable } from 'svelte/store';

const DEFAULT_ACCENT_COLOR = '#a855f7';
const LEGACY_ACCENT_COLOR = '#1e90ff';

function isValidHex(hex: string | null): boolean {
  return typeof hex === 'string' && /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(hex);
}

function getAccentColor(): string {
  try {
    // 优先读取持久化保存的主题色 旧版颜色用于兼容老配置
    const colors = [
      localStorage.getItem('clickgui.color'),
      localStorage.getItem('lb_accentColor')
    ];

    for (const color of colors) {
      if (isValidHex(color) && color!.toLowerCase() !== LEGACY_ACCENT_COLOR) {
        return color!;
      }
    }

    return DEFAULT_ACCENT_COLOR;
  } catch {
    return DEFAULT_ACCENT_COLOR;
  }
}

const accentColorStore = writable(DEFAULT_ACCENT_COLOR);

function initAccentColorStore() {
  accentColorStore.set(getAccentColor());
}

/** 重新读取持久化主题色 */
export function refreshAccentColor() {
  accentColorStore.set(getAccentColor());
}

if (document.readyState === 'complete' || document.readyState === 'interactive') {
  initAccentColorStore();
} else {
  window.addEventListener('DOMContentLoaded', initAccentColorStore);
}

window.addEventListener('storage', () => {
  refreshAccentColor();
});

export function setAccentColor(color: string) {
  if (!isValidHex(color)) return;
  localStorage.setItem('lb_accentColor', color);
  accentColorStore.set(color);
}

export { accentColorStore };
