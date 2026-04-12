# FitShare — Gadgetbridge Workout-Visualizer for Instagram Stories

## Ziel
Android-App, die Gadgetbridge-Exports (JSON) importiert und daraus Instagram-Story-taugliche Bilder (1080x1920) generiert — im Stil von Mi Fitness / Garmin Connect.

---

## 1. Datenformat (Gadgetbridge Export)

```json
{
  "activeSeconds": { "unit": "seconds", "value": 1206.0 },
  "distanceMeters": { "unit": "meters", "value": 1000.0 },
  "active_calories": { "unit": "calories_unit", "value": 263.0 },
  "strokes": { "unit": "strokes_unit", "value": 471.0 },
  "swimStyle": { "unit": "string", "value": "breaststroke" },
  "laps": { "unit": "laps_unit", "value": 50.0 },
  "swolfAvg": { "unit": "", "value": 34.0 },
  "configuredLengthsGoal": { "unit": "", "value": 50.0 },
  "vitality_gain": { "unit": "", "value": 6.0 }
}
```

Felder variieren je nach Aktivitätstyp (Schwimmen, Laufen, Radfahren etc.).

---

## 2. Tech Stack

| Komponente | Technologie |
|---|---|
| Framework | **Kotlin + Jetpack Compose** |
| Build | Gradle (Kotlin DSL) |
| Min SDK | API 26 (Android 8.0) |
| Bild-Export | `Canvas` → `Bitmap` → PNG speichern |
| Share | Android Sharesheet / Intent.ACTION_SEND |
| File Picker | SAF (Storage Access Framework) für JSON-Import |
| Hintergrundbild | Optional: Kamera / Galerie-Bild als Story-Background |

---

## 3. App-Architektur

```
FitShare/
├── app/src/main/java/com/fitshare/
│   ├── MainActivity.kt              # Entry, File Picker
│   ├── data/
│   │   ├── WorkoutData.kt           # Data class für Gadgetbridge JSON
│   │   └── WorkoutParser.kt         # JSON → WorkoutData
│   ├── ui/
│   │   ├── theme/
│   │   │   └── Theme.kt             # Dark sport-theme
│   │   ├── screens/
│   │   │   ├── ImportScreen.kt       # Datei auswählen
│   │   │   ├── EditorScreen.kt       # Story bearbeiten (Hintergrund, Farbe)
│   │   │   └── PreviewScreen.kt      # Vorschau 9:16
│   │   └── components/
│   │       ├── StoryCard.kt          # Das Haupt-Layout (Compose Canvas)
│   │       ├── StatRow.kt            # Einzelne Stat-Zeile
│   │       └── ActivityIcon.kt       # Schwimmen/Laufen/Rad Icons
│   └── export/
│       ├── StoryRenderer.kt         # Compose → Bitmap (1080x1920)
│       └── ShareHelper.kt           # Intent zum Teilen
```

---

## 4. Story-Layout Design (angelehnt an Mi Fitness Screenshot)

```
┌──────────────────────────────┐  1080x1920
│  [App Logo]        [QR opt.] │
│                              │
│                              │
│     ┌──────────────────┐     │
│     │                  │     │
│     │   HINTERGRUND    │     │  ← Foto aus Galerie
│     │   (optional)     │     │    oder Farbverlauf
│     │                  │     │
│     └──────────────────┘     │
│                              │
│  📅 7. April 2026            │
│  🏊 Beckenschwimmen          │
│                              │
│  ┌────────┬──────────┬─────┐ │
│  │ 20:06  │ Brust    │  50 │ │
│  │ Zeit   │ Stil     │Bahn.│ │
│  └────────┴──────────┴─────┘ │
│                              │
│  ██████████████████████████  │
│  1000 m                      │  ← Hero-Zahl, groß
│                              │
│  ┌────────┬──────────┬─────┐ │
│  │  263   │   471    │  34 │ │
│  │ kcal   │ Züge     │SWOLF│ │
│  └────────┴──────────┴─────┘ │
│                              │
│  ───── Puls-Graph ─────────  │  ← falls HR-Daten vorhanden
│                              │
│  FitShare • Gadgetbridge     │
└──────────────────────────────┘
```

