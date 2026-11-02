<h1 align="center">FitShare – Gadgetbridge-Workouts als Instagram-Story</h1>

<p align="center"><strong>Android-App, die aus deinem Gadgetbridge-Export und deiner GPS-Route eine fertige Instagram-Story (1080 × 1920) macht. Offline, ohne Konto.</strong></p>

<p align="center">
  <a href="https://kotlinlang.org/"><img alt="Gebaut mit Kotlin" src="https://img.shields.io/badge/gebaut_mit-Kotlin-0C0D0B?style=for-the-badge&logo=kotlin&logoColor=C6F432"></a>
  <br>
  <a href="#installation"><img alt="Android 8.0 oder neuer" src="https://img.shields.io/badge/Android-8.0%2B-C6F432?logo=android&logoColor=C6F432&labelColor=0C0D0B"></a>
  <a href="https://developer.android.com/compose"><img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack_Compose-C6F432?logo=jetpackcompose&logoColor=C6F432&labelColor=0C0D0B"></a>
  <a href="https://gadgetbridge.org/"><img alt="Gadgetbridge JSON-Export" src="https://img.shields.io/badge/Gadgetbridge-JSON_Export-C6F432?labelColor=0C0D0B"></a>
  <a href="#funktionen"><img alt="Routen aus GPX, KML und KMZ" src="https://img.shields.io/badge/Route-GPX_·_KML_·_KMZ-C6F432?labelColor=0C0D0B"></a>
  <a href="#funktionen"><img alt="Offline ohne Tracking" src="https://img.shields.io/badge/Tracking-keins-C6F432?labelColor=0C0D0B"></a>
  <a href="https://github.com/BrickyStudios/FitShare/commits/main"><img alt="Letzter Commit" src="https://img.shields.io/github/last-commit/BrickyStudios/FitShare?label=letzter%20Commit&color=C6F432&labelColor=0C0D0B"></a>
  <a href="https://github.com/BrickyStudios/FitShare/stargazers"><img alt="GitHub-Sterne" src="https://img.shields.io/github/stars/BrickyStudios/FitShare?label=Sterne&color=C6F432&labelColor=0C0D0B"></a>
</p>

<p align="center">
  <a href="#installation">Installation</a> ·
  <a href="#so-gehts">So geht's</a> ·
  <a href="#selbst-bauen">Selbst bauen</a> ·
  <a href="https://github.com/BrickyStudios/FitShare/releases">Releases</a>
</p>

Gadgetbridge zeichnet Workouts von Mi Band, Amazfit und anderen Uhren ohne Hersteller-Cloud auf. Einen Teilen-Knopf wie bei Mi Fitness, Strava oder Garmin Connect hat es aber nicht. FitShare schließt die Lücke: Workout-JSON rein, Route dazu, Story teilen.

<p align="center">
  <img src="docs/bilder/1-nacht.png" width="240" alt="FitShare Instagram-Story im Stil Nacht: Radfahren 64,15 km mit leuchtender GPS-Route, Pulszonen, Tempo und Höhenmetern">
  <img src="docs/bilder/2-startnummer.png" width="240" alt="FitShare Instagram-Story im Stil Startnummer: Papier-Look mit orangem Kopfbalken, Route und Trainingswerten">
  <img src="docs/bilder/3-sticker.png" width="240" alt="FitShare Sticker-Stil: transparentes PNG mit Workout-Werten über einem eigenen Foto">
</p>

<p align="center"><sub>Die Bilder zeigen eine erfundene Demo-Runde, keine echte Strecke.</sub></p>

## Funktionen

- 🎟️ **Drei Stile zum Durchwischen**: *Nacht* (dunkel, leuchtende Route), *Startnummer* (Papier mit Farbbalken wie eine Startnummer) und *Sticker* (transparentes PNG zum Drüberlegen in Instagram).
- 🗺️ **Route aus GPX, KML oder KMZ**: OpenTracks-Exporte funktionieren direkt. Fehlt im Gadgetbridge-Export die Distanz (beim Mi Band 10 üblich), rechnet FitShare sie aus der Route, dazu die Höhenmeter.
- 🚴 **Sportart erkannt oder gewählt**: Radfahren, Laufen, Schwimmen, Wandern oder Training. Jede Sportart hat eigene Kennzahlen und eine eigene Farbe.
- ❤️ **Pulszonen**: Die fünf Gadgetbridge-Zonen erscheinen als Balken.
- 🖼️ **Eigenes Foto** als Hintergrund.
- 📤 **Teilen oder speichern**: Teilen-Menü an Instagram und jede andere App, oder als PNG nach *Bilder/FitShare*.
- 💾 **Merkt sich alles**: Wechselst du zu Instagram und Android beendet FitShare im Hintergrund, ist beim Zurückkommen alles noch da, auch der zuletzt gewählte Stil.
- 🔒 **Offline**: keine Konten, kein Tracking, keine Server.

## Installation

Android 8.0 oder neuer. APKs baut der Workflow [Build & Release APK](.github/workflows/release.yml), sobald ein Tag `v*` gepusht wird. Sie landen unter [Releases](https://github.com/BrickyStudios/FitShare/releases) und lassen sich mit Obtainium verfolgen.

## So geht's

1. In Gadgetbridge die Aktivität öffnen und als JSON teilen oder exportieren. Optional die Route aus OpenTracks als KMZ oder GPX.
2. In FitShare **Dateien wählen** und beide auf einmal markieren. Alternativ beide nacheinander an FitShare teilen.
3. Stil wischen, Sportart antippen, Foto drunterlegen.
4. **In Story teilen** tippen und Instagram auswählen.

<p align="center">
  <img src="docs/bilder/app-start.png" width="240" alt="FitShare Startbildschirm: Dateien aus Gadgetbridge und OpenTracks wählen">
  <img src="docs/bilder/app-editor.png" width="240" alt="FitShare Editor: Stil wischen, Sportart wählen, Foto und GPS-Route hinzufügen, in die Instagram-Story teilen">
</p>

## Selbst bauen

```bash
./gradlew testDebugUnitTest assembleRelease
```

Kotlin, Jetpack Compose, JDK 17. Vorschau und Export zeichnet derselbe Canvas-Renderer (`export/StoryRenderer.kt`), deshalb sieht die geteilte Story genau aus wie die Vorschau. Schriften: [Big Shoulders Display](https://fonts.google.com/specimen/Big+Shoulders+Display) und [Archivo](https://fonts.google.com/specimen/Archivo), beide unter der SIL Open Font License (`app/src/main/assets/fonts`).

## Hinweis

FitShare ist ein unabhängiges Projekt und gehört weder zu Gadgetbridge noch zu Instagram oder Meta.
