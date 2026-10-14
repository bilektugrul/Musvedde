import { useRef, useState, useLayoutEffect } from 'preact/hooks';
import { computed, signal } from '@preact/signals';
import { html } from '../html.js';
import { t } from '../i18n.js';
import {
  section, goTo, labels, notesVersion, allNotes, drawerOpen, createLabel, renameLabel, deleteLabel, appVersion,
  showError, showSnack,
} from '../store.js';
import { Icon } from './icons.js';
import { IconButton, confirmAction } from './common.js';

const counts = computed(() => {
  notesVersion.value;
  const result = { notes: 0, archive: 0, trash: 0, labels: new Map() };
  for (const note of allNotes()) {
    if (note.state === 'ACTIVE') result.notes++;
    else if (note.state === 'ARCHIVED') result.archive++;
    else result.trash++;
    if (note.state !== 'TRASHED') for (const id of note.labelIds) result.labels.set(id, (result.labels.get(id) ?? 0) + 1);
  }
  return result;
});

export const labelsDialogOpen = signal(false);

function NavItem({ icon, label, active, count, onClick }) {
  return html`
    <button type="button" class=${`nav-item ${active ? 'is-active' : ''}`} aria-current=${active ? 'page' : undefined} onClick=${onClick} title=${label}>
      <${Icon} name=${icon} />
      <span class="nav-label">${label}</span>
      ${count ? html`<span class="nav-count">${count}</span>` : null}
    </button>`;
}

export function Sidebar() {
  const s = section.value;
  const c = counts.value;
  return html`
    <nav class="sidebar-nav" aria-label=${t('appName')}>
      <${NavItem} icon="notes" label=${t('notes')} active=${s.kind === 'notes'} count=${c.notes} onClick=${() => goTo({ kind: 'notes' })} />
      ${labels.value.length ? html`<div class="nav-section">${t('labels')}</div>` : null}
      ${labels.value.map((label) => html`
        <${NavItem} key=${label.id} icon="label" label=${label.name} count=${c.labels.get(label.id)}
          active=${s.kind === 'label' && s.labelId === label.id} onClick=${() => goTo({ kind: 'label', labelId: label.id })} />`)}
      <${NavItem} icon="edit" label=${t('editLabels')} onClick=${() => { drawerOpen.value = false; labelsDialogOpen.value = true; }} />
      <div class="nav-divider"></div>
      <${NavItem} icon="archive" label=${t('archive')} active=${s.kind === 'archive'} count=${c.archive} onClick=${() => goTo({ kind: 'archive' })} />
      <${NavItem} icon="trash" label=${t('trash')} active=${s.kind === 'trash'} count=${c.trash} onClick=${() => goTo({ kind: 'trash' })} />
      ${appVersion.value ? html`<div class="nav-footer">${t('appName')} ${appVersion.value}</div>` : null}
    </nav>`;
}

function LabelRow({ label }) {
  const [value, setValue] = useState(label.name);
  const commit = async () => {
    const name = value.trim();
    if (!name || name === label.name) { setValue(label.name); return; }
    if (!(await renameLabel(label.id, name))) setValue(label.name);
  };
  return html`
    <li class="label-row">
      <${Icon} name="label" size=${20} class="muted" />
      <input type="text" value=${value} aria-label=${`${t('rename')}: ${label.name}`} maxlength="100"
        onInput=${(e) => setValue(e.currentTarget.value)} onBlur=${commit}
        onKeyDown=${(e) => {
          if (e.key === 'Enter') { e.preventDefault(); e.currentTarget.blur(); }
          if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); setValue(label.name); }
        }} />
      <${IconButton} icon="trash" label=${t('deleteLabel')} onClick=${async () => {
        if (await confirmAction(t('confirmDeleteLabel', label.name), t('deleteLabel'))) {
          if (section.value.kind === 'label' && section.value.labelId === label.id) goTo({ kind: 'notes' });
          deleteLabel(label.id);
        }
      }} />
    </li>`;
}

export function LabelsDialog() {
  const ref = useRef(null);
  const [name, setName] = useState('');
  const open = labelsDialogOpen.value;
  useLayoutEffect(() => {
    const dialog = ref.current;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const create = async (event) => {
    event.preventDefault();
    const value = name.trim();
    if (!value) return;
    try {
      await createLabel(value);
      setName('');
    } catch (error) {
      if (error.code === 'exists') showSnack({ text: t('labelExists'), error: true });
      else showError(error);
    }
  };

  return html`
    <dialog ref=${ref} class="labels-dialog" aria-labelledby="labels-title"
      onClose=${() => { labelsDialogOpen.value = false; }}
      onClick=${(e) => { if (e.target === e.currentTarget) labelsDialogOpen.value = false; }}>
      ${open ? html`
        <div class="dialog-inner">
          <h2 id="labels-title" class="dialog-title">${t('editLabels')}</h2>
          <form class="label-create" onSubmit=${create}>
            <${Icon} name="add" size=${20} class="muted" />
            <input type="text" value=${name} placeholder=${t('newLabel')} aria-label=${t('newLabel')} maxlength="100"
              onInput=${(e) => setName(e.currentTarget.value)} autofocus />
            <button type="submit" class="tonal-btn" disabled=${!name.trim()}>${t('create')}</button>
          </form>
          <ul class="label-list">
            ${labels.value.map((label) => html`<${LabelRow} key=${`${label.id}:${label.name}`} label=${label} />`)}
          </ul>
          ${!labels.value.length ? html`<p class="muted pad">${t('noLabels')}</p>` : null}
          <div class="dialog-actions">
            <button type="button" class="text-btn" onClick=${() => { labelsDialogOpen.value = false; }}>${t('done')}</button>
          </div>
        </div>` : null}
    </dialog>`;
}
