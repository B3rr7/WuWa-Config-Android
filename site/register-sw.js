// Registers the offline shell. Kept as a separate file rather than inlined in six
// pages so the registration logic exists once.
//
// No-ops when the browser has no Service Worker support, or when the page is opened
// over file:// (where sw.js cannot register at all).
if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch((err) => {
      // Registration failing must never surface to the user: the site works fine
      // without offline support, so this is a capability miss, not a broken page.
      console.warn('SW registration failed:', err);
    });
  });
}