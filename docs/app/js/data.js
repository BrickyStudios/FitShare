// FitShare web: file parsing, workout model and stat formatting.
// Ported from the Android app (app/src/main/java/com/fitshare/data); keep both in step.
// No DOM in here, so tools/webapp_test.mjs can run it in Node.

export const DE = (globalThis.navigator?.language || "en").toLowerCase().startsWith("de");
export const LOCALE = DE ? "de-DE" : "en-GB";
export const tr = (de, en) => (DE ? de : en);

// ------------------------------------------------------------------ model

export const SPORTS = {
  CYCLING: { de: "Radfahren", en: "Cycling", accent: "#FF5B1F" },
  RUNNING: { de: "Laufen", en: "Running", accent: "#C6F432" },
  SWIMMING: { de: "Schwimmen", en: "Swimming", accent: "#27D3F5" },
  WALKING: { de: "Wandern", en: "Hiking", accent: "#FFB423" },
  WORKOUT: { de: "Training", en: "Workout", accent: "#FF4F8B" },
};
export const sportLabel = (s) => tr(SPORTS[s].de, SPORTS[s].en);

export const STAT_LABELS = {
  DISTANCE: ["Distanz", "Distance"], TIME: ["Zeit", "Time"], SPEED: ["Ø Tempo", "Avg speed"],
  PACE: ["Ø Pace", "Avg pace"], ELEVATION: ["Anstieg", "Elev. gain"], AVG_HR: ["Ø Puls", "Avg HR"],
  MAX_HR: ["Max Puls", "Max HR"], ENERGY: ["Energie", "Calories"], STEPS: ["Schritte", "Steps"],
  LAPS: ["Bahnen", "Laps"], SWOLF: ["SWOLF", "SWOLF"], STROKES: ["Züge", "Strokes"], STYLE: ["Stil", "Stroke"],
};
export const statLabel = (key) => tr(...STAT_LABELS[key]);
const stat = (key, value, unit = "") => ({ key, value, unit, label: statLabel(key) });

export const emptyStats = () => ({
  startMillis: null, activeSeconds: 0, distanceMeters: 0, calories: 0, averageHR: 0, maxHR: 0,
  laps: 0, strokes: 0, swolf: 0, steps: 0, swimStyle: null, hrZoneSeconds: [], sport: null,
  elevationGainMeters: 0, hrCurve: [],
});

export function guessSport(s, distance, seconds) {
  if (s.sport) return s.sport;
  if (s.laps > 0 || s.strokes > 0 || s.swolf > 0 || s.swimStyle) return "SWIMMING";
  const kmh = distance > 0 && seconds > 0 ? (distance / seconds) * 3.6 : 0;
  if (kmh >= 13) return "CYCLING";
  if (kmh >= 6.5) return "RUNNING";
  if (kmh > 0) return "WALKING";
  if (s.steps > 0) return "RUNNING";
  return "WORKOUT";
}

/** Workout file values, filled up from the route where the file has gaps. */
export function mergeWorkout(stats, route, sportOverride) {
  const s = stats || emptyStats();
  const distance = s.distanceMeters > 0 ? s.distanceMeters : route?.distanceMeters ?? 0;
  const seconds = s.activeSeconds > 0 ? s.activeSeconds : route?.movingSeconds ?? 0;
  return {
    sport: sportOverride || guessSport(s, distance, seconds),
    startMillis: s.startMillis ?? route?.startMillis ?? null,
    activeSeconds: seconds,
    distanceMeters: distance,
    elevationGainMeters: s.elevationGainMeters > 0 ? s.elevationGainMeters : route?.elevationGainMeters ?? 0,
    stats: s,
    route: route || null,
  };
}

// ------------------------------------------------------------------ formatting

const num = (n, digits) => n.toLocaleString(LOCALE, { minimumFractionDigits: digits, maximumFractionDigits: digits });
export const formatInt = (n) => Math.round(n).toLocaleString(LOCALE);
const pad2 = (n) => String(n).padStart(2, "0");

export function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600), m = Math.floor((seconds % 3600) / 60), s = seconds % 60;
  return h > 0 ? `${h}:${pad2(m)}:${pad2(s)}` : `${m}:${pad2(s)}`;
}
export const formatDistance = (m) => (m < 1000 ? stat("DISTANCE", String(Math.round(m)), "M") : stat("DISTANCE", num(m / 1000, 2), "KM"));
const paceMinSec = (sec) => { const t = Math.round(sec); return `${Math.floor(t / 60)}:${pad2(t % 60)}`; };
export const formatSplit = (sec) => `${Math.floor(sec / 60)}:${pad2(sec % 60)}`;

function swimStyleLabel(raw) {
  const k = raw.toLowerCase();
  if (k === "freestyle") return tr("Kraul", "Freestyle");
  if (k === "breaststroke") return tr("Brust", "Breaststroke");
  if (k === "backstroke") return tr("Rücken", "Backstroke");
  if (k === "butterfly") return tr("Delfin", "Butterfly");
  if (k === "mixed" || k === "medley") return tr("Lagen", "Medley");
  return raw.charAt(0).toUpperCase() + raw.slice(1);
}

