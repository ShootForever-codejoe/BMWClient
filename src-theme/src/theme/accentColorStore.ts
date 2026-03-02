import { writable } from 'svelte/store';

function isValidHex(hex: string | null): boolean {
  return typeof hex === 'string' && /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/.test(hex);
}

function getAccentColor(): string {
  try {
    const color = localStorage.getItem('lb_accentColor');
    return isValidHex(color) ? color! : '#1e90ff';
  } catch {
    return '#1e90ff';
  }
}

const accentColorStore = writable('#1e90ff');

function initAccentColorStore() {
  accentColorStore.set(getAccentColor());
}

if (document.readyState === 'complete' || document.readyState === 'interactive') {
  initAccentColorStore();
} else {
  window.addEventListener('DOMContentLoaded', initAccentColorStore);
}

window.addEventListener('storage', () => {
  accentColorStore.set(getAccentColor());
});

export function setAccentColor(color: string) {
  if (!isValidHex(color)) return;
  localStorage.setItem('lb_accentColor', color);
  accentColorStore.set(color);
}

export { accentColorStore };
