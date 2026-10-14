import { render } from 'preact';
import { html } from './html.js';
import { App } from './views/App.js';
import { boot } from './sync.js';
import {
  screen, layout, setLayout, selection, clearSelection, selectAllVisible, openNote, openBlankNote, closeNote,
  drawerOpen, pendingWrites, getNote,
} from './store.js';
import { unsavedCount, flushAll } from './sessions.js';
import { noteText } from './clipboard.js';
import { focusSearch } from './views/TopBar.js';
import { composerFocusRequest } from './views/Composer.js';
import { copyWithFeedback } from './views/common.js';

const isTyping = (el) => !!el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName));
const modalOpen = () => !!document.querySelector('dialog[open]:not(.note-dialog), :popover-open');

function newNote() {
  if (layout.value === 'B') {
    openBlankNote();
  } else {
    if (openNote.value) closeNote();
    composerFocusRequest.value++;
  }
}

window.addEventListener('keydown', (event) => {
  if (screen.value !== 'app') return;
  const mod = event.ctrlKey || event.metaKey;
  const typing = isTyping(event.target);
  const key = event.key.toLowerCase();

  if ((mod && key === 'k') || (event.key === '/' && !typing && !mod)) {
    event.preventDefault();
    focusSearch();
    return;
  }
  if ((mod || event.altKey) && !event.shiftKey && ['1', '2', '3'].includes(event.key)) {
    event.preventDefault();
    setLayout(['A', 'B', 'C'][Number(event.key) - 1]);
    return;
  }
  if ((mod || event.altKey) && !event.shiftKey && key === 'n') {
    event.preventDefault();
    newNote();
    return;
  }
  if (mod && key === 'a' && !typing && !modalOpen()) {
    event.preventDefault();
    selectAllVisible();
    return;
  }
  if (mod && event.shiftKey && key === 'c' && openNote.value?.id != null) {
    const note = getNote(openNote.value.id);
    if (note) {
      event.preventDefault();
      copyWithFeedback(noteText(note));
    }
    return;
  }
  if (event.key === 'Escape' && !event.defaultPrevented && !document.querySelector(':popover-open')) {
    if (selection.value.size) {
      clearSelection();
    } else if (drawerOpen.value) {
      drawerOpen.value = false;
    } else if (openNote.value && layout.value !== 'A' && !modalOpen()) {
      closeNote();
    }
  }
});

window.addEventListener('beforeunload', (event) => {
  if (unsavedCount.value > 0 || pendingWrites.value > 0) {
    flushAll();
    event.preventDefault();
    event.returnValue = '';
  }
});

window.addEventListener('pagehide', () => flushAll());

render(html`<${App} />`, document.getElementById('app'));
boot();
