import { useRef, useState, useId, useEffect, useLayoutEffect } from 'preact/hooks';
import { signal } from '@preact/signals';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { NOTE_COLORS } from '../theme.js';
import { labels, createLabel, showError, showSnack } from '../store.js';
import { findRanges } from '../search.js';
import { copyText } from '../clipboard.js';
import { Icon } from './icons.js';

export const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export function IconButton({ icon, label, onClick, active, disabled, class: cls = '', size, ...rest }) {
  return html`
    <button type="button" class=${`icon-btn ${active ? 'is-active' : ''} ${cls}`} aria-label=${label} title=${label}
      aria-pressed=${active === undefined ? undefined : String(!!active)} disabled=${disabled} onClick=${onClick} ...${rest}>
      <${Icon} name=${icon} size=${size} />
    </button>`;
}

function placePopover(popover, anchor, align) {
  const rect = anchor.getBoundingClientRect();
  const width = popover.offsetWidth;
  const height = popover.offsetHeight;
  const margin = 8;
  let left = align === 'end' ? rect.right - width : rect.left;
  left = Math.max(margin, Math.min(left, window.innerWidth - width - margin));
  let top = rect.bottom + 4;
  if (top + height > window.innerHeight - margin) top = Math.max(margin, rect.top - height - 4);
  popover.style.left = `${left}px`;
  popover.style.top = `${top}px`;
}

// Button + native popover. Content is rendered only while open, so lists of
// cards don't pay for menus nobody opened.
export function Popover({ icon, label, buttonClass = '', align = 'start', class: cls = '', disabled, children, trigger }) {
  const id = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const popId = `pop-${id}`;
  const buttonRef = useRef(null);
  const popRef = useRef(null);
  const [open, setOpen] = useState(false);

  useLayoutEffect(() => {
    if (open && popRef.current && buttonRef.current) placePopover(popRef.current, buttonRef.current, align);
  }, [open]);

  const onToggle = (event) => setOpen(event.newState === 'open');
  const close = () => popRef.current?.hidePopover?.();

  return html`
    <button type="button" ref=${buttonRef} class=${trigger ? `chip-btn ${buttonClass}` : `icon-btn ${buttonClass}`} popovertarget=${popId}
      aria-label=${label} title=${label} aria-haspopup="true" aria-expanded=${String(open)} disabled=${disabled}>
      ${trigger ?? html`<${Icon} name=${icon} />`}
    </button>
    <div popover="auto" id=${popId} ref=${popRef} class=${`popover ${cls}`} onToggle=${onToggle}>
      ${open ? (typeof children === 'function' ? children(close) : children) : null}
    </div>`;
}

export function ColorPicker({ value, onPick }) {
  return html`
    <div class="color-grid" role="radiogroup" aria-label=${t('color')}>
      ${NOTE_COLORS.map((color) => html`
        <button type="button" role="radio" aria-checked=${String(value === color)} key=${color}
          class=${`swatch swatch-${color} ${value === color ? 'is-current' : ''}`}
          style=${color === 'DEFAULT' ? undefined : `background: var(--note-${color})`}
          title=${t('colorNames')[color]} aria-label=${t('colorNames')[color]} onClick=${() => onPick(color)}>
          ${value === color ? html`<${Icon} name="check" size=${18} />` : color === 'DEFAULT' ? html`<${Icon} name="colorReset" size=${18} />` : null}
        </button>`)}
    </div>`;
}

