// Offline cache + Android share target for the FitShare web app.
const VERSION = "fs-5";
const SHELL = ["./", "app.css", "js/app.js", "js/data.js", "js/render.js", "manifest.webmanifest", "icon-192.png", "../fonts/bigshoulders.ttf", "../fonts/archivo.ttf"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION && k !== "fs-share").map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  // share_target: park the files, then open the app, which picks them up
  if (e.request.method === "POST" && url.pathname.endsWith("/app/share")) {
    e.respondWith((async () => {
      const form = await e.request.formData();
      const cache = await caches.open("fs-share");
      for (const k of await cache.keys()) await cache.delete(k);
      let i = 0;
      for (const f of form.getAll("files")) {
        if (!(f instanceof File)) continue;
        await cache.put(new URL(`shared/${i++}`, self.registration.scope), new Response(f, { headers: { "content-type": f.type, "x-name": encodeURIComponent(f.name) } }));
      }
      return Response.redirect(new URL("./?shared=1", self.registration.scope).href, 303);
    })());
    return;
  }
  if (e.request.method !== "GET" || url.origin !== location.origin) return;
  // OCR files and fonts never change under the same name: cache first. Everything else: network first, cache offline.
  const immutable = url.pathname.includes("/app/vendor/") || url.pathname.includes("/fonts/");
  e.respondWith((async () => {
    const cache = await caches.open(VERSION);
    if (immutable) {
      const hit = await cache.match(e.request);
      if (hit) return hit;
      const res = await fetch(e.request);
      if (res.ok) cache.put(e.request, res.clone());
      return res;
    }
    try {
      const res = await fetch(e.request);
      if (res.ok) cache.put(e.request, res.clone());
      return res;
    } catch {
      return (await cache.match(e.request, { ignoreSearch: true })) || Response.error();
    }
  })());
});
