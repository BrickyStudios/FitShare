<div align="center">

# FitShare – Gadgetbridge-Workouts als Instagram Story

<!-- badges -->
[![kotlin](https://img.shields.io/badge/made_with-kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](#installation)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Gadgetbridge](https://img.shields.io/badge/Gadgetbridge-JSON_Export-FF6F00)](https://gadgetbridge.org/)
[![Last Commit](https://img.shields.io/github/last-commit/BrickyStudios/FitShare)](https://github.com/BrickyStudios/FitShare/commits/main)
[![Stars](https://img.shields.io/github/stars/BrickyStudios/FitShare?style=flat)](https://github.com/BrickyStudios/FitShare/stargazers)

**Android-App, die deinen Gadgetbridge-Export in eine fertige Instagram Story (1080 × 1920) verwandelt: mit Distanz, Zeit, Pace, GPS-Route und eigenem Hintergrundbild.**

</div>

Gadgetbridge zeichnet Workouts von Mi Band, Amazfit & Co. ohne Hersteller-Cloud auf, hat aber keinen hübschen Teilen-Knopf wie Mi Fitness oder Garmin Connect. FitShare schließt die Lücke: JSON importieren, optional GPX-Route dazu, Story teilen.

## Funktionen

- 🏊 **Schwimmen, Laufen, Radfahren**: passende Kennzahlen je Sportart, etwa Schwimmstil, Bahnen und SWOLF beim Schwimmen oder Pace beim Laufen.
- 🗺️ **GPS-Route**: GPX-Datei laden, FitShare zeichnet den Track in die Story.
- 🖼️ **Eigener Hintergrund**: Foto aus der Galerie hinter die Werte legen.
- 📤 **Direkt teilen**: über das Android-Teilen-Menü an Instagram oder jede andere App, oder als PNG speichern.
- 🔒 **Offline**: keine Konten, kein Tracking, keine Server. Alles passiert auf dem Handy.

## Installation

Android 8.0 oder neuer. APKs entstehen über den Workflow [Build & Release APK](.github/workflows/release.yml), sobald ein Tag `v*` gepusht wird, und landen unter [Releases](https://github.com/BrickyStudios/FitShare/releases).

## So geht's

1. In Gadgetbridge: **Aktivität → Export → JSON**.
2. FitShare öffnen und die JSON-Datei wählen.
3. Optional GPX-Route und Hintergrundbild hinzufügen.
4. **Teilen →** tippen und Instagram auswählen.

## Selbst bauen

```bash
./gradlew :app:assembleRelease
```

Kotlin, Jetpack Compose, Gradle (Kotlin DSL), JDK 17. Den Aufbau beschreibt [PLAN.md](PLAN.md).

## Hinweis

FitShare ist ein unabhängiges Projekt und gehört weder zu Gadgetbridge noch zu Instagram oder Meta.
