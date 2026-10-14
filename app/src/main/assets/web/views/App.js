import { useRef, useEffect, useLayoutEffect, useCallback } from 'preact/hooks';
import { html } from '../html.js';
import { t } from '../i18n.js';
import {
  screen, layout, section, visibleNotes, isSearching, searchTerms, selection, openNote, openNoteById, openBlankNote,
  closeNote, toggleSelected, drawerOpen, emptyTrash, labelsById,
} from '../store.js';
import { Icon } from './icons.js';
import { ConfirmHost, confirmAction, prefersReducedMotion } from './common.js';
import { TopBar, sidebarCollapsed, shortcutsOpen } from './TopBar.js';
import { Sidebar, LabelsDialog } from './Sidebar.js';
import { SelectionBar } from './SelectionBar.js';
import { Composer, composerFocusRequest } from './Composer.js';
import { Grid } from './Grid.js';
import { List } from './List.js';
import { Editor, EditorEmpty } from './Editor.js';
import { Snackbar, ConnectionBanner, ShortcutsDialog } from './Status.js';
import { DropOverlay, ImportProgress } from './Import.js';
import { PairScreen, ByeScreen, LoadingScreen } from './Screens.js';

const MORPH = 'note-morph';
const afterRender = () => new Promise((resolve) => setTimeout(resolve, 0));
const canTransition = () => typeof document.startViewTransition === 'function' && !prefersReducedMotion();

export function openWithTransition(id, origin) {
  if (!origin || !canTransition()) {
    openNoteById(id);
    return;
  }
  origin.style.viewTransitionName = MORPH;
  const transition = document.startViewTransition(async () => {
    origin.style.viewTransitionName = '';
    openNoteById(id);
    await afterRender();
  });
  transition.finished.finally(() => { origin.style.viewTransitionName = ''; });
}

export function closeWithTransition() {
  const open = openNote.value;
  if (!open) return;
  const dialogSurface = document.querySelector('.note-dialog .editor');
  if (!dialogSurface || open.id == null || !canTransition()) {
    closeNote();
    return;
  }
  let card = null;
  const transition = document.startViewTransition(async () => {
    closeNote();
    await afterRender();
    card = document.querySelector(`.card[data-note-id="${open.id}"]`);
    if (card) card.style.viewTransitionName = MORPH;
  });
  transition.finished.finally(() => { if (card) card.style.viewTransitionName = ''; });
}

function EmptyState() {
  const s = section.value;
  let icon = 'notes';
  let text = t('emptyNotes');
  if (isSearching.value) { icon = 'search'; text = t('emptySearch'); }
  else if (s.kind === 'archive') { icon = 'archive'; text = t('emptyArchive'); }
  else if (s.kind === 'trash') { icon = 'trash'; text = t('emptyTrashView'); }
  else if (s.kind === 'label') { icon = 'label'; text = t('emptyLabel'); }
  return html`<div class="empty-state"><${Icon} name=${icon} size=${96} /><p>${text}</p></div>`;
}

function TrashHeader() {
  if (section.value.kind !== 'trash' || isSearching.value) return null;
  const hasNotes = visibleNotes.value.length > 0;
  return html`
    <div class="trash-header">
      <span>${t('trashInfo')}</span>
      ${hasNotes ? html`
        <button type="button" class="text-btn danger" onClick=${async () => {
          if (await confirmAction(t('confirmEmptyTrash'), t('emptyTrash'))) emptyTrash();
        }}>${t('emptyTrash')}</button>` : null}
    </div>`;
}

const onSelect = (id, range) => toggleSelected(id, { range });
const showComposer = () => !isSearching.value && (section.value.kind === 'notes' || section.value.kind === 'label');

function NoteDialog() {
  const ref = useRef(null);
  const open = openNote.value;
  useLayoutEffect(() => {
    const dialog = ref.current;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open?.key]);
  useEffect(() => () => { if (ref.current?.open) ref.current.close(); }, []);
  const onNext = () => {
    closeNote();
    composerFocusRequest.value++;
  };
  return html`
    <dialog ref=${ref} class="note-dialog" aria-label=${t('openNote')}
      onCancel=${(e) => { e.preventDefault(); closeWithTransition(); }}
      onClick=${(e) => { if (e.target === e.currentTarget) closeWithTransition(); }}>
      ${open ? html`<${Editor} key=${open.key} open=${open} variant="dialog" onClose=${closeWithTransition} onNext=${onNext} />` : null}
    </dialog>`;
}

