// FitShare web: draws the overlay sticker and the two full story designs on a canvas.
// Port of export/Overlay.kt and export/StoryRenderer.kt; numbers are the same 1080 px layout units.
import {
  SPORTS, availableStats, bibNumber, formatShortDate, formatSplit, formatStoryDate, hrZoneName, pickStats,
  sportLabel, storyContent, tr, LOCALE,
} from "./data.js";

export const W = 1080;
export const LAYOUTS = { STACK: [3, "Stapel", "Stack"], ROW: [3, "Reihe", "Row"], CARD: [4, "Karte", "Card"], ROUTE: [3, "Route", "Map"] };
export const INKS = { WHITE: ["Weiß", "White"], BLACK: ["Schwarz", "Black"], SPORT: ["Sportfarbe", "Sport colour"] };
export const STYLES = { OVERLAY: ["Overlay", "Overlay"], NIGHT: ["Nacht", "Night"], BIB: ["Startnummer", "Race bib"] };
export const FORMATS = { STORY: ["9:16", 1920], FEED: ["4:5", 1350] };
export const defaultOverlay = () => ({
  layout: "STACK", ink: "WHITE", stats: null, showRoute: true, showProfile: false, showZones: false,
  showHeart: false, showSplits: false, showDate: false, showBrand: true,
});

const INK = "#14140F", PAPER = "#EFEBE1", BONE = "#F4F1EA", NIGHT = "#0C0D0B";
const ZONE_COLORS = ["#5AA9FF", "#3DDC97", "#FFD23F", "#FF8A3D", "#FF3D5A"];
const DISPLAY = "FS Display", TEXT = "FS Text";
const UPPER = (s) => s.toLocaleUpperCase(LOCALE);

