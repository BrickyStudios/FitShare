#!/usr/bin/env python3
"""Builds the GitHub Pages site into docs/ (served at brickystudios.github.io/FitShare).

One home page per language plus one page per search intent. Run: python3 tools/build_site.py
"""
import html
import json
import re
from pathlib import Path

BASE = "https://brickystudios.github.io/FitShare/"
REPO = "https://github.com/BrickyStudios/FitShare"
APK = REPO + "/releases/latest/download/FitShare.apk"  # direct download, same name in every release
WEBAPP = BASE + "app/"  # static web app in docs/app, not generated here
DOCS = Path(__file__).resolve().parent.parent / "docs"
IMG = BASE + "bilder/social-preview.png"


def page(slug, lang, title, desc, body, schema, alternates=None):
    """slug '' = home. Relative paths keep the site working under any sub-path."""
    root = "../" * slug.count("/") if slug else ""
    url = BASE + slug
    alt = "".join(f'<link rel="alternate" hreflang="{l}" href="{BASE + s}">' for l, s in (alternates or {}).items())
    nav_de = lang == "de"
    nav = (
        f'<a href="{root}app/">Web-App</a><a href="{root}de/fitness-app-instagram-story/">Apps</a><a href="{REPO}">GitHub</a>' if nav_de else
        f'<a href="{root}app/">Web app</a><a href="{root}fitness-app-instagram-sticker/">Apps</a><a href="{root}de/">Deutsch</a><a href="{REPO}">GitHub</a>'
    )
    foot = (
        "FitShare ist freie Software unter GPL-3.0. Kein Projekt von Gadgetbridge, Xiaomi, Garmin, Strava, Instagram oder Meta."
        if nav_de else
        "FitShare is free software under GPL-3.0. Not affiliated with Gadgetbridge, Xiaomi, Garmin, Strava, Instagram or Meta."
    )
    out = f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(desc)}">
<link rel="canonical" href="{url}">{alt}
<meta property="og:type" content="website">
<meta property="og:url" content="{url}">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(desc)}">
<meta property="og:image" content="{IMG}">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0C0D0B">
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect width='32' height='32' rx='7' fill='%230C0D0B'/%3E%3Cpath d='M10 7h13v4h-8.5v4H21v4h-6.5v6H10z' fill='%23C6F432'/%3E%3C/svg%3E">
<link rel="preload" href="{root}fonts/bigshoulders.ttf" as="font" type="font/ttf" crossorigin>
<link rel="stylesheet" href="{root}style.css">
<script type="application/ld+json">{json.dumps(schema, ensure_ascii=False)}</script>
</head>
<body>
<header class="top"><a class="brand" href="{root or './'}">FITSHARE</a><nav>{nav}</nav></header>
<main>
{body}
</main>
<footer><p>{foot}</p><p><a href="{REPO}">Source code</a> · <a href="{REPO}/blob/main/LICENSE">GPL-3.0</a> · <a href="{root}sitemap.xml">Sitemap</a></p></footer>
</body>
</html>
"""
    target = DOCS / slug / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(out)
    return url


def app_schema(lang, desc):
    return {
        "@context": "https://schema.org", "@type": "SoftwareApplication",
        "name": "FitShare", "operatingSystem": "Android 8.0+", "applicationCategory": "HealthApplication",
        "description": desc, "inLanguage": lang, "license": "https://www.gnu.org/licenses/gpl-3.0.html",
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "EUR"},
        "downloadUrl": APK, "image": IMG, "url": BASE,
    }


def howto_schema(name, steps):
    return {
        "@context": "https://schema.org", "@type": "HowTo", "name": name,
        "step": [{"@type": "HowToStep", "position": i + 1, "text": re.sub(r"<[^>]+>", "", s)} for i, s in enumerate(steps)],
    }


def faq(items, lang="en"):
    """Visible FAQ + matching FAQPage schema."""
    blocks = "".join(f"<details><summary>{html.escape(q)}</summary><p>{a}</p></details>" for q, a in items)
    title = "Häufige Fragen" if lang == "de" else "Questions"
    schema = {
        "@context": "https://schema.org", "@type": "FAQPage",
        "mainEntity": [{"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in items],
    }
    return f'<section class="faq"><h2>{title}</h2>{blocks}</section>', schema


def steps_html(steps):
    return '<ol class="steps">' + "".join(f"<li><span>{s}</span></li>" for s in steps) + "</ol>"


def cta(lang="en"):
    if lang == "de":
        return (f'<p class="cta"><a class="btn" href="{APK}">APK herunterladen</a>'
                f'<a class="btn ghost" href="{WEBAPP}">Im Browser öffnen</a></p>'
                '<p class="small">Die APK braucht Android 8.0 oder neuer, ohne Konto und ohne Internetzugriff. '
                'Die Web-App läuft auf iPhone, Android und am Rechner, ohne Installation; Bilder und Dateien verlassen das Gerät nicht.</p>')
    return (f'<p class="cta"><a class="btn" href="{APK}">Download the APK</a>'
            f'<a class="btn ghost" href="{WEBAPP}">Open in the browser</a></p>'
            '<p class="small">The APK needs Android 8.0 or newer, no account, no internet permission. '
            'The web app runs on iPhone, Android and desktop with nothing to install; pictures and files never leave the device.</p>')


def gallery(root):
    return f"""<div class="gallery">