/** All stats this workout has data for, in the order that matters most for its sport. */
export function availableStats(w) {
  const s = w.stats;
  const hasMove = w.distanceMeters > 0 && w.activeSeconds > 0;
  const distance = w.distanceMeters > 0 ? formatDistance(w.distanceMeters) : null;
  const time = w.activeSeconds > 0 ? stat("TIME", formatDuration(w.activeSeconds)) : null;
  let speed = null;
  if (hasMove) {
    if (w.sport === "CYCLING") speed = stat("SPEED", num((w.distanceMeters / w.activeSeconds) * 3.6, 1), "KM/H");
    else if (w.sport === "SWIMMING") speed = stat("PACE", paceMinSec(w.activeSeconds / (w.distanceMeters / 100)), "/100M");
    else speed = stat("PACE", paceMinSec(w.activeSeconds / (w.distanceMeters / 1000)), "/KM");
  }
  const avgHr = s.averageHR > 0 ? stat("AVG_HR", String(s.averageHR), "BPM") : null;
  const maxHr = s.maxHR > 0 ? stat("MAX_HR", String(s.maxHR), "BPM") : null;
  const kcal = s.calories > 0 ? stat("ENERGY", formatInt(s.calories), "KCAL") : null;
  const elev = Math.round(w.elevationGainMeters) > 0 ? stat("ELEVATION", formatInt(w.elevationGainMeters), tr("HM", "M")) : null;
  const steps = s.steps > 0 ? stat("STEPS", formatInt(s.steps)) : null;
  const laps = s.laps > 0 ? stat("LAPS", String(s.laps)) : null;
  const swolf = s.swolf > 0 ? stat("SWOLF", String(s.swolf)) : null;
  const strokes = s.strokes > 0 ? stat("STROKES", formatInt(s.strokes)) : null;
  const style = s.swimStyle ? stat("STYLE", swimStyleLabel(s.swimStyle)) : null;
  const order = {
    CYCLING: [distance, time, speed, elev, avgHr, maxHr, kcal],
    RUNNING: [distance, time, speed, avgHr, steps, elev, kcal, maxHr],
    WALKING: [distance, time, speed, steps, elev, avgHr, kcal, maxHr],
    SWIMMING: [distance, time, speed, laps, swolf, strokes, kcal, style, avgHr, maxHr],
    WORKOUT: [time, avgHr, maxHr, kcal, steps, distance],
  }[w.sport];
  return order.filter(Boolean);
}

export function storyContent(w) {
  const all = availableStats(w);
  const hero = all.find((x) => x.key === "DISTANCE") || all.find((x) => x.key === "TIME") ||
    all.find((x) => x.key === "ENERGY") || stat("TIME", "—");
  return { hero, cells: all.filter((x) => x !== hero).slice(0, 6) };
}

/** Up to [max] stats in tap order; unknown keys are skipped, nothing usable falls back to the sport's default. */
export function pickStats(w, chosen, max) {
  const all = availableStats(w);
  const picked = (chosen || []).map((k) => all.find((x) => x.key === k)).filter(Boolean).slice(0, max);
  return picked.length ? picked : all.slice(0, max);
}

/** Tap order counts. Tapping a chosen stat removes it (never the last one); a new one past the limit pushes out the oldest. */
export function toggleStat(current, key, max) {
  if (current.includes(key)) return current.length > 1 ? current.filter((k) => k !== key) : current;
  return [...current, key].slice(-max);
}