export function alpha(color, a) {
  const h = color.replace("#", "");
  const n = parseInt(h.length === 3 ? h.split("").map((c) => c + c).join("") : h, 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

/** Android Paint in canvas terms: font, colour, tracking (em), alignment, optional soft shadow. */
function paint(c, family, weight, size, color, { tracking = 0, align = "center", shadow = null } = {}) {
  c.font = `${weight} ${size}px "${family}"`;
  c.fillStyle = color;
  c.textAlign = align;
  if ("letterSpacing" in c) c.letterSpacing = `${tracking * size}px`;
  if (shadow) { c.shadowColor = shadow.color; c.shadowBlur = shadow.blur; c.shadowOffsetY = shadow.dy; }
  else { c.shadowColor = "transparent"; c.shadowBlur = 0; c.shadowOffsetY = 0; }
  return { family, weight, size, color, tracking, align, shadow };
}
const apply = (c, p, size = p.size) => paint(c, p.family, p.weight, size, p.color, { ...p });
const measure = (c, p, s, size = p.size) => { apply(c, p, size); return c.measureText(s).width; };
function fitSize(c, p, s, maxW, min = 20) { let size = p.size; while (measure(c, p, s, size) > maxW && size > min) size -= 4; return size; }

function newCanvas(w, h) {
  const cv = document.createElement("canvas");
  cv.width = Math.max(1, Math.round(w)); cv.height = Math.max(1, Math.round(h));
  return cv;
}

// ------------------------------------------------------------------ route

function routeBounds(route) {
  const all = route.segments.flat();
  if (all.length < 2) return null;
  let minLat = Infinity, maxLat = -Infinity, minLon = Infinity, maxLon = -Infinity;
  for (const p of all) { minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat); minLon = Math.min(minLon, p.lon); maxLon = Math.max(maxLon, p.lon); }
  // equirectangular with latitude correction so the route isn't stretched sideways
  const kx = Math.cos(((minLat + maxLat) / 2) * Math.PI / 180);
  const spanX = (maxLon - minLon) * kx, spanY = maxLat - minLat;
  if (spanX < 1e-7 && spanY < 1e-7) return null;
  return { all, minLat, maxLat, minLon, kx, spanX, spanY };
}

/** Size of the box the route needs inside maxW × maxH, keeping its real proportions. */
function fitRouteBox(route, maxW, maxH) {
  const b = routeBounds(route);
  if (!b) return null;
  const s = Math.min(maxW / Math.max(b.spanX, 1e-9), maxH / Math.max(b.spanY, 1e-9));
  return [Math.max(60, b.spanX * s), Math.max(60, b.spanY * s)];
}

function drawRoute(c, route, box, style, pal, accent) {
  const b = routeBounds(route);
  if (!b) return;
  const s = Math.min(box.w / Math.max(b.spanX, 1e-9), box.h / Math.max(b.spanY, 1e-9));
  const ox = box.x + box.w / 2 - (b.spanX * s) / 2, oy = box.y + box.h / 2 - (b.spanY * s) / 2;
  const px = (p) => ox + (p.lon - b.minLon) * b.kx * s, py = (p) => oy + (b.maxLat - p.lat) * s;
  const path = new Path2D();
  for (const seg of route.segments) {
    const step = Math.max(1, Math.floor(seg.length / 1500)); // plenty for 1080 px, keeps drawing fast
    seg.forEach((p, i) => {
      if (i % step !== 0 && i !== seg.length - 1) return;
      if (i === 0) path.moveTo(px(p), py(p)); else path.lineTo(px(p), py(p));
    });
  }
  const stroke = (width, color, glow = 0) => {
    c.save();
    c.lineWidth = width; c.strokeStyle = color; c.lineCap = "round"; c.lineJoin = "round";
    // glow via shadow: works in every browser, unlike ctx.filter
    if (glow) { c.shadowColor = color; c.shadowBlur = glow * 2; c.shadowOffsetY = 0; }
    else if (pal.shadow) { c.shadowColor = "rgba(0,0,0,.53)"; c.shadowBlur = 10; c.shadowOffsetY = 2; }
    c.stroke(path);
    c.restore();
  };
  if (style === "NIGHT") { stroke(34, alpha(pal.route, 0.28), 22); stroke(16, alpha(pal.route, 0.55), 6); }
  stroke(9, pal.route);

  const start = b.all[0], end = b.all[b.all.length - 1];
  const dot = (p, r, fill, line = 0) => {
    c.beginPath(); c.arc(px(p), py(p), r, 0, Math.PI * 2);
    if (line) { c.lineWidth = line; c.strokeStyle = fill; c.stroke(); } else { c.fillStyle = fill; c.fill(); }
  };
  c.save();
  if (pal.shadow) { c.shadowColor = "rgba(0,0,0,.4)"; c.shadowBlur = 10; c.shadowOffsetY = 2; }
  if (style === "OVERLAY") { dot(start, 14, pal.fg, 6); dot(end, 16, pal.fg); c.restore(); return; }
  dot(start, 17, pal.fg); dot(start, 9, style === "BIB" ? PAPER : NIGHT);
  dot(end, 19, accent); dot(end, 19, pal.fg, 5);
  c.restore();
}

// ------------------------------------------------------------------ overlay sticker

const GAP = 36, PAD = 44;

/** Transparent PNG cropped to its content; built as a stack of blocks so every option combination fits. */
export function renderOverlay(workout, o, scale = 1) {
  const accent = SPORTS[workout.sport].accent;
  const ink = o.ink === "WHITE" ? "#FFFFFF" : o.ink === "BLACK" ? INK : accent;
  const shadowOn = o.layout !== "CARD"; // the card itself gives contrast
  const sh = shadowOn ? { color: o.ink === "BLACK" ? "rgba(255,255,255,.33)" : "rgba(0,0,0,.4)", blur: 10, dy: 2 } : null;
  const stats = pickStats(workout, o.stats, LAYOUTS[o.layout][0]);
  const route = o.showRoute ? workout.route : null;
  const profile = o.showProfile && workout.route?.profile?.length > 1 ? workout.route.profile : null;
  const zones = o.showZones && workout.stats.hrZoneSeconds.reduce((a, b) => a + b, 0) > 0 ? workout.stats.hrZoneSeconds : null;
  const heart = o.showHeart && workout.stats.hrCurve.length > 1 ? workout.stats.hrCurve : null;
  const sp = o.showSplits && (workout.route?.splitSeconds?.length ?? 0) >= 2 ? workout.route.splitSeconds : null;
  const cx = W / 2, card = o.layout === "CARD", cw = card ? 848 : 920, left = cx - cw / 2;
  const blocks = [];
  const P = (c, fam, wt, size, color = ink, opts = {}) => paint(c, fam, wt, size, color, { shadow: sh, ...opts });

  const dateLine = () => workout.startMillis == null ? null : {
    h: 44, draw: (c, y) => { P(c, TEXT, 700, 30, alpha(ink, 0.85), { tracking: 0.14 }); c.fillText(`${UPPER(sportLabel(workout.sport))} · ${formatShortDate(workout.startMillis)}`, cx, y + 32); },
  };
  const routeBlock = (maxW, maxH) => {
    if (!route) return null;
    const box = fitRouteBox(route, maxW, maxH);
    if (!box) return null;
    const [rw, rh] = box;
    return { h: rh, draw: (c, y) => drawRoute(c, route, { x: cx - rw / 2, y, w: rw, h: rh }, "OVERLAY", { fg: ink, route: ink, shadow: shadowOn }, ink) };
  };
  const header = (c, y, title, right) => {
    P(c, TEXT, 700, 26, alpha(ink, 0.85), { tracking: 0.14, align: "left" }); c.fillText(title, left, y + 26);
    if (right) { P(c, TEXT, 700, 26, ink, { tracking: 0.08, align: "right" }); c.fillText(right, left + cw, y + 26); }
  };
  // filled line chart under a label row; shared by elevation and heart rate
  const curveBlock = (title, right, pts, minSpan) => ({
    h: 44 + 150, draw: (c, y) => {
      header(c, y, title, right);
      const top = y + 44, h = 150, min = Math.min(...pts), span = Math.max(Math.max(...pts) - min, minSpan);
      const path = new Path2D();
      pts.forEach((e, i) => { const x = left + (cw * i) / (pts.length - 1), py = top + h - ((e - min) / span) * (h - 8); i ? path.lineTo(x, py) : path.moveTo(x, py); });
      const fill = new Path2D(path); fill.lineTo(left + cw, top + h); fill.lineTo(left, top + h); fill.closePath();
      const g = c.createLinearGradient(0, top, 0, top + h); g.addColorStop(0, alpha(ink, 0.45)); g.addColorStop(1, alpha(ink, 0.04));
      c.save(); c.shadowColor = "transparent"; c.fillStyle = g; c.fill(fill); c.restore();
      c.save(); c.lineWidth = 5; c.strokeStyle = ink; c.lineJoin = "round";
      if (sh) { c.shadowColor = sh.color; c.shadowBlur = 8; c.shadowOffsetY = 2; }
      c.stroke(path); c.restore();
    },
  });
  const profileBlock = () => {
    if (!profile) return null;
    const gain = availableStats(workout).find((x) => x.key === "ELEVATION");
    return curveBlock(tr("HÖHENPROFIL", "ELEVATION"), gain ? `+${gain.value} ${gain.unit}` : null, profile, 10);
  };
  const heartBlock = () => {
    if (!heart) return null;
    const s = workout.stats;
    const right = s.averageHR > 0 ? `Ø ${s.averageHR}` + (s.maxHR > 0 ? ` · MAX ${s.maxHR}` : "") + " BPM" : null;
    return curveBlock(tr("PULS", "HEART RATE"), right, heart, 20);
  };
  // one bar per km, taller = faster; the fastest km in full ink
  const splitsBlock = () => {
    if (!sp) return null;
    const best = sp.indexOf(Math.min(...sp)), h = 120;
    return {
      h: 44 + h, draw: (c, y) => {
        header(c, y, tr("KM-SPLITS", "KM SPLITS"), tr("SCHNELLSTER KM ", "FASTEST KM ") + `${best + 1} · ${formatSplit(sp[best])}`);
        const slowest = Math.max(...sp), fastest = Math.min(...sp), gap = sp.length > 40 ? 2 : 6;
        const bw = (cw - gap * (sp.length - 1)) / sp.length, r = Math.min(6, bw / 2);
        c.save(); if (sh) { c.shadowColor = sh.color; c.shadowBlur = 6; c.shadowOffsetY = 2; }
        sp.forEach((t, i) => {
          // never shorter than a quarter so slow kms stay visible
          const k = slowest > fastest ? (slowest - t) / (slowest - fastest) : 1, bh = h * (0.25 + 0.75 * k);
          c.fillStyle = i === best ? ink : alpha(ink, 0.5);
          c.beginPath(); c.roundRect(left + i * (bw + gap), y + 44 + h - bh, bw, bh, r); c.fill();
        });
        c.restore();
      },
    };
  };
  const zonesBlock = () => {
    if (!zones) return null;
    const total = zones.reduce((a, b) => a + b, 0), top = zones.indexOf(Math.max(...zones));
    return {
      h: 44 + 20, draw: (c, y) => {
        header(c, y, tr("PULSZONEN", "HR ZONES"), `${UPPER(hrZoneName(top))} ${Math.round((zones[top] / total) * 100)} %`);
        const gap = 8, usable = cw - gap * (zones.filter((z) => z > 0).length - 1);
        let x = left;
        c.save(); c.shadowColor = "transparent";
        zones.forEach((s, i) => { if (s <= 0) return; const w = Math.max(10, (s / total) * usable); c.fillStyle = ZONE_COLORS[i]; c.beginPath(); c.roundRect(x, y + 44, w, 20, 10); c.fill(); x += w + gap; });
        c.restore();
      },
    };
  };
  const brandBlock = () => !o.showBrand ? null : {
    h: 36, draw: (c, y) => { P(c, TEXT, 800, 28, alpha(ink, 0.85), { tracking: 0.34 }); c.fillText("FITSHARE", cx + 5, y + 30); }, // +5: tracking trails the last glyph
  };
  // value + smaller unit, drawn as one group around x (centre) or from x (left)
  const drawValue = (c, st, x, base, vp, up, maxW, centered) => {
    const gap = st.unit ? 14 : 0;
    let vs = vp.size, us = up.size;
    while (measure(c, vp, st.value, vs) + gap + (st.unit ? measure(c, up, st.unit, us) : 0) > maxW && vs > 30) { vs -= 4; us = (up.size * vs) / vp.size; }
    const vw = measure(c, vp, st.value, vs), total = vw + gap + (st.unit ? measure(c, up, st.unit, us) : 0);
    const start = centered ? x - total / 2 : x;
    apply(c, { ...vp, align: "left" }, vs); c.fillText(st.value, start, base);
    if (st.unit) { apply(c, { ...up, align: "left" }, us); c.fillText(st.unit, start + vw + gap, base); }
  };
  const vpaint = (fam, wt, size, color = ink, tracking = 0) => ({ family: fam, weight: wt, size, color, tracking, align: "left", shadow: sh });

  if (o.layout === "STACK") {
    if (o.showDate) blocks.push(dateLine());
    for (const st of stats) blocks.push({
      h: 62 + 150 * 0.74 + 24, draw: (c, y) => {
        P(c, TEXT, 700, 34, alpha(ink, 0.85), { tracking: 0.16 }); c.fillText(UPPER(st.label), cx, y + 34);
        drawValue(c, st, cx, y + 62 + 150 * 0.74, vpaint(DISPLAY, 800, 150, ink, -0.01), vpaint(DISPLAY, 700, 70, ink, 0.02), cw, true);
      },
    });
    blocks.push(routeBlock(760, 560));
  } else if (o.layout === "ROW") {
    if (o.showDate) blocks.push(dateLine());
    blocks.push(routeBlock(760, 480));
    const colW = cw / Math.max(1, stats.length);
    blocks.push({
      h: 36 + 110 * 0.74 + 10, draw: (c, y) => stats.forEach((st, i) => {
        const mid = left + colW * i + colW / 2;
        if (i > 0) { c.save(); c.shadowColor = "transparent"; c.strokeStyle = alpha(ink, 0.4); c.lineWidth = 3; c.beginPath(); c.moveTo(left + colW * i, y + 8); c.lineTo(left + colW * i, y + 36 + 110 * 0.74); c.stroke(); c.restore(); }
        P(c, TEXT, 700, 26, alpha(ink, 0.85), { tracking: 0.14 }); c.fillText(UPPER(st.label), mid, y + 26);
        drawValue(c, st, mid, y + 36 + 110 * 0.74, vpaint(DISPLAY, 800, 100), vpaint(DISPLAY, 700, 40), colW - 36, true);
      }),
    });
  } else if (o.layout === "ROUTE") {
    blocks.push(routeBlock(920, 900));
    const line = stats.map((x) => `${x.value} ${x.unit}`.trim()).join("   ·   ");
    const p = { family: DISPLAY, weight: 800, size: 64, color: ink, tracking: 0.02, align: "center", shadow: sh };
    const probe = newCanvas(1, 1).getContext("2d");
    const size = fitSize(probe, p, line, cw);
    blocks.push({ h: size * 0.8, draw: (c, y) => { apply(c, p, size); c.fillText(line, cx, y + size * 0.76); } });
    if (o.showDate) blocks.push(dateLine());
  } else { // CARD: sport dot + name left, date right, then route and a 2-column stat grid
    blocks.push({
      h: 44, draw: (c, y) => {
        c.fillStyle = accent; c.beginPath(); c.arc(left + 12, y + 22, 12, 0, Math.PI * 2); c.fill();
        P(c, TEXT, 700, 34, ink, { align: "left" }); c.fillText(sportLabel(workout.sport), left + 38, y + 34);
        if (o.showDate && workout.startMillis != null) { P(c, TEXT, 500, 30, alpha(ink, 0.7), { align: "right" }); c.fillText(formatShortDate(workout.startMillis), left + cw, y + 34); }
      },
    });
    blocks.push(routeBlock(cw, 380));
    const rows = Math.ceil(stats.length / 2), cellW = cw / 2;
    const labelColor = o.ink === "BLACK" ? alpha(ink, 0.6) : accent;
    blocks.push({
      h: rows * 150, draw: (c, y) => stats.forEach((st, i) => {
        const x = left + (i % 2) * cellW, top = y + Math.floor(i / 2) * 150;
        if (i % 2 === 0 && i > 0) { c.save(); c.strokeStyle = alpha(ink, 0.16); c.lineWidth = 2; c.beginPath(); c.moveTo(left, top); c.lineTo(left + cw, top); c.stroke(); c.restore(); }
        P(c, TEXT, 650, 28, labelColor, { align: "left" }); c.fillText(st.label, x, top + 44);
        drawValue(c, { ...st, unit: st.unit.toLocaleLowerCase(LOCALE) }, x, top + 126, vpaint(TEXT, 650, 84, ink, -0.01), vpaint(TEXT, 600, 34, alpha(ink, 0.65), 0.02), cellW - 20, false);
      }),
    });
  }
  blocks.push(profileBlock(), heartBlock(), splitsBlock(), zonesBlock(), brandBlock());
  const list = blocks.filter(Boolean);

  const inner = PAD + list.reduce((a, b) => a + b.h, 0) + GAP * Math.max(0, list.length - 1) + PAD;
  const cardPad = 56, margin = 60; // margin: room for the card's shadow
  const height = card ? inner + 2 * cardPad + 2 * margin - 2 * PAD : inner;
  const cv = newCanvas(W * scale, height * scale);
  const c = cv.getContext("2d");
  c.scale(scale, scale);
  let y = PAD;
  if (card) {
    const r = { x: cx - cw / 2 - cardPad, y: margin, w: cw + 2 * cardPad, h: height - 2 * margin };
    c.save();
    c.fillStyle = o.ink === "BLACK" ? "rgba(255,255,255,.66)" : "rgba(0,0,0,.42)";
    c.shadowColor = "rgba(0,0,0,.22)"; c.shadowBlur = 48; c.shadowOffsetY = 18;
    c.beginPath(); c.roundRect(r.x, r.y, r.w, r.h, 64); c.fill();
    c.restore();
    c.strokeStyle = alpha(ink, 0.18); c.lineWidth = 2;
    c.beginPath(); c.roundRect(r.x + 1, r.y + 1, r.w - 2, r.h - 2, 63); c.stroke();
    y = margin + cardPad;
  }
  for (const b of list) { c.save(); b.draw(c, y); c.restore(); y += b.h + GAP; }
  return cv;
}

// ------------------------------------------------------------------ full stories

function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function grain(c, color, strength, h) {
  const r = rng(7);
  c.fillStyle = alpha(color, strength);
  for (let i = 0; i < 9000; i++) c.fillRect(r() * W - 1.1, r() * h - 1.1, 2.2, 2.2);
}
function drawCropped(c, img, h) {
  const iw = img.naturalWidth || img.width, ih = img.naturalHeight || img.height;
  const src = iw / ih, dst = W / h;
  if (src > dst) { const w = ih * dst; c.drawImage(img, (iw - w) / 2, 0, w, ih, 0, 0, W, h); }
  else { const hh = iw / dst; c.drawImage(img, 0, (ih - hh) / 2, iw, hh, 0, 0, W, h); }
}

/** 1080×1920 story or 1080×1350 feed post. The editor preview calls this at a smaller scale. */
export function render(workout, style, background, overlay, format = "STORY", scale = 1) {
  if (style === "OVERLAY") return renderOverlay(workout, overlay, scale);
  const h = FORMATS[format][1];
  const cv = newCanvas(W * scale, h * scale);
  const c = cv.getContext("2d");
  c.scale(scale, scale);
  const accent = SPORTS[workout.sport].accent;
  const pal = style === "NIGHT"
    ? { fg: BONE, muted: alpha(BONE, 0.58), line: alpha(BONE, 0.16), route: accent, shadow: false }
    : { fg: INK, muted: alpha(INK, 0.6), line: alpha(INK, 0.2), route: INK, shadow: false };

  // background
  if (style === "NIGHT") {
    if (background) {
      drawCropped(c, background, h);
      const g = c.createLinearGradient(0, 0, 0, h);
      g.addColorStop(0, alpha(NIGHT, 0.35)); g.addColorStop(0.35, alpha(NIGHT, 0.55)); g.addColorStop(0.7, alpha(NIGHT, 0.94)); g.addColorStop(1, alpha(NIGHT, 0.94));
      c.fillStyle = g; c.fillRect(0, 0, W, h);
    } else {
      c.fillStyle = NIGHT; c.fillRect(0, 0, W, h);
      for (const [x, y, r, a] of [[W * 0.92, h * 0.12, 1150, 0.34], [0, h * 0.95, 900, 0.12]]) {
        const g = c.createRadialGradient(x, y, 0, x, y, r); g.addColorStop(0, alpha(accent, a)); g.addColorStop(1, alpha(accent, 0));
        c.fillStyle = g; c.fillRect(0, 0, W, h);
      }
    }
    grain(c, "#FFFFFF", 0.05, h);
  } else {
    c.fillStyle = PAPER; c.fillRect(0, 0, W, h);
    if (background) { drawCropped(c, background, h); c.fillStyle = alpha(PAPER, 0.82); c.fillRect(0, 0, W, h); }
    grain(c, INK, 0.05, h);
  }

  const M = 84, content = storyContent(workout);
  const feed = h < 1600; // the 4:5 post: smaller title, one stat row, zones only without a route
  const headerBase = feed ? 110 : 150;
  const title = UPPER(sportLabel(workout.sport));
  const titleP = { family: DISPLAY, weight: 900, size: feed ? 150 : 210, color: style === "NIGHT" ? accent : pal.fg, tracking: -0.01, align: "left" };
  const titleSize = fitSize(c, titleP, title, W - 2 * M);
  const titleBase = headerBase + 40 + titleSize * 0.78, dateBase = titleBase + 66, headerBottom = dateBase + 44;

  if (style === "BIB") {
    c.fillStyle = accent; c.fillRect(0, 0, W, headerBottom + 26);
    // perforated edge under the coloured band, like a tear-off start number
    c.fillStyle = PAPER;
    for (let x = 18; x < W; x += 36) { c.beginPath(); c.arc(x, headerBottom + 26, 7, 0, Math.PI * 2); c.fill(); }
  }
  paint(c, TEXT, 800, 28, pal.fg, { tracking: 0.32, align: "left" }); c.fillText("FITSHARE", M, headerBase);
  paint(c, DISPLAY, 800, 40, pal.fg, { tracking: 0.02, align: "right" }); c.fillText(`Nº ${bibNumber(workout.startMillis)}`, W - M, headerBase + 2);
  apply(c, titleP, titleSize); c.fillText(title, M - 6, titleBase);
  if (workout.startMillis != null) { paint(c, TEXT, 600, 32, style === "BIB" ? alpha(INK, 0.72) : pal.muted, { tracking: 0.06, align: "left" }); c.fillText(formatStoryDate(workout.startMillis), M, dateBase); }

  // footer + perforation (bottom-up layout from here)
  const footerBase = h - 96;
  paint(c, TEXT, 700, 24, pal.muted, { tracking: 0.24, align: "left" }); c.fillText("FITSHARE", M, footerBase);
  const perfY = footerBase - 66;
  c.save(); c.strokeStyle = pal.line; c.lineWidth = 3; c.setLineDash([14, 12]); c.beginPath(); c.moveTo(M, perfY); c.lineTo(W - M, perfY); c.stroke(); c.restore();

  // stat table: 3 columns, hairlines like a race ticket
  const cells = feed ? content.cells.slice(0, 3) : content.cells;
  const rows = Math.ceil(cells.length / 3), rowH = feed ? 150 : 172;
  const gridTop = perfY - 46 - rows * rowH, colW = (W - 2 * M) / 3;
  cells.forEach((st, i) => {
    const row = Math.floor(i / 3), col = i % 3, top = gridTop + row * rowH;
    c.strokeStyle = pal.line; c.lineWidth = 2; c.beginPath();
    if (col === 0) { c.moveTo(M, top); c.lineTo(W - M, top); } else { c.moveTo(M + col * colW, top + 26); c.lineTo(M + col * colW, top + rowH - 14); }
    c.stroke();
    const x = M + col * colW + (col > 0 ? 30 : 0), maxW = colW - (col > 0 ? 44 : 14);
    paint(c, TEXT, 700, 24, pal.muted, { tracking: 0.16, align: "left" }); c.fillText(UPPER(st.label), x, top + (feed ? 44 : 56));
    const unitP = { family: TEXT, weight: 700, size: 26, color: pal.muted, tracking: 0.08, align: "left" };
    const unitW = st.unit ? measure(c, unitP, st.unit) + 10 : 0;
    const valueP = { family: DISPLAY, weight: 800, size: feed ? 84 : 96, color: pal.fg, align: "left" };
    const vs = fitSize(c, valueP, st.value, maxW - unitW);
    const base = top + rowH - 22;
    apply(c, valueP, vs); c.fillText(st.value, x, base);
    const vw = c.measureText(st.value).width;
    if (st.unit) { apply(c, unitP); c.fillText(st.unit, x + vw + 10, base); }
  });

  // heart-rate zones
  let heroLimit = gridTop - 64;
  const zones = workout.stats.hrZoneSeconds, zsum = zones.reduce((a, b) => a + b, 0);
  if (zsum > 0 && !(feed && workout.route)) {
    const barTop = gridTop - 70, labelBase = barTop - 26, top = zones.indexOf(Math.max(...zones));
    paint(c, TEXT, 700, 24, pal.muted, { tracking: 0.16, align: "left" }); c.fillText(tr("PULSZONEN", "HEART RATE ZONES"), M, labelBase);
    paint(c, TEXT, 700, 24, pal.fg, { tracking: 0.16, align: "right" }); c.fillText(`${UPPER(hrZoneName(top))} ${Math.round((zones[top] / zsum) * 100)} %`, W - M, labelBase);
    let x = M;
    const gap = 8, usable = W - 2 * M - gap * (zones.filter((z) => z > 0).length - 1);
    zones.forEach((s, i) => { if (s <= 0) return; const w = Math.max(10, (s / zsum) * usable); c.fillStyle = ZONE_COLORS[i]; c.beginPath(); c.roundRect(x, barTop, w, 22, 11); c.fill(); x += w + gap; });
    heroLimit = labelBase - 72;
  }

  // hero number
  const hero = content.hero, hasRoute = !!workout.route;
  const heroP = { family: DISPLAY, weight: 900, size: (hasRoute ? 330 : 460) * (feed ? 0.6 : 1), color: pal.fg, tracking: -0.02, align: "left" };
  const unitP = { family: DISPLAY, weight: 800, size: 0, color: style === "BIB" ? INK : accent, tracking: 0.02, align: "left" };
  let hs = heroP.size;
  const totalW = () => measure(c, heroP, hero.value, hs) + (hero.unit ? 18 + measure(c, unitP, hero.unit, hs * 0.34) : 0);
  while (totalW() > W - 2 * M && hs > 120) hs -= 8;
  const heroBase = heroLimit;
  apply(c, heroP, hs); c.fillText(hero.value, M - 8, heroBase);
  if (hero.unit) {
    const ux = M - 8 + measure(c, heroP, hero.value, hs) + 18, us = hs * 0.34;
    if (style === "BIB") { const uw = measure(c, unitP, hero.unit, us); c.fillStyle = accent; c.fillRect(ux - 10, heroBase - us * 0.82, uw + 20, us * 0.82 + 10); }
    apply(c, unitP, us); c.fillText(hero.unit, ux, heroBase);
  }
  const heroTop = heroBase - hs * 0.74;

  // route fills the space between header and hero
  const routeTop = headerBottom + (style === "BIB" ? 110 : 70), routeBottom = heroTop - 70;
  if (workout.route && routeBottom - routeTop > 180) drawRoute(c, workout.route, { x: M, y: routeTop, w: W - 2 * M, h: routeBottom - routeTop }, style, pal, accent);
  return cv;
}
