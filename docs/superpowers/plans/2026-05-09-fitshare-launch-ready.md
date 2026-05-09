# FitShare Launch-Ready Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** FitShare korrekt und poliert für persönliche Nutzung (Schwimmen + Radfahren mit Mi Band 10) — alle 3 kritischen Bugs gefixt, Stat-Layout optimiert, App-Icon gesetzt.

**Architecture:** Bestehende Architektur (MVVM + Compose + Canvas Renderer) bleibt unverändert. Korrekturen in 3 Layern: ViewModel (Filename-Extraktion), StoryRenderer (Background + Datum + Stats), AndroidManifest/MainActivity (Permission). App-Icon via Adaptive Icon XML (API 26+, kein PNG-Fallback nötig).

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose BOM 2024.12.01, Android Canvas API, MediaStore/ContentResolver, ActivityResultContracts für Runtime-Permission

---

## Datei-Übersicht

| Datei | Was ändert sich |
|---|---|
| `data/WorkoutViewModel.kt` | Filename via `OpenableColumns.DISPLAY_NAME` statt `lastPathSegment` |
| `export/StoryRenderer.kt` | Background-Bild rendern, Datum zeichnen, `maxHR` ergänzen |
| `ui/components/StoryCard.kt` | Swimming Primary Stats: Bahnen statt Stil |
| `AndroidManifest.xml` | `WRITE_EXTERNAL_STORAGE` für API ≤ 28 |
| `MainActivity.kt` | Runtime-Permission-Request vor Export auf API < 29 |
| `res/drawable/ic_launcher_foreground.xml` | **Neu:** Icon-Vordergrund (VectorDrawable) |
| `res/drawable/ic_launcher_background.xml` | **Neu:** Icon-Hintergrund (Farbe) |
| `res/mipmap-anydpi-v26/ic_launcher.xml` | **Neu:** Adaptive Icon Config |
| `res/mipmap-anydpi-v26/ic_launcher_round.xml` | **Neu:** Adaptive Round Icon Config |

---

## Task 1: Bug 3 — Dateiname aus ContentResolver extrahieren

**Files:**
- Modify: `app/src/main/java/com/fitshare/data/WorkoutViewModel.kt`

Wenn Gadgetbridge direkt per Share-Intent teilt, liefert `uri.lastPathSegment` einen encoded Content-Path statt des Dateinamens. Der Regex in `parseFilenameDate()` findet dann kein Match → Datum bleibt leer.

- [ ] **Schritt 1: `loadFromUri()` in WorkoutViewModel.kt anpassen**

Ersetze die Zeile `val filename = uri.lastPathSegment ?: ""` durch:

```kotlin
val filename = context.contentResolver.query(
    uri,
    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
    null, null, null
)?.use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
} ?: uri.lastPathSegment ?: ""
```

Die Methode `loadFromUri()` beginnt bei Zeile 44. Der vollständige Block nach der Änderung:

```kotlin
fun loadFromUri(uri: Uri) {
    viewModelScope.launch {
        _state.value = WorkoutState.Loading
        _gpsTrack.value = null
        try {
            val context = getApplication<Application>()
            val filename = context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: ""

            val jsonString = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalStateException("Datei konnte nicht gelesen werden")

            val result = WorkoutParser.parse(jsonString, filename)
            result.fold(
                onSuccess = { _state.value = WorkoutState.Success(it) },
                onFailure = { _state.value = WorkoutState.Error("Ungültige Datei: ${it.message}") }
            )
        } catch (e: Exception) {
            _state.value = WorkoutState.Error(e.message ?: "Unbekannter Fehler")
        }
    }
}
```

- [ ] **Schritt 2: Kompilierung prüfen**

```bash
cd /home/user/Dokumente/VibeCoding/FitShare
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Erwartetes Ergebnis: `BUILD SUCCESSFUL`

- [ ] **Schritt 3: Commit**

```bash
git add app/src/main/java/com/fitshare/data/WorkoutViewModel.kt
git commit -m "fix: extract filename via OpenableColumns for content:// URIs"
```

---

## Task 2: Bug 2 — Datum im exportierten Bild zeichnen

**Files:**
- Modify: `app/src/main/java/com/fitshare/export/StoryRenderer.kt`

`drawWorkoutContent()` zeichnet kein Datum. Das Compose-Preview zeigt es, das PNG nicht.

- [ ] **Schritt 1: Import ergänzen**

In `StoryRenderer.kt` sicherstellen dass diese Imports vorhanden sind (am Dateianfang nach `package`):

```kotlin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

- [ ] **Schritt 2: Datum-Zeichnung in `drawWorkoutContent()` einfügen**

