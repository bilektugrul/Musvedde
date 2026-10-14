import { signal, computed, effect, batch } from '@preact/signals';
import { api } from './api.js';
import { t } from './i18n.js';
import { parseQuery, filterAsync, searchText } from './search.js';

// ---- app-level state ----

export const screen = signal('loading'); // loading | pair | bye | app
export const appVersion = signal('');
export const connection = signal('online'); // online | offline

let revision = 0;
export const getRevision = () => revision;
export const setRevision = (value) => { revision = value; };

// ---- notes ----

const notes = new Map();
export const notesVersion = signal(0);
let bumpQueued = false;

function bump() {
  if (bumpQueued) return;
  bumpQueued = true;
  queueMicrotask(() => {
    bumpQueued = false;
    notesVersion.value++;
  });
}

export const getNote = (id) => notes.get(id);
export const allNotes = () => notes.values();

const localTextOverrides = new Map();

export function setLocalOverride(id, fn) {
  if (fn) localTextOverrides.set(id, fn);
  else localTextOverrides.delete(id);
}

function withOverride(note) {
  const override = localTextOverrides.get(note.id)?.();
  return override ? { ...note, ...override } : note;
}

export function upsertNote(note, fromServer = true) {
  const next = fromServer ? withOverride(note) : note;
  notes.set(next.id, next);
  bump();
  return next;
}

export function upsertNotes(list) {
  for (const note of list) notes.set(note.id, withOverride(note));
  bump();
}

export function removeNotes(ids) {
  let changed = false;
  for (const id of ids) changed = notes.delete(id) || changed;
  if (changed) bump();
  removedListeners.forEach((fn) => fn(ids));
}

const removedListeners = new Set();
export const onNotesRemoved = (fn) => removedListeners.add(fn);

export function replaceAllNotes(list) {
  const gone = [];
  const incoming = new Set(list.map((n) => n.id));
  for (const id of notes.keys()) if (id > 0 && !incoming.has(id)) gone.push(id);
  for (const [id, note] of notes) if (id > 0) notes.delete(id);
  for (const note of list) notes.set(note.id, withOverride(note));
  bump();
  if (gone.length) removedListeners.forEach((fn) => fn(gone));
}

function warmSearchIndex() {
  const list = [...notes.values()];
  let i = 0;
  const idle = window.requestIdleCallback ?? ((cb) => setTimeout(() => cb({ timeRemaining: () => 10 }), 16));
  const step = (deadline) => {
    while (i < list.length && deadline.timeRemaining() > 1) searchText(list[i++]);
    if (i < list.length) idle(step);
  };
  idle(step);
}

export function loadBootstrap(data) {
  batch(() => {
    replaceAllNotes(data.notes ?? []);
    labels.value = sortLabels(data.labels ?? []);
    appVersion.value = data.appVersion ?? '';
    revision = data.revision ?? 0;
  });
  warmSearchIndex();
}

// ---- labels ----

const collator = new Intl.Collator('tr', { sensitivity: 'base', numeric: true });
const sortLabels = (list) => [...list].sort((a, b) => collator.compare(a.name, b.name));

export const labels = signal([]);
export const labelsById = computed(() => new Map(labels.value.map((l) => [l.id, l])));

export function setLabels(list) {
  labels.value = sortLabels(list);
  const known = labelsById.value;
  if (section.value.kind === 'label' && !known.has(section.value.labelId)) section.value = { kind: 'notes' };
}

// ---- navigation, layout, search ----

export const section = signal({ kind: 'notes' });
export const query = signal('');
export const drawerOpen = signal(false);

const LAYOUTS = ['A', 'B', 'C'];

function readLayout() {
  try {
    const saved = localStorage.getItem('musvedde.layout');
    if (LAYOUTS.includes(saved)) return saved;
  } catch {}
  return 'A';
}

export const layout = signal(readLayout());

export function setLayout(value) {
  if (!LAYOUTS.includes(value) || layout.value === value) return;
  layout.value = value;
  try { localStorage.setItem('musvedde.layout', value); } catch {}
}

export function goTo(next) {
  batch(() => {
    section.value = next;
    query.value = '';
    selection.value = new Set();
    drawerOpen.value = false;
  });
}

const byOrder = (a, b) => (b.pinned - a.pinned) || (b.position - a.position) || (b.id - a.id);

