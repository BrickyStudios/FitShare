// FitShare web app: state, file import, OCR, editor UI and export.
import * as D from "./data.js";
import { DIM_LABELS, PLACES, SIZE_CHIPS, FORMATS, INKS, LAYOUTS, STYLES, defaultOverlay, render } from "./render.js";

const { tr } = D;
const $ = (id) => document.getElementById(id);
const MAX_BYTES = 50 * 1024 * 1024;

document.documentElement.lang = D.DE ? "de" : "en";
for (const el of document.querySelectorAll("[data-t]")) { const [de, en] = el.dataset.t.split("|"); el.textContent = tr(de, en); }

// ------------------------------------------------------------------ storage (per browser, best effort)

const store = {
  get(k, fallback) { try { const v = localStorage.getItem("fs." + k); return v == null ? fallback : JSON.parse(v); } catch { return fallback; } },
  set(k, v) { try { if (v == null) localStorage.removeItem("fs." + k); else localStorage.setItem("fs." + k, JSON.stringify(v)); } catch { /* private mode or full */ } },
};
const cleanOverlay = (o) => ({ ...defaultOverlay(), ...(o && typeof o === "object" ? o : {}), layout: LAYOUTS[o?.layout] ? o.layout : "STACK", ink: INKS[o?.ink] ? o.ink : "WHITE", dim: DIM_LABELS[o?.dim] ? o.dim : 0, place: [0, 1, 2].includes(o?.place) ? o.place : 0, size: [0, 1, 2].includes(o?.size) ? o.size : 0 });

const session = store.get("session", null);
const state = {
  stats: session?.stats || null, route: session?.route || null, sport: session?.sport || null,
  background: null, backgroundUrl: null,
  overlay: cleanOverlay(store.get("overlay", null)),
  looks: { presets: [], history: [], ...store.get("looks", {}) },
  style: STYLES[store.get("style", "OVERLAY")] ? store.get("style", "OVERLAY") : "OVERLAY",
  format: FORMATS[store.get("format", "STORY")] ? store.get("format", "STORY") : "STORY",
  section: null,
};
state.looks.presets = (state.looks.presets || []).map((l) => ({ ...l, options: cleanOverlay(l.options) }));
state.looks.history = (state.looks.history || []).map((l) => ({ ...l, options: cleanOverlay(l.options) }));
const workout = () => (state.stats || state.route ? D.mergeWorkout(state.stats, state.route, state.sport) : null);
const saveSession = () => store.set("session", state.stats || state.route ? { stats: state.stats, route: state.route, sport: state.sport } : null);

// ------------------------------------------------------------------ toast

let toastTimer;
function toast(msg) {
  const t = $("toast");
  t.textContent = msg; t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 3200);
}

// ------------------------------------------------------------------ OCR (Tesseract, self-hosted, runs in a worker)

function loadScript(src) {
  return new Promise((ok, fail) => { const s = document.createElement("script"); s.src = src; s.onload = ok; s.onerror = () => fail(new Error("load " + src)); document.head.append(s); });
}

async function ocr(blob, onProgress) {
  if (!window.Tesseract) await loadScript("vendor/tesseract.min.js");
  const base = new URL("vendor/", location.href).href;
  // share cards are ~1080 px wide; small screenshots read better scaled up, huge photos get scaled down
  const img = await createImageBitmap(blob);
  const k = Math.min(2, Math.max(0.25, 1600 / Math.max(img.width, img.height)));
  const cv = document.createElement("canvas");
  cv.width = Math.round(img.width * k); cv.height = Math.round(img.height * k);
  cv.getContext("2d").drawImage(img, 0, 0, cv.width, cv.height);
  const worker = await window.Tesseract.createWorker(["eng", "deu"], 1, {
    workerPath: base + "worker.min.js", corePath: base + "core", langPath: base + "lang",
    logger: (m) => onProgress?.(m),
  });
  try { return (await worker.recognize(cv)).data.text; } finally { worker.terminate(); }
}

// ------------------------------------------------------------------ import

const ROUTE_HINT = "GPX · TCX · KML · KMZ";

