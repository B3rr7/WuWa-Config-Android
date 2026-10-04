// Bump CACHE_VERSION whenever SHELL changes, or returning visitors keep the old
// precache forever. Cleaned up by the `activate` handler below.
const CACHE_VERSION = 'v2';
const SHELL_CACHE = `wuwacfg-shell-${CACHE_VERSION}`;
const ASSET_CACHE = `wuwacfg-assets-${CACHE_VERSION}`;

// The shell: every page plus the CSS, fonts and icons needed to render one.
// Deliberately NOT including mall/ — it is 11 MB across 64 sprites, and
// precaching it would make a first visit download more than the app itself.
// Sprites are cached on first use by RUNTIME below instead.
const SHELL = [
  './',
  './index.html',
  './download.html',
  './faq.html',
  './presets.html',
  './troubleshooting.html',
  './fps-lab.html',
  './styles.css',
  './manifest.json',
  './register-sw.js',
  './favicon.png',
  './app_icon.png',
  './apple-touch-icon.png',
  './icon-96.webp',
  './icon-192.png',
  './icon-512.png',
  './fonts/InterVariable.woff2',
  './fonts/InterVariable-Italic.woff2',
  './fonts/JetBrainsMono[wght].woff2',
  './fonts/JetBrainsMono-Italic[wght].woff2',
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches
      .open(SHELL_CACHE)
      // `addAll` rejects wholesale if any single entry 404s, which would leave the
      // worker permanently uninstalled. One bad path must not cost the whole cache.
      .then(c => Promise.allSettled(SHELL.map(u => c.add(u))))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches
      .keys()
      .then(keys =>
        Promise.all(
          keys
            .filter(k => k.startsWith('wuwacfg-') && k !== SHELL_CACHE && k !== ASSET_CACHE)
            .map(k => caches.delete(k)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

/**
 * Sprite and image runtime cache.
 *
 * Cache-first with no revalidation: these are versioned with the site, so a hit is
 * as correct as a fetch. Entries are left unbounded — the asset set is finite and
 * known (~64 files), so eviction would only cost re-downloads.
 */
async function cacheFirst(request) {
  const cache = await caches.open(ASSET_CACHE);
  const hit = await cache.match(request);
  if (hit) return hit;
  const response = await fetch(request);
  if (response.ok && response.type === 'basic') cache.put(request, response.clone());
  return response;
}

/**
 * Navigations: network-first so a deployed change is picked up immediately,
 * falling back to the cached shell when offline.
 */
async function navigationHandler(request) {
  try {
    return await fetch(request);
  } catch {
    const cached = await caches.match('./index.html');
    return cached || Response.error();
  }
}

self.addEventListener('fetch', e => {
  const { request } = e;
  if (request.method !== 'GET') return;

  const url = new URL(request.url);
  // Cross-origin (fonts from a CDN, analytics) is left entirely alone: this worker
  // exists so the site works offline, and opaque responses cannot be inspected or
  // trusted into a cache.
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    e.respondWith(navigationHandler(request));
    return;
  }

  e.respondWith(cacheFirst(request));
});