<img src="{root}bilder/0-overlay-stack.png" width="540" height="960" loading="lazy" alt="Stack overlay: distance, time and speed above the route on a sunset photo">
<img src="{root}bilder/0-overlay-card.png" width="540" height="960" loading="lazy" alt="Card overlay: translucent card with route, four stats and an elevation profile">
<img src="{root}bilder/1-night.png" width="540" height="960" loading="lazy" alt="Night story: glowing route, distance, heart rate zones and a stats table">
</div>"""


# ------------------------------------------------------------------ shared

# Share button = what the app sends when you tap Share on a workout. Map = where a route file comes from.
# Menu names change between app versions, so the wording stays close to what the vendors document.
APPS_EN = [
    ("Mi Fitness", "Xiaomi Smart Band, Redmi Watch", "picture", "no file export in the app; record the route with OpenTracks"),
    ("Zepp / Zepp Life", "Amazfit", "picture", "GPX export in the workout (watches with GPS)"),
    ("Garmin Connect", "Forerunner, Venu, Fenix, Edge …", "picture", "GPX or TCX from the activity on connect.garmin.com"),
    ("Huawei Health", "Huawei Watch, Band", "picture", "route icon → Export route (GPX or TCX)"),
    ("Samsung Health", "Galaxy Watch", "picture", "Export as GPX file, at the bottom of the workout"),
    ("Strava", "any", "picture", "Export GPX on the activity at strava.com"),
    ("Polar Flow", "Polar", "picture", "GPX, TCX or FIT from Polar Flow on the web"),
    ("COROS", "COROS", "picture", "Export Data in the workout"),
    ("Gadgetbridge", "Mi Band, Amazfit, Garmin … without the vendor app", "FIT or JSON file", "in the FIT if the watch has GPS, otherwise OpenTracks"),
]
APPS_DE = [
    ("Mi Fitness", "Xiaomi Smart Band, Redmi Watch", "Bild", "kein Datei-Export in der App; Route mit OpenTracks aufzeichnen"),
    ("Zepp / Zepp Life", "Amazfit", "Bild", "GPX-Export im Training (Uhren mit GPS)"),
    ("Garmin Connect", "Forerunner, Venu, Fenix, Edge …", "Bild", "GPX oder TCX aus der Aktivität auf connect.garmin.com"),
    ("Huawei Health", "Huawei Watch, Band", "Bild", "Routen-Symbol → Route exportieren (GPX oder TCX)"),
    ("Samsung Health", "Galaxy Watch", "Bild", "„Als GPX-Datei exportieren“ unten im Training"),
    ("Strava", "alle", "Bild", "GPX exportieren in der Aktivität auf strava.com"),
    ("Polar Flow", "Polar", "Bild", "GPX, TCX oder FIT aus Polar Flow im Browser"),
    ("COROS", "COROS", "Bild", "„Daten exportieren“ im Training"),
    ("Gadgetbridge", "Mi Band, Amazfit, Garmin … ohne Hersteller-App", "FIT- oder JSON-Datei", "in der FIT, wenn die Uhr GPS hat, sonst OpenTracks"),
]


def apps_table(rows, lang="en"):
    head = ("App", "Uhren", "Teilen schickt", "Datei für die Karte") if lang == "de" else ("App", "Watches", "Share sends", "File for the map")
    body = "".join("<tr>" + "".join(f"<td>{html.escape(c)}</td>" for c in r) + "</tr>" for r in rows)
    return '<div class="table"><table><thead><tr>' + "".join(f"<th>{h}</th>" for h in head) + f"</tr></thead><tbody>{body}</tbody></table></div>"


APP_STEPS = [
    "In your fitness app, open the workout and tap <b>Share</b>.",
    "Pick <b>FitShare</b>. It reads distance, time, heart rate, calories and elevation off the picture.",
    "For the map, export the workout as GPX or TCX and share that file to FitShare too.",
    "Choose a layout, the stats and a colour, then tap <b>Save PNG</b>.",
    "In Instagram, open a story, tap Stickers → Photo and pick the PNG.",
]
APP_STEPS_DE = [
    "In deiner Fitness-App das Training öffnen und auf <b>Teilen</b> tippen.",
    "<b>FitShare</b> wählen. Distanz, Zeit, Puls, Kalorien und Höhenmeter liest FitShare vom Bild.",
    "Für die Karte das Training als GPX oder TCX exportieren und die Datei ebenfalls an FitShare teilen.",
    "Layout, Werte und Farbe wählen, dann <b>PNG speichern</b>.",
    "In Instagram eine Story öffnen, Sticker → Foto und das PNG wählen.",
]

# ------------------------------------------------------------------ English home

HOME_DESC = ("Turn workouts from Mi Fitness, Garmin Connect, Gadgetbridge and other fitness apps into Instagram story "
             "stickers with distance, time, route and heart rate. Free Android app, offline.")
GB_STEPS = [
    "In Gadgetbridge, open the workout and tap ⋮ → <b>Share FIT File</b>.",
    "Pick <b>FitShare</b> in the share sheet.",
    "No GPS in the file? Share the route from OpenTracks (KMZ or GPX) to FitShare too.",
    "Choose a layout, the stats and a colour, then tap <b>Save PNG</b>.",
    "In Instagram, open a story, tap Stickers → Photo and pick the PNG.",
]
home_faq, home_faq_schema = faq([
    ("Is FitShare free?", "Yes. It is open source under GPL-3.0, with no ads and no in-app purchases."),
    ("Which apps work?", "Any app whose Share button sends a picture of the workout: Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava, Polar Flow, COROS and others. Gadgetbridge exports and GPX or TCX files work too. FitShare reads Latin script, so the app can be set to English, German, French, Spanish and similar languages."),
    ("Do I get the map from the share picture?", "No, only the numbers. For the route, the elevation profile and the km splits, also share the workout's GPX or TCX file. <a href=\"fitness-app-instagram-sticker/\">Where each app hides its export</a>."),
    ("Do I need a subscription?", "No. FitShare is free, and so is every layout and graph in it. You don't need Strava, Garmin Connect+ or any other paid plan."),
    ("Is there an iPhone version?", "Yes, as a web app. Open <a href=\"app/\">brickystudios.github.io/FitShare/app</a> in Safari, pick the saved share picture or a GPX file, and save the sticker. Tap Share → Add to Home Screen to keep it as an icon. It isn't in the App Store."),
    ("Is it on Google Play?", "Not yet. Install the APK from GitHub Releases, or add the repository to Obtainium for automatic updates."),
    ("Does my data leave the phone?", "No. Text recognition runs on the phone, and the app has no internet permission, so it cannot upload anything."),
])
page("", "en", "FitShare: Instagram story stickers from Mi Fitness, Garmin Connect and Gadgetbridge", HOME_DESC, f"""
<section class="hero">
<p class="kicker">FREE ANDROID APP · OPEN SOURCE</p>
<h1>Your workout.<br><span>As a sticker.</span></h1>
<p class="lead">Tap Share in Mi Fitness, Garmin Connect, Zepp or Huawei Health, pick FitShare, and you get a transparent PNG for your Instagram story with distance, time and heart rate. Add the GPX and the route goes on it too. Like Strava's stats sticker, without Strava, an account or a subscription.</p>
{cta()}
</section>
{gallery("")}
<section>
<h2>From fitness app to story in five taps</h2>
{steps_html(APP_STEPS)}
<p><a href="fitness-app-instagram-sticker/">Which apps work, and where they hide the GPX →</a></p>
</section>
<section class="grid">
<div><h3>Works with the app you already use</h3><p>Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava: their share picture is enough. Also Gadgetbridge FIT and JSON, and routes as GPX, TCX, KML or KMZ.</p></div>
<div><h3>Four overlay layouts</h3><p>Stack, Row, Card and Map. Pick up to four stats in your order, white, black or the sport's colour. Or a finished design, Night or Race bib, as a 9:16 story or 4:5 post.</p></div>
<div><h3>Elevation, heart rate, splits</h3><p>Elevation profile, heart rate graph, km splits with your fastest kilometre, heart rate zones.</p></div>
<div><h3>Your look, one tap away</h3><p>Save a setup as a named look. The last eight exports stay in a history, so the next workout looks like the last one.</p></div>
<div><h3>No subscription</h3><p>Elevation profile, splits and heart rate zones are free. Nothing in FitShare sits behind a paid plan.</p></div>
<div><h3>Stays on your phone</h3><p>Text recognition runs on the phone. No account, no analytics, no internet permission.</p></div>
</section>
<section>
<h2>Guides</h2>
<ul class="links">
<li><a href="fitness-app-instagram-sticker/">Every fitness app: share button and GPX export at a glance</a></li>
<li><a href="garmin-connect-instagram-story/">Garmin Connect workouts as an Instagram story sticker</a></li>
<li><a href="mi-band-instagram-story/">Mi Fitness, Zepp and Mi Band workouts in your story</a></li>
<li><a href="gadgetbridge-instagram-story/">How to share a Gadgetbridge workout to Instagram</a></li>
<li><a href="strava-sticker-alternative/">A Strava stats sticker without Strava</a></li>
<li><a href="de/">Auf Deutsch: Gadgetbridge-Training als Instagram-Story</a></li>
</ul>
</section>
{home_faq}
""", [app_schema("en", HOME_DESC), home_faq_schema], {"en": "", "de": "de/", "x-default": ""})

# ------------------------------------------------------------------ intent pages

gb_faq, gb_faq_schema = faq([
    ("Where is the share button in Gadgetbridge?", "Open the workout from the activity list, then tap the three-dot menu in the top right. <b>Share FIT File</b> sends the whole workout; <b>Share JSON Details</b> works too but has no sport and no heart rate curve."),
    ("FitShare doesn't show up in the share sheet", "Update FitShare to 1.5.0 or newer. Older versions only accepted JSON, and Gadgetbridge shares FIT files as a generic binary file."),
    ("My sticker has no map", "Watches without GPS, like most Mi Bands, only record heart rate. Record the route on the phone with OpenTracks and share the KMZ or GPX to FitShare as well; it joins the open workout."),
])
page("gadgetbridge-instagram-story/", "en", "How to share a Gadgetbridge workout to Instagram stories",
     "Gadgetbridge has no Instagram button. Here is how to turn a workout into a story sticker with route, distance and heart rate, step by step.", f"""
