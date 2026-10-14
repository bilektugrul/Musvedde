import { useEffect, useState } from 'preact/hooks';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { uploadKeepZip, MAX_IMPORT_BYTES } from '../api.js';
import { importState, showSnack } from '../store.js';
import { pull } from '../sync.js';
import { Icon } from './icons.js';

const isZip = (file) => /\.zip$/i.test(file.name) || /zip/.test(file.type);

export async function importFile(file) {
  if (importState.value && importState.value.phase !== 'done') return;
  if (!isZip(file)) {
    showSnack({ text: t('importNotZip'), error: true });
    return;
  }
  if (file.size > MAX_IMPORT_BYTES) {
    showSnack({ text: t('importTooLarge'), error: true });
    return;
  }
  importState.value = { phase: 'upload', progress: 0, name: file.name };
  try {
    const result = await uploadKeepZip(file, (progress) => {
      importState.value = { phase: progress >= 1 ? 'processing' : 'upload', progress, name: file.name };
    });
    importState.value = null;
    showSnack({ text: t('importResult', result?.added ?? 0, result?.skipped ?? 0) });
    pull();
  } catch (error) {
    importState.value = null;
    if (error.status === 401) return;
    const text = error.code === 'too_large' ? t('importTooLarge')
      : error.code === 'unreadable' ? t('importUnreadable')
        : error.isNetwork ? t('disconnected') : t('importUnreadable');
    showSnack({ text, error: true });
  }
}

export function pickImportFile() {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.zip,application/zip';
  input.onchange = () => { if (input.files?.[0]) importFile(input.files[0]); };
  input.click();
}

const hasFiles = (event) => [...(event.dataTransfer?.types ?? [])].includes('Files');

export function DropOverlay() {
  const [active, setActive] = useState(false);
  useEffect(() => {
    let depth = 0;
    const enter = (e) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      depth++;
      setActive(true);
    };
    const over = (e) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = 'copy';
    };
    const leave = (e) => {
      if (!hasFiles(e)) return;
      depth = Math.max(0, depth - 1);
      if (!depth) setActive(false);
    };
    const drop = (e) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      depth = 0;
      setActive(false);
      const file = e.dataTransfer.files?.[0];
      if (file) importFile(file);
    };
    window.addEventListener('dragenter', enter);
    window.addEventListener('dragover', over);
    window.addEventListener('dragleave', leave);
    window.addEventListener('drop', drop);
    return () => {
      window.removeEventListener('dragenter', enter);
      window.removeEventListener('dragover', over);
      window.removeEventListener('dragleave', leave);
      window.removeEventListener('drop', drop);
    };
  }, []);

  if (!active) return null;
  return html`
    <div class="drop-overlay" aria-live="assertive">
      <div class="drop-card">
        <${Icon} name="upload" size=${48} />
        <div class="drop-title">${t('importDrop')}</div>
        <div class="muted">${t('importDropHint')}</div>
      </div>
    </div>`;
}

export function ImportProgress() {
  const state = importState.value;
  if (!state) return null;
  const pct = Math.round(state.progress * 100);
  const processing = state.phase === 'processing';
  return html`
    <div class="import-progress" role="status" aria-live="polite">
      <div class="import-row">
        <${Icon} name="upload" size=${20} />
        <div class="import-text">
          <div class="import-title">${processing ? t('importProcessing') : t('importUploading')}</div>
          <div class="muted small ellipsis">${state.name}</div>
        </div>
        ${processing ? null : html`<span class="import-pct">${pct}%</span>`}
      </div>
      <div class=${`progress ${processing ? 'is-indeterminate' : ''}`} role="progressbar"
        aria-valuemin="0" aria-valuemax="100" aria-valuenow=${processing ? undefined : pct}>
        <div class="progress-bar" style=${processing ? undefined : `width:${pct}%`}></div>
      </div>
    </div>`;
}