const sectionFilter = (s) => {
  switch (s.kind) {
    case 'archive': return (n) => n.state === 'ARCHIVED';
    case 'trash': return (n) => n.state === 'TRASHED';
    case 'label': return (n) => n.state !== 'TRASHED' && n.labelIds.includes(s.labelId);
    default: return (n) => n.state === 'ACTIVE';
  }
};

export const sectionNotes = computed(() => {
  notesVersion.value;
  const keep = sectionFilter(section.value);
  const list = [];
  for (const note of notes.values()) if (keep(note)) list.push(note);
  return list.sort(byOrder);
});

const searchBase = computed(() => {
  notesVersion.value;
  const list = [];
  for (const note of notes.values()) if (note.state !== 'TRASHED') list.push(note);
  return list.sort(byOrder);
});

export const searchTerms = computed(() => parseQuery(query.value));
export const searchResults = signal(null);

effect(() => {
  const terms = searchTerms.value;
  const base = terms ? searchBase.value : null;
  if (!terms) {
    searchResults.value = null;
    return;
  }
  return filterAsync(base, terms, (result) => { searchResults.value = result; });
});

export const visibleNotes = computed(() => {
  if (searchTerms.value) return searchResults.value ?? [];
  return sectionNotes.value;
});

export const isSearching = computed(() => !!searchTerms.value);

// ---- selection ----

export const selection = signal(new Set());
let selectionAnchor = null;

export function toggleSelected(id, { range = false } = {}) {
  const next = new Set(selection.value);
  const list = visibleNotes.value;
  if (range && selectionAnchor != null) {
    const a = list.findIndex((n) => n.id === selectionAnchor);
    const b = list.findIndex((n) => n.id === id);
    if (a >= 0 && b >= 0) {
      const [from, to] = a < b ? [a, b] : [b, a];
      for (let i = from; i <= to; i++) if (list[i].id > 0) next.add(list[i].id);
      selection.value = next;
      return;
    }
  }
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selectionAnchor = id;
  selection.value = next;
}

export function selectAllVisible() {
  selection.value = new Set(visibleNotes.value.filter((n) => n.id > 0).map((n) => n.id));
}

export function clearSelection() {
  selection.value = new Set();
  selectionAnchor = null;
}

export const selectedNotes = computed(() => {
  notesVersion.value;
  return [...selection.value].map((id) => notes.get(id)).filter(Boolean).sort(byOrder);
});

onNotesRemoved((ids) => {
  if (!ids.some((id) => selection.value.has(id))) return;
  const next = new Set(selection.value);
  ids.forEach((id) => next.delete(id));
  selection.value = next;
});

// ---- open note ----

let openKeySeq = 0;
export const openNote = signal(null); // { id: number|null, key: string, origin?: Element }

export function openNoteById(id, origin = null) {
  openNote.value = { id, key: `n${++openKeySeq}`, origin };
}

export function openBlankNote() {
  openNote.value = { id: null, key: `n${++openKeySeq}`, origin: null };
}

export function closeNote() {
  openNote.value = null;
}

onNotesRemoved((ids) => {
  const open = openNote.value;
  if (open?.id != null && ids.includes(open.id)) {
    openNote.value = null;
    showSnack({ text: t('noteNotFound') });
  }
});

// ---- snackbar ----

let snackSeq = 0;
export const snack = signal(null); // { key, text, actionLabel?, onAction?, error? }

export function showSnack(value) {
  snack.value = { ...value, key: ++snackSeq };
}

export function dismissSnack(key) {
  if (snack.value?.key === key) snack.value = null;
}

export function showError(error) {
  showSnack({ text: error?.isNetwork ? t('networkError') : t('genericError'), error: true });
}

// ---- composer (sticky for the session) ----

export const composerColor = signal('DEFAULT');
export const composerLabels = signal([]);

// ---- import ----

export const importState = signal(null); // { phase, progress, result?, error? }

// ---- bulk actions ----

const INVERSE = { trash: 'restore', restore: 'trash', archive: 'unarchive', unarchive: 'archive', pin: 'unpin', unpin: 'pin' };

function transform(note, action, extra) {
  switch (action) {
    case 'color': return { ...note, color: extra.color };
    case 'addLabel': return note.labelIds.includes(extra.labelId) ? note : { ...note, labelIds: [...note.labelIds, extra.labelId] };
    case 'removeLabel': return { ...note, labelIds: note.labelIds.filter((id) => id !== extra.labelId) };
    case 'pin': return { ...note, pinned: true };
    case 'unpin': return { ...note, pinned: false };
    case 'archive': return { ...note, state: 'ARCHIVED' };
    case 'unarchive': return { ...note, state: 'ACTIVE' };
    case 'trash': return { ...note, state: 'TRASHED', trashedAt: Date.now() };
    case 'restore': return { ...note, state: 'ACTIVE', trashedAt: null };
    default: return note;
  }
}

