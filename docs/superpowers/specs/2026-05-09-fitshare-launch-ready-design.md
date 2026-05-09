# FitShare — Launch-Ready Design Spec
**Date:** 2026-05-09  
**Scope:** Bug fixes + Schwimmen/Radfahren Polish für persönliche Nutzung  
**Goal:** App funktioniert zuverlässig end-to-end für Gadgetbridge-Exporte (Mi Band 10)

---

## Kontext

FitShare ist eine Android-App (Kotlin + Jetpack Compose), die Gadgetbridge-Workout-JSONs in Instagram-Story-Bilder (1080×1920 PNG) umwandelt. Der User nutzt die App persönlich für Schwimm- und Rad-Workouts mit einem Mi Band 10.

**Aktueller Stand:** Alle Kern-Features sind implementiert, aber drei kritische Bugs verhindern korrekte Funktion. Zusätzlich sind Stat-Layouts für die Zielsportarten suboptimal.

---

## Abschnitt 1: Bug Fixes

### Bug 1 — Background-Bild wird im Export ignoriert

**Problem:** `StoryRenderer.renderToBitmap()` nimmt `backgroundUri: Uri?` entgegen, zeichnet aber ausschließlich den Farb-Gradient. Das exportierte PNG hat kein Hintergrundbild, auch wenn der User in der Vorschau eines gewählt hat.

**Fix:** Am Anfang von `renderToBitmap()` wird geprüft, ob `backgroundUri != null`. Falls ja:
1. `context.contentResolver.openInputStream(backgroundUri)` → `BitmapFactory.decodeStream()`
2. Skalierung auf 1080×1920 mit Center-Crop (Aspect-Ratio erhalten, kein Strecken)
3. Bild auf Canvas zeichnen
4. 55% schwarzes Overlay drüber (`Paint` mit `alpha = 140`)

Falls `backgroundUri == null` → bestehende Gradient-Logik unverändert.

**Betroffene Datei:** `app/src/main/java/com/fitshare/export/StoryRenderer.kt`

---

### Bug 2 — Datum fehlt im exportierten Bild

**Problem:** `StoryCard.kt` (Compose-Vorschau) zeigt das Workout-Datum korrekt an. `StoryRenderer.drawWorkoutContent()` zeichnet es nie — das exportierte PNG hat kein Datum.

**Fix:** Neue private Funktion `drawDate(canvas, workout, paddingH, y, paint)` in `StoryRenderer`, die aufgerufen wird direkt vor dem Activity-Label. Verwendet `SimpleDateFormat("d. MMMM yyyy", Locale.GERMAN)` — identisch zu `formatDate()` in `StoryCard.kt`. Farbe: `#8A9BBF` (OnSurfaceMuted), `textSize = height * 0.018f`.

**Betroffene Datei:** `app/src/main/java/com/fitshare/export/StoryRenderer.kt`

---

### Bug 3 — Datums-Parsing schlägt bei content:// URIs fehl

**Problem:** `WorkoutViewModel.loadFromUri()` extrahiert den Dateinamen via `uri.lastPathSegment`. Bei `content://` URIs (z.B. wenn Gadgetbridge direkt per Share-Intent teilt) liefert `lastPathSegment` einen encoded Pfad statt des echten Dateinamens. `parseFilenameDate()` findet dann kein passendes Muster und fällt auf `System.currentTimeMillis()` zurück.

**Fix:** In `WorkoutViewModel.loadFromUri()` wird der Dateiname via `ContentResolver.query()` abgefragt:
```kotlin
val filename = context.contentResolver.query(uri, 
    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor -> 
        if (cursor.moveToFirst()) cursor.getString(0) else null 
    } ?: uri.lastPathSegment ?: ""
```
`uri.lastPathSegment` bleibt als Fallback.

**Betroffene Datei:** `app/src/main/java/com/fitshare/data/WorkoutViewModel.kt`

---

## Abschnitt 2: Schwimmen & Radfahren Polish

### Schwimmen — Stat-Reihenfolge

