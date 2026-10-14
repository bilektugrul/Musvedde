import { useEffect, useRef } from 'preact/hooks';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { snack, dismissSnack, connection } from '../store.js';
import { unsavedCount } from '../sessions.js';
import { Icon } from './icons.js';
import { IconButton } from './common.js';

export function Snackbar() {
  const current = snack.value;
  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(() => dismissSnack(current.key), current.error ? 7000 : 5000);
    return () => clearTimeout(timer);
  }, [current?.key]);
  return html`
    <div class="snackbar-host" aria-live="polite">
      ${current ? html`
        <div class=${`snackbar ${current.error ? 'is-error' : ''}`} key=${current.key} role=${current.error ? 'alert' : 'status'}>
          <span class="snackbar-text">${current.text}</span>
          ${current.actionLabel ? html`
            <button type="button" class="snackbar-action" onClick=${() => { dismissSnack(current.key); current.onAction(); }}>
              ${current.actionLabel}
            </button>` : null}
          <${IconButton} icon="close" label=${t('close')} class="snackbar-close" size=${18} onClick=${() => dismissSnack(current.key)} />
        </div>` : null}
    </div>`;
}

export function ConnectionBanner() {
  if (connection.value !== 'offline') return null;
  const pending = unsavedCount.value;
  return html`
    <div class="banner" role="alert">
      <${Icon} name="cloudOff" size=${20} />
      <span><strong>${t('disconnected')}</strong> · ${t('reconnecting')}</span>
      ${pending ? html`<span class="banner-pending">${t('offlinePending', pending)}</span>` : null}
    </div>`;
}

export function ShortcutsDialog({ open, onClose }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return html`
    <dialog ref=${ref} class="shortcuts-dialog" onClose=${onClose}
      onClick=${(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div class="dialog-inner">
        <h2 class="dialog-title">${t('shortcuts')}</h2>
        <dl class="shortcut-list">
          ${t('shortcutList').map(([keys, text]) => html`<div key=${keys}><dt><kbd>${keys}</kbd></dt><dd>${text}</dd></div>`)}
        </dl>
        <div class="dialog-actions"><button type="button" class="text-btn" onClick=${onClose}>${t('done')}</button></div>
      </div>
    </dialog>`;
}
