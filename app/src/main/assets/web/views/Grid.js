import { useRef, useState, useMemo, useEffect, useLayoutEffect, useCallback } from 'preact/hooks';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { NoteCard } from './NoteCard.js';
import { bodyPreview, PREVIEW_LINES } from './preview.js';

const GAP = 16;
const MIN_COLUMN = 232;
const MAX_COLUMN = 320;
const HEADER_HEIGHT = 40;
const OVERSCAN = 800;

const heights = new WeakMap(); // note -> { w, q, h }

function estimateHeight(note, width, terms) {
  const charsPerLine = Math.max(12, Math.floor((width - 32) / 7.6));
  let h = 16 + 44; // padding + action row
  const title = note.title.trim();
  if (title) h += Math.min(3, Math.ceil(title.length / (charsPerLine * 0.85))) * 24 + 6;
  const { text } = bodyPreview(note.body, terms);
  if (text.trim()) {
    let lines = 0;
    for (const line of text.split('\n')) {
      lines += Math.max(1, Math.ceil(line.length / charsPerLine));
      if (lines >= PREVIEW_LINES) { lines = PREVIEW_LINES; break; }
    }
    h += lines * 20;
  }
  if (!title && !text.trim()) h += 20;
  if (note.labelIds.length) h += 32;
  return h;
}

function heightOf(note, width, termsKey, terms) {
  const cached = heights.get(note);
  if (cached && cached.w === width && cached.q === termsKey) return cached.h;
  return estimateHeight(note, width, terms);
}

function computeLayout(notes, width, grouped, terms, termsKey) {
  const columns = Math.max(1, Math.floor((width + GAP) / (MIN_COLUMN + GAP)));
  const colWidth = Math.floor(Math.min(MAX_COLUMN, (width - GAP * (columns - 1)) / columns));
  const used = columns * colWidth + GAP * (columns - 1);
  const offset = Math.max(0, Math.floor((width - used) / 2));
  const items = [];
  const cols = new Array(columns).fill(0);
  let pinnedCount = 0;
  while (pinnedCount < notes.length && notes[pinnedCount].pinned) pinnedCount++;
  const showHeaders = grouped && pinnedCount > 0;

  const place = (list, header) => {
    if (header) {
      const top = Math.max(...cols);
      items.push({ key: `h-${header}`, header, top, height: HEADER_HEIGHT });
      cols.fill(top + HEADER_HEIGHT);
    }
    for (const note of list) {
      let c = 0;
      for (let i = 1; i < columns; i++) if (cols[i] < cols[c] - 1) c = i;
      const height = heightOf(note, colWidth, termsKey, terms);
      items.push({ key: note.id, note, top: cols[c], left: offset + c * (colWidth + GAP), height });
      cols[c] += height + GAP;
    }
    if (list.length) {
      const bottom = Math.max(...cols) + 8;
      cols.fill(bottom);
    }
  };

  if (showHeaders) {
    place(notes.slice(0, pinnedCount), 'pinnedHeader');
    if (pinnedCount < notes.length) place(notes.slice(pinnedCount), 'othersHeader');
  } else {
    place(notes, null);
  }
  return { items, colWidth, offset, total: Math.max(0, Math.max(...cols)) };
}

export function Grid({ notes, scrollerRef, grouped, terms, selection, openId, onActivate, onSelect }) {
  const gridRef = useRef(null);
  const [width, setWidth] = useState(0);
  const [view, setView] = useState({ top: 0, height: window.innerHeight });
  const [measured, setMeasured] = useState(0);
  const [animate, setAnimate] = useState(false);
  const termsKey = terms ? terms.join('\u0000') : '';
  const stateRef = useRef({});

  useLayoutEffect(() => {
    const grid = gridRef.current;
    const ro = new ResizeObserver(([entry]) => setWidth(Math.floor(entry.contentRect.width)));
    ro.observe(grid);
    setWidth(Math.floor(grid.clientWidth));
    return () => ro.disconnect();
  }, []);

  const updateView = useCallback(() => {
    const scroller = scrollerRef.current;
    const grid = gridRef.current;
    if (!scroller || !grid) return;
    const top = scroller.getBoundingClientRect().top - grid.getBoundingClientRect().top;
    const height = scroller.clientHeight;
    setView((prev) => (Math.abs(prev.top - top) < 1 && prev.height === height ? prev : { top, height }));
  }, []);

  useEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    let frame = 0;
    const schedule = () => { if (!frame) frame = requestAnimationFrame(() => { frame = 0; updateView(); }); };
    scroller.addEventListener('scroll', schedule, { passive: true });
    const ro = new ResizeObserver(schedule);
    ro.observe(scroller);
    if (scroller.firstElementChild) ro.observe(scroller.firstElementChild);
    updateView();
    return () => {
      cancelAnimationFrame(frame);
      scroller.removeEventListener('scroll', schedule);
      ro.disconnect();
    };
  }, []);

  const layout = useMemo(
    () => (width > 0 ? computeLayout(notes, width, grouped, terms, termsKey) : { items: [], colWidth: 0, total: 0 }),
    [notes, width, grouped, termsKey, measured],
  );
  stateRef.current = { layout, termsKey };

  const measureRO = useMemo(() => {
    let frame = 0;
    return new ResizeObserver((entries) => {
      const { layout: current, termsKey: q } = stateRef.current;
      let changed = false;
      for (const entry of entries) {
        const el = entry.target;
        const note = el.__note;
        if (!note || !el.isConnected) continue;
        const h = Math.round(entry.borderBoxSize?.[0]?.blockSize ?? el.offsetHeight);
        if (!h) continue;
        const cached = heights.get(note);
        if (cached && cached.w === current.colWidth && cached.q === q && cached.h === h) continue;
        heights.set(note, { w: current.colWidth, q, h });
        changed = true;
      }
      if (changed && !frame) frame = requestAnimationFrame(() => { frame = 0; setMeasured((m) => m + 1); });
    });
  }, []);
  useEffect(() => () => measureRO.disconnect(), []);
  useEffect(() => {
    const timer = setTimeout(() => setAnimate(true), 700);
    return () => clearTimeout(timer);
  }, []);

  useLayoutEffect(updateView);

  const from = view.top - OVERSCAN;
  const to = view.top + view.height + OVERSCAN;
  const visible = [];
  for (const item of layout.items) {
    if (item.top + item.height >= from && item.top <= to) visible.push(item);
  }
  const selecting = selection.size > 0;

  return html`
    <div class=${`grid ${animate ? 'is-animated' : ''}`} ref=${gridRef} style=${`height:${layout.total}px`} role="list">
      ${visible.map((item) => item.header
        ? html`<div class="grid-header" key=${item.key} style=${`transform:translateY(${item.top}px);padding-inline-start:${layout.offset + 4}px`}>${t(item.header)}</div>`
        : html`
          <div class=${`grid-item ${item.note.id === openId ? 'is-open' : ''}`} key=${item.key} role="listitem"
            style=${`transform:translate(${item.left}px,${item.top}px);width:${layout.colWidth}px`}
            ref=${(el) => {
              if (!el) return;
              const key = `${layout.colWidth}|${termsKey}`;
              if (el.__note !== item.note || el.__key !== key) {
                el.__note = item.note;
                el.__key = key;
                measureRO.unobserve(el);
                measureRO.observe(el);
              }
            }}>
            <${NoteCard} note=${item.note} selected=${selection.has(item.note.id)} selecting=${selecting}
              terms=${terms} onActivate=${onActivate} onSelect=${onSelect} />
          </div>`)}
    </div>`;
}