const UNDO_MESSAGES = { trash: 'trashedN', archive: 'archivedN', unarchive: 'unarchivedN', restore: 'restoredN' };

export async function bulk(ids, action, extra = {}, { undoable = true, clear = true } = {}) {
  const real = ids.filter((id) => id > 0 && notes.has(id));
  if (!real.length) return false;
  const previous = real.map((id) => notes.get(id));
  if (action === 'deleteForever') {
    removeNotes(real);
  } else {
    for (const note of previous) notes.set(note.id, transform(note, action, extra));
    bump();
  }
  if (clear) clearSelection();
  try {
    await api.bulk(real, action, extra);
  } catch (error) {
    for (const note of previous) notes.set(note.id, note);
    bump();
    showError(error);
    return false;
  }
  const message = UNDO_MESSAGES[action];
  if (message && undoable) {
    showSnack({
      text: t(message, real.length),
      actionLabel: t('undo'),
      onAction: () => bulk(real, INVERSE[action], {}, { undoable: false, clear: false }),
    });
  } else if (action === 'deleteForever') {
    showSnack({ text: t('deletedN', real.length) });
  }
  return true;
}

export async function emptyTrash() {
  const trashed = [...notes.values()].filter((n) => n.state === 'TRASHED');
  removeNotes(trashed.map((n) => n.id));
  clearSelection();
  try {
    await api.emptyTrash();
  } catch (error) {
    upsertNotes(trashed);
    showError(error);
  }
}

// ---- creating notes ----

let tempSeq = 0;

function maxPosition() {
  let max = 0;
  for (const note of notes.values()) if (note.position > max) max = note.position;
  return max;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export const isRetryable = (error) => error.isNetwork || error.status >= 500 || error.status === 401;

export async function postWithRetry(send) {
  let delay = 1000;
  while (true) {
    try {
      return await send();
    } catch (error) {
      if (!isRetryable(error)) throw error;
      await sleep(delay);
      delay = Math.min(delay * 2, 30000);
    }
  }
}

export const pendingWrites = signal(0);

export async function createNote({ title, body, color = 'DEFAULT', labelIds = [] }) {
  const tempId = -(++tempSeq);
  const now = Date.now();
  upsertNote({
    id: tempId, title, body, color, pinned: false, state: 'ACTIVE', position: maxPosition() + 1,
    createdAt: now, updatedAt: now, trashedAt: null, labelIds, pending: true,
  }, false);
  pendingWrites.value++;
  try {
    const created = await postWithRetry(() => api.createNote({ title, body, color, labelIds }));
    batch(() => {
      removeNotes([tempId]);
      upsertNote(created);
    });
    return created;
  } catch (error) {
    removeNotes([tempId]);
    const text = title ? `${title}\n${body}` : body;
    showSnack({
      text: t('createFailed'),
      error: true,
      actionLabel: t('copy'),
      onAction: async () => { (await import('./clipboard.js')).copyText(text); },
    });
    return null;
  } finally {
    pendingWrites.value--;
  }
}

// ---- labels CRUD ----

export async function createLabel(name) {
  const label = await api.createLabel(name);
  setLabels([...labels.value.filter((l) => l.id !== label.id), label]);
  return label;
}

export async function renameLabel(id, name) {
  const before = labels.value;
  setLabels(before.map((l) => (l.id === id ? { ...l, name } : l)));
  try {
    const label = await api.renameLabel(id, name);
    setLabels(labels.value.map((l) => (l.id === id ? label : l)));
    return true;
  } catch (error) {
    labels.value = before;
    if (error.code === 'exists') showSnack({ text: t('labelExists'), error: true });
    else showError(error);
    return false;
  }
}

export async function deleteLabel(id) {
  const before = labels.value;
  const touched = [...notes.values()].filter((n) => n.labelIds.includes(id));
  setLabels(before.filter((l) => l.id !== id));
  for (const note of touched) notes.set(note.id, { ...note, labelIds: note.labelIds.filter((x) => x !== id) });
  bump();
  composerLabels.value = composerLabels.value.filter((x) => x !== id);
  try {
    await api.deleteLabel(id);
  } catch (error) {
    labels.value = before;
    for (const note of touched) notes.set(note.id, note);
    bump();
    showError(error);
  }
}