<article>
<p class="kicker">GUIDE</p>
<h1>How to share a Gadgetbridge workout to your Instagram story</h1>
<p class="lead">Gadgetbridge keeps your workouts on your phone and has no Instagram button. Share the workout's FIT file to FitShare, and you get a transparent sticker with your stats and route to place on any story photo.</p>
{steps_html(GB_STEPS)}
<img class="shot" src="../bilder/app-editor.png" width="540" height="1100" loading="lazy" alt="FitShare editor with the overlay preview and the tabs for looks, layout, stats, colour, extras and sport">
<h2>Tips</h2>
<ul>
<li>Save your favourite setup under <b>Looks</b>. Next time you share a workout, one tap gives it the same design.</li>
<li>Turn on <b>Km splits</b> for rides and runs with GPS: one bar per kilometre, fastest marked.</li>
<li>Posting to the feed instead of a story? Use the Night or Race bib design in 4:5.</li>
</ul>
{cta()}
{gb_faq}
</article>
""", [howto_schema("Share a Gadgetbridge workout to Instagram stories", [s.replace("<b>", "").replace("</b>", "") for s in GB_STEPS]), gb_faq_schema])

strava_faq, strava_faq_schema = faq([
    ("Does FitShare look exactly like Strava's sticker?", "No, it has its own designs. The idea is the same: stats and route on a transparent background you place on top of your photo."),
    ("Can I import a Strava activity?", "Yes. Tap Share on the activity in the Strava app and pick FitShare; it reads the numbers off the picture. For the route, export the GPX on strava.com and share that too."),
    ("Why not just use Strava?", "Some people don't want their workouts on a server. FitShare works fully offline with files you already have."),
])
page("strava-sticker-alternative/", "en", "Strava stats sticker alternative without Strava: FitShare",
     "Get a Strava-style stats sticker for Instagram stories without a Strava account. FitShare draws distance, time, pace and route as a transparent PNG, offline.", f"""
