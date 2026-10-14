import { useRef, useEffect, useMemo, useLayoutEffect } from 'preact/hooks';
import { html } from '../html.js';
import { t, formatDateTime } from '../i18n.js';
import {
  getNote, notesVersion, openNote, bulk, composerColor, composerLabels, labelsById,
} from '../store.js';
import { acquireSession, releaseSession } from '../sessions.js';
import { noteText } from '../clipboard.js';
import { Icon } from './icons.js';
import {
  IconButton, Popover, ColorPicker, LabelPicker, copyWithFeedback, confirmAction, useAutosize, titleSuggestion,
} from './common.js';

function StatusIndicator({ session }) {
  const status = session.status.value;
  const map = {
    editing: ['dot', ''],
    saving: ['sync', t('saving')],
    saved: ['done', t('saved')],
    pending: ['cloudOff', t('savePending')],
    gone: ['error', t('noteNotFound')],
  };
  const entry = map[status];
  if (!entry) return html`<span class="save-status" aria-live="polite"></span>`;
  return html`
    <span class=${`save-status is-${status}`} aria-live="polite">
      ${entry[0] === 'dot' ? html`<span class="status-dot"></span>` : html`<${Icon} name=${entry[0]} size=${16} />`}
      <span>${entry[1]}</span>
    </span>`;
}

function ConflictStrip({ session }) {
  if (!session.conflict.value) return null;
  return html`
    <div class="conflict" role="alert">
      <${Icon} name="sync" size=${20} />
      <div class="conflict-text">
        <strong>${t('conflictTitle')}</strong>
        <span>${t('conflictBody')}</span>
      </div>
      <div class="conflict-actions">
        <button type="button" class="tonal-btn" onClick=${() => session.takePhone()}>${t('takePhone')}</button>
        <button type="button" class="filled-btn" onClick=${() => session.keepMine()}>${t('keepMine')}</button>
      </div>
    </div>`;
}

function EditorLabels({ ids, onRemove, readOnly }) {
  const map = labelsById.value;
  const labels = ids.map((id) => map.get(id)).filter(Boolean);
  if (!labels.length) return null;
  return html`
    <div class="chips editor-chips">
      ${labels.map((label) => html`
        <span class="chip" key=${label.id}>
          ${label.name}
          ${readOnly ? null : html`
            <button type="button" class="chip-remove" aria-label=${t('labelChipRemove', label.name)} onClick=${() => onRemove(label.id)}>
              <${Icon} name="close" size=${14} />
            </button>`}
        </span>`)}
    </div>`;
}

