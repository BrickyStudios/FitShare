// Tests for the web app's data layer (docs/app/js/data.js). Same fixtures as the Kotlin tests.
// Run: node tools/webapp_test.mjs
import assert from "node:assert/strict";
import { test } from "node:test";
import { deflateRawSync } from "node:zlib";
import * as D from "../docs/app/js/data.js";

const enc = (s) => new TextEncoder().encode(s);
const near = (a, b, eps, msg) => assert.ok(Math.abs(a - b) <= eps, `${msg ?? ""} ${a} ≉ ${b}`);
const { tr } = D;

const kml = `<kml><Document><Placemark><MultiTrack>
<Track>
  <when>2026-06-21T17:57:21.671+02:00</when><coord/>
  <when>2026-06-21T17:57:22+02:00</when><coord>6.8700 50.8800 100</coord>
  <when>2026-06-21T18:07:22+02:00</when><coord>6.8700 50.8900 110</coord>
</Track>
<Track>
  <when>2026-06-21T18:30:00+02:00</when><coord>6.8700 50.9000 120</coord>
  <when>2026-06-21T18:40:00+02:00</when><coord>6.8700 50.9100 121</coord>
</Track>
</MultiTrack></Placemark></Document></kml>`;

const cyclingJson = `{"activeSeconds":{"unit":"seconds","value":9663.0},"active_calories":{"unit":"calories_unit","value":1310.0},
 "averageHR":{"unit":"bpm","value":158.0},"maxHR":{"unit":"bpm","value":185.0},
 "hrZoneWarmUp":{"unit":"seconds","value":0.0},"hrZoneFatBurn":{"unit":"seconds","value":102.0},
 "hrZoneAerobic":{"unit":"seconds","value":6488.0},"hrZoneAnaerobic":{"unit":"seconds","value":2934.0},
 "hrZoneExtreme":{"unit":"seconds","value":121.0},"internal_hasGps":{"unit":"string","value":"false"}}`;

test("gadgetbridge json and filename date", () => {
  const s = D.parseWorkoutJson(cyclingJson, "2026-06-21T17_57_19+02_00.json");
  assert.equal(s.activeSeconds, 9663);
  assert.equal(s.calories, 1310);
  assert.equal(s.maxHR, 185);
  assert.equal(s.hrZoneSeconds.length, 5);
  assert.equal(s.startMillis, 1782057439000);
  assert.equal(D.mergeWorkout(s, null, null).sport, "WORKOUT");
  assert.throws(() => D.parseWorkoutJson('{"foo":1}'));
  assert.throws(() => D.parseWorkoutJson('{"activeSeconds": '));
  assert.throws(() => D.parseWorkoutJson("[1,2]"));
  assert.equal(D.parseFilenameDate("export.json"), null);
});

test("kml pairs when and coord, skips pauses, profile", async () => {
  const r = await D.parseRoute(enc(kml));
  assert.equal(r.segments.length, 2);
  assert.equal(r.pointCount, 4);
  near(r.distanceMeters, 2224, 5);
  assert.equal(r.movingSeconds, 1200);
  near(r.elevationGainMeters, 10, 0.01);
  assert.equal(r.profile.length, 120);
  near(r.profile[0], 100, 0.01);
  near(r.profile[119], 121, 0.01);
});

test("kmz is unzipped", async () => {
  const data = deflateRawSync(Buffer.from(kml));
  const name = Buffer.from("doc.kml");
  const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(8, 8);
  local.writeUInt32LE(data.length, 18); local.writeUInt32LE(kml.length, 22); local.writeUInt16LE(name.length, 26);
  const cdOff = 30 + name.length + data.length;
  const cd = Buffer.alloc(46); cd.writeUInt32LE(0x02014b50, 0); cd.writeUInt16LE(8, 10);
  cd.writeUInt32LE(data.length, 20); cd.writeUInt32LE(kml.length, 24); cd.writeUInt16LE(name.length, 28); cd.writeUInt32LE(0, 42);
  const eocd = Buffer.alloc(22); eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(1, 8); eocd.writeUInt16LE(1, 10);
  eocd.writeUInt32LE(46 + name.length, 12); eocd.writeUInt32LE(cdOff, 16);
  const zip = new Uint8Array(Buffer.concat([local, name, data, cd, name, eocd]));
  assert.ok(D.looksLikeRoute(zip));
  assert.equal((await D.parseRoute(zip)).pointCount, 4);
});

