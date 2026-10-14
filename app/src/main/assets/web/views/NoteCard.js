import { html, memo } from '../html.js';
import { t } from '../i18n.js';
import { labelsById, bulk } from '../store.js';
import { noteText } from '../clipboard.js';
import { Icon } from './icons.js';
import { Highlight, IconButton, copyWithFeedback, confirmAction } from './common.js';
import { bodyPreview } from './preview.js';

export function LabelChips({ ids, max = 3 }) {
  if (!ids.length) return null;
  const map = labelsById.value;
  const names = ids.map((id) => map.get(id)?.name).filter(Boolean);
  if (!names.length) return null;
  const shown = names.slice(0, max);
  return html`
    <div class="chips">
      ${shown.map((name) => html`<span class="chip" key=${name}>${name}</span>`)}
      ${names.length > max ? html`<span class="chip chip-more">+${names.length - max}</span>` : null}
    </div>`;
}

export function NoteActions({ note }) {
  const ids = [note.id];
  if (note.state === 'TRASHED') {
    return html`
      <${IconButton} icon="restore" label=${t('restore')} onClick=${() => bulk(ids, 'restore', {}, { clear: false })} />
      <${IconButton} icon="deleteForever" label=${t('deleteForever')} onClick=${async () => {
        if (await confirmAction(t('confirmDeleteForever', 1), t('deleteForever'))) bulk(ids, 'deleteForever', {}, { clear: false });
      }} />`;
  }
  return html`
    <${IconButton} icon="copy" label=${t('copy')} onClick=${() => copyWithFeedback(noteText(note))} />
    ${note.state === 'ARCHIVED'
      ? html`<${IconButton} icon="unarchive" label=${t('unarchive')} onClick=${() => bulk(ids, 'unarchive', {}, { clear: false })} />`
      : html`<${IconButton} icon="archive" label=${t('archiveAction')} onClick=${() => bulk(ids, 'archive', {}, { clear: false })} />`}
    <${IconButton} icon="trash" label=${t('trashAction')} onClick=${() => bulk(ids, 'trash', {}, { clear: false })} />`;
}

const preventShiftSelect = (event) => { if (event.shiftKey) event.preventDefault(); };

function NoteCardImpl({ note, selected, selecting, terms, onActivate, onSelect }) {
  const preview = bodyPreview(note.body, terms);
  const empty = !note.title.trim() && !note.body.trim();
  const pending = note.pending;

  const onClick = (event) => {
    if (pending || event.target.closest('button, a, input')) return;
    if (selecting || event.shiftKey || event.ctrlKey || event.metaKey) onSelect(note.id, event.shiftKey);
    else onActivate(note.id, event.currentTarget);
  };
  const onKeyDown = (event) => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'Enter') { event.preventDefault(); onActivate(note.id, event.currentTarget); }
    if (event.key === ' ') { event.preventDefault(); onSelect(note.id, event.shiftKey); }
  };

  return html`
    <article class=${`card note-${note.color} ${selected ? 'is-selected' : ''} ${pending ? 'is-pending' : ''}`}
      data-note-id=${note.id} tabindex=${pending ? -1 : 0} aria-label=${note.title || t('openNote')}
      aria-selected=${selecting ? String(!!selected) : undefined}
      onMouseDown=${preventShiftSelect} onClick=${onClick} onKeyDown=${onKeyDown}>
      <button type="button" class="card-check" aria-label=${t('select')} aria-pressed=${String(!!selected)}
        tabindex="-1" disabled=${pending} onClick=${(e) => onSelect(note.id, e.shiftKey)}>
        <${Icon} name="checkCircle" size=${22} />
      </button>
      ${note.state !== 'TRASHED' && !pending ? html`
        <button type="button" class=${`card-pin ${note.pinned ? 'is-on' : ''}`} aria-label=${note.pinned ? t('unpin') : t('pin')}
          title=${note.pinned ? t('unpin') : t('pin')} aria-pressed=${String(note.pinned)}
          onClick=${() => bulk([note.id], note.pinned ? 'unpin' : 'pin', {}, { clear: false })}>
          <${Icon} name=${note.pinned ? 'pinFilled' : 'pin'} size=${20} />
        </button>` : null}
      ${note.title.trim() ? html`<h3 class="card-title"><${Highlight} text=${note.title} terms=${terms} /></h3>` : null}
      ${preview.text.trim() ? html`
        <div class=${`card-body ${preview.clipped ? 'is-clipped' : ''}`}><${Highlight} text=${preview.text} terms=${terms} /></div>` : null}
      ${empty ? html`<div class="card-body muted">${t('emptyNote')}</div>` : null}
      <${LabelChips} ids=${note.labelIds} />
      <div class="card-actions">${pending ? null : html`<${NoteActions} note=${note} />`}</div>
    </article>`;
}

export const NoteCard = memo(NoteCardImpl);