<article>
<p class="kicker">ALTERNATIVE</p>
<h1>A Strava-style stats sticker, without Strava</h1>
<p class="lead">Strava's stats sticker puts your distance, time and route on an Instagram story. It needs a Strava account and your workout on Strava's servers. FitShare draws the same kind of sticker on your phone from a FIT, GPX or KMZ file.</p>
{gallery("../")}
<h2>What you get</h2>
<ul>
<li>A transparent PNG, cropped to its content, so Instagram treats it as a movable photo sticker.</li>
<li>Distance, time, speed or pace, elevation gain, heart rate, calories, steps, laps: pick up to four.</li>
<li>Route, elevation profile, heart rate graph and km splits as extras.</li>
<li>No account and no upload. The app has no internet permission.</li>
</ul>
<h2>Where the data comes from</h2>
<p>From <a href="https://gadgetbridge.org/">Gadgetbridge</a> (FIT or JSON), from OpenTracks (KMZ or GPX), or from any GPX file, including a GPX exported from Strava or Komoot.</p>
{cta()}
{strava_faq}
<p class="small">Strava is a trademark of Strava, Inc. FitShare is an independent project.</p>
</article>
""", [app_schema("en", "Strava-style Instagram stats sticker without a Strava account."), strava_faq_schema])

band_faq, band_faq_schema = faq([
    ("Does it work with the Mi Fitness app?", "Yes. Open the workout in Mi Fitness, tap Share and pick FitShare. Mi Fitness only shares a picture, so you get the numbers but no map."),
    ("Does it work without Mi Fitness or Zepp?", "Yes, with Gadgetbridge. It syncs the band without the vendor's app and cloud, and FitShare reads its FIT export."),
    ("Why is the distance missing?", "Bands without GPS often don't store distance. FitShare takes distance, time and elevation from a route recorded on the phone, for example with OpenTracks."),
    ("Does it show heart rate zones?", "Yes, when the band records them. FIT exports from Gadgetbridge include the time in each zone and the heart rate over the workout."),
])
page("mi-band-instagram-story/", "en", "Mi Band and Amazfit workouts as Instagram stories (Mi Fitness, Zepp, Gadgetbridge)",
     "Share Xiaomi Smart Band or Amazfit workouts from Mi Fitness, Zepp or Gadgetbridge as Instagram story stickers with time, heart rate and route. Free, offline Android app.", f"""
