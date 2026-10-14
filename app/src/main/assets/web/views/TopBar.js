import { useRef } from 'preact/hooks';
import { signal } from '@preact/signals';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { query, layout, setLayout, drawerOpen, isSearching, visibleNotes, section, labelsById } from '../store.js';
import { Icon } from './icons.js';
import { IconButton, Popover } from './common.js';
import { labelsDialogOpen } from './Sidebar.js';
import { pickImportFile } from './Import.js';

export function focusSearch() {
  const input = document.getElementById('search-input');
  input?.focus();
  input?.select();
}

export const sidebarCollapsed = signal(readCollapsed());
export const shortcutsOpen = signal(false);

function readCollapsed() {
  try { return localStorage.getItem('musvedde.sidebar') === 'collapsed'; } catch { return false; }
}

export function toggleSidebar() {
  if (window.matchMedia('(max-width: 860px)').matches) {
    drawerOpen.value = !drawerOpen.value;
    return;
  }
  sidebarCollapsed.value = !sidebarCollapsed.value;
  try { localStorage.setItem('musvedde.sidebar', sidebarCollapsed.value ? 'collapsed' : 'open'); } catch {}
}

function sectionTitle() {
  const s = section.value;
  if (s.kind === 'archive') return t('archive');
  if (s.kind === 'trash') return t('trash');
  if (s.kind === 'label') return labelsById.value.get(s.labelId)?.name ?? t('labels');
  return t('notes');
}

function SearchBox() {
  const inputRef = useRef(null);
  return html`
    <div class=${`search ${query.value ? 'has-value' : ''}`} role="search">
      <${Icon} name="search" class="search-icon" />
      <input ref=${inputRef} id="search-input" type="search" value=${query.value} placeholder=${t('searchPlaceholder')}
        aria-label=${t('searchPlaceholder')} autocomplete="off" spellcheck="false"
        onInput=${(e) => { query.value = e.currentTarget.value; }}
        onKeyDown=${(e) => {
          if (e.key === 'Escape') {
            e.preventDefault();
            e.stopPropagation();
            if (query.value) query.value = '';
            else e.currentTarget.blur();
          }
          if (e.key === 'ArrowDown' || e.key === 'Enter') {
            const first = document.querySelector('.card[data-note-id]:not(.is-pending), .row[data-note-id]:not(.is-pending)');
            if (first) { e.preventDefault(); first.focus(); }
          }
        }} />
      ${isSearching.value ? html`<span class="search-count">${t('searchResultsN', visibleNotes.value.length)}</span>` : null}
      ${query.value ? html`<${IconButton} icon="close" label=${t('clearSearch')} class="search-clear"
        onClick=${() => { query.value = ''; inputRef.current?.focus(); }} />` : html`<kbd class="search-kbd">/</kbd>`}
    </div>`;
}

const LAYOUT_ICONS = [['A', 'layoutA', 'layoutA'], ['B', 'layoutB', 'layoutB'], ['C', 'layoutC', 'layoutC']];

function LayoutSwitch() {
  return html`
    <div class="segmented" role="radiogroup" aria-label=${t('layout')}>
      ${LAYOUT_ICONS.map(([value, icon, label], i) => html`
        <button type="button" role="radio" key=${value} aria-checked=${String(layout.value === value)}
          class=${`segment ${layout.value === value ? 'is-active' : ''}`} title=${`${t(label)} (Ctrl+${i + 1})`}
          aria-label=${t(label)} onClick=${() => setLayout(value)}>
          <${Icon} name=${icon} size=${20} />
        </button>`)}
    </div>`;
}

export function TopBar() {
  return html`
    <header class="topbar">
      <div class="topbar-start">
        <${IconButton} icon="menu" label=${t('openMenu')} onClick=${toggleSidebar} />
        <div class="brand">
          <span class="brand-mark" aria-hidden="true"><${Icon} name="edit" size=${20} /></span>
          <span class="brand-name">${t('appName')}</span>
          <span class="brand-section">${sectionTitle()}</span>
        </div>
      </div>
      <${SearchBox} />
      <div class="topbar-end">
        <${LayoutSwitch} />
        <${Popover} icon="more" label=${t('moreOptions')} align="end" class="menu-popover">
          ${(close) => html`
            <div class="menu" role="menu">
              <button type="button" role="menuitem" class="menu-item" onClick=${() => { close(); pickImportFile(); }}>
                <${Icon} name="upload" size=${20} /><span>${t('importKeep')}</span>
              </button>
              <button type="button" role="menuitem" class="menu-item" onClick=${() => { close(); labelsDialogOpen.value = true; }}>
                <${Icon} name="label" size=${20} /><span>${t('editLabels')}</span>
              </button>
              <button type="button" role="menuitem" class="menu-item" onClick=${() => { close(); shortcutsOpen.value = true; }}>
                <${Icon} name="keyboard" size=${20} /><span>${t('shortcuts')}</span>
              </button>
            </div>`}
        <//>
      </div>
    </header>`;
}