// state(labelId) → true | false | 'mixed'
export function LabelPicker({ state, onToggle }) {
  const [filter, setFilter] = useState('');
  const inputRef = useRef(null);
  useEffect(() => { inputRef.current?.focus(); }, []);
  const name = filter.trim();
  const lower = name.toLocaleLowerCase('tr');
  const list = labels.value.filter((l) => !lower || l.name.toLocaleLowerCase('tr').includes(lower));
  const exact = labels.value.some((l) => l.name.toLocaleLowerCase('tr') === lower);

  async function create() {
    if (!name || exact) return;
    try {
      const label = await createLabel(name);
      setFilter('');
      onToggle(label.id, true);
    } catch (error) {
      if (error.code === 'exists') showSnack({ text: t('labelExists'), error: true });
      else showError(error);
    }
  }

  return html`
    <div class="label-picker">
      <input ref=${inputRef} class="label-filter" type="text" value=${filter} placeholder=${t('labelFilter')}
        aria-label=${t('labelFilter')} maxlength="100"
        onInput=${(e) => setFilter(e.currentTarget.value)}
        onKeyDown=${(e) => { if (e.key === 'Enter') { e.preventDefault(); create(); } }} />
      <div class="label-options" role="group">
        ${list.map((label) => {
          const s = state(label.id);
          return html`
            <label class="label-option" key=${label.id}>
              <input type="checkbox" checked=${s === true} indeterminate=${s === 'mixed'}
                onChange=${() => onToggle(label.id, s !== true)} />
              <span>${label.name}</span>
            </label>`;
        })}
        ${!list.length && !name ? html`<p class="muted small pad">${t('noLabels')}</p>` : null}
      </div>
      ${name && !exact ? html`
        <button type="button" class="menu-item" onClick=${create}>
          <${Icon} name="add" size=${20} /><span>${t('createLabelNamed', name)}</span>
        </button>` : null}
    </div>`;
}

export function Highlight({ text, terms }) {
  const ranges = terms ? findRanges(text, terms) : [];
  if (!ranges.length) return text;
  const parts = [];
  let at = 0;
  for (const [from, to] of ranges) {
    if (from > at) parts.push(text.slice(at, from));
    parts.push(html`<mark>${text.slice(from, to)}</mark>`);
    at = to;
  }
  if (at < text.length) parts.push(text.slice(at));
  return parts;
}

export async function copyWithFeedback(text, count = 1) {
  const ok = await copyText(text);
  showSnack({ text: ok ? (count > 1 ? t('copiedN', count) : t('copied')) : t('copyFailed'), error: !ok });
}

// ---- confirm dialog ----

const confirmState = signal(null);

export function confirmAction(text, okLabel) {
  return new Promise((resolve) => {
    confirmState.value = { text, okLabel, resolve };
  });
}

export function ConfirmHost() {
  const ref = useRef(null);
  const state = confirmState.value;
  useLayoutEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (state && !dialog.open) dialog.showModal();
    if (!state && dialog.open) dialog.close();
  }, [state]);
  const finish = (result) => {
    state?.resolve(result);
    confirmState.value = null;
  };
  return html`
    <dialog ref=${ref} class="confirm-dialog" onCancel=${(e) => { e.preventDefault(); finish(false); }}>
      ${state ? html`
        <form method="dialog" onSubmit=${(e) => { e.preventDefault(); finish(true); }}>
          <p class="confirm-text">${state.text}</p>
          <div class="dialog-actions">
            <button type="button" class="text-btn" onClick=${() => finish(false)}>${t('cancel')}</button>
            <button type="submit" class="filled-btn danger" autofocus>${state.okLabel}</button>
          </div>
        </form>` : null}
    </dialog>`;
}

export function useAutosize(ref, value, enabled = true) {
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el || !enabled) return;
    const fit = () => {
      el.style.height = 'auto';
      el.style.height = `${el.scrollHeight}px`;
    };
    if (el.getClientRects().length) fit();
    else {
      // Not laid out yet (e.g. inside a dialog that opens in the same commit).
      const frame = requestAnimationFrame(fit);
      return () => cancelAnimationFrame(frame);
    }
  }, [value, enabled]);
}

export function titleSuggestion(title, body) {
  if (title.trim()) return null;
  const newline = body.indexOf('\n');
  if (newline < 0) return null;
  const first = body.slice(0, newline).trim();
  const rest = body.slice(newline + 1);
  if (!first || first.length >= 60 || !rest.trim()) return null;
  return { title: first, body: rest.replace(/^\s*\n/, '') };
}