<article>
<p class="kicker">XIAOMI · AMAZFIT</p>
<h1>Mi Band and Amazfit workouts in your Instagram story</h1>
<p class="lead">Mi Fitness and Zepp share a workout as a finished picture with their own layout. FitShare turns it into a sticker you style yourself. If you sync with Gadgetbridge instead, there is no share button at all; FitShare reads the FIT file.</p>
<h2>From Mi Fitness or Zepp</h2>
<p>Open the workout, tap <b>Share</b> and pick FitShare. It reads distance, time, average and max heart rate and calories off the picture. Zepp can also export a GPX for watches with GPS; share it afterwards and the route joins the sticker.</p>
<h2>From Gadgetbridge</h2>
<h2>Bands without GPS</h2>
<p>Most Mi Bands measure heart rate but record no route. Two ways to still get a map on the sticker:</p>
<ul>
<li>Record the ride or run with OpenTracks on the phone and share the KMZ to FitShare after the FIT.</li>
<li>Or skip the map: the <b>heart rate graph</b> and <b>heart rate zones</b> make a good sticker on their own.</li>
</ul>
{steps_html(GB_STEPS)}
{cta()}
{band_faq}
</article>
""", [howto_schema("Share a Mi Band workout to Instagram stories", [s.replace("<b>", "").replace("</b>", "") for s in GB_STEPS]), band_faq_schema])

apps_faq, apps_faq_schema = faq([
    ("My app isn't in the list", "Try it. If the app's Share button sends a picture, FitShare shows up in the share sheet and reads what it can. Values it can't read stay off the sticker."),
    ("FitShare read a wrong number", "Text recognition works best on the app's own share picture, not on a cropped screenshot. Switch the stat off under Stats, or share the GPX, TCX or FIT file instead; files carry the exact values."),
    ("Why is there no map?", "A share picture only gives FitShare numbers. The route, elevation profile and km splits need a GPX, TCX, KML, KMZ or FIT file. The table above shows where each app exports one."),
])
page("fitness-app-instagram-sticker/", "en", "Share any fitness app workout as an Instagram story sticker",
     "Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava: tap Share, pick FitShare, get a transparent story sticker. Plus where each app exports GPX.", f"""
