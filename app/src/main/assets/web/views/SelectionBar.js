import { html } from '../html.js';
import { t } from '../i18n.js';
import { selection, selectedNotes, clearSelection, bulk } from '../store.js';
import { notesText } from '../clipboard.js';
import { IconButton, Popover, ColorPicker, LabelPicker, copyWithFeedback, confirmAction } from './common.js';

export function SelectionBar() {
  const ids = [...selection.value];
  if (!ids.length) return null;
  const notes = selectedNotes.value;
  const inTrash = notes.length > 0 && notes.every((n) => n.state === 'TRASHED');
  const allPinned = notes.length > 0 && notes.every((n) => n.pinned);
  const allArchived = notes.length > 0 && notes.every((n) => n.state === 'ARCHIVED');
  const colors = new Set(notes.map((n) => n.color));
  const labelState = (labelId) => {
    let count = 0;
    for (const note of notes) if (note.labelIds.includes(labelId)) count++;
    return count === 0 ? false : count === notes.length ? true : 'mixed';
  };

  return html`
    <div class="selection-bar" role="toolbar" aria-label=${t('selectedN', ids.length)}>
      <${IconButton} icon="close" label=${t('clearSelection')} onClick=${clearSelection} />
      <span class="selection-count" aria-live="polite">${t('selectedN', ids.length)}</span>
      <span class="toolbar-spacer"></span>
      ${inTrash ? html`
        <${IconButton} icon="restore" label=${t('restore')} onClick=${() => bulk(ids, 'restore')} />
        <${IconButton} icon="deleteForever" label=${t('deleteForever')} onClick=${async () => {
          if (await confirmAction(t('confirmDeleteForever', ids.length), t('deleteForever'))) bulk(ids, 'deleteForever');
        }} />` : html`
        <${IconButton} icon=${allPinned ? 'pinFilled' : 'pin'} label=${allPinned ? t('unpin') : t('pin')}
          onClick=${() => bulk(ids, allPinned ? 'unpin' : 'pin', {}, { clear: false })} />
        <${Popover} icon="palette" label=${t('color')} class="color-popover" align="end">
          ${(close) => html`<${ColorPicker} value=${colors.size === 1 ? [...colors][0] : null}
            onPick=${(color) => { bulk(ids, 'color', { color }, { clear: false }); close(); }} />`}
        <//>
        <${Popover} icon="label" label=${`${t('addLabel')} / ${t('removeLabel')}`} class="label-popover" align="end">
          <${LabelPicker} state=${labelState}
            onToggle=${(labelId, add) => bulk(ids, add ? 'addLabel' : 'removeLabel', { labelId }, { clear: false })} />
        <//>
        ${allArchived
          ? html`<${IconButton} icon="unarchive" label=${t('unarchive')} onClick=${() => bulk(ids, 'unarchive')} />`
          : html`<${IconButton} icon="archive" label=${t('archiveAction')} onClick=${() => bulk(ids, 'archive')} />`}
        <${IconButton} icon="trash" label=${t('trashAction')} onClick=${() => bulk(ids, 'trash')} />
        <${IconButton} icon="copy" label=${t('copy')} onClick=${() => copyWithFeedback(notesText(notes), notes.length)} />`}
    </div>`;
}
