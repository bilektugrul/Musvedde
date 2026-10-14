import { useRef, useState, useEffect, useLayoutEffect } from 'preact/hooks';
import { html, memo } from '../html.js';
import { t, formatDateTime } from '../i18n.js';
import { noteText } from '../clipboard.js';
import { Icon } from './icons.js';
import { Highlight, copyWithFeedback } from './common.js';
import { rowTitle, rowSnippet } from './preview.js';
import { firstMatchIndex } from '../search.js';

export const ROW_HEIGHT = 76;
const HEADER_HEIGHT = 36;
const OVERSCAN = 10;

const preventShiftSelect = (event) => { if (event.shiftKey) event.preventDefault(); };

function snippetFor(note, terms) {
  const snippet = rowSnippet(note);
  if (!terms) return snippet;
  const flat = note.body.replace(/\s+/g, ' ');
  const hit = firstMatchIndex(flat, terms);
  if (hit > 60) return `…${flat.slice(hit - 30, hit + 170)}`;
  return snippet;
}

function NoteRowImpl({ note, selected, selecting, open, terms, onActivate, onSelect }) {
  const title = rowTitle(note);
  const snippet = snippetFor(note, terms);
  const onClick = (event) => {
    if (note.pending || event.target.closest('button')) return;
    if (selecting || event.shiftKey || event.ctrlKey || event.metaKey) onSelect(note.id, event.shiftKey);
    else onActivate(note.id, event.currentTarget);
  };
  const onKeyDown = (event) => {
    if (event.target !== event.currentTarget) return;
    if (event.key === 'Enter') { event.preventDefault(); onActivate(note.id, event.currentTarget); }
    if (event.key === ' ') { event.preventDefault(); onSelect(note.id, event.shiftKey); }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const rows = [...event.currentTarget.closest('.list').querySelectorAll('.row:not(.is-pending)')];
      const row = rows[rows.indexOf(event.currentTarget) + (event.key === 'ArrowDown' ? 1 : -1)];
      if (row) { row.focus(); onActivate(Number(row.dataset.noteId), row); }
    }
  };
  return html`
    <div class=${`row note-${note.color} ${selected ? 'is-selected' : ''} ${open ? 'is-open' : ''} ${note.pending ? 'is-pending' : ''}`}
      data-note-id=${note.id} tabindex=${note.pending ? -1 : 0} onMouseDown=${preventShiftSelect} onClick=${onClick} onKeyDown=${onKeyDown}
      aria-current=${open ? 'true' : undefined}>
      <span class="row-swatch" aria-hidden="true"></span>
      <button type="button" class="row-check" aria-label=${t('select')} aria-pressed=${String(!!selected)} tabindex="-1"
        onClick=${(e) => onSelect(note.id, e.shiftKey)}>
        <${Icon} name="checkCircle" size=${22} />
      </button>
      <div class="row-text">
        <div class="row-title">
          ${note.pinned ? html`<${Icon} name="pinFilled" size=${14} class="row-pin" />` : null}
          <span>${title ? html`<${Highlight} text=${title} terms=${terms} />` : html`<span class="muted">${t('emptyNote')}</span>`}</span>
        </div>
        <div class="row-snippet"><${Highlight} text=${snippet} terms=${terms} /></div>
      </div>
      <div class="row-meta">
        <time class="row-time">${formatDateTime(note.updatedAt)}</time>
        <button type="button" class="icon-btn row-copy" aria-label=${t('copy')} title=${t('copy')}
          onClick=${() => copyWithFeedback(noteText(note))}>
          <${Icon} name="copy" size=${18} />
        </button>
      </div>
    </div>`;
}

const NoteRow = memo(NoteRowImpl);

export function List({ notes, grouped, terms, selection, openId, onActivate, onSelect }) {
  const scrollerRef = useRef(null);
  const [view, setView] = useState({ top: 0, height: window.innerHeight });

  useEffect(() => {
    const el = scrollerRef.current;
    let frame = 0;
    const update = () => { frame = 0; setView({ top: el.scrollTop, height: el.clientHeight }); };
    const schedule = () => { if (!frame) frame = requestAnimationFrame(update); };
    el.addEventListener('scroll', schedule, { passive: true });
    const ro = new ResizeObserver(schedule);
    ro.observe(el);
    update();
    return () => { cancelAnimationFrame(frame); el.removeEventListener('scroll', schedule); ro.disconnect(); };
  }, []);

  let pinnedCount = 0;
  while (pinnedCount < notes.length && notes[pinnedCount].pinned) pinnedCount++;
  const headers = grouped && pinnedCount > 0;
  const rows = [];
  let y = 0;
  const pushHeader = (label) => { rows.push({ key: `h-${label}`, header: label, top: y }); y += HEADER_HEIGHT; };
  if (headers) pushHeader('pinnedHeader');
  notes.forEach((note, i) => {
    if (headers && i === pinnedCount) pushHeader('othersHeader');
    rows.push({ key: note.id, note, top: y });
    y += ROW_HEIGHT;
  });

  // Keyboard-selected row must stay mounted and visible.
  useLayoutEffect(() => {
    if (openId == null) return;
    const el = scrollerRef.current;
    const row = rows.find((r) => r.note?.id === openId);
    if (!row) return;
    if (row.top < el.scrollTop) el.scrollTop = row.top;
    else if (row.top + ROW_HEIGHT > el.scrollTop + el.clientHeight) el.scrollTop = row.top + ROW_HEIGHT - el.clientHeight;
  }, [openId]);

  const from = view.top - OVERSCAN * ROW_HEIGHT;
  const to = view.top + view.height + OVERSCAN * ROW_HEIGHT;
  const selecting = selection.size > 0;
  let start = 0;
  let lo = 0;
  let hi = rows.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (rows[mid].top + ROW_HEIGHT < from) lo = mid + 1;
    else hi = mid;
  }
  start = lo;
  const visible = [];
  for (let i = start; i < rows.length && rows[i].top <= to; i++) visible.push(rows[i]);

  return html`
    <div class="list-scroll" ref=${scrollerRef}>
      <div class="list" style=${`height:${y}px`} role="list">
        ${visible.map((row) => row.header
          ? html`<div class="list-header" key=${row.key} style=${`transform:translateY(${row.top}px)`}>${t(row.header)}</div>`
          : html`
            <div class="list-item" key=${row.key} role="listitem" style=${`transform:translateY(${row.top}px)`}>
              <${NoteRow} note=${row.note} selected=${selection.has(row.note.id)} selecting=${selecting}
                open=${row.note.id === openId} terms=${terms} onActivate=${onActivate} onSelect=${onSelect} />
            </div>`)}
      </div>
    </div>`;
}
