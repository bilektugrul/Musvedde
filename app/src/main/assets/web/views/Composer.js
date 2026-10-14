import { useRef, useState, useEffect, useLayoutEffect } from 'preact/hooks';
import { signal } from '@preact/signals';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { composerColor, composerLabels, createNote, section } from '../store.js';
import { Icon } from './icons.js';
import { Popover, ColorPicker, LabelPicker, useAutosize, titleSuggestion } from './common.js';
import { LabelChips } from './NoteCard.js';

export const composerFocusRequest = signal(0);

export function Composer() {
  const [expanded, setExpanded] = useState(false);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const rootRef = useRef(null);
  const titleRef = useRef(null);
  const bodyRef = useRef(null);
  const state = useRef({});
  state.current = { title, body, expanded };

  useAutosize(titleRef, title, expanded);
  useAutosize(bodyRef, body, expanded);

  const save = () => {
    const { title: tt, body: bb } = state.current;
    if (!tt.trim() && !bb.trim()) return false;
    const current = section.value;
    const labelIds = [...composerLabels.value];
    if (current.kind === 'label' && !labelIds.includes(current.labelId)) labelIds.push(current.labelId);
    createNote({ title: tt.trim(), body: bb, color: composerColor.value, labelIds });
    setTitle('');
    setBody('');
    return true;
  };

  const collapse = () => {
    save();
    setExpanded(false);
    rootRef.current?.querySelector(':focus')?.blur();
  };

  const next = () => {
    save();
    requestAnimationFrame(() => bodyRef.current?.focus());
  };

  useEffect(() => {
    if (!composerFocusRequest.value) return;
    setExpanded(true);
    requestAnimationFrame(() => bodyRef.current?.focus());
  }, [composerFocusRequest.value]);

  // Focus moves to the body in the same commit that expands the composer, so keys typed right after the
  // click land in it instead of in the placeholder that is being replaced.
  useLayoutEffect(() => {
    if (!expanded) return;
    const el = bodyRef.current;
    if (!el || rootRef.current?.contains(document.activeElement)) return;
    el.focus();
    el.setSelectionRange(el.value.length, el.value.length);
  }, [expanded]);

  useEffect(() => {
    if (!expanded) return;
    const onPointerDown = (event) => {
      const root = rootRef.current;
      if (!root || root.contains(event.target)) return;
      if (event.target.closest?.('[popover], dialog')) return;
      collapse();
    };
    document.addEventListener('pointerdown', onPointerDown, true);
    return () => document.removeEventListener('pointerdown', onPointerDown, true);
  }, [expanded]);

  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      if (event.target.closest?.('[popover]')) return;
      event.preventDefault();
      event.stopPropagation();
      collapse();
    } else if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      next();
    }
  };

  const suggestion = titleSuggestion(title, body);
  const color = composerColor.value;

  if (!expanded) {
    return html`
      <div class="composer-wrap">
        <div class="composer is-collapsed" ref=${rootRef}>
          <input class="composer-placeholder" type="text" placeholder=${t('takeNote')} aria-label=${t('takeNote')}
            onFocus=${() => setExpanded(true)}
            onInput=${(e) => { setBody(e.currentTarget.value); setExpanded(true); }} />
          <${Icon} name="edit" size=${20} class="muted" />
        </div>
      </div>`;
  }

  return html`
    <div class="composer-wrap">
      <div class=${`composer is-expanded note-${color}`} ref=${rootRef} onKeyDown=${onKeyDown}>
        <textarea ref=${titleRef} class="composer-title" rows="1" placeholder=${t('title')} aria-label=${t('title')}
          value=${title} maxlength="1000"
          onInput=${(e) => setTitle(e.currentTarget.value.replace(/\n/g, ' '))}
          onKeyDown=${(e) => { if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey) { e.preventDefault(); bodyRef.current?.focus(); } }}></textarea>
        <textarea ref=${bodyRef} class="composer-body" rows="2" placeholder=${t('takeNote')} aria-label=${t('bodyPlaceholder')}
          value=${body} onInput=${(e) => setBody(e.currentTarget.value)}></textarea>
        ${suggestion ? html`
          <button type="button" class="suggest-chip" onClick=${() => { setTitle(suggestion.title); setBody(suggestion.body); bodyRef.current?.focus(); }}>
            <${Icon} name="title" size=${16} /> ${t('makeTitle')}: <span class="suggest-preview">${suggestion.title}</span>
          </button>` : null}
        <${LabelChips} ids=${composerLabels.value} max=${8} />
        <div class="composer-toolbar">
          <${Popover} icon="palette" label=${t('color')} class="color-popover">
            ${(close) => html`<${ColorPicker} value=${color} onPick=${(c) => { composerColor.value = c; close(); bodyRef.current?.focus(); }} />`}
          <//>
          <${Popover} icon="label" label=${t('labels')} class="label-popover">
            <${LabelPicker} state=${(id) => composerLabels.value.includes(id)}
              onToggle=${(id, add) => {
                composerLabels.value = add ? [...composerLabels.value, id] : composerLabels.value.filter((x) => x !== id);
              }} />
          <//>
          <span class="toolbar-spacer"></span>
          <span class="kbd-hint"><kbd>Ctrl</kbd>+<kbd>Enter</kbd></span>
          <button type="button" class="text-btn" onClick=${collapse}>${t('close')}</button>
        </div>
      </div>
    </div>`;
}