async function open(files) {
  files = [...files].filter(Boolean);
  if (!files.length) return;
  setBusy(true);
  const problems = [];
  let stats = null, route = null;
  for (const f of files) {
    try {
      if (f.size > MAX_BYTES) throw new Error(tr("Datei ist größer als 50 MB.", "File is larger than 50 MB."));
      const b = new Uint8Array(await f.arrayBuffer());
      if (D.looksLikeFit(b)) { const fit = D.parseFit(b); stats = fit.stats; if (fit.route) route = fit.route; }
      else if (D.looksLikeImage(b) || f.type.startsWith("image/")) {
        const text = await ocr(f, (m) => progress(m));
        stats = D.parseShareCard(text) || (() => { throw new Error(tr("Auf dem Bild stehen keine Trainingswerte.", "No workout numbers found in this picture.")); })();
      } else if (D.looksLikeJson(b)) stats = D.parseWorkoutJson(new TextDecoder().decode(b), f.name);
      else if (D.looksLikeRoute(b)) {
        // a TCX can carry the workout totals too (Mi Fitness export, also without GPS)
        const tcxStats = b[0] === 0x50 && b[1] === 0x4b ? null : D.parseTcxStats(new TextDecoder().decode(b));
        const r = await D.parseRoute(b);
        if (!r && !tcxStats) throw new Error(tr("Keine GPS-Punkte gefunden.", "No GPS points found."));
        if (r) route = r;
        if (tcxStats) stats = tcxStats;
      }
      else throw new Error(tr("Unbekanntes Format. Erwartet: Teilen-Bild, FIT, JSON, GPX, TCX, KML oder KMZ.", "Unknown format. Expected a share picture, FIT, JSON, GPX, TCX, KML or KMZ."));
    } catch (e) {
      console.error(e);
      problems.push(`${f.name || tr("Datei", "File")}: ${e?.message || tr("konnte nicht gelesen werden", "could not be read")}`);
    }
  }
  // a new workout drops the old route, sport choice and photo; a route on its own joins the open workout
  if (stats) { state.stats = stats; state.route = route; state.sport = null; setBackground(null); }
  else if (route) state.route = route;
  saveSession();
  setBusy(false);
  if (problems.length) showError(problems.join("\n"));
  show();
}

function progress(m) {
  const p = $("progress");
  p.hidden = false;
  const pct = Math.round((m.progress || 0) * 100);
  p.textContent = m.status === "recognizing text" ? tr(`Lese Werte vom Bild … ${pct} %`, `Reading the picture … ${pct} %`)
    : tr("Texterkennung lädt (einmalig ca. 8 MB) …", "Loading text recognition (about 8 MB, once) …");
}

function setBusy(on) {
  $("pick").setAttribute("aria-busy", on); $("progress").hidden = !on;
  if (on) $("progress").textContent = tr("Lese Dateien …", "Reading files …");
}
function showError(msg) { if (workout()) toast(msg); else { $("error").textContent = msg; $("error").hidden = false; } }

function setBackground(file) {
  if (state.backgroundUrl) URL.revokeObjectURL(state.backgroundUrl);
  state.background = null; state.backgroundUrl = null;
  if (!file) return;
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => { state.background = img; state.backgroundUrl = url; show(); }; // show: photo tile + "Photo" tab depend on it
  img.onerror = () => { URL.revokeObjectURL(url); toast(tr("Foto konnte nicht geladen werden.", "Couldn't load the photo.")); };
  img.src = url;
}

// ------------------------------------------------------------------ editor UI

const SECTIONS = { LOOKS: ["Looks", "Looks"], LAYOUT: ["Layout", "Layout"], STATS: ["Werte", "Stats"], COLOR: ["Farbe", "Colour"], PHOTO: ["Foto", "Photo"], EXTRAS: ["Extras", "Extras"], SPORT: ["Sport", "Sport"] };

function tabs(el, entries, selected, onPick) {
  el.replaceChildren(...Object.entries(entries).map(([k, [de, en]]) => {
    const b = document.createElement("button");
    b.type = "button"; b.role = "tab"; b.textContent = tr(de, en).toUpperCase(); b.setAttribute("aria-selected", k === selected);
    b.onclick = () => onPick(k);
    return b;
  }));
}

