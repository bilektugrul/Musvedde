import { signal, computed } from '@preact/signals';
import { api } from './api.js';
import {
  getNote, upsertNote, setLocalOverride, pendingWrites, isRetryable, onNotesRemoved, composerColor, composerLabels,
} from './store.js';

const SAVE_DELAY_MS = 600;
const MAX_BACKOFF_MS = 30000;

const live = new Map(); // note id -> EditSession
const unsaved = signal(new Set());

export const unsavedCount = computed(() => unsaved.value.size);

function markUnsaved(session, flag) {
  const has = unsaved.value.has(session);
  if (has === flag) return;
  const next = new Set(unsaved.value);
  if (flag) next.add(session);
  else next.delete(session);
  unsaved.value = next;
}

export class EditSession {
  constructor(note) {
    this.id = note?.id ?? null;
    this.base = note?.updatedAt ?? 0;
    this.title = signal(note?.title ?? '');
    this.body = signal(note?.body ?? '');
    this.status = signal('idle'); // idle | editing | saving | saved | pending | conflict | gone
    this.conflict = signal(null);
    this.dirty = false;
    this.inflight = null;
    this.timer = 0;
    this.retryDelay = 1000;
    this.refs = 0;
    this.serverSession = false;
    if (this.id != null) live.set(this.id, this);
  }

  edit(title, body) {
    this.title.value = title;
    this.body.value = body;
    this.dirty = true;
    markUnsaved(this, true);
    if (this.id != null) setLocalOverride(this.id, () => (this.dirty ? { title: this.title.value, body: this.body.value } : null));
    if (this.status.value !== 'conflict' && this.status.value !== 'pending') this.status.value = 'editing';
    this.schedule(SAVE_DELAY_MS);
  }

  schedule(delay) {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.save(), delay);
  }

  isBlank() {
    return !this.title.value.trim() && !this.body.value.trim();
  }

  flush() {
    clearTimeout(this.timer);
    return this.save();
  }

  async save(force = false) {
    if (this.status.value === 'conflict' && !force) return;
    if (this.status.value === 'gone') return;
    if (!this.dirty && !force) return;
    if (this.inflight) {
      await this.inflight.catch(() => {});
      return this.dirty || force ? this.save(force) : undefined;
    }
    if (this.id == null && this.isBlank()) return;
    this.inflight = this.send(force);
    try {
      await this.inflight;
    } finally {
      this.inflight = null;
    }
  }

  async send(force) {
    const title = this.title.value;
    const body = this.body.value;
    this.status.value = 'saving';
    if (this.id != null) {
      const current = getNote(this.id);
      if (current) upsertNote({ ...current, title, body }, false);
    }
    pendingWrites.value++;
    try {
      let note;
      if (this.id == null) {
        note = await api.createNote({ title, body, color: composerColor.value, labelIds: composerLabels.value });
        this.id = note.id;
        live.set(note.id, this);
        this.onCreated?.(note.id);
        if (this.refs > 0) this.openServerSession();
      } else {
        note = await api.patchNote(this.id, { title, body, baseUpdatedAt: this.base, ...(force ? { force: true } : {}) });
      }
      this.base = note.updatedAt;
      this.retryDelay = 1000;
      const unchanged = this.title.value === title && this.body.value === body;
      if (unchanged) {
        this.dirty = false;
        markUnsaved(this, false);
      }
      upsertNote(note);
      this.status.value = unchanged ? 'saved' : 'editing';
      if (!unchanged) this.schedule(SAVE_DELAY_MS);
      else this.releaseIfIdle();
    } catch (error) {
      if (error.status === 409 && error.body?.note) {
        this.conflict.value = error.body.note;
        this.status.value = 'conflict';
      } else if (error.status === 404) {
        this.status.value = 'gone';
        this.dirty = false;
        markUnsaved(this, false);
      } else if (isRetryable(error)) {
        this.status.value = 'pending';
        this.schedule(this.retryDelay);
        this.retryDelay = Math.min(this.retryDelay * 2, MAX_BACKOFF_MS);
      } else {
        this.status.value = 'pending';
        this.schedule(MAX_BACKOFF_MS);
      }
    } finally {
      pendingWrites.value--;
    }
  }

  keepMine() {
    const server = this.conflict.value;
    if (!server) return;
    this.conflict.value = null;
    this.base = server.updatedAt;
    this.status.value = 'saving';
    this.dirty = true;
    return this.save(true);
  }

  takePhone() {
    const server = this.conflict.value;
    if (!server) return;
    clearTimeout(this.timer);
    this.conflict.value = null;
    this.base = server.updatedAt;
    this.title.value = server.title;
    this.body.value = server.body;
    this.dirty = false;
    markUnsaved(this, false);
    this.status.value = 'saved';
    upsertNote(server);
    this.releaseIfIdle();
  }

  applyRemote(note) {
    if (this.dirty || this.inflight || this.status.value === 'conflict') return;
    if (note.updatedAt === this.base && note.title === this.title.value && note.body === this.body.value) return;
    this.base = note.updatedAt;
    this.title.value = note.title;
    this.body.value = note.body;
  }

  openServerSession() {
    if (this.serverSession || this.id == null || this.id < 0) return;
    this.serverSession = true;
    api.openSession(this.id).catch(() => {});
  }

  closeServerSession() {
    if (!this.serverSession) return;
    this.serverSession = false;
    const id = this.id;
    const finish = () => api.closeSession(id).catch(() => {});
    if (this.inflight || this.dirty) this.save().finally(finish);
    else finish();
  }

  releaseIfIdle() {
    if (this.refs > 0 || this.dirty || this.inflight) return;
    if (this.id != null) {
      setLocalOverride(this.id, null);
      if (live.get(this.id) === this) live.delete(this.id);
    }
  }
}

export function acquireSession(id) {
  let session = id == null ? null : live.get(id);
  if (!session) session = new EditSession(id == null ? null : getNote(id));
  session.refs++;
  if (session.id != null && session.status.value === 'saved') session.status.value = 'idle';
  session.openServerSession();
  return session;
}

export function releaseSession(session) {
  session.refs = Math.max(0, session.refs - 1);
  if (session.refs > 0) return;
  session.closeServerSession();
  session.flush();
  session.releaseIfIdle();
}

export function notifyRemoteNotes(list) {
  for (const note of list) live.get(note.id)?.applyRemote(note);
}

onNotesRemoved((ids) => {
  for (const id of ids) {
    const session = live.get(id);
    if (!session || id < 0) continue;
    clearTimeout(session.timer);
    session.dirty = false;
    session.serverSession = false;
    session.status.value = 'gone';
    markUnsaved(session, false);
    setLocalOverride(id, null);
    live.delete(id);
  }
});

// The phone ends every web session when the event stream drops.
export function reopenServerSessions() {
  for (const session of new Set(live.values())) {
    if (session.refs > 0 && session.serverSession) {
      session.serverSession = false;
      session.openServerSession();
    }
  }
}

export function flushAll() {
  for (const session of unsaved.value) session.flush();
}