**Problem:** `buildPrimaryStats()` (in `StoryCard.kt` und `StoryRenderer.kt`) hat Priorität: Zeit → Pace/100m → Stil → Bahnen. Da nur 3 Slots vorhanden, fällt **Bahnen** immer raus — dabei ist Bahnen für Schwimmer ein zentraler Trainingsindikator.

**Fix:** Neue Reihenfolge für Primary Stats (Schwimmen): **Zeit → Pace/100m → Bahnen**  
Stil wird aus den Stats entfernt (in der Primary wegen Platzmangel rausgedrängt, in Secondary nicht nötig da Bahnen+SWOLF aussagekräftiger sind).

Secondary Stats Schwimmen: **kcal → Züge → SWOLF** (unverändert zur bestehenden Logik).

**Betroffene Dateien:** `StoryCard.kt`, `StoryRenderer.kt` (beide `buildPrimaryStats`/`buildSecondaryStats`)

---

### Radfahren — Secondary Stats aufwerten

**Problem:** `buildSecondaryStats()` für Radfahren zeigt nur `kcal` allein in der Secondary-Reihe. Das wirkt leer und verschwendet Platz.

**Fix:** Secondary Stats Radfahren: **kcal → Ø Puls → Max Puls** (alle drei nur wenn > 0). Das Mi Band 10 zeichnet HR beim Radfahren auf.

`StoryCard.kt` zeigt `maxHR` bereits. `StoryRenderer.buildSecondaryStatsList()` fehlt `maxHR` — muss explizit ergänzt werden:
```kotlin
if (workout.maxHR > 0) stats.add(workout.maxHR.toString() to "Max Puls")
```
Beide Funktionen müssen identisch sein.

**Betroffene Dateien:** `StoryCard.kt`, `StoryRenderer.kt`

---

## Abschnitt 3: App Icon

**Problem:** Die App verwendet wahrscheinlich den Standard-Android-Placeholder-Icon.

**Fix:** Programmatisch generiertes adaptive Icon:
- **Hintergrund:** Dunkelblauer Verlauf (`#0A1628` → `#1A3A5C`) — identisch mit Schwimm-Gradient
- **Vordergrund:** Weißes `F` in Bold + kleines Wellen-Symbol (🌊) als SVG `ic_launcher_foreground.xml`
- **Dateien:** `res/mipmap-*/ic_launcher.png` (via Android Studio Image Asset oder manuell als VectorDrawable)

---

---

## Abschnitt 4: Fehlende Permission (API 26–28)

**Problem:** `ShareHelper.saveBitmapToGallery()` nutzt `RELATIVE_PATH` und `IS_PENDING` (API 29+). Auf Android 8/8.1/9 (API 26–28) schlägt der MediaStore-Insert ohne `WRITE_EXTERNAL_STORAGE` still fehl — das exportierte Bild landet nicht in der Galerie.

**Fix:** In `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```
Damit wird die Permission nur auf API ≤ 28 angefordert; ab API 29 ist sie nicht nötig. `minSdk` bleibt bei 26.

Zusätzlich Runtime-Permission-Request in `MainActivity` für API < 29: vor dem ersten Export prüfen ob `WRITE_EXTERNAL_STORAGE` granted ist, sonst `ActivityCompat.requestPermissions()`.

**Betroffene Dateien:** `AndroidManifest.xml`, `MainActivity.kt`

---

## Nicht im Scope

- Zweites Story-Template
- Workout-Historie (Room DB)
- Animierte Stories (MP4)
- Instagram Direct-Post API
- Triathlon/Multi-Sport

---

## Dateien die geändert werden

| Datei | Änderung |
|---|---|
| `export/StoryRenderer.kt` | Bug 1 (Background), Bug 2 (Datum), Schwimm/Rad Stats |
| `data/WorkoutViewModel.kt` | Bug 3 (Dateiname aus ContentResolver) |
| `ui/components/StoryCard.kt` | Schwimm/Rad Stat-Reihenfolge |
| `res/mipmap-*/ic_launcher*` | App Icon |
| `res/drawable/ic_launcher_foreground.xml` | Icon Vordergrund |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive Icon |
| `AndroidManifest.xml` | WRITE_EXTERNAL_STORAGE Permission (API 26–28) |
| `MainActivity.kt` | Runtime Permission Request vor Export |