function chip(text, on, click, { color, dot, badge, remove } = {}) {
  const b = document.createElement("button");
  b.type = "button"; b.className = "chip"; b.setAttribute("aria-pressed", !!on);
  if (color) b.style.setProperty("--chip", color);
  if (badge) { const s = document.createElement("span"); s.className = "badge"; s.textContent = badge; b.append(s); }
  else if (dot) { const s = document.createElement("span"); s.className = "dot"; s.style.background = dot; b.append(s); }
  b.append(text);
  if (remove) {
    const x = document.createElement("span"); x.className = "x"; x.textContent = "✕"; x.setAttribute("aria-label", tr("Löschen", "Delete"));
    x.onclick = (e) => { e.stopPropagation(); remove(); }; b.append(x);
  }
  b.onclick = click;
  return b;
}
const note = (t) => { const s = document.createElement("span"); s.className = "note"; s.textContent = t; return s; };

const setOverlay = (change) => { state.overlay = { ...state.overlay, ...change }; store.set("overlay", state.overlay); show(); };
const sameOptions = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const lookLabel = (l) => `${tr(...LAYOUTS[l.options.layout].slice(1))} · ${tr(...INKS[l.options.ink])} · ${new Date(l.savedAt).toLocaleDateString(D.LOCALE, { day: "numeric", month: "numeric" })}`;
function rememberLook() {
  const look = { name: null, options: state.overlay, savedAt: Date.now() };
  state.looks.history = [look, ...state.looks.history.filter((l) => !sameOptions(l.options, look.options))].slice(0, 8);
  store.set("looks", state.looks);
}