<article>
<p class="kicker">ALL APPS</p>
<h1>Any fitness app's workout as an Instagram story sticker</h1>
<p class="lead">Fitness apps share a workout as a picture in their own design, and Strava and Garmin put some charts behind a subscription. FitShare takes that picture, reads the numbers and lets you build your own transparent sticker. Free, on your phone.</p>
{steps_html(APP_STEPS)}
<h2>Which app sends what</h2>
{apps_table(APPS_EN)}
<p class="small">Menu names change between app versions. If FitShare is missing from the share sheet, update it to 1.7.0 or newer.</p>
<h2>On an iPhone or without the APK</h2>
<p>Tap Share in the fitness app and save the picture, then open it in the <a href="../app/">FitShare web app</a>. The same works with GPX, TCX, FIT, KML and KMZ files. Text recognition runs in the browser, nothing is uploaded.</p>
{cta()}
{apps_faq}
<p class="small">Mi Fitness, Zepp, Garmin Connect, Huawei Health, Samsung Health, Strava, Polar Flow and COROS are trademarks of their owners. FitShare is an independent project.</p>
</article>
""", [howto_schema("Share a fitness app workout as an Instagram story sticker", [s.replace("<b>", "").replace("</b>", "") for s in APP_STEPS]), apps_faq_schema],
     {"en": "fitness-app-instagram-sticker/", "de": "de/fitness-app-instagram-story/"})

GARMIN_STEPS = [
    "In Garmin Connect, open the activity and tap the <b>share</b> icon. If Garmin offers several options, pick the one that shares a picture. On an iPhone, save the picture and open it in the <a href=\"../app/\">web app</a>.",
    "Pick <b>FitShare</b>. It reads distance, time, pace or speed, heart rate, elevation and calories off the picture.",
    "For the route, open the same activity on connect.garmin.com, click the gear icon and export it as GPX. Share the file to FitShare on the phone.",
    "Choose a layout, the stats and a colour, then tap <b>Save PNG</b>.",
    "In Instagram, open a story, tap Stickers → Photo and pick the PNG.",
]
garmin_faq, garmin_faq_schema = faq([
    ("Do I need Garmin Connect+?", "No. FitShare works with the free Garmin Connect app. It only needs the picture the Share button already makes."),
    ("Why does the sticker lack a value I see in Garmin Connect?", "FitShare reads what is on the share picture. If the picture shows four stats, you get those four. The GPX adds route, distance, time and elevation."),
    ("Does it work with a Garmin synced by Gadgetbridge?", "Yes. Share the FIT file from Gadgetbridge instead of a picture."),
])
page("garmin-connect-instagram-story/", "en", "Garmin Connect workout as an Instagram story sticker: FitShare",
     "Share a Garmin Connect run or ride as a transparent Instagram story sticker with distance, pace, heart rate and route. Free Android app, no Connect+ needed.", f"""
