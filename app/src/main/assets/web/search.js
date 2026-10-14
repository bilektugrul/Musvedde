const COMBINING = /[̀-ͯ]/g;

export function normalize(text) {
  return text.toLocaleLowerCase('tr').normalize('NFD').replace(COMBINING, '').replace(/ı/g, 'i');
}

function normalizeChar(ch) {
  const n = normalize(ch);
  return n.length === 1 ? n : ch.length === 1 ? (n[0] ?? ch) : ch;
}

// Same as normalize() but guaranteed to keep one output char per input code unit,
// so indices of matches map straight back onto the original text.
function normalizeAligned(text) {
  const fast = normalize(text);
  if (fast.length === text.length) return fast;
  let out = '';
  for (let i = 0; i < text.length; i++) out += normalizeChar(text[i]);
  return out;
}

const index = new WeakMap();

export function searchText(note) {
  let cached = index.get(note);
  if (cached === undefined) {
    cached = normalize(`${note.title}\n${note.body}`);
    index.set(note, cached);
  }
  return cached;
}

export function parseQuery(query) {
  const terms = normalize(query.trim()).split(/\s+/).filter(Boolean);
  return terms.length ? terms : null;
}

export function matches(note, terms) {
  const text = searchText(note);
  for (const term of terms) if (!text.includes(term)) return false;
  return true;
}

const SLICE_BUDGET_MS = 150;

// Filters synchronously while it stays under the budget; past that the rest is
// finished in idle callbacks so typing never blocks. Returns a cancel function.
export function filterAsync(notes, terms, onDone) {
  let cancelled = false;
  const result = [];
  let i = 0;
  const idle = window.requestIdleCallback ?? ((cb) => setTimeout(() => cb({ timeRemaining: () => 8 }), 1));

  function run(deadlineMs) {
    const start = performance.now();
    while (i < notes.length) {
      const note = notes[i++];
      if (matches(note, terms)) result.push(note);
      if ((i & 255) === 0 && performance.now() - start > deadlineMs) {
        idle((d) => { if (!cancelled) run(Math.max(4, d.timeRemaining())); });
        return;
      }
    }
    if (!cancelled) onDone(result);
  }

  run(SLICE_BUDGET_MS);
  return () => { cancelled = true; };
}

export function findRanges(text, terms) {
  if (!terms || !text) return [];
  const normalized = normalizeAligned(text);
  const ranges = [];
  for (const term of terms) {
    let from = 0;
    while (true) {
      const at = normalized.indexOf(term, from);
      if (at < 0) break;
      ranges.push([at, at + term.length]);
      from = at + term.length;
    }
  }
  if (ranges.length < 2) return ranges;
  ranges.sort((a, b) => a[0] - b[0]);
  const merged = [ranges[0]];
  for (const range of ranges.slice(1)) {
    const last = merged[merged.length - 1];
    if (range[0] <= last[1]) last[1] = Math.max(last[1], range[1]);
    else merged.push(range);
  }
  return merged;
}

export function firstMatchIndex(text, terms) {
  if (!terms) return -1;
  const normalized = normalizeAligned(text);
  let best = -1;
  for (const term of terms) {
    const at = normalized.indexOf(term);
    if (at >= 0 && (best < 0 || at < best)) best = at;
  }
  return best;
}
