import { firstMatchIndex } from '../search.js';

export const PREVIEW_LINES = 10;
const MAX_CHARS = 700;

// The slice of a body a card shows. When searching and the first hit sits below
// the visible lines, the preview starts just above the hit instead.
export function bodyPreview(body, terms) {
  let start = 0;
  let lead = false;
  if (terms) {
    const hit = firstMatchIndex(body, terms);
    if (hit > 0) {
      const lines = body.slice(0, hit).split('\n');
      if (lines.length > PREVIEW_LINES - 2 || hit > MAX_CHARS - 100) {
        const lineStart = body.lastIndexOf('\n', hit - 1) + 1;
        start = hit - lineStart > 120 ? Math.max(lineStart, body.lastIndexOf(' ', hit - 40) + 1) : lineStart;
        lead = start > 0;
      }
    }
  }
  let text = body.slice(start, start + MAX_CHARS);
  const lines = text.split('\n');
  const clipped = lines.length > PREVIEW_LINES || start + MAX_CHARS < body.length;
  if (lines.length > PREVIEW_LINES) text = lines.slice(0, PREVIEW_LINES).join('\n');
  return { text: lead ? `…${text}` : text, clipped };
}

export function rowSnippet(note) {
  const source = note.title.trim() ? note.body : note.body.replace(/^.*\n?/, '');
  return source.replace(/\s+/g, ' ').trim().slice(0, 200);
}

export function rowTitle(note) {
  return note.title.trim() || note.body.trim().split('\n', 1)[0].slice(0, 120);
}