function GridLayout({ withPanel }) {
  const scrollerRef = useRef(null);
  const notes = visibleNotes.value;
  const open = openNote.value;
  const onActivate = useCallback((id, el) => {
    if (layout.value === 'A') openWithTransition(id, el);
    else openNoteById(id);
  }, []);
  const panelNext = () => {
    closeNote();
    composerFocusRequest.value++;
  };
  return html`
    <main class="content" ref=${scrollerRef} id="main">
      <div class="content-inner">
        ${showComposer() ? html`<${Composer} />` : null}
        <${TrashHeader} />
        ${notes.length ? null : html`<${EmptyState} />`}
        <${Grid} notes=${notes} scrollerRef=${scrollerRef} grouped=${!isSearching.value} terms=${searchTerms.value}
          selection=${selection.value} openId=${withPanel ? open?.id : null} onActivate=${onActivate} onSelect=${onSelect} />
      </div>
    </main>
    ${withPanel && open ? html`
      <aside class="side-panel" aria-label=${t('openNote')}>
        <${Editor} key=${open.key} open=${open} variant="panel" onClose=${closeNote} onNext=${panelNext} />
      </aside>` : null}
    ${withPanel ? null : html`<${NoteDialog} />`}`;
}

function sectionTitle() {
  const s = section.value;
  if (isSearching.value) return t('searchResultsN', visibleNotes.value.length);
  if (s.kind === 'archive') return t('archive');
  if (s.kind === 'trash') return t('trash');
  if (s.kind === 'label') return labelsById.value.get(s.labelId)?.name ?? t('labels');
  return t('notes');
}

function PaneLayout() {
  const notes = visibleNotes.value;
  const open = openNote.value;
  const onActivate = useCallback((id) => openNoteById(id), []);
  const canCreate = section.value.kind !== 'trash' && section.value.kind !== 'archive';
  return html`
    <section class="list-pane" aria-label=${sectionTitle()}>
      <div class="list-toolbar">
        <h2 class="list-title">${sectionTitle()}</h2>
        ${canCreate ? html`
          <button type="button" class="tonal-btn" onClick=${openBlankNote} title="Ctrl+N">
            <${Icon} name="add" size=${20} /> ${t('newNote')}
          </button>` : null}
      </div>
      <${TrashHeader} />
      ${notes.length ? html`
        <${List} notes=${notes} grouped=${!isSearching.value} terms=${searchTerms.value} selection=${selection.value}
          openId=${open?.id} onActivate=${onActivate} onSelect=${onSelect} />` : html`<${EmptyState} />`}
    </section>
    <section class="editor-pane">
      ${open ? html`<${Editor} key=${open.key} open=${open} variant="pane" onClose=${closeNote} onNext=${openBlankNote} />`
        : html`<${EditorEmpty} onNew=${openBlankNote} />`}
    </section>`;
}

function Shell() {
  const current = layout.value;
  const selecting = selection.value.size > 0;
  return html`
    <div class=${`shell layout-${current} ${sidebarCollapsed.value ? 'sidebar-collapsed' : ''} ${drawerOpen.value ? 'drawer-open' : ''} ${selecting ? 'is-selecting' : ''}`}>
      <div class="topbar-wrap">
        <${TopBar} />
        <${SelectionBar} />
      </div>
      <${ConnectionBanner} />
      <div class="body">
        <aside class="sidebar"><${Sidebar} /></aside>
        <div class="scrim" onClick=${() => { drawerOpen.value = false; }} aria-hidden="true"></div>
        ${current === 'B' ? html`<${PaneLayout} />` : html`<${GridLayout} key=${current} withPanel=${current === 'C'} />`}
      </div>
      <${DropOverlay} />
      <${ImportProgress} />
      <${Snackbar} />
      <${LabelsDialog} />
      <${ShortcutsDialog} open=${shortcutsOpen.value} onClose=${() => { shortcutsOpen.value = false; }} />
      <${ConfirmHost} />
    </div>`;
}

export function App() {
  switch (screen.value) {
    case 'pair': return html`<${PairScreen} />`;
    case 'bye': return html`<${ByeScreen} />`;
    case 'app': return html`<${Shell} />`;
    default: return html`<${LoadingScreen} />`;
  }
}