test("gpx with attributes in any order", async () => {
  const r = await D.parseRoute(enc(`<gpx><trk><trkseg>
    <trkpt lon="7.0" lat="51.0"><ele>50</ele><time>2026-01-01T10:00:00Z</time></trkpt>
    <trkpt lat='51.001' lon='7.0'><time>2026-01-01T10:01:00Z</time></trkpt>
    <trkpt lat="51.002" lon="7.0"/>
  </trkseg></trk></gpx>`));
  assert.equal(r.pointCount, 3);
  near(r.distanceMeters, 222.4, 1);
  assert.equal(r.movingSeconds, 60);
});

test("tcx track becomes route", async () => {
  const tcx = enc(`<?xml version="1.0"?><TrainingCenterDatabase><Activities><Activity Sport="Biking"><Lap><Track>
    <Trackpoint><Time>2026-06-21T10:00:00Z</Time><HeartRateBpm><Value>120</Value></HeartRateBpm></Trackpoint>
    <Trackpoint><Time>2026-06-21T10:00:10Z</Time><Position><LatitudeDegrees>48.30</LatitudeDegrees><LongitudeDegrees>9.90</LongitudeDegrees></Position><AltitudeMeters>500</AltitudeMeters></Trackpoint>
    <Trackpoint><Time>2026-06-21T10:10:10Z</Time><Position><LatitudeDegrees>48.31</LatitudeDegrees><LongitudeDegrees>9.90</LongitudeDegrees></Position><AltitudeMeters>510</AltitudeMeters></Trackpoint>
    </Track></Lap></Activity></Activities></TrainingCenterDatabase>`);
  assert.ok(D.looksLikeRoute(tcx));
  const r = await D.parseRoute(tcx);
  assert.equal(r.pointCount, 2);
  near(r.distanceMeters, 1112, 5);
  assert.equal(r.movingSeconds, 600);
  near(r.elevationGainMeters, 10, 0.1);
});

test("km splits interpolate and skip pauses", () => {
  const step = 0.009;
  const seg1 = [0, 1, 2].map((i) => ({ lat: 48 + i * step, lon: 9, ele: null, time: i * 240000 }));
  const seg2 = [{ lat: 48 + 2 * step, lon: 9, time: 2280000 }, { lat: 48 + 3 * step, lon: 9, time: 2580000 }];
  const sp = D.splits([seg1, seg2]);
  assert.equal(sp.length, 3);
  assert.ok(sp[0] >= 239 && sp[0] <= 240 && sp[2] >= 299 && sp[2] <= 301, String(sp));
  assert.deepEqual(D.splits([[{ lat: 48, lon: 9, time: null }, { lat: 48.1, lon: 9, time: null }]]), []);
});

