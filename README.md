<p align="center"><img src="docs/bilder/social-preview.png" width="860" alt="FitShare: your workout as an Instagram story sticker. Three example stories with route, distance, time and speed"></p>

<h1 align="center">FitShare: Instagram story stickers from Mi Fitness, Garmin Connect, Gadgetbridge and other fitness apps</h1>

<p align="center"><strong>Android app that turns a workout into a transparent PNG for your Instagram story, similar to Strava's stats sticker. Tap Share in Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health or Strava, or hand it a Gadgetbridge FIT file. Works offline, no account, no subscription.</strong></p>

<p align="center">
  <a href="https://kotlinlang.org/"><img alt="Made with Kotlin" src="https://img.shields.io/badge/made_with-Kotlin-0C0D0B?style=for-the-badge&logo=kotlin&logoColor=C6F432"></a>
  <br>
  <a href="#install"><img alt="Android 8.0 or newer" src="https://img.shields.io/badge/Android-8.0%2B-C6F432?logo=android&logoColor=C6F432&labelColor=0C0D0B"></a>
  <a href="https://developer.android.com/compose"><img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack_Compose-C6F432?logo=jetpackcompose&logoColor=C6F432&labelColor=0C0D0B"></a>
  <a href="https://gadgetbridge.org/"><img alt="Reads Gadgetbridge FIT and JSON exports" src="https://img.shields.io/badge/Gadgetbridge-FIT_%26_JSON-C6F432?labelColor=0C0D0B"></a>
  <a href="#which-apps-work"><img alt="Share target for Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health and Strava" src="https://img.shields.io/badge/Share_from-Mi_Fitness_·_Garmin_·_Zepp_·_Huawei-C6F432?labelColor=0C0D0B"></a>
  <a href="#what-it-does"><img alt="Routes from GPX, TCX, KML and KMZ" src="https://img.shields.io/badge/Routes-GPX_·_TCX_·_KML_·_KMZ-C6F432?labelColor=0C0D0B"></a>
  <a href="#what-it-does"><img alt="English and German" src="https://img.shields.io/badge/Languages-EN_·_DE-C6F432?labelColor=0C0D0B"></a>
  <a href="#what-it-does"><img alt="No tracking" src="https://img.shields.io/badge/Tracking-none-C6F432?labelColor=0C0D0B"></a>
  <a href="LICENSE"><img alt="License GPL-3.0" src="https://img.shields.io/github/license/BrickyStudios/FitShare?color=C6F432&labelColor=0C0D0B"></a>
  <a href="https://github.com/BrickyStudios/FitShare/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/BrickyStudios/FitShare?label=release&color=C6F432&labelColor=0C0D0B"></a>
  <a href="https://github.com/BrickyStudios/FitShare/commits/main"><img alt="Last commit" src="https://img.shields.io/github/last-commit/BrickyStudios/FitShare?color=C6F432&labelColor=0C0D0B"></a>
</p>

<p align="center">
  <a href="https://github.com/BrickyStudios/FitShare/releases/latest/download/FitShare.apk"><img alt="Download FitShare.apk" src="https://img.shields.io/badge/Download-FitShare.apk-C6F432?style=for-the-badge&logo=android&logoColor=0C0D0B&labelColor=C6F432&color=0C0D0B"></a>
</p>

<p align="center">
  <a href="#install">Install</a> ·
  <a href="#how-to-use-it">How to use it</a> ·
  <a href="#which-apps-work">Which apps work</a> ·
  <a href="#build-it-yourself">Build it yourself</a> ·
  <a href="https://github.com/BrickyStudios/FitShare/releases">Releases</a> ·
  <a href="https://brickystudios.github.io/FitShare/">Website</a>
</p>

Fitness apps share a workout as a finished picture in their own design, and Strava and Garmin put some charts behind a subscription. FitShare shows up in their share sheet, reads the numbers off that picture on the phone, and lets you build your own sticker from them. Share the GPX or TCX as well and the route, elevation profile and km splits go on it too.

Gadgetbridge, which syncs Mi Band, Amazfit and other watches without the vendor's cloud, has no share button at all, and a Mi Band 10 export doesn't even contain the distance. FitShare reads its FIT or JSON export and takes distance, elevation and the map from your GPX or KMZ route.

<p align="center">
  <img src="docs/bilder/0-overlay-stack.png" width="200" alt="FitShare stack overlay: distance, time and average speed above the route, white on a transparent background, placed over a sunset photo">
  <img src="docs/bilder/0-overlay-card.png" width="200" alt="FitShare card overlay: translucent rounded card with route, four stats, elevation profile and date">
  <img src="docs/bilder/0-overlay-map.png" width="200" alt="FitShare map overlay: large route in the sport colour with a one-line summary of distance, time and speed">
</p>
<p align="center">
  <img src="docs/bilder/1-night.png" width="200" alt="FitShare full story in the Night style: dark background, glowing route, heart rate zones and a stats table">
  <img src="docs/bilder/2-race-bib.png" width="200" alt="FitShare full story in the Race bib style: paper look with an orange header band, route and stats">
</p>

<p align="center"><sub>Demo data from an invented route. The backgrounds are drawn, in the app you use your own photo.</sub></p>

## What it does

**Overlay (transparent PNG).** Cropped to its content, so Instagram adds it as a sticker you can move and resize.

