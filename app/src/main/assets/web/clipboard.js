export function noteText(note) {
  const title = note.title.trim();
  const body = note.body.replace(/\s+$/, '');
  if (title && body) return `${title}\n${body}`;
  return title || body;
}

export function notesText(notes) {
  return notes.map(noteText).filter(Boolean).join('\n\n');
}

function legacyCopy(text) {
  const active = document.activeElement;
  const selection = document.getSelection();
  const ranges = [];
  for (let i = 0; i < (selection?.rangeCount ?? 0); i++) ranges.push(selection.getRangeAt(i));
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none';
  (document.querySelector('dialog[open]') ?? document.body).appendChild(area);
  area.select();
  area.setSelectionRange(0, text.length);
  let ok = false;
  try { ok = document.execCommand('copy'); } catch { ok = false; }
  area.remove();
  if (selection) {
    selection.removeAllRanges();
    ranges.forEach((range) => selection.addRange(range));
  }
  if (active && typeof active.focus === 'function') active.focus({ preventScroll: true });
  return ok;
}

export async function copyText(text) {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {}
  }
  return legacyCopy(text);
}