// minimal FIT writer, little endian (same as FitParserTest)
function fitFile(build) {
  const body = [];
  const w = {
    def(local, global, ...fields) { body.push(0x40 | local, 0, 0, global & 0xff, global >> 8, fields.length); for (const f of fields) body.push(...f); },
    data(header, ...values) { body.push(header); for (const [v, size] of values) for (let i = 0; i < size; i++) body.push(Math.floor(v / 2 ** (8 * i)) & 0xff); },
  };
  build(w);
  const h = [14, 0x20, 0x34, 0x08, ...[0, 1, 2, 3].map((i) => (body.length >> (8 * i)) & 0xff), 0x2e, 0x46, 0x49, 0x54, 0, 0];
  return new Uint8Array([...h, ...body, 0, 0]);
}
const semi = (deg) => { const v = Math.round((deg / 180) * 2147483648); return v < 0 ? v + 2 ** 32 : v; };
const t0 = 1150000000;
const ride = (sport) => fitFile((f) => {
  f.def(0, 20, [253, 4, 0x86], [0, 4, 0x85], [1, 4, 0x85], [78, 4, 0x86]);
  f.data(0, [t0, 4], [semi(48.3), 4], [semi(9.9), 4], [600 * 5, 4]);
  f.data(0, [t0 + 10, 4], [semi(48.31), 4], [semi(9.9), 4], [610 * 5, 4]);
  f.data(0, [t0 + 600, 4], [semi(48.32), 4], [semi(9.9), 4], [0xffffffff, 4]);
  f.def(2, 20, [0, 4, 0x85], [1, 4, 0x85]);
  f.data(0x80 | (2 << 5) | ((t0 + 605) & 0x1f), [semi(48.33), 4], [semi(9.9), 4]);
  f.def(1, 18, [2, 4, 0x86], [5, 1, 0x00], [8, 4, 0x86], [9, 4, 0x86], [11, 2, 0x84], [16, 1, 0x02], [17, 1, 0x02], [22, 2, 0x84], [26, 2, 0x84], [65, 20, 0x86]);
  f.data(1, [t0, 4], [sport, 1], [3600000, 4], [2500000, 4], [900, 2], [150, 1], [181, 1], [321, 2], [1, 2], [0, 4], [60000, 4], [3000000, 4], [540000, 4], [0, 4]);
});

test("fit session and track", () => {
  const { stats: s, route: r } = D.parseFit(ride(2));
  assert.equal(s.sport, "CYCLING");
  assert.equal(s.activeSeconds, 3600);
  near(s.distanceMeters, 25000, 0.01);
  assert.equal(s.calories, 900);
  assert.equal(s.maxHR, 181);
  assert.equal(s.laps, 0);
  assert.deepEqual(s.hrZoneSeconds, [0, 60, 3000, 540, 0]);
  assert.equal(s.startMillis, (t0 + 631065600) * 1000);
  assert.equal(r.segments.length, 2);
  assert.equal(r.pointCount, 4);
  assert.equal(r.segments[1].length, 2);
  near(r.segments[0][1].lat, 48.31, 1e-6);
  near(D.mergeWorkout(s, r, null).elevationGainMeters, 321, 0.01);
  assert.equal(D.parseFit(ride(0)).stats.sport, null);
  assert.equal(D.mergeWorkout(D.parseFit(ride(0)).stats, null, null).sport, "CYCLING");
  assert.throws(() => D.parseFit(ride(2).slice(0, 60)));
  assert.ok(!D.looksLikeFit(enc('{"a":1}')));
});