function controls(w) {
  const o = state.overlay, accent = D.SPORTS[w.sport].accent, sec = state.style === "OVERLAY" ? state.section : "SPORT";
  const out = [];
  if (sec === "LOOKS") {
    out.push(chip(tr("+ Speichern", "+ Save"), false, () => { $("look-name").value = ""; $("name-dialog").showModal(); }));
    for (const l of state.looks.presets) out.push(chip(l.name, sameOptions(o, l.options), () => setOverlay(l.options), {
      remove: () => { if (confirm(tr(`„${l.name}“ löschen?`, `Delete “${l.name}”?`))) { state.looks.presets = state.looks.presets.filter((x) => x !== l); store.set("looks", state.looks); show(); } },
    }));
    for (const l of state.looks.history) out.push(chip("↺ " + lookLabel(l), sameOptions(o, l.options), () => setOverlay(l.options)));
    if (!state.looks.presets.length && !state.looks.history.length) out.push(note(tr("Gesicherte PNGs landen hier im Verlauf", "Every saved PNG shows up here")));
  } else if (sec === "LAYOUT") {
    for (const [k, [, de, en]] of Object.entries(LAYOUTS)) out.push(chip(tr(de, en), o.layout === k, () => setOverlay({ layout: k })));
  } else if (sec === "STATS") {
    const max = LAYOUTS[o.layout][0];
    const shown = D.pickStats(w, o.stats ?? D.pickStats(w, null, max).map((x) => x.key), max).map((x) => x.key);
    // "Auto" = let the sport decide (and follow it when the sport changes)
    out.push(chip("Auto", o.stats == null, () => setOverlay({ stats: null })));
    for (const st of D.availableStats(w)) {
      const i = shown.indexOf(st.key);
      out.push(chip(st.label, i >= 0, () => setOverlay({ stats: D.toggleStat(shown, st.key, max) }), { badge: i >= 0 ? String(i + 1) : null }));
    }
  } else if (sec === "COLOR") {
    for (const [k, [de, en]] of Object.entries(INKS)) out.push(chip(tr(de, en), o.ink === k, () => setOverlay({ ink: k }), { dot: k === "WHITE" ? "#fff" : k === "BLACK" ? "#14140F" : accent }));
    // black ink needs a lighter, not a darker, background
    out.push(note(o.ink === "BLACK" ? tr("AUFHELLEN", "LIGHTEN") : tr("ABDUNKELN", "DARKEN")));
    DIM_LABELS.forEach(([de, en], i) => out.push(chip(tr(de, en), o.dim === i, () => setOverlay({ dim: i }))));
  } else if (sec === "EXTRAS") {
    const r = w.route, toggle = (key) => () => setOverlay({ [key]: !o[key] });
    if (r) out.push(chip("Route", o.showRoute, toggle("showRoute")));
    if (r?.profile?.length > 1) out.push(chip(tr("Höhenprofil", "Elevation"), o.showProfile, toggle("showProfile")));
    if (w.stats.hrCurve.length > 1) out.push(chip(tr("Pulskurve", "HR graph"), o.showHeart, toggle("showHeart")));
    if ((r?.splitSeconds?.length ?? 0) >= 2) out.push(chip(tr("Km-Splits", "Km splits"), o.showSplits, toggle("showSplits")));
    if (w.stats.hrZoneSeconds.reduce((a, b) => a + b, 0) > 0) out.push(chip(tr("Pulszonen", "HR zones"), o.showZones, toggle("showZones")));
    if (w.startMillis != null) out.push(chip(tr("Datum", "Date"), o.showDate, toggle("showDate")));
    out.push(chip("Logo", o.showBrand, toggle("showBrand")));
    if (!r) out.push(note(tr("Karte, Höhenprofil, Splits: GPX/TCX dazunehmen", "Map, elevation, splits: add a GPX/TCX")));
  } else if (sec === "PHOTO") {
    const toggle = (key) => () => setOverlay({ [key]: !o[key] });
    out.push(chip(tr("Foto ins PNG", "Photo in PNG"), o.withPhoto, toggle("withPhoto")));
    if (!o.withPhoto) out.push(note(tr("Dann gehen Position, Größe, Verlauf und Weich", "Unlocks position, size, gradient and blur")));
    else {
      out.push(note(tr("POSITION", "POSITION")));
      for (const [i, de, en] of PLACES) out.push(chip(tr(de, en), o.place === i, () => setOverlay({ place: i })));
      out.push(note(tr("GRÖSSE", "SIZE")));
      for (const [i, l] of SIZE_CHIPS) out.push(chip(l, o.size === i, () => setOverlay({ size: i })));
      out.push(chip(tr("Verlauf", "Gradient"), o.fade, toggle("fade")));
      out.push(chip(tr("Weich", "Blur"), o.soft, toggle("soft")));
    }
  } else {
    // full stories: format first (story or feed post), then the sport
    if (state.style !== "OVERLAY") for (const [k, [label]] of Object.entries(FORMATS)) out.push(chip(label, state.format === k, () => { state.format = k; store.set("format", k); show(); }));
    for (const [k, s] of Object.entries(D.SPORTS)) out.push(chip(D.sportLabel(k), w.sport === k, () => { state.sport = k; saveSession(); show(); }, { color: s.accent, dot: s.accent }));
  }
  $("chips").replaceChildren(...out);
}