In `drawWorkoutContent()` beginnt der mittlere Abschnitt mit `var y = height * 0.6f`. Direkt nach dieser Zeile und **vor** dem Activity-Label Block einfügen:

```kotlin
// Date
if (workout.dateTimeMillis > 0) {
    val datePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8A9BBF")
        textSize = height * 0.018f
    }
    val dateStr = SimpleDateFormat("d. MMMM yyyy", Locale.GERMAN)
        .format(Date(workout.dateTimeMillis))
    canvas.drawText(dateStr, paddingH, y, datePaint)
    y += datePaint.textSize * 1.5f
}
```

Der vollständige Block ab `var y = height * 0.6f`:

```kotlin
var y = height * 0.6f

// Date
if (workout.dateTimeMillis > 0) {
    val datePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8A9BBF")
        textSize = height * 0.018f
    }
    val dateStr = SimpleDateFormat("d. MMMM yyyy", Locale.GERMAN)
        .format(Date(workout.dateTimeMillis))
    canvas.drawText(dateStr, paddingH, y, datePaint)
    y += datePaint.textSize * 1.5f
}

// Activity label
val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = whiteArgb
    textSize = height * 0.038f
    typeface = android.graphics.Typeface.DEFAULT_BOLD
}
canvas.drawText(workout.activityType.labelDe, paddingH, y, labelPaint)
y += labelPaint.textSize * 1.6f
```

- [ ] **Schritt 3: Kompilierung prüfen**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/fitshare/export/StoryRenderer.kt
git commit -m "fix: draw workout date in exported story bitmap"
```

---

## Task 3: Bug 1 — Background-Bild im Export rendern

**Files:**
- Modify: `app/src/main/java/com/fitshare/export/StoryRenderer.kt`

`renderToBitmap()` zeichnet immer nur den Gradient — `backgroundUri` wird ignoriert.

- [ ] **Schritt 1: Import ergänzen**

Sicherstellen dass diese Imports in `StoryRenderer.kt` vorhanden sind:

```kotlin
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
```

- [ ] **Schritt 2: Gradient-Logik in eigene Funktion extrahieren**

Am Ende von `StoryRenderer` (vor der letzten `}`), neue private Funktion hinzufügen:

```kotlin
private fun drawGradientBackground(
    canvas: android.graphics.Canvas,
    workout: WorkoutData,
    width: Int,
    height: Int
) {
    val gradientStart = workout.activityType.gradientStart
    val gradientEnd = workout.activityType.gradientEnd
    val paint = android.graphics.Paint()
    val shader = android.graphics.LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        gradientStart.toArgb(),
        gradientEnd.toArgb(),
        android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = shader
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
}

private fun drawCroppedBitmap(
    canvas: android.graphics.Canvas,
    src: android.graphics.Bitmap,
    dstW: Int,
    dstH: Int
) {
    val srcAspect = src.width.toFloat() / src.height
    val dstAspect = dstW.toFloat() / dstH
    val srcRect: Rect
    if (srcAspect > dstAspect) {
        val w = (src.height * dstAspect).toInt()
        val x = (src.width - w) / 2
        srcRect = Rect(x, 0, x + w, src.height)
    } else {
        val h = (src.width / dstAspect).toInt()
        val y = (src.height - h) / 2
        srcRect = Rect(0, y, src.width, y + h)
    }
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(src, srcRect, Rect(0, 0, dstW, dstH), paint)
}
```

- [ ] **Schritt 3: `renderToBitmap()` umschreiben**

Die bestehende `renderToBitmap()` Funktion ersetzen:

```kotlin
suspend fun renderToBitmap(
    context: Context,
    workout: WorkoutData,
    backgroundUri: Uri?
): Bitmap {
    val backgroundBitmap = backgroundUri?.let { uri ->
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) { null }
        }
    }

    return withContext(Dispatchers.Main) {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        if (backgroundBitmap != null) {
            drawCroppedBitmap(canvas, backgroundBitmap, width, height)
            backgroundBitmap.recycle()
            val overlayPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(140, 0, 0, 0)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        } else {
            drawGradientBackground(canvas, workout, width, height)
        }

        drawWorkoutContent(canvas, workout, width, height)
        bitmap
    }
}
```

- [ ] **Schritt 4: Kompilierung prüfen**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Schritt 5: Commit**

```bash
git add app/src/main/java/com/fitshare/export/StoryRenderer.kt
git commit -m "fix: render background image in exported story bitmap"
```

---

## Task 4: Polish — Swimming Primary Stats (Bahnen statt Stil)

**Files:**
- Modify: `app/src/main/java/com/fitshare/ui/components/StoryCard.kt`
- Modify: `app/src/main/java/com/fitshare/export/StoryRenderer.kt`

Aktuell: Primary = Zeit, Pace/100m, Stil → Bahnen fällt raus (4. Stelle, aber nur 3 Slots).
Fix: Primary = Zeit, Pace/100m, Bahnen. Stil komplett entfernt (selten relevant).

- [ ] **Schritt 1: `buildPrimaryStats()` in `StoryCard.kt` anpassen**

In `StoryCard.kt` die `buildPrimaryStats()` Funktion, SWIMMING-Branch ersetzen:

```kotlin
// Vorher:
ActivityType.SWIMMING -> {
    workout.swimStyle?.let { stats.add(it.labelDe to "Stil") }
    if (workout.laps > 0) stats.add(workout.laps.toString() to "Bahnen")
}