export function formatStoryDate(ms) {
  const d = new Date(ms);
  const day = d.toLocaleDateString(LOCALE, { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  return `${day} · ${pad2(d.getHours())}:${pad2(d.getMinutes())}`.toUpperCase();
}
export const formatShortDate = (ms) => new Date(ms).toLocaleDateString(LOCALE, { day: "numeric", month: "short", year: "numeric" }).toUpperCase();
export const bibNumber = (ms) => (ms == null ? "0001" : (() => { const d = new Date(ms); return pad2(d.getMonth() + 1) + pad2(d.getDate()); })());
export const hrZoneName = (i) => [tr("Aufwärmen", "Warm-up"), tr("Fettverbrennung", "Fat burn"), tr("Aerob", "Aerobic"), tr("Anaerob", "Anaerobic"), tr("Maximal", "Maximum")][i];

// ------------------------------------------------------------------ routes (GPX, TCX, KML, KMZ)

const R = 6371000;
const rad = (d) => (d * Math.PI) / 180;
function haversine(a, b) {
  const p1 = rad(a.lat), p2 = rad(b.lat), dp = p2 - p1, dl = rad(b.lon - a.lon);
  return 2 * R * Math.asin(Math.sqrt(Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2));
}
const parseTime = (s) => { const t = Date.parse(s.trim()); return Number.isNaN(t) ? null : t; };
const toNum = (s) => { if (s == null) return null; const n = parseFloat(String(s).trim()); return Number.isFinite(n) ? n : null; };

/** Time per full kilometre; pauses between segments don't count, like moving time. */
export function splits(segments) {
  const out = [];
  let dist = 0, moving = 0, lastMark = 0;
  for (const seg of segments) for (let i = 1; i < seg.length; i++) {
    const a = seg[i - 1], b = seg[i];
    if (a.time == null || b.time == null) return [];
    const d = haversine(a, b), dt = (b.time - a.time) / 1000;
    // a step can cross a km mark; interpolate the time at the mark
    while (d > 0 && dist + d >= (out.length + 1) * 1000) {
      const f = ((out.length + 1) * 1000 - dist) / d;
      const at = moving + dt * f;
      out.push(Math.round(at - lastMark));
      lastMark = at;
      if (out.length > 2000) return out;
    }
    dist += d; moving += dt;
  }
  return out;
}

/** Elevation over distance, resampled to [samples] evenly spaced points for drawing. */
function elevationProfile(segments, samples = 120) {
  const dist = [], ele = [];
  let d = 0;
  for (const seg of segments) {
    let prev = null; // pauses don't add distance
    for (const p of seg) {
      if (prev) d += haversine(prev, p);
      prev = p;
      if (p.ele != null) { dist.push(d); ele.push(p.ele); }
    }
  }
  if (ele.length < 2 || d <= 0) return [];
  let j = 0;
  return Array.from({ length: samples }, (_, i) => {
    const target = (d * i) / (samples - 1);
    while (j < dist.length - 2 && dist[j + 1] < target) j++;
    const span = dist[j + 1] - dist[j];
    const t = span > 0 ? Math.min(1, Math.max(0, (target - dist[j]) / span)) : 0;
    return ele[j] + (ele[j + 1] - ele[j]) * t;
  });
}

export function buildRoute(segments) {
  if (segments.reduce((n, s) => n + s.length, 0) < 2) return null;
  let distance = 0, gain = 0, moving = 0;
  for (const seg of segments) {
    for (let i = 1; i < seg.length; i++) distance += haversine(seg[i - 1], seg[i]);
    const times = seg.map((p) => p.time).filter((t) => t != null);
    if (times.length >= 2) moving += times[times.length - 1] - times[0];
    // GPS altitude is noisy: only count climbs once they exceed 3 m above the last low point
    let anchor = null;
    for (const p of seg) {
      if (p.ele == null) continue;
      if (anchor == null || p.ele < anchor) anchor = p.ele;
      else if (p.ele - anchor >= 3) { gain += p.ele - anchor; anchor = p.ele; }
    }
  }
  const pointCount = segments.reduce((n, s) => n + s.length, 0);
  let startMillis = null;
  for (const seg of segments) { const p = seg.find((x) => x.time != null); if (p) { startMillis = p.time; break; } }
  return {
    segments: segments.map((s) => s.map((p) => ({ lat: p.lat, lon: p.lon }))),
    distanceMeters: distance, elevationGainMeters: gain, startMillis,
    movingSeconds: Math.floor(moving / 1000), profile: elevationProfile(segments), splitSeconds: splits(segments), pointCount,
  };
}

export function parseGpx(gpx) {
  const segs = [];
  for (const seg of gpx.matchAll(/<trkseg[^>]*>([\s\S]*?)<\/trkseg>|<rte[^>]*>([\s\S]*?)<\/rte>/g)) {
    const body = seg[1] || seg[2] || "";
    const pts = [];
    for (const m of body.matchAll(/<(?:trkpt|rtept)\b([^>]*?)(?:\/>|>([\s\S]*?)<\/(?:trkpt|rtept)>)/g)) {
      const attrs = m[1], inner = m[2] || "";
      const lat = toNum(/\blat\s*=\s*["']([^"']+)/.exec(attrs)?.[1]), lon = toNum(/\blon\s*=\s*["']([^"']+)/.exec(attrs)?.[1]);
      if (lat == null || lon == null) continue;
      const time = /<time>(.*?)<\/time>/.exec(inner)?.[1];
      pts.push({ lat, lon, ele: toNum(/<ele>(.*?)<\/ele>/.exec(inner)?.[1]), time: time ? parseTime(time) : null });
    }
    if (pts.length) segs.push(pts);
  }
  return buildRoute(segs);
}

export function parseTcx(tcx) {
  const segs = [];
  for (const track of tcx.matchAll(/<Track>([\s\S]*?)<\/Track>/g)) {
    const pts = [];
    for (const m of track[1].matchAll(/<Trackpoint>([\s\S]*?)<\/Trackpoint>/g)) {
      const b = m[1];
      const lat = toNum(/<LatitudeDegrees>(.*?)<\/LatitudeDegrees>/.exec(b)?.[1]);
      const lon = toNum(/<LongitudeDegrees>(.*?)<\/LongitudeDegrees>/.exec(b)?.[1]);
      if (lat == null || lon == null) continue;
      const time = /<Time>(.*?)<\/Time>/.exec(b)?.[1];
      pts.push({ lat, lon, ele: toNum(/<AltitudeMeters>(.*?)<\/AltitudeMeters>/.exec(b)?.[1]), time: time ? parseTime(time) : null });
    }
    if (pts.length) segs.push(pts);
  }
  return buildRoute(segs);
}

/** Workout totals from the <Lap> summaries of a TCX (Mi Fitness writes them even without GPS); null without lap times. */
export function parseTcxStats(tcx) {
  const laps = [...tcx.matchAll(/<Lap\b[^>]*>([\s\S]*?)<\/Lap>/g)].map((m) => m[1].split("<Track>")[0]);
  const num = (re) => laps.map((l) => toNum(re.exec(l)?.[1]) || 0);
  const sum = (a) => a.reduce((x, y) => x + y, 0);
  const seconds = num(/<TotalTimeSeconds>\s*([\d.]+)/);
  if (sum(seconds) <= 0) return null;
  // Garmin: <AverageHeartRateBpm><Value>, Mi Fitness: a bare <HeartRateBpm> number
  const bare = num(/<HeartRateBpm>\s*([\d.]+)/);
  const hr = num(/<AverageHeartRateBpm>\s*<Value>\s*([\d.]+)/).map((a, i) => a || bare[i]);
  const hrSec = sum(seconds.filter((_, i) => hr[i] > 0));
  const avgHr = hrSec > 0 ? sum(seconds.map((s, i) => s * hr[i])) / hrSec : 0;
  const sport = (/<Activity[^>]*Sport="([^"]*)"/.exec(tcx)?.[1] || "").toLowerCase();
  const has = (...k) => k.some((x) => sport.includes(x));
  // Mi Fitness formats local time with a literal "Z"; Date.parse reads it as local once the Z is gone
  const id = /<Id>(.*?)<\/Id>/.exec(tcx)?.[1]?.trim();
  return {
    ...emptyStats(),
    startMillis: id ? parseTime(tcx.includes('creator="Mi Fitness"') ? id.replace(/Z$/, "") : id) : null,
    activeSeconds: Math.trunc(sum(seconds)),
    distanceMeters: sum(num(/<DistanceMeters>\s*([\d.]+)/)),
    calories: Math.trunc(sum(num(/<Calories>\s*([\d.]+)/))),
    averageHR: Math.trunc(avgHr),
    maxHR: Math.trunc(Math.max(0, ...num(/<MaximumHeartRateBpm>\s*<Value>\s*([\d.]+)/))),
    steps: Math.trunc(sum(num(/<Steps>\s*([\d.]+)/))),
    elevationGainMeters: sum(num(/<CumulativeClimb>\s*([\d.]+)/)),
    sport: has("bik", "cycl", "rad", "fahr") ? "CYCLING" : has("run", "lauf") ? "RUNNING" : has("swim", "schwimm") ? "SWIMMING"
      : has("walk", "hik", "wander", "gehen") ? "WALKING" : null,
  };
}

export function parseKml(kml) {
  // OpenTracks writes empty <coord/> after some <when>; pairing by document order keeps them aligned
  const segs = [];
  for (const track of kml.matchAll(/<(?:gx:)?Track>([\s\S]*?)<\/(?:gx:)?Track>/g)) {
    const pts = [];
    let pending = null;
    for (const m of track[1].matchAll(/<when>(.*?)<\/when>|<(?:gx:)?coord>(.*?)<\/(?:gx:)?coord>|<(?:gx:)?coord\s*\/>/g)) {
      if (m[1]) { pending = parseTime(m[1]); continue; }
      const p = (m[2] || "").trim().split(/\s+/);
      if (p.length >= 2) {
        const lon = toNum(p[0]), lat = toNum(p[1]);
        if (lat != null && lon != null) pts.push({ lat, lon, ele: toNum(p[2]), time: pending });
      }
      pending = null;
    }
    if (pts.length) segs.push(pts);
  }
  if (segs.length) return buildRoute(segs);
  const lines = [];
  for (const m of kml.matchAll(/<LineString>[\s\S]*?<coordinates>([\s\S]*?)<\/coordinates>/g)) {
    const pts = m[1].trim().split(/\s+/).map((t) => t.split(",")).map((p) => ({ lon: toNum(p[0]), lat: toNum(p[1]), ele: toNum(p[2]), time: null }))
      .filter((p) => p.lat != null && p.lon != null);
    if (pts.length) lines.push(pts);
  }
  return buildRoute(lines);
}

const isZip = (b) => b.length > 4 && b[0] === 0x50 && b[1] === 0x4b;

/** First .kml inside a KMZ. Reads the zip's central directory, inflates with the browser's DecompressionStream. */
async function kmlFromKmz(b) {
  const dv = new DataView(b.buffer, b.byteOffset, b.byteLength);
  let eocd = -1;
  for (let i = b.length - 22; i >= Math.max(0, b.length - 65557); i--) if (dv.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) return null;
  let p = dv.getUint32(eocd + 16, true);
  const n = dv.getUint16(eocd + 10, true);
  for (let e = 0; e < n && p + 46 <= b.length; e++) {
    if (dv.getUint32(p, true) !== 0x02014b50) break;
    const method = dv.getUint16(p + 10, true), size = dv.getUint32(p + 20, true);
    const nameLen = dv.getUint16(p + 28, true), extra = dv.getUint16(p + 30, true), comment = dv.getUint16(p + 32, true);
    const local = dv.getUint32(p + 42, true);
    const name = new TextDecoder().decode(b.subarray(p + 46, p + 46 + nameLen));
    p += 46 + nameLen + extra + comment;
    if (!name.toLowerCase().endsWith(".kml")) continue;
    const start = local + 30 + dv.getUint16(local + 26, true) + dv.getUint16(local + 28, true);
    const data = b.subarray(start, start + size);
    if (method === 0) return new TextDecoder().decode(data);
    if (method !== 8) return null;
    const out = await new Response(new Blob([data]).stream().pipeThrough(new DecompressionStream("deflate-raw"))).arrayBuffer();
    return new TextDecoder().decode(out);
  }
  return null;
}

export function looksLikeRoute(b) {
  if (isZip(b)) return true;
  const head = new TextDecoder().decode(b.subarray(0, 4096));
  return head.includes("<gpx") || head.includes("<kml") || head.includes("<TrainingCenterDatabase");
}

export async function parseRoute(b) {
  const text = isZip(b) ? await kmlFromKmz(b) : new TextDecoder().decode(b);
  if (text == null) return null;
  if (text.includes("<kml")) return parseKml(text);
  if (text.includes("<gpx")) return parseGpx(text);
  if (text.includes("<TrainingCenterDatabase")) return parseTcx(text);
  return null;
}

// ------------------------------------------------------------------ Gadgetbridge JSON

export const looksLikeJson = (b) => { for (const c of b.subarray(0, 64)) { if (c > 32) return c === 0x7b; } return false; };

/** Gadgetbridge names exports like "2026-04-07T13_55_47+02_00.json"; null if the name doesn't say. */
export function parseFilenameDate(name) {
  const m = /(\d{4}-\d{2}-\d{2})T(\d{2})_(\d{2})_(\d{2})([+-]\d{2})_(\d{2})/.exec(name || "");
  if (!m) return null;
  const t = Date.parse(`${m[1]}T${m[2]}:${m[3]}:${m[4]}${m[5]}:${m[6]}`);
  return Number.isNaN(t) ? null : t;
}

export function parseWorkoutJson(text, filename = "") {
  let root;
  try { root = JSON.parse(text); } catch { root = null; }
  if (!root || typeof root !== "object" || Array.isArray(root))
    throw new Error(tr("Die JSON-Datei ist unvollständig oder beschädigt. Bitte neu aus Gadgetbridge exportieren.", "The JSON file is incomplete or damaged. Export it again from Gadgetbridge."));
  const known = ["activeSeconds", "distanceMeters", "active_calories", "averageHR", "laps", "steps"];
  if (!known.some((k) => k in root)) throw new Error(tr("Das sieht nicht nach einem Gadgetbridge-Workout aus.", "This doesn't look like a Gadgetbridge workout."));
  // Gadgetbridge wraps values as {"unit": "...", "value": 1.0}; older exports use bare numbers
  const val = (k) => { const e = root[k]; return e && typeof e === "object" ? e.value : e; };
  const n = (k) => { const v = Number(val(k)); return Number.isFinite(v) ? v : 0; };
  const zones = ["hrZoneWarmUp", "hrZoneFatBurn", "hrZoneAerobic", "hrZoneAnaerobic", "hrZoneExtreme"].map(n);
  const style = val("swimStyle");
  return {
    ...emptyStats(),
    startMillis: parseFilenameDate(filename),
    activeSeconds: Math.trunc(n("activeSeconds")), distanceMeters: n("distanceMeters"),
    calories: Math.trunc(n("active_calories")), averageHR: Math.trunc(n("averageHR")), maxHR: Math.trunc(n("maxHR")),
    laps: Math.trunc(n("laps")), strokes: Math.trunc(n("strokes")), swolf: Math.trunc(n("swolfAvg")), steps: Math.trunc(n("steps")),
    swimStyle: typeof style === "string" && style.trim() ? style : null,
    hrZoneSeconds: zones.reduce((a, b) => a + b, 0) > 0 ? zones : [],
  };
}

// ------------------------------------------------------------------ FIT (session summary + records)

const FIT_EPOCH = 631065600; // 1989-12-31T00:00:00Z in Unix seconds
const MAX_PAUSE_SECONDS = 60; // a longer GPS gap starts a new segment
const SIZES = [1, 1, 1, 2, 2, 4, 4, 1, 4, 8, 1, 2, 4, 1, 8, 8, 8];

export const looksLikeFit = (b) => b.length >= 12 && b[8] === 0x2e && b[9] === 0x46 && b[10] === 0x49 && b[11] === 0x54;

function u(b, o, size, big) {
  if (o + size > b.length) throw new RangeError("truncated");
  let r = 0;
  for (let i = 0; i < size; i++) r = big ? r * 256 + b[o + i] : r + b[o + i] * 2 ** (8 * i);
  return r;
}

function element(b, o, size, baseType, big) {
  const base = baseType & 0x1f;
  if (base === 8 || base === 9 || size > 4) return null; // floats and 64-bit values aren't needed
  const raw = u(b, o, size, big);
  const all = 2 ** (size * 8) - 1;
  if (base === 1 || base === 3 || base === 5 || base === 14) { // signed: invalid is the max positive value
    if (raw === Math.floor(all / 2)) return null;
    return raw >= 2 ** (size * 8 - 1) ? raw - 2 ** (size * 8) : raw;
  }
  if (base === 10 || base === 11 || base === 12 || base === 16) return raw === 0 ? null : raw; // "z" types
  return raw === all ? null : raw;
}

/** Heart rate averaged into [samples] equal time slices; empty slices take the previous value. */
function resample(heart, samples = 120) {
  if (heart.length < 10) return [];
  const t0 = heart[0][0], span = Math.max(1, heart[heart.length - 1][0] - t0);
  const sum = new Array(samples).fill(0), n = new Array(samples).fill(0);
  for (const [t, hr] of heart) {
    const i = Math.min(samples - 1, Math.max(0, Math.floor(((t - t0) * samples) / (span + 1))));
    sum[i] += hr; n[i]++;
  }
  let last = heart[0][1];
  return sum.map((s, i) => (n[i] > 0 ? (last = s / n[i]) : last));
}

export function parseFit(b) {
  if (!looksLikeFit(b)) throw new Error(tr("Keine FIT-Datei.", "Not a FIT file."));
  const headerSize = b[0];
  const end = Math.min(b.length, headerSize + u(b, 4, 4, false)); // a truncated file still yields what's there
  const defs = new Array(16).fill(null);
  let pos = headerSize, lastTs = 0, session = null;
  const points = [], heart = [];

  const handle = (start, def) => {
    let p = start;
    const msg = {};
    const wanted = def.global === 18 || def.global === 20;
    for (const f of def.fields) {
      if (wanted) {
        const base = f.baseType & 0x1f, es = SIZES[base] ?? 1;
        if (base !== 7 && f.size >= es) msg[f.num] = Array.from({ length: Math.floor(f.size / es) }, (_, i) => element(b, p + i * es, es, f.baseType, def.big));
      }
      p += f.size;
    }
    if (p > b.length) throw new RangeError("truncated");
    return [p + def.devBytes, msg];
  };
  const collect = (def, msg, ts) => {
    if (def.global === 18) { session = msg; return; }
    if (def.global !== 20) return;
    const t = msg[253]?.[0] ?? ts;
    const hr = msg[3]?.[0];
    if (hr != null && hr >= 30 && hr <= 250) heart.push([t, hr]);
    const lat = msg[0]?.[0], lon = msg[1]?.[0];
    if (lat != null && lon != null) {
      const semi = 180 / 2 ** 31;
      const altRaw = msg[78]?.[0] ?? msg[2]?.[0];
      points.push({ lat: lat * semi, lon: lon * semi, ele: altRaw != null ? altRaw / 5 - 500 : null, time: (t + FIT_EPOCH) * 1000 });
    }
  };

  try {
    while (pos < end) {
      const h = b[pos++];
      if (h & 0x80) { // compressed timestamp header
        const def = defs[(h >> 5) & 0x3];
        if (!def) throw new Error("bad local type");
        const offset = h & 0x1f, low = lastTs % 32;
        lastTs = lastTs - low + offset + (offset < low ? 0x20 : 0);
        const [p, msg] = handle(pos, def); pos = p;
        collect(def, msg, lastTs);
        continue;
      }
      const local = h & 0x0f;
      if (h & 0x40) { // definition message
        pos++; // reserved
        const big = b[pos++] === 1;
        const global = u(b, pos, 2, big); pos += 2;
        const n = b[pos++];
        const fields = Array.from({ length: n }, (_, i) => ({ num: b[pos + i * 3], size: b[pos + i * 3 + 1], baseType: b[pos + i * 3 + 2] }));
        pos += n * 3;
        let dev = 0;
        if (h & 0x20) { const nd = b[pos++]; for (let i = 0; i < nd; i++) dev += b[pos + i * 3 + 1]; pos += nd * 3; }
        defs[local] = { global, big, fields, devBytes: dev };
      } else {
        const def = defs[local];
        if (!def) throw new Error("data before definition");
        const [p, msg] = handle(pos, def); pos = p;
        if (msg[253]?.[0] != null) lastTs = msg[253][0];
        collect(def, msg, lastTs);
      }
    }
  } catch (e) {
    if (!(e instanceof RangeError)) throw e; // truncated mid-message: keep what was read so far
  }

  const segments = [];
  let prev = null;
  for (const p of points) {
    if (!segments.length || (prev && p.time - prev.time > MAX_PAUSE_SECONDS * 1000)) segments.push([]);
    segments[segments.length - 1].push(p); prev = p;
  }
  const route = buildRoute(segments);
  if (!session) throw new Error(tr("Die FIT-Datei enthält keine Zusammenfassung.", "The FIT file has no workout summary."));
  const v = (n) => session[n]?.[0] ?? null;
  const sport = { 1: "RUNNING", 2: "CYCLING", 5: "SWIMMING", 11: "WALKING", 17: "WALKING", 4: "WORKOUT", 10: "WORKOUT" }[v(5)] || null;
  const swim = sport === "SWIMMING";
  const zones = (session[65] || []).map((z) => (z ?? 0) / 1000);
  return {
    stats: {
      ...emptyStats(),
      startMillis: v(2) != null ? (v(2) + FIT_EPOCH) * 1000 : null,
      activeSeconds: Math.floor((v(8) ?? v(7) ?? 0) / 1000),
      distanceMeters: (v(9) ?? 0) / 100,
      calories: v(11) ?? 0, averageHR: v(16) ?? 0, maxHR: v(17) ?? 0,
      laps: swim ? v(26) ?? 0 : 0, // every FIT has ≥ 1 lap; only pool laps count
      strokes: swim ? v(10) ?? 0 : 0,
      hrZoneSeconds: zones.reduce((a, c) => a + c, 0) > 0 ? zones : [],
      sport, elevationGainMeters: v(22) ?? 0, hrCurve: resample(heart),
    },
    route,
  };
}

// ------------------------------------------------------------------ share pictures (after OCR)

export function looksLikeImage(b) {
  const a = String.fromCharCode(...b.subarray(0, 12));
  return (b[0] === 0x89 && a.startsWith("PNG", 1)) || (b[0] === 0xff && b[1] === 0xd8) ||
    (a.startsWith("RIFF") && a.startsWith("WEBP", 8)) || a.startsWith("ftyp", 4);
}

const WS = "[\\s\\u00A0\\u202F]*";
const INT = "(\\d{1,3}(?:[.,'\\u00A0\\u202F ]\\d{3})+|\\d+)";
const re = (s, f = "") => new RegExp(s, f);
const KM = re(`(?<![\\d:.,])(\\d+(?:[.,]\\d{1,3})?)${WS}(km|mi|kilometer|meilen|miles)\\b(?!${WS}/)`, "i");
const METERS = re(`(?<![\\d:.,])${INT}${WS}m\\b(?!${WS}/)`);
const HMS = /(?<![\d:])(\d{1,2}):(\d{2}):(\d{2})(?![\d:])/;
const HM = re(`(\\d{1,2})${WS}(?:h|std\\.?|hr)${WS}(\\d{1,2})${WS}(?:m|min)\\b(?:${WS}(\\d{1,2})${WS}s\\b)?`, "i");
const MIN_ONLY = re(`(?<![\\d:.,])(\\d{1,3})${WS}(?:min|minuten|minutes)\\b(?!${WS}/)`, "i");
const MS = re(`(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])(?!${WS}(?:/|min/|'|"|″|′|/km|/mi))`);
const BPM = re(`(?<!\\d)(\\d{2,3})${WS}(?:bpm|s/min|schläge|♥)`, "gi");
const KCAL = re(`(?<![\\d.,])${INT}${WS}(?:kcal|kalorien|calories|cal)\\b`, "i");
const HM_UNIT = re(`(?<![\\d.,])${INT}${WS}hm\\b`, "i");
const INT_ALONE = re(`^\\D{0,3}${INT}\\D{0,6}$`);
const L = {
  distance: /distanz|distance|strecke|entfernung|距离/i, time: /dauer|zeit|time|duration|bewegungszeit|trainingszeit|moving/i,
  hr: /herzfrequenz|heart\s*rate|puls|\bhf\b|\bhr\b|心率/i, max: /max/i, avg: /ø|⌀|avg|average|durchschn|mittl|mittel|schnitt/i,
  cal: /kalorien|calories|energie|kcal|verbrauch/i, elev: /anstieg|aufstieg|höhenmeter|höhengewinn|elev|ascent|climb|gain|bergauf/i,
  steps: /schritte|steps/i,
};
const SPORT_WORDS = [
  ["CYCLING", /rad|fahrrad|cycling|ride|bike|biking|gravel|mtb|velo/i], ["RUNNING", /lauf|laufen|run|running|jogging|trail/i],
  ["SWIMMING", /schwimm|swim/i], ["WALKING", /wander|hike|hiking|walk|gehen|spazier/i],
];
const around = (i) => [i, i + 1, i - 1, i + 2, i - 2];
const decimal = (s) => parseFloat(s.replace(",", "."));
// OCR can glue numbers together; anything too long to be a stat counts as nothing
const int = (s) => parseInt(s.replace(/\D/g, "").slice(0, 9), 10);

/** First [value] on a line with [label] or up to two lines away; [needs] must also appear nearby if given. */
function labeled(lines, label, value, needs = null) {
  for (let i = 0; i < lines.length; i++) {
    if (!label.test(lines[i])) continue;
    if (needs && !around(i).some((j) => j >= 0 && j < lines.length && needs.test(lines[j]))) continue;
    for (const j of around(i)) {
      if (j < 0 || j >= lines.length) continue;
      // a label line can carry digits of its own ("Ø HF"), so strip the label before matching
      const text = j === i ? lines[j].replace(new RegExp(label.source, "gi"), " ").trim() : lines[j];
      const m = value.exec(text);
      if (m) return m;
    }
  }
  return null;
}

/** Workout numbers from the OCR text of a share picture (Mi Fitness, Garmin Connect, Zepp, …); null when there are none. */
export function parseShareCard(text) {
  const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
  const all = lines.join("\n");
  let distance = 0;
  const dm = labeled(lines, L.distance, KM) || KM.exec(all);
  if (dm) { const unit = dm[2].toLowerCase(); distance = decimal(dm[1]) * (unit.startsWith("mi") || unit === "meilen" ? 1609.344 : 1000); }
  else { const mm = labeled(lines, L.distance, METERS); if (mm) distance = int(mm[1]); }

  let seconds = 0, m;
  if ((m = HMS.exec(all))) seconds = +m[1] * 3600 + +m[2] * 60 + +m[3];
  else if ((m = HM.exec(all))) seconds = +m[1] * 3600 + +m[2] * 60 + (+m[3] || 0);
  // "45:12" is a time of day or a pace just as often, so only trust it next to a time label
  else if ((m = labeled(lines, L.time, MS))) seconds = +m[1] * 60 + +m[2];
  else if ((m = labeled(lines, L.time, MIN_ONLY))) seconds = +m[1] * 60;

  const hrValues = [];
  lines.forEach((line, i) => { for (const x of line.matchAll(BPM)) { const v = +x[1]; if (v >= 30 && v <= 240) hrValues.push([i, v]); } });
  const inRange = (v) => v >= 30 && v <= 240;
  const hrNear = (label) => {
    const hit = hrValues.find(([i]) => around(i).some((j) => j >= 0 && j < lines.length && label.test(lines[j])));
    if (hit) return hit[1];
    const x = labeled(lines, label, INT_ALONE, L.hr);
    return x && inRange(int(x[1])) ? int(x[1]) : null;
  };
  const maxHr = hrNear(L.max) ?? 0;
  let avgHr = hrNear(L.avg);
  if (avgHr != null && avgHr === maxHr && hrValues.length !== 1) avgHr = null;
  if (avgHr == null) avgHr = hrValues.find(([, v]) => v !== maxHr)?.[1] ?? null;
  if (avgHr == null) { const x = labeled(lines, L.hr, INT_ALONE); const v = x ? int(x[1]) : 0; avgHr = inRange(v) && v !== maxHr ? v : 0; }

  const cm = KCAL.exec(all) || labeled(lines, L.cal, INT_ALONE);
  const calories = cm && int(cm[1]) < 20000 ? int(cm[1]) : 0;
  const em = labeled(lines, L.elev, METERS) || HM_UNIT.exec(all) || labeled(lines, L.elev, INT_ALONE);
  const elevation = em && int(em[1]) < 10000 ? int(em[1]) : 0;
  const sm = labeled(lines, L.steps, INT_ALONE);
  let sport = null;
  for (const line of lines) { const hit = SPORT_WORDS.find(([, r]) => r.test(line)); if (hit) { sport = hit[0]; break; } }

  if (distance <= 0 && seconds <= 0 && avgHr <= 0 && calories <= 0) return null;
  return {
    ...emptyStats(), activeSeconds: seconds, distanceMeters: distance < 1e6 ? distance : 0, calories,
    averageHR: avgHr, maxHR: maxHr, steps: sm ? int(sm[1]) : 0, sport, elevationGainMeters: elevation,
  };
}

// ------------------------------------------------------------------ start screen: where the workout comes from

const SAVE = ["Bild sichern, dann hier wählen.", "save the picture, then pick it here."];
/** Same apps as Source.kt in the Android app; the web app can't open them, so every path ends in "save, then pick here". */
export const SOURCES = [
  { id: "MI_FITNESS", label: "Mi Fitness",
    numbers: [`Training öffnen → Teilen → ${SAVE[0]}`, `Open the workout → Share → ${SAVE[1]}`],
    map: ["Training → Exportieren → GPX → ⋯ → in Dateien sichern, dann hier wählen. Ohne GPS gibt es nur TCX: Werte, keine Karte.", "Workout → Export → GPX → ⋯ → save to Files, then pick it here. Without GPS there is only TCX: numbers, no map."] },
  { id: "GARMIN", label: "Garmin Connect",
    numbers: [`Aktivität öffnen → Teilen → ${SAVE[0]}`, `Open the activity → Share → ${SAVE[1]}`],
    map: ["Auf connect.garmin.com: Aktivität → ⚙ → „Nach GPX exportieren“.", "On connect.garmin.com: activity → ⚙ → “Export to GPX”."] },
  { id: "ZEPP", label: "Zepp",
    numbers: [`Training öffnen → Teilen → ${SAVE[0]}`, `Open the workout → Share → ${SAVE[1]}`],
    map: ["Training → GPX exportieren (Uhren mit GPS).", "Workout → Export GPX (watches with GPS)."] },
  { id: "HUAWEI", label: "Huawei Health",
    numbers: [`Training öffnen → Teilen → ${SAVE[0]}`, `Open the workout → Share → ${SAVE[1]}`],
    map: ["Routen-Symbol → Route exportieren → GPX.", "Route icon → Export route → GPX."] },
  { id: "SAMSUNG", label: "Samsung Health",
    numbers: [`Training öffnen → Teilen → ${SAVE[0]}`, `Open the workout → Share → ${SAVE[1]}`],
    map: ["Ganz unten im Training: Als GPX exportieren.", "At the bottom of the workout: Export as GPX file."] },
  { id: "STRAVA", label: "Strava",
    numbers: [`Aktivität öffnen → Teilen → ${SAVE[0]}`, `Open the activity → Share → ${SAVE[1]}`],
    map: ["Auf strava.com: Aktivität → ⋯ → GPX exportieren.", "On strava.com: activity → ⋯ → Export GPX."] },
  { id: "GADGETBRIDGE", label: "Gadgetbridge",
    numbers: ["Training öffnen → ⋮ → „Fit-Datei teilen“ → in Dateien sichern, dann hier wählen.", "Open the workout → ⋮ → “Share FIT File” → save to Files, then pick it here."],
    map: ["Steckt schon in der FIT, wenn die Uhr GPS hat. Sonst die Route mit OpenTracks aufzeichnen.", "Already in the FIT if the watch has GPS. Otherwise record the route with OpenTracks."] },
  { id: "OTHER", label: null,
    numbers: ["Screenshot vom Training wählen, oder eine Datei aus Polar Flow, COROS & Co. (FIT, TCX, GPX).", "Pick a screenshot of the workout, or a file from Polar Flow, COROS and others (FIT, TCX, GPX)."],
    map: ["GPX, TCX, KML oder KMZ dazuwählen.", "Pick a GPX, TCX, KML or KMZ as well."] },
];
export const sourceTitle = (s) => s.label ?? tr("Andere", "Other");

/** An invented gravel loop for "See an example" (same as Demo.kt). */
export function demoWorkout() {
  const START = 1782036000000;
  const pts = [];
  let time = START, prev = null;
  for (let i = 0; i <= 600; i++) {
    const t = (2 * Math.PI * i) / 600;
    const lat = 48.30 + 0.75 * (0.045 * Math.sin(t) + 0.014 * Math.sin(2 * t + 1.1) + 0.004 * Math.sin(9 * t + 0.4) + 0.002 * Math.sin(23 * t));
    const lon = 9.90 + 0.75 * (0.068 * Math.cos(t) + 0.018 * Math.cos(3 * t + 0.5) + 0.005 * Math.sin(7 * t + 2.0) + 0.002 * Math.cos(19 * t));
    const ele = 480 + 55 * Math.sin(2 * t) + 18 * Math.sin(5 * t + 1);
    const p = { lat, lon, ele };
    if (prev) time += (haversine(prev, p) / (6.6 + 1.4 * Math.cos(3 * t))) * 1000;
    pts.push({ ...p, time: Math.trunc(time) });
    prev = p;
  }
  const route = buildRoute([pts]);
  const hr = Array.from({ length: 120 }, (_, i) => 128 + 22 * Math.sin((Math.PI * i) / 60) + 9 * Math.sin((2 * Math.PI * i) / 13));
  const stats = {
    ...emptyStats(), startMillis: START, activeSeconds: route.movingSeconds, distanceMeters: route.distanceMeters,
    calories: 812, averageHR: Math.trunc(hr.reduce((a, b) => a + b, 0) / hr.length), maxHR: Math.trunc(Math.max(...hr)) + 4,
    hrZoneSeconds: [0.08, 0.27, 0.41, 0.19, 0.05].map((f) => f * route.movingSeconds), sport: "CYCLING",
    elevationGainMeters: route.elevationGainMeters, hrCurve: hr,
  };
  return { stats, route };
}