### Design-Regeln
- **Hintergrund**: Dunkler Farbverlauf (z.B. #0A1628 → #1A3A5C für Schwimmen) ODER eigenes Foto mit Dark Overlay (60% schwarz)
- **Hero-Zahl**: Distanz riesig (80sp+), weiß, bold
- **Stat-Grid**: 3 Spalten, Label klein+grau, Wert groß+weiß
- **Schrift**: Sans-serif (Roboto/Inter), alles weiß auf dunkel
- **Farb-Akzente pro Sportart**:
  - 🏊 Schwimmen: Blau (#2196F3)
  - 🏃 Laufen: Grün (#4CAF50)
  - 🚴 Radfahren: Orange (#FF9800)
  - 💪 Sonstiges: Violett (#9C27B0)

---

## 5. Features (MVP)

### Must-Have
1. **JSON importieren** — Datei über SAF öffnen, parsen
2. **Aktivitätstyp erkennen** — anhand vorhandener Felder (swimStyle → Schwimmen, etc.)
3. **Story rendern** — Canvas-basiertes Layout, 1080x1920
4. **Hintergrundbild wählen** — Galerie oder vordefinierte Gradients
5. **Als PNG exportieren** — in Galerie speichern
6. **Direkt teilen** — Share Intent (Instagram, WhatsApp, etc.)

### Nice-to-Have (v2)
- Mehrere Templates/Themes wählbar
- Herzfrequenz-Graph (falls in erweiterten Exports vorhanden)
- Automatischer Import wenn Gadgetbridge exportiert (Intent Filter)
- Eigenes Profilbild/Name einstellbar
- Pace-Berechnung für Laufen
- Wöchentliche/monatliche Zusammenfassung
- Widget für Homescreen

---

## 6. Implementierungsplan

### Phase 1: Projekt-Setup & Daten (Tag 1)
- [ ] Android-Projekt mit Compose erstellen
- [ ] WorkoutData.kt — Data classes
- [ ] WorkoutParser.kt — Gson/Moshi JSON Parsing
- [ ] Unit Tests für Parser

### Phase 2: UI & Layout (Tag 2)
- [ ] Theme (Dark, Sport-Farben)
- [ ] ImportScreen — File Picker
- [ ] StoryCard Composable — das Haupt-Layout
- [ ] StatRow, ActivityIcon Komponenten
- [ ] Preview im 9:16 Format

### Phase 3: Bild-Export & Sharing (Tag 3)
- [ ] Compose → Bitmap Rendering
- [ ] PNG in MediaStore speichern
- [ ] Share Intent implementieren
- [ ] Hintergrundbild aus Galerie

### Phase 4: Polish (Tag 4)
- [ ] Animationen / Übergänge
- [ ] Verschiedene Sportart-Erkennung
- [ ] Fehlerbehandlung (ungültige JSON etc.)
- [ ] App Icon & Splash Screen

---

## 7. Gadgetbridge-Felder Mapping

| JSON-Feld | Anzeige | Formatierung |
|---|---|---|
| `activeSeconds` | Zeit | MM:SS oder HH:MM:SS |
| `distanceMeters` | Distanz (Hero) | X.XX km oder XXX m |
| `active_calories` | Kalorien | XXX kcal |
| `strokes` | Züge | nur bei Schwimmen |
| `swimStyle` | Stil | breaststroke→Brust, freestyle→Freistil |
| `laps` | Bahnen | nur bei Schwimmen |
| `swolfAvg` | SWOLF | nur bei Schwimmen |
| `steps` | Schritte | nur bei Laufen |
| `averageHR` | Ø Puls | falls vorhanden |
| `maxHR` | Max Puls | falls vorhanden |

---

## 8. Dateinamen-Parsing

Der Gadgetbridge-Export-Dateiname enthält den Zeitstempel:
`2026-04-07T13_55_47+02_00.json` → **7. April 2026, 13:55**

Diesen als Datum auf der Story anzeigen.

---

## 9. Abhängigkeiten

```kotlin
// build.gradle.kts
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")
    
    // JSON Parsing
    implementation("com.google.code.gson:gson:2.11.0")
    
    // Image Loading (für Hintergrundbilder)
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Activity Result API (File Picker)
    implementation("androidx.activity:activity-compose:1.9.3")
}
```