// Nachher:
ActivityType.SWIMMING -> {
    if (workout.laps > 0) stats.add(workout.laps.toString() to "Bahnen")
}
```

- [ ] **Schritt 2: `buildPrimaryStatsList()` in `StoryRenderer.kt` identisch anpassen**

In `StoryRenderer.kt` die `buildPrimaryStatsList()` Funktion, SWIMMING-Branch ersetzen:

```kotlin
// Vorher:
ActivityType.SWIMMING -> {
    workout.swimStyle?.let { stats.add(it.labelDe to "Stil") }
    if (workout.laps > 0) stats.add(workout.laps.toString() to "Bahnen")
}

// Nachher:
ActivityType.SWIMMING -> {
    if (workout.laps > 0) stats.add(workout.laps.toString() to "Bahnen")
}
```

- [ ] **Schritt 3: Kompilierung prüfen**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/java/com/fitshare/ui/components/StoryCard.kt
git add app/src/main/java/com/fitshare/export/StoryRenderer.kt
git commit -m "polish: show Bahnen instead of Stil in swimming primary stats"
```

---

## Task 5: Polish — maxHR in StoryRenderer Secondary Stats ergänzen

**Files:**
- Modify: `app/src/main/java/com/fitshare/export/StoryRenderer.kt`

`StoryCard.kt` zeigt `maxHR` in Secondary Stats. `StoryRenderer.buildSecondaryStatsList()` fehlt `maxHR` → das exportierte PNG zeigt Max Puls nicht, die Vorschau schon.

- [ ] **Schritt 1: `buildSecondaryStatsList()` in `StoryRenderer.kt` ergänzen**

Die Funktion endet aktuell nach `averageHR`. Direkt danach `maxHR` hinzufügen:

```kotlin
private fun buildSecondaryStatsList(workout: WorkoutData): List<Pair<String, String>> {
    val stats = mutableListOf<Pair<String, String>>()
    if (workout.calories > 0) stats.add(workout.calories.toString() to "kcal")
    if (workout.strokes > 0) stats.add(workout.strokes.toString() to "Züge")
    if (workout.swolfAvg > 0) stats.add(workout.swolfAvg.toString() to "SWOLF")
    if (workout.averageHR > 0) stats.add(workout.averageHR.toString() to "Ø Puls")
    if (workout.maxHR > 0) stats.add(workout.maxHR.toString() to "Max Puls")
    return stats.take(3)
}
```

- [ ] **Schritt 2: Kompilierung prüfen**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Schritt 3: Commit**

```bash
git add app/src/main/java/com/fitshare/export/StoryRenderer.kt
git commit -m "fix: add maxHR to StoryRenderer secondary stats (matches StoryCard)"
```

---

## Task 6: WRITE_EXTERNAL_STORAGE Permission für API 26–28

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/fitshare/MainActivity.kt`

Auf Android 8/9 (API 26–28) schlägt `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` Insert ohne `WRITE_EXTERNAL_STORAGE` still fehl — das exportierte Bild landet nicht in der Galerie.

- [ ] **Schritt 1: Permission in `AndroidManifest.xml` eintragen**

Nach dem bestehenden `READ_MEDIA_IMAGES` Permission-Block einfügen:

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

Der vollständige Permissions-Block danach:

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

- [ ] **Schritt 2: Runtime-Permission-Request in `MainActivity.kt` einbauen**

Zunächst sicherstellen dass diese Imports in `MainActivity.kt` vorhanden sind (nach den bestehenden Imports einfügen falls fehlend):

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
```

In `MainActivity.kt` innerhalb von `setContent { FitShareTheme { ... } }` direkt nach `val scope = rememberCoroutineScope()` hinzufügen:

```kotlin
val storagePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { /* granted or denied — user can retry */ }
```

Dann den `onShare` Lambda so anpassen, dass auf API < 29 die Permission geprüft wird:

```kotlin
onShare = {
    val workout = (state as WorkoutState.Success).workout
    scope.launch {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@launch
        }
        try {
            val bitmap = StoryRenderer.renderToBitmap(context, workout, backgroundUri)
            ShareHelper.saveBitmapToGallery(context, bitmap)
            val shareUri = ShareHelper.saveBitmapForSharing(context, bitmap)
            if (shareUri != null) {
                ShareHelper.shareImage(context, shareUri, workout.activityType.labelDe)
            } else {
                Toast.makeText(context, "Fehler beim Speichern", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
},
```

- [ ] **Schritt 3: Kompilierung prüfen**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

- [ ] **Schritt 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git add app/src/main/java/com/fitshare/MainActivity.kt
git commit -m "fix: request WRITE_EXTERNAL_STORAGE on API 26-28 before gallery save"
```

---

## Task 7: App Icon

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

Da minSdk = 26, unterstützen alle Geräte Adaptive Icons. Die vorhandenen PNG-Dateien in den mipmap-Ordnern bleiben als Fallback für sehr alte Launcher, werden aber nicht geändert.

- [ ] **Schritt 1: Verzeichnis erstellen**

```bash
mkdir -p /home/user/Dokumente/VibeCoding/FitShare/app/src/main/res/mipmap-anydpi-v26
```

- [ ] **Schritt 2: Background-Drawable erstellen**

Datei: `app/src/main/res/drawable/ic_launcher_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:startColor="#0A1628"
        android:endColor="#1A3A5C"
        android:angle="270" />
</shape>
```

- [ ] **Schritt 3: Foreground-Drawable erstellen**

Datei: `app/src/main/res/drawable/ic_launcher_foreground.xml`

Design: Weißes "F" (Bold) zentriert im 108dp-Canvas + Wellen-Linie darunter (Sport-Symbol).

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Bold "F" letterform -->
    <!-- Vertical bar -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M34,22 L46,22 L46,86 L34,86 Z" />
    <!-- Top horizontal bar -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M34,22 L78,22 L78,34 L34,34 Z" />
    <!-- Middle horizontal bar -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M34,50 L70,50 L70,62 L34,62 Z" />

    <!-- Wave accent line (accent blue) -->
    <path
        android:strokeColor="#2196F3"
        android:strokeWidth="3.5"
        android:strokeLineCap="round"
        android:fillColor="@android:color/transparent"
        android:pathData="M22,78 Q31,70 40,78 Q49,86 58,78 Q67,70 76,78 Q85,86 94,78" />
</vector>
```

- [ ] **Schritt 4: Adaptive Icon Configs erstellen**

Datei: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

Datei: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Schritt 5: Full Build prüfen**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -30
```
Erwartetes Ergebnis: `BUILD SUCCESSFUL` und APK unter `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Schritt 6: Commit**

```bash
git add app/src/main/res/drawable/ic_launcher_foreground.xml
git add app/src/main/res/drawable/ic_launcher_background.xml
git add app/src/main/res/mipmap-anydpi-v26/
git commit -m "feat: add FitShare adaptive app icon"
```

---

## Task 8: End-to-End Verifikation

Manuelle Prüfung nach Installation des APK auf dem Gerät.

- [ ] **Schritt 1: Debug-APK bauen**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -10
# APK liegt unter: app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Schritt 2: Auf Gerät installieren**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Oder APK manuell auf Gerät übertragen und installieren.

- [ ] **Schritt 3: Schwimm-Workout testen**

1. Gadgetbridge → Schwimm-Workout → JSON exportieren → mit FitShare öffnen
2. Vorschau: Datum, Beckenschwimmen, Zeit + Pace/100m + **Bahnen** (nicht Stil), Distanz, kcal + Züge + SWOLF
3. Foto als Hintergrund wählen
4. "Story teilen" → Share Sheet öffnet sich
5. In DCIM/FitShare im Galerie-App: Bild vorhanden, **mit Foto-Hintergrund**, mit Datum

- [ ] **Schritt 4: Rad-Workout testen**

1. Rad-Workout JSON importieren
2. Vorschau: Datum, Radfahren, Zeit + Tempo (km/h), Distanz, kcal + Ø Puls + Max Puls
3. Ohne Hintergrundbild exportieren → orange Gradient im PNG
4. Galerie: Bild vorhanden, korrekte Daten

- [ ] **Schritt 5: Gadgetbridge Direct Share testen**

1. In Gadgetbridge: Aktivität → Teilen → FitShare wählen
2. App öffnet sich und springt direkt zur Vorschau (kein manueller Import)
3. Datum in der Vorschau korrekt (nicht "heute" sondern Workout-Datum)