<article>
<p class="kicker">GARMIN CONNECT</p>
<h1>Your Garmin Connect workout as an Instagram story sticker</h1>
<p class="lead">Garmin Connect's share picture has Garmin's layout, and you can't move its parts around. FitShare reads the numbers from it and draws a transparent sticker in your style. You don't need a Garmin Connect+ subscription.</p>
{steps_html(GARMIN_STEPS)}
{gallery("../")}
{cta()}
{garmin_faq}
<p class="small">Garmin and Garmin Connect are trademarks of Garmin Ltd. FitShare is an independent project.</p>
</article>
""", [howto_schema("Share a Garmin Connect workout to Instagram stories", [s.replace("<b>", "").replace("</b>", "") for s in GARMIN_STEPS]), garmin_faq_schema])

# ------------------------------------------------------------------ German home

DE_DESC = ("Trainings aus Mi Fitness, Garmin Connect, Gadgetbridge und anderen Fitness-Apps als Instagram-Story-Sticker "
           "mit Distanz, Zeit, Route und Puls. Kostenlose Android-App, offline.")
DE_STEPS = [
    "In Gadgetbridge das Training öffnen, ⋮ → <b>Fit-Datei teilen</b>.",
    "Im Teilen-Menü <b>FitShare</b> wählen.",
    "Kein GPS in der Datei? Die Route aus OpenTracks (KMZ oder GPX) ebenfalls an FitShare teilen.",
    "Layout, Werte und Farbe wählen, dann <b>PNG speichern</b>.",
    "In Instagram eine Story öffnen, Sticker → Foto und das PNG wählen.",
]
de_faq, de_faq_schema = faq([
    ("Kostet FitShare etwas?", "Nein. Die App ist Open Source unter GPL-3.0, ohne Werbung und ohne In-App-Käufe."),
    ("Welche Apps gehen?", "Jede App, deren Teilen-Knopf ein Bild vom Training schickt: Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava, Polar Flow, COROS und andere. Dazu Gadgetbridge-Exporte und GPX- oder TCX-Dateien."),
    ("Kommt die Karte mit dem Bild?", "Nein, nur die Zahlen. Für Route, Höhenprofil und Km-Splits zusätzlich die GPX- oder TCX-Datei teilen. <a href=\"fitness-app-instagram-story/\">Wo jede App ihren Export versteckt</a>."),
    ("Brauche ich ein Abo?", "Nein. FitShare kostet nichts, alle Layouts und Kurven inklusive. Strava-Abo oder Garmin Connect+ brauchst du nicht."),
    ("Gibt es FitShare für das iPhone?", "Ja, als Web-App. <a href=\"../app/\">brickystudios.github.io/FitShare/app</a> in Safari öffnen, das gesicherte Teilen-Bild oder eine GPX-Datei wählen und den Sticker sichern. Über Teilen → Zum Home-Bildschirm liegt sie als Icon bereit. Im App Store ist sie nicht."),
    ("Verlassen meine Daten das Handy?", "Nein. Die Texterkennung läuft auf dem Handy, und die App hat keine Internet-Berechtigung."),
], "de")
page("de/", "de", "FitShare: Training aus Mi Fitness, Garmin Connect & Co. als Instagram-Story-Sticker", DE_DESC, f"""
<section class="hero">
<p class="kicker">KOSTENLOSE ANDROID-APP · OPEN SOURCE</p>
<h1>Dein Training.<br><span>Als Sticker.</span></h1>
<p class="lead">In Mi Fitness, Garmin Connect, Zepp oder Huawei Health auf Teilen tippen, FitShare wählen, und du hast ein transparentes PNG für deine Instagram-Story mit Distanz, Zeit und Puls. Mit der GPX kommt die Route dazu. Wie der Statistik-Sticker von Strava, nur ohne Strava, Konto oder Abo.</p>
{cta("de")}
</section>
{gallery("../")}
<section>
<h2>So kommt das Training in die Story</h2>
{steps_html(APP_STEPS_DE)}
<p><a href="fitness-app-instagram-story/">Welche App was schickt und wo die GPX steckt →</a></p>
</section>
<section>
<h2>Mit Gadgetbridge</h2>
{steps_html(DE_STEPS)}
</section>
<section class="grid">
<div><h3>Geht mit deiner App</h3><p>Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava: Das Teilen-Bild reicht. Dazu Gadgetbridge-FIT und -JSON, Routen als GPX, TCX, KML oder KMZ.</p></div>
<div><h3>Vier Overlay-Layouts</h3><p>Stapel, Reihe, Karte und Route. Bis zu vier Werte in deiner Reihenfolge, in Weiß, Schwarz oder Sportfarbe.</p></div>
<div><h3>Kurven und Splits</h3><p>Höhenprofil, Pulskurve, Km-Splits mit dem schnellsten Kilometer, Pulszonen.</p></div>
<div><h3>Eigene Looks</h3><p>Einstellungen unter einem Namen speichern. Die letzten acht Exporte bleiben im Verlauf, ein Tippen überträgt sie aufs nächste Training.</p></div>
<div><h3>Kein Abo</h3><p>Höhenprofil, Splits und Pulszonen kosten nichts. In FitShare liegt nichts hinter einer Bezahlschranke.</p></div>
<div><h3>Bleibt auf dem Handy</h3><p>Die Texterkennung läuft auf dem Handy. Kein Konto, keine Analyse, keine Internet-Berechtigung.</p></div>
</section>
{de_faq}
""", [app_schema("de", DE_DESC), de_faq_schema, howto_schema("Gadgetbridge-Training in die Instagram-Story", [s.replace("<b>", "").replace("</b>", "") for s in DE_STEPS])],
     {"en": "", "de": "de/", "x-default": ""})

de_apps_faq, de_apps_faq_schema = faq([
    ("Meine App steht nicht in der Liste", "Probier es aus. Schickt der Teilen-Knopf ein Bild, taucht FitShare im Teilen-Menü auf und liest, was es lesen kann."),
    ("FitShare hat eine Zahl falsch gelesen", "Am besten klappt es mit dem Teilen-Bild der App, nicht mit einem zugeschnittenen Screenshot. Den Wert unter Werte abwählen oder die GPX-, TCX- oder FIT-Datei nehmen; Dateien enthalten die genauen Werte."),
    ("Warum fehlt die Karte?", "Aus einem Bild bekommt FitShare nur Zahlen. Route, Höhenprofil und Km-Splits brauchen eine GPX-, TCX-, KML-, KMZ- oder FIT-Datei. Die Tabelle zeigt, wo jede App eine exportiert."),
], "de")
page("de/fitness-app-instagram-story/", "de", "Training aus jeder Fitness-App als Instagram-Story-Sticker",
     "Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava: auf Teilen tippen, FitShare wählen, transparenten Story-Sticker bekommen. Dazu: wo jede App GPX exportiert.", f"""