test("stats, hero and pick order", () => {
  const s = D.parseWorkoutJson(cyclingJson, "x.json");
  const route = { segments: [[{ lat: 0, lon: 0 }, { lat: 0, lon: 0.1 }]], distanceMeters: 67320, elevationGainMeters: 410, startMillis: null, movingSeconds: 9000, profile: [], splitSeconds: [] };
  const w = D.mergeWorkout(s, route, null);
  assert.equal(w.sport, "CYCLING");
  const c = D.storyContent(w);
  assert.deepEqual([c.hero.value, c.hero.unit], [tr("67,32", "67.32"), "KM"]);
  assert.deepEqual(c.cells.map((x) => x.key), ["TIME", "SPEED", "ELEVATION", "AVG_HR", "MAX_HR", "ENERGY"]);
  assert.equal(c.cells[0].value, "2:41:03");
  assert.equal(c.cells[1].value, tr("25,1", "25.1"));
  assert.equal(c.cells[5].value, tr("1.310", "1,310"));
  const w2 = D.mergeWorkout({ ...D.emptyStats(), activeSeconds: 3600, distanceMeters: 30000, averageHR: 140 }, null, "CYCLING");
  assert.deepEqual(D.pickStats(w2, null, 3).map((x) => x.key), ["DISTANCE", "TIME", "SPEED"]);
  assert.deepEqual(D.pickStats(w2, ["AVG_HR", "LAPS", "DISTANCE"], 3).map((x) => x.key), ["AVG_HR", "DISTANCE"]);
  assert.deepEqual(D.toggleStat(["DISTANCE", "TIME", "SPEED"], "AVG_HR", 3), ["TIME", "SPEED", "AVG_HR"]);
  assert.deepEqual(D.toggleStat(["DISTANCE"], "DISTANCE", 3), ["DISTANCE"]);
  const swim = D.storyContent(D.mergeWorkout({ ...D.emptyStats(), activeSeconds: 1800, distanceMeters: 1000, laps: 40, swolf: 38, swimStyle: "freestyle" }, null, null));
  assert.equal(swim.cells[1].value, "3:00");
  assert.equal(swim.cells[1].unit, "/100M");
  assert.equal(D.storyContent(D.mergeWorkout({ ...D.emptyStats(), activeSeconds: 2892, averageHR: 120 }, null, null)).hero.value, "48:12");
});

test("share cards: Mi Fitness, Garmin, Strava, traps", () => {
  const mi = D.parseShareCard(`Radfahren im Freien\n02.11.2026 10:15\n42,18\nKilometer\n1:38:22\nDauer\n25,7 km/h\nØ Geschwindigkeit\n142 bpm\nØ Herzfrequenz\n171 bpm\nMax. Herzfrequenz\n812 kcal\nKalorien`);
  near(mi.distanceMeters, 42180, 0.1);
  assert.deepEqual([mi.activeSeconds, mi.averageHR, mi.maxHR, mi.calories, mi.sport], [5902, 142, 171, 812, "CYCLING"]);
  const g = D.parseShareCard(`Cologne Running\nDistance\n10.02 km\nTime\n52:41\nAvg Pace\n5:15 /km\nTotal Ascent\n84 m\nCalories\n1,034\nAvg HR\n151`);
  near(g.distanceMeters, 10020, 0.1);
  assert.deepEqual([g.activeSeconds, g.elevationGainMeters, g.calories, g.averageHR, g.sport], [3161, 84, 1034, 151, "RUNNING"]);
  const st = D.parseShareCard(`Morning Ride\nDistance Elev Gain Time\n26.2 mi 289 m 1h 38m`);
  near(st.distanceMeters, 26.2 * 1609.344, 1);
  assert.deepEqual([st.elevationGainMeters, st.activeSeconds], [289, 5880]);
  const trap = D.parseShareCard(`Run 18:45\n5:32 /km\n12,4 km/h\n3,5 km`);
  near(trap.distanceMeters, 3500, 0.1);
  assert.equal(trap.activeSeconds, 0);
  assert.equal(D.parseShareCard("Holiday photo\nBeach"), null);
  assert.ok(D.looksLikeImage(new Uint8Array([0x89, 0x50, 0x4e, 0x47])));
  assert.ok(D.looksLikeImage(new Uint8Array([0xff, 0xd8, 0xff])));
  assert.ok(!D.looksLikeImage(enc("{}")));
});