function show() {
  const w = workout();
  if (!!w === $("editor").hidden) scrollTo(0, 0); // switching screens starts at the top
  $("import").hidden = !!w; $("editor").hidden = !w;
  if (!w) { document.documentElement.style.setProperty("--accent", "#C6F432"); return; }
  $("error").hidden = true;
  if (!state.section) state.section = state.looks.presets.length || state.looks.history.length ? "LOOKS" : "LAYOUT";
  document.documentElement.style.setProperty("--accent", D.SPORTS[w.sport].accent);
  tabs($("styles"), STYLES, state.style, (k) => { state.style = k; store.set("style", k); show(); });
  const secs = $("sections");
  secs.hidden = state.style !== "OVERLAY";
  // "Photo" only once there is one
  const secList = Object.fromEntries(Object.entries(SECTIONS).filter(([k]) => k !== "PHOTO" || state.background));
  if (!secList[state.section]) state.section = "LAYOUT";
  tabs(secs, secList, state.section, (k) => { state.section = k; show(); });
  controls(w);

  const photo = $("photo"), route = $("route");
  photo.classList.toggle("on", !!state.background || !!state.backgroundUrl);
  photo.querySelector("span").textContent = state.backgroundUrl ? tr("Gesetzt", "Set") : tr("Hintergrund wählen", "Pick a background");
  photo.querySelector("i").textContent = state.backgroundUrl ? "✕" : "+";
  route.classList.toggle("on", !!state.route);
  route.querySelector("span").textContent = state.route
    ? `${D.formatDistance(state.route.distanceMeters).value} ${D.formatDistance(state.route.distanceMeters).unit.toLowerCase()} · ${D.formatInt(state.route.pointCount ?? 0)} ${tr("Pkt.", "pts")}`
    : ROUTE_HINT;
  route.querySelector("i").textContent = state.route ? "✕" : "+";
  $("ed-hint").textContent = state.style === "OVERLAY" && state.overlay.withPhoto && state.background ? tr("Fertige Story 1080 × 1920 mit deinem Foto", "Finished 1080 × 1920 story with your photo")
    : state.style === "OVERLAY" ? tr("Instagram: Story öffnen → Sticker → Foto → PNG wählen", "Instagram: open a story → Stickers → Photo → pick the PNG")
    : state.format === "FEED" ? tr("Fertiger Beitrag 1080 × 1350 für den Instagram-Feed", "Finished 1080 × 1350 post for the Instagram feed")
    : tr("Fertige Story 1080 × 1920, als Hintergrund in Instagram", "Finished 1080 × 1920 story, use it as the Instagram background");
  draw();
}

// ------------------------------------------------------------------ preview + export

let frame = 0, exportBlob = null, exportKey = "";
function draw() {
  cancelAnimationFrame(frame);
  frame = requestAnimationFrame(() => {
    const w = workout();
    if (!w) return;
    const baked = state.style === "OVERLAY" && state.overlay.withPhoto && !!state.background; // photo is part of the PNG
    const stage = $("stage"), overlay = state.style === "OVERLAY" && !baked;
    stage.className = "stage" + (overlay ? " sticker" + (state.backgroundUrl ? " photo" : " checker" + (state.overlay.ink === "BLACK" ? " light" : "")) : "") + (state.style !== "OVERLAY" && state.format === "FEED" ? " feed" : "");
    // the sticker is previewed on your photo if you picked one; the PNG itself stays transparent
    stage.style.backgroundImage = overlay && state.backgroundUrl ? `url("${state.backgroundUrl}")` : "";
    stage.replaceChildren(render(w, state.style, state.background, state.overlay, state.format, 0.5));
    // render the full-size PNG ahead of time: iOS only allows the share sheet right after a tap
    exportBlob = null;
    const key = exportKey = Math.random().toString(36);
    render(w, state.style, state.background, state.overlay, state.format, 1).toBlob((b) => { if (key === exportKey) exportBlob = b; }, "image/png");
  });
}

async function pngFile() {
  for (let i = 0; !exportBlob && i < 50; i++) await new Promise((r) => setTimeout(r, 40));
  if (!exportBlob) throw new Error("render");
  const day = new Date().toISOString().slice(0, 10);
  return new File([exportBlob], `fitshare-${state.style.toLowerCase()}-${day}.png`, { type: "image/png" });
}

function download(file) {
  const a = document.createElement("a");
  a.href = URL.createObjectURL(file); a.download = file.name;
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}

$("save").onclick = async () => {
  try {
    const f = await pngFile();
    download(f);
    if (state.style === "OVERLAY") rememberLook();
    toast(/iPhone|iPad/.test(navigator.userAgent) ? tr("Tipp: „Teilen“ → „Bild sichern“ legt es in Fotos ab", "Tip: “Share” → “Save Image” puts it in Photos") : tr("PNG gesichert", "PNG saved"));
  } catch { toast(tr("Sichern hat nicht geklappt", "Saving failed")); }
};
$("share").onclick = async () => {
  try {
    const f = await pngFile();
    if (navigator.canShare?.({ files: [f] })) { await navigator.share({ files: [f] }); if (state.style === "OVERLAY") rememberLook(); }
    else { download(f); toast(tr("Teilen geht in diesem Browser nicht, PNG wurde gesichert", "This browser can't share files, the PNG was saved instead")); }
  } catch (e) { if (e?.name !== "AbortError") toast(tr("Teilen fehlgeschlagen", "Sharing failed")); }
};