<article>
<p class="kicker">ALLE APPS</p>
<h1>Training aus jeder Fitness-App als Instagram-Story-Sticker</h1>
<p class="lead">Fitness-Apps teilen ein Training als fertiges Bild im eigenen Design, und bei Strava und Garmin gibt es manche Kurven nur im Abo. FitShare liest die Zahlen aus diesem Bild, und du baust daraus deinen eigenen transparenten Sticker. Kostenlos, auf dem Handy.</p>
{steps_html(APP_STEPS_DE)}
<h2>Welche App was schickt</h2>
{apps_table(APPS_DE, "de")}
<p class="small">Menünamen ändern sich mit App-Updates. Fehlt FitShare im Teilen-Menü, auf Version 1.7.0 oder neuer aktualisieren.</p>
<h2>Auf dem iPhone oder ohne APK</h2>
<p>In der Fitness-App auf Teilen tippen und das Bild sichern, dann in der <a href="../../app/">FitShare-Web-App</a> öffnen. Mit GPX-, TCX-, FIT-, KML- und KMZ-Dateien geht das genauso. Die Texterkennung läuft im Browser, hochgeladen wird nichts.</p>
{cta("de")}
{de_apps_faq}
<p class="small">Mi Fitness, Zepp, Garmin Connect, Huawei Health, Samsung Health, Strava, Polar Flow und COROS sind Marken ihrer Inhaber. FitShare ist ein unabhängiges Projekt.</p>
</article>
""", [howto_schema("Fitness-App-Training als Instagram-Story-Sticker teilen", [s.replace("<b>", "").replace("</b>", "") for s in APP_STEPS_DE]), de_apps_faq_schema],
     {"en": "fitness-app-instagram-sticker/", "de": "de/fitness-app-instagram-story/"})

# ------------------------------------------------------------------ crawl files

slugs = ["", "app/", "fitness-app-instagram-sticker/", "garmin-connect-instagram-story/", "gadgetbridge-instagram-story/",
         "strava-sticker-alternative/", "mi-band-instagram-story/", "de/", "de/fitness-app-instagram-story/"]
(DOCS / "sitemap.xml").write_text(
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    + "".join(f"  <url><loc>{BASE + s}</loc></url>\n" for s in slugs) + "</urlset>\n")
# no robots.txt: crawlers only read it at the domain root, submit the sitemap in Search Console instead
(DOCS / ".nojekyll").write_text("")  # serve files as they are; the markdown plans in docs/ stay unrendered
print("built", len(slugs), "pages")
