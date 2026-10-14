import { useEffect, useRef, useState } from 'preact/hooks';
import { html } from '../html.js';
import { t } from '../i18n.js';
import { pair } from '../sync.js';
import { Icon } from './icons.js';

function guessDeviceName() {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua) ? 'Edge'
    : /OPR\//.test(ua) ? 'Opera'
      : /Firefox\//.test(ua) ? 'Firefox'
        : /Chrome\//.test(ua) ? 'Chrome'
          : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  const os = /Windows/.test(ua) ? 'Windows'
    : /Mac OS X|Macintosh/.test(ua) ? 'macOS'
      : /CrOS/.test(ua) ? 'ChromeOS'
        : /Android/.test(ua) ? 'Android'
          : /Linux/.test(ua) ? 'Linux'
            : /iPhone|iPad/.test(ua) ? 'iOS' : '';
  return os ? `${browser} · ${os}` : browser;
}

export function PairScreen() {
  const [code, setCode] = useState('');
  const [name, setName] = useState(guessDeviceName);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [waitUntil, setWaitUntil] = useState(0);
  const [, tick] = useState(0);
  const codeRef = useRef(null);

  useEffect(() => { codeRef.current?.focus(); }, []);
  const remaining = Math.max(0, Math.ceil((waitUntil - Date.now()) / 1000));
  useEffect(() => {
    if (!remaining) return;
    const timer = setInterval(() => tick((x) => x + 1), 500);
    return () => clearInterval(timer);
  }, [remaining > 0]);

  const submit = async (event) => {
    event.preventDefault();
    if (busy || remaining || code.length < 6) return;
    setBusy(true);
    setError(null);
    try {
      await pair(code, name.trim() || guessDeviceName());
    } catch (err) {
      setCode('');
      if (err.status === 429) {
        setWaitUntil(Date.now() + (err.body?.retryAfterSeconds ?? 30) * 1000);
        setError('throttled');
      } else if (err.status === 403) {
        setError('wrong');
      } else {
        setError('network');
      }
      requestAnimationFrame(() => codeRef.current?.focus());
    } finally {
      setBusy(false);
    }
  };

  const message = error === 'throttled' && remaining ? t('pairThrottled', remaining)
    : error === 'wrong' ? t('pairWrong')
      : error === 'network' ? t('disconnected') : null;

  return html`
    <main class="center-screen">
      <form class="pair-card" onSubmit=${submit}>
        <span class="pair-icon" aria-hidden="true"><${Icon} name="laptop" size=${32} /></span>
        <h1 class="pair-title">${t('pairTitle')}</h1>
        <p class="pair-body">${t('pairBody')}</p>
        <label class="field">
          <span class="field-label">${t('pairCode')}</span>
          <input ref=${codeRef} class="code-input" type="text" inputmode="numeric" autocomplete="one-time-code"
            maxlength="6" pattern="[0-9]{6}" value=${code} aria-invalid=${error === 'wrong' ? 'true' : undefined}
            disabled=${remaining > 0}
            onInput=${(e) => {
              const digits = e.currentTarget.value.replace(/\D/g, '').slice(0, 6);
              e.currentTarget.value = digits;
              setCode(digits);
            }} placeholder="••••••" />
        </label>
        <label class="field">
          <span class="field-label">${t('pairName')}</span>
          <input class="text-input" type="text" value=${name} maxlength="60" onInput=${(e) => setName(e.currentTarget.value)} />
        </label>
        ${message ? html`<p class="pair-error" role="alert">${message}</p>` : null}
        <button type="submit" class="filled-btn pair-submit" disabled=${busy || remaining > 0 || code.length < 6}>
          ${t('pairButton')}
        </button>
        <p class="muted small">${t('pairFooter')}</p>
      </form>
    </main>`;
}

export function ByeScreen() {
  return html`
    <main class="center-screen">
      <div class="pair-card">
        <span class="pair-icon" aria-hidden="true"><${Icon} name="cloudOff" size=${32} /></span>
        <h1 class="pair-title">${t('sessionClosed')}</h1>
        <p class="pair-body">${t('sessionClosedBody')}</p>
        <button type="button" class="filled-btn pair-submit" onClick=${() => location.reload()}>${t('reload')}</button>
      </div>
    </main>`;
}

export function LoadingScreen() {
  return html`
    <main class="center-screen" aria-busy="true">
      <div class="loader" role="status"><span class="spinner"></span><span class="muted">${t('loading')}</span></div>
    </main>`;
}
