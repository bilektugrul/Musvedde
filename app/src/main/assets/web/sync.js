import { batch } from '@preact/signals';
import { api, onUnauthorized } from './api.js';
import { setLocale } from './i18n.js';
import { applyTheme } from './theme.js';
import {
  screen, connection, loadBootstrap, getRevision, setRevision, upsertNotes, removeNotes, setLabels,
} from './store.js';
import { notifyRemoteNotes, reopenServerSessions } from './sessions.js';

let source = null;
let reconnectTimer = 0;
let reconnectDelay = 1000;
let offlineTimer = 0;
let everConnected = false;

onUnauthorized(() => {
  stopEvents();
  screen.value = 'pair';
});

export async function boot() {
  try {
    const data = await api.bootstrap();
    setLocale(data.locale);
    applyTheme(data.theme);
    loadBootstrap(data);
    notifyRemoteNotes(data.notes ?? []);
    screen.value = 'app';
    startEvents();
  } catch (error) {
    if (error.status === 401) {
      screen.value = 'pair';
    } else {
      connection.value = 'offline';
      clearTimeout(reconnectTimer);
      reconnectTimer = setTimeout(boot, nextDelay());
    }
  }
}

function nextDelay() {
  const delay = reconnectDelay;
  reconnectDelay = Math.min(reconnectDelay * 2, 15000);
  return delay;
}

let pulling = null;
let pullAgain = false;

export function pull() {
  if (pulling) {
    pullAgain = true;
    return pulling;
  }
  pulling = (async () => {
    do {
      pullAgain = false;
      try {
        const diff = await api.since(getRevision());
        batch(() => {
          if (diff.notes?.length) upsertNotes(diff.notes);
          if (diff.deletedIds?.length) removeNotes(diff.deletedIds);
          setRevision(diff.revision);
        });
        if (diff.notes?.length) notifyRemoteNotes(diff.notes);
      } catch (error) {
        if (error.status === 410) {
          await reloadAll();
        } else {
          break;
        }
      }
    } while (pullAgain);
    pulling = null;
  })();
  return pulling;
}

async function reloadAll() {
  const data = await api.bootstrap();
  applyTheme(data.theme);
  loadBootstrap(data);
  notifyRemoteNotes(data.notes ?? []);
}

async function refreshLabels() {
  try {
    setLabels(await api.labels());
  } catch {}
}

function setOffline() {
  if (offlineTimer || connection.value === 'offline') return;
  offlineTimer = setTimeout(() => {
    offlineTimer = 0;
    connection.value = 'offline';
  }, 1200);
}

function setOnline() {
  clearTimeout(offlineTimer);
  offlineTimer = 0;
  connection.value = 'online';
}

function startEvents() {
  stopEvents();
  const es = new EventSource('api/events', { withCredentials: true });
  source = es;

  es.addEventListener('open', () => {
    reconnectDelay = 1000;
    setOnline();
    if (everConnected) {
      reopenServerSessions();
      pull();
      refreshLabels();
    }
    everConnected = true;
  });

  es.addEventListener('notes', (event) => {
    const data = parse(event.data);
    if (data && typeof data.revision === 'number' && data.revision <= getRevision()) return;
    pull();
  });

  es.addEventListener('labels', () => refreshLabels());

  es.addEventListener('theme', (event) => {
    const theme = parse(event.data);
    if (theme) applyTheme(theme);
  });

  es.addEventListener('bye', () => {
    stopEvents();
    screen.value = 'bye';
  });

  es.addEventListener('error', () => {
    if (source !== es) return;
    setOffline();
    if (es.readyState === EventSource.CLOSED) {
      source = null;
      clearTimeout(reconnectTimer);
      reconnectTimer = setTimeout(probeAndReconnect, nextDelay());
    }
  });
}

// EventSource gives no status code when it gives up, so ask a cheap endpoint
// whether the phone is gone or our pairing was revoked (401 → pairing screen).
async function probeAndReconnect() {
  if (screen.value !== 'app') return;
  try {
    await api.labels();
    startEvents();
  } catch (error) {
    if (error.status === 401) return;
    reconnectTimer = setTimeout(probeAndReconnect, nextDelay());
  }
}

function stopEvents() {
  clearTimeout(reconnectTimer);
  if (source) {
    source.close();
    source = null;
  }
}

function parse(text) {
  try { return JSON.parse(text); } catch { return null; }
}

export async function pair(code, name) {
  await api.pair(code, name);
  screen.value = 'loading';
  everConnected = false;
  await boot();
}