$("name-dialog").addEventListener("close", () => {
  const name = $("look-name").value.trim();
  if ($("name-dialog").returnValue !== "ok" || !name) return;
  state.looks.presets = [...state.looks.presets.filter((l) => l.name.toLowerCase() !== name.toLowerCase()), { name, options: state.overlay, savedAt: Date.now() }];
  store.set("looks", state.looks);
  toast(tr(`Look „${name}“ gespeichert`, `Saved look “${name}”`));
  show();
});

// ------------------------------------------------------------------ wiring

$("pick").onclick = () => $("files").click();
$("demo").onclick = () => { const { stats, route } = D.demoWorkout(); state.stats = stats; state.route = route; state.sport = null; setBackground(null); saveSession(); show(); };

// start screen: the steps for one fitness app at a time; the browser can't see installed apps, so it remembers the last pick
let source = D.SOURCES.find((x) => x.id === store.get("source", null)) || D.SOURCES.at(-1);
function showSources() {
  $("sources").replaceChildren(...D.SOURCES.map((x) => {
    const b = chip(D.sourceTitle(x), x === source, () => { source = x; store.set("source", x.id); showSources(); });
    b.role = "tab"; b.setAttribute("aria-selected", x === source);
    return b;
  }));
  const step = (title, [de, en]) => { const li = document.createElement("li"), b = document.createElement("b"), sp = document.createElement("span"); b.textContent = title; sp.textContent = tr(de, en); li.append(b, sp); return li; };
  $("steps").replaceChildren(
    step(tr("Werte", "Numbers"), source.numbers),
    step(tr("Karte · optional", "Map · optional"), source.map),
    step("Sticker", ["Gestalten, als PNG sichern, in Instagram als Foto-Sticker einfügen.", "Style it, save the PNG, add it in Instagram as a photo sticker."]),
  );
  // bring the picked chip into view sideways only; scrollIntoView would also move the page
  const row = $("sources"), on = row.querySelector('[aria-pressed="true"]');
  if (on && (on.offsetLeft < row.scrollLeft || on.offsetLeft + on.offsetWidth > row.scrollLeft + row.clientWidth)) row.scrollLeft = on.offsetLeft - 20;
}
showSources();
$("files").onchange = (e) => { open(e.target.files); e.target.value = ""; };
$("photo").onclick = () => { if (state.backgroundUrl) { setBackground(null); show(); } else $("photo-input").click(); };
$("photo-input").onchange = (e) => { setBackground(e.target.files[0]); e.target.value = ""; show(); };
$("route").onclick = () => { if (state.route) { state.route = null; saveSession(); show(); } else $("route-input").click(); };
$("route-input").onchange = (e) => { open(e.target.files); e.target.value = ""; };
$("back").onclick = () => { state.stats = null; state.route = null; state.sport = null; setBackground(null); saveSession(); show(); };

addEventListener("dragover", (e) => { e.preventDefault(); document.body.classList.add("dragging"); });
addEventListener("dragleave", (e) => { if (!e.relatedTarget) document.body.classList.remove("dragging"); });
addEventListener("drop", (e) => { e.preventDefault(); document.body.classList.remove("dragging"); open(e.dataTransfer.files); });
addEventListener("paste", (e) => { const files = [...(e.clipboardData?.files || [])]; if (files.length) open(files); });

// files shared to the installed web app (Android share sheet) arrive through the service worker
async function sharedFiles() {
  if (!new URLSearchParams(location.search).has("shared") || !("caches" in window)) return;
  history.replaceState(null, "", location.pathname);
  const cache = await caches.open("fs-share");
  const files = [];
  for (const req of await cache.keys()) {
    const res = await cache.match(req);
    files.push(new File([await res.blob()], decodeURIComponent(res.headers.get("x-name") || "shared"), { type: res.headers.get("content-type") || "" }));
    await cache.delete(req);
  }
  await open(files);
}

if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
await Promise.all(['800 40px "FS Display"', '700 20px "FS Text"'].map((f) => document.fonts.load(f).catch(() => {})));
show();
sharedFiles();