test("mi fitness tcx without gps gives stats", () => {
  const tcx = `<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><TrainingCenterDatabase creator="Mi Fitness" version="1.0"><Activities><Activity Sport="Radfahren im Freien"><Id>2026-11-01T14:05:00.000Z</Id><Calories>412</Calories><Lap><TotalTimeSeconds>3725</TotalTimeSeconds><DistanceMeters>24310</DistanceMeters><Calories>412</Calories><CumulativeClimb>186.50</CumulativeClimb><HeartRateBpm>138</HeartRateBpm><AverageSpeed>6.5</AverageSpeed><Steps>0</Steps></Lap></Activity></Activities></TrainingCenterDatabase>`;
  const s = D.parseTcxStats(tcx);
  assert.equal(s.activeSeconds, 3725);
  assert.equal(s.distanceMeters, 24310);
  assert.equal(s.calories, 412);
  assert.equal(s.averageHR, 138);
  near(s.elevationGainMeters, 186.5, 0.01);
  assert.equal(s.sport, "CYCLING");
  assert.equal(s.startMillis, new Date(2026, 10, 1, 14, 5).getTime());
});

test("garmin tcx laps are summed", () => {
  const tcx = `<TrainingCenterDatabase><Activities><Activity Sport="Running"><Id>2026-06-21T10:00:00Z</Id>
    <Lap><TotalTimeSeconds>600</TotalTimeSeconds><DistanceMeters>2000</DistanceMeters><Calories>100</Calories><AverageHeartRateBpm><Value>140</Value></AverageHeartRateBpm><MaximumHeartRateBpm><Value>160</Value></MaximumHeartRateBpm>
    <Track><Trackpoint><HeartRateBpm><Value>99</Value></HeartRateBpm></Trackpoint></Track></Lap>
    <Lap><TotalTimeSeconds>300</TotalTimeSeconds><DistanceMeters>1000</DistanceMeters><Calories>50</Calories><AverageHeartRateBpm><Value>170</Value></AverageHeartRateBpm><MaximumHeartRateBpm><Value>180</Value></MaximumHeartRateBpm></Lap>
    </Activity></Activities></TrainingCenterDatabase>`;
  const s = D.parseTcxStats(tcx);
  assert.equal(s.activeSeconds, 900);
  assert.equal(s.distanceMeters, 3000);
  assert.equal(s.calories, 150);
  assert.equal(s.averageHR, 150);
  assert.equal(s.maxHR, 180);
  assert.equal(s.sport, "RUNNING");
  assert.equal(s.startMillis, 1782036000000);
  assert.equal(D.parseTcxStats("<TrainingCenterDatabase><Lap></Lap></TrainingCenterDatabase>"), null);
});

test("start screen sources and demo", () => {
  assert.equal(D.SOURCES.at(-1).id, "OTHER");
  for (const s of D.SOURCES) { assert.equal(s.numbers.length, 2); assert.equal(s.map.length, 2); }
  const { stats, route } = D.demoWorkout();
  assert.ok(route.distanceMeters > 25000 && route.distanceMeters < 40000);
  assert.ok(route.splitSeconds.length >= 25);
  assert.equal(stats.hrCurve.length, 120);
  assert.equal(stats.sport, "CYCLING");
});

test("photo-in-PNG placement and gradient stops stay on the story", async () => {
  const { fadeStops, stickerTop } = await import("../docs/app/js/render.js");
  for (const place of [0, 1, 2]) for (const [y0, y1] of [[0, 400], [150, 900], [1200, 1920], [700, 700]]) {
    const { pos: s, share } = fadeStops(place, y0, y1, 1920);
    assert.equal(share.length, s.length);
    assert.ok(s.every((v, i) => v >= 0 && v <= 1 && (i === 0 || v >= s[i - 1])), `${place} ${y0}..${y1} → ${s}`);
  }
  assert.equal(fadeStops(2, 1300, 1760, 1920).share.at(-1), 1); // bottom: darkest at the bottom edge
  assert.equal(fadeStops(1, 150, 600, 1920).share[0], 1); // top: darkest at the top edge
  for (const place of [0, 1, 2]) for (const h of [300, 1700, 2500]) {
    const top = stickerTop(place, h, 1920);
    assert.ok(top >= 0 && (top + h <= 1920 || top === 0), `place ${place} h ${h} top ${top}`);
  }
  assert.equal(stickerTop(0, 400, 1920), 760);
});