| Option | Choices |
|---|---|
| Layout | Stack (stats above the route, like Strava), Row, Card (translucent panel), Map (big route, one line of stats) |
| Stats | Any of distance, time, speed or pace, elevation gain, average and max heart rate, calories, steps, laps, SWOLF, strokes, stroke type. Tap order sets the order; "Auto" picks by sport |
| Colour | White, black, or the sport's colour |
| Extras | Route, elevation profile, heart rate graph (from FIT), km splits as bars with the fastest km marked, heart rate zones, date, logo |
| Looks | Save the current setup under a name and put it on the next workout with one tap. Every saved or shared PNG also lands in a history of the last eight setups. Long-press a saved look to delete it |

The preview shows the overlay on your photo if you pick one. The saved PNG stays transparent.

**Full stories.** Two finished designs, Night and Race bib, for when you want the image as the whole story (1080 × 1920) or as a feed post (4:5, 1080 × 1350).

**Data.** Reads the share picture of fitness apps (text recognition on the phone), Gadgetbridge workouts as FIT or JSON, and routes as GPX, TCX, KML or KMZ (OpenTracks KMZ works as is). From a picture FitShare gets distance, time, average and max heart rate, calories, elevation gain, steps and the sport, as far as the picture shows them. A FIT file also contains the sport, the heart rate zones and, if the watch or phone recorded GPS, the route. If the export lacks distance or time, FitShare computes them from the route, plus elevation gain. Without a sport in the file it guesses from the speed; you can change it.

**Everyday details.** Share the workout and the route to FitShare one after the other, or pick both at once. If Android closes the app while you are in Instagram, your workout, photo and settings are still there when you come back. The app follows your phone's language (English or German), or set it per app in Android 13+.

**Privacy.** No account, no network access, no analytics. Text recognition runs on the phone. Files stay on the phone.

## Install

Android 8.0 or newer. [Download FitShare.apk](https://github.com/BrickyStudios/FitShare/releases/latest/download/FitShare.apk) (always the latest version), or add `https://github.com/BrickyStudios/FitShare` to [Obtainium](https://github.com/ImranR98/Obtainium) to get updates.

## How to use it

1. In your fitness app, open the workout, tap **Share** and pick FitShare. From Gadgetbridge: ⋮ → **Share FIT File** (Share JSON Details works too).
2. For the map, share the route as GPX, TCX or KMZ to FitShare as well, or tap **Choose files** and pick workout and route together.
3. Check the sport, then pick layout, stats, colour and extras.
4. Tap **Save PNG**. In Instagram, open a story, tap Stickers → Photo and pick the PNG.

<p align="center">
  <img src="docs/bilder/app-start.png" width="220" alt="FitShare start screen with the button to choose a Gadgetbridge JSON and a route file">
  <img src="docs/bilder/app-editor.png" width="220" alt="FitShare editor: overlay preview on a checkerboard, tabs for layout, stats, colour, extras and sport, Save PNG button">
</p>

## Which apps work

Any app whose Share button sends a picture of the workout. FitShare reads Latin script, so the app can be set to English, German, French, Spanish and similar languages.

| App | Watches | Share sends | File for the map |
|---|---|---|---|
| Mi Fitness | Xiaomi Smart Band, Redmi Watch | picture | no file export in the app; record the route with OpenTracks |
| Zepp / Zepp Life | Amazfit | picture | GPX export in the workout (watches with GPS) |
| Garmin Connect | Forerunner, Venu, Fenix, Edge … | picture | GPX or TCX from the activity on connect.garmin.com |
| Huawei Health | Huawei Watch, Band | picture | route icon → Export route (GPX or TCX) |
| Samsung Health | Galaxy Watch | picture | Export as GPX file, at the bottom of the workout |
| Strava | any | picture | Export GPX on the activity at strava.com |
| Polar Flow | Polar | picture | GPX, TCX or FIT from Polar Flow on the web |
| COROS | COROS | picture | Export Data in the workout |
| Gadgetbridge | Mi Band, Amazfit, Garmin … | FIT or JSON | in the FIT if the watch has GPS, otherwise OpenTracks |

Menu names change between app versions. Numbers read from a picture can be wrong when the picture is cropped or busy; the files carry the exact values. More on the [website](https://brickystudios.github.io/FitShare/fitness-app-instagram-sticker/).

## Build it yourself

```bash
./gradlew testDebugUnitTest assembleRelease
```

Kotlin, Jetpack Compose, JDK 17. One Canvas renderer draws both the preview and the export, so what you save is what you saw. Without the signing secrets, release builds use the debug key. Share pictures are read with Google's [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), bundled in the APK so it runs without Play Services (Huawei and degoogled phones). ML Kit is not open source. The INTERNET permission it asks for is removed in the manifest, so its usage statistics never leave the phone. Fonts: [Big Shoulders Display](https://fonts.google.com/specimen/Big+Shoulders+Display) and [Archivo](https://fonts.google.com/specimen/Archivo), both under the SIL Open Font License (`app/src/main/assets/fonts`).

## License

[GPL-3.0](LICENSE), the same licence Gadgetbridge and OpenTracks use. You can use, change and share FitShare; if you distribute a changed version, publish its source under GPL-3.0 too. The bundled fonts keep their own SIL Open Font License. As an additional permission under GPL-3.0 section 7, you may combine FitShare with Google ML Kit and distribute the result.

FitShare is an independent project and is not affiliated with Gadgetbridge, Xiaomi, Garmin, Zepp, Huawei, Samsung, Strava, Instagram or Meta.