export function Editor({ open, variant, onClose, onNext }) {
  const session = useMemo(() => acquireSession(open.id), [open.key]);
  const titleRef = useRef(null);
  const bodyRef = useRef(null);
  const rootRef = useRef(null);

  useEffect(() => {
    session.onCreated = (id) => {
      if (openNote.value?.key === open.key) openNote.value = { ...openNote.value, id };
    };
    return () => {
      session.onCreated = null;
      releaseSession(session);
    };
  }, [session]);

  notesVersion.value;
  const id = session.id;
  const note = id != null ? getNote(id) : null;
  const isDraft = id == null;
  const readOnly = note?.state === 'TRASHED';
  const title = session.title.value;
  const body = session.body.value;
  const color = isDraft ? composerColor.value : note?.color ?? 'DEFAULT';
  const labelIds = isDraft ? composerLabels.value : note?.labelIds ?? [];
  const suggestion = readOnly ? null : titleSuggestion(title, body);

  useAutosize(titleRef, title);
  useAutosize(bodyRef, body, variant === 'dialog');

  useLayoutEffect(() => {
    if (variant === 'pane' && !isDraft) return;
    const focusBody = () => {
      const target = bodyRef.current;
      if (!target || readOnly) return;
      target.focus({ preventScroll: true });
      if (!isDraft) target.setSelectionRange(target.value.length, target.value.length);
    };
    if (variant !== 'dialog') {
      focusBody();
      return;
    }
    // The host dialog's showModal() runs after this and moves focus to its first control.
    const frame = requestAnimationFrame(focusBody);
    return () => cancelAnimationFrame(frame);
  }, [open.key]);

  const edit = (nextTitle, nextBody) => session.edit(nextTitle, nextBody);
  const ids = id != null ? [id] : [];

  const pickColor = (next) => {
    if (isDraft) composerColor.value = next;
    else bulk(ids, 'color', { color: next }, { clear: false });
  };
  const toggleLabel = (labelId, add) => {
    if (isDraft) {
      composerLabels.value = add ? [...composerLabels.value, labelId] : composerLabels.value.filter((x) => x !== labelId);
    } else {
      bulk(ids, add ? 'addLabel' : 'removeLabel', { labelId }, { clear: false });
    }
  };
  const copy = () => copyWithFeedback(noteText({ title: session.title.value, body: session.body.value }));

  const onKeyDown = (event) => {
    const mod = event.ctrlKey || event.metaKey;
    if (event.key === 'Escape') {
      if (event.target.closest?.('[popover]')) return;
      event.preventDefault();
      event.stopPropagation();
      onClose();
    } else if (mod && event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      session.flush();
      onNext?.();
    } else if (mod && event.shiftKey && (event.key === 'C' || event.key === 'c')) {
      event.preventDefault();
      event.stopPropagation();
      copy();
    }
  };

  if (!isDraft && !note) {
    return html`<div class="editor editor-gone"><p class="muted">${t('noteNotFound')}</p></div>`;
  }

  const archiveButton = note?.state === 'ARCHIVED'
    ? html`<${IconButton} icon="unarchive" label=${t('unarchive')} onClick=${() => bulk(ids, 'unarchive', {}, { clear: false })} />`
    : html`<${IconButton} icon="archive" label=${t('archiveAction')} onClick=${() => { bulk(ids, 'archive', {}, { clear: false }); if (variant !== 'pane') onClose(); }} />`;

  return html`
    <div class=${`editor editor-${variant} note-${color}`} ref=${rootRef} onKeyDown=${onKeyDown}>
      <${ConflictStrip} session=${session} />
      ${readOnly ? html`<div class="editor-notice"><${Icon} name="trash" size=${18} /> ${t('trashReadOnly')}</div>` : null}
      <div class="editor-scroll">
        <div class="editor-head">
          <textarea ref=${titleRef} class="editor-title" rows="1" placeholder=${t('title')} aria-label=${t('title')}
            value=${title} readOnly=${readOnly} maxlength="1000"
            onInput=${(e) => edit(e.currentTarget.value.replace(/\n/g, ' '), session.body.value)}
            onKeyDown=${(e) => { if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey) { e.preventDefault(); bodyRef.current?.focus(); } }}></textarea>
          ${note && !readOnly ? html`
            <${IconButton} icon=${note.pinned ? 'pinFilled' : 'pin'} label=${note.pinned ? t('unpin') : t('pin')}
              active=${note.pinned} onClick=${() => bulk(ids, note.pinned ? 'unpin' : 'pin', {}, { clear: false })} />` : null}
        </div>
        ${suggestion ? html`
          <button type="button" class="suggest-chip" onClick=${() => edit(suggestion.title, suggestion.body)}>
            <${Icon} name="title" size=${16} /> ${t('makeTitle')}: <span class="suggest-preview">${suggestion.title}</span>
          </button>` : null}
        <textarea ref=${bodyRef} class="editor-body" placeholder=${t('bodyPlaceholder')} aria-label=${t('bodyPlaceholder')}
          value=${body} readOnly=${readOnly}
          onInput=${(e) => edit(session.title.value, e.currentTarget.value)}></textarea>
        <${EditorLabels} ids=${labelIds} readOnly=${readOnly} onRemove=${(labelId) => toggleLabel(labelId, false)} />
        ${note ? html`<div class="editor-meta">${t('edited', formatDateTime(note.updatedAt))}</div>` : null}
      </div>
      <div class="editor-toolbar">
        ${readOnly ? html`
          <${IconButton} icon="restore" label=${t('restore')} onClick=${() => bulk(ids, 'restore', {}, { clear: false })} />
          <${IconButton} icon="deleteForever" label=${t('deleteForever')} onClick=${async () => {
            if (await confirmAction(t('confirmDeleteForever', 1), t('deleteForever'))) bulk(ids, 'deleteForever', {}, { clear: false });
          }} />` : html`
          <${Popover} icon="palette" label=${t('color')} class="color-popover">
            ${(close) => html`<${ColorPicker} value=${color} onPick=${(c) => { pickColor(c); close(); }} />`}
          <//>
          <${Popover} icon="label" label=${t('labels')} class="label-popover">
            <${LabelPicker} state=${(labelId) => labelIds.includes(labelId)} onToggle=${toggleLabel} />
          <//>
          ${note ? archiveButton : null}
          ${note ? html`<${IconButton} icon="trash" label=${t('trashAction')} onClick=${() => { bulk(ids, 'trash', {}, { clear: false }); if (variant !== 'pane') onClose(); }} />` : null}
          <${IconButton} icon="copy" label=${`${t('copy')} (Ctrl+Shift+C)`} onClick=${copy} />`}
        <span class="toolbar-spacer"></span>
        <${StatusIndicator} session=${session} />
        ${variant !== 'pane' || isDraft ? html`<button type="button" class="text-btn" onClick=${onClose}>${t('close')}</button>` : null}
      </div>
    </div>`;
}

export function EditorEmpty({ onNew }) {
  return html`
    <div class="editor-empty">
      <${Icon} name="edit" size=${48} />
      <button type="button" class="filled-btn" onClick=${onNew}><${Icon} name="add" size=${20} /> ${t('newNote')}</button>
      <p class="muted small">Ctrl+N</p>
    </div>`;
}
