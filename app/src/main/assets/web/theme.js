import { signal } from '@preact/signals';

export const NOTE_COLORS = [
  'DEFAULT', 'RED', 'ORANGE', 'YELLOW', 'GREEN', 'TEAL', 'BLUE', 'DARK_BLUE', 'PURPLE', 'PINK', 'BROWN', 'GRAY',
];

export const isDark = signal(true);

const kebab = (name) => name.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
const HEX = /^#[0-9a-fA-F]{6}$/;

export function applyTheme(theme) {
  if (!theme?.colors) return;
  const style = document.documentElement.style;
  for (const [name, value] of Object.entries(theme.colors)) {
    if (HEX.test(value)) style.setProperty(`--md-${kebab(name)}`, value);
  }
  for (const [name, value] of Object.entries(theme.noteColors ?? {})) {
    if (HEX.test(value)) style.setProperty(`--note-${name}`, value);
  }
  const dark = !!theme.dark;
  isDark.value = dark;
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  style.colorScheme = dark ? 'dark' : 'light';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta && theme.colors.surface) meta.content = theme.colors.surface;
}
