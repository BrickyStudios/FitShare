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
    # the same page in the other language, from the hreflang alternates; the other home page as fallback
    other = (alternates or {}).get("en" if nav_de else "de", "" if nav_de else "de/")
    nav = (
        f'<a href="{root}app/">Web-App</a><a href="{root}de/fitness-app-instagram-story/">Apps</a><a href="{root}{other}">English</a><a href="{REPO}">GitHub</a>' if nav_de else
        f'<a href="{root}app/">Web app</a><a href="{root}fitness-app-instagram-sticker/">Apps</a><a href="{root}{other}">Deutsch</a><a href="{REPO}">GitHub</a>'
    )
    # A German browser on an English page (and vice versa) gets a one-line pointer to the other version.
    # No automatic redirect: Google crawls from the US and has to see both versions.
    langbar = (
        f'<p class="langbar" id="langbar" hidden><a href="{root}{other}" hreflang="{"en" if nav_de else "de"}">'
        f'{"Read this page in English →" if nav_de else "Diese Seite auf Deutsch lesen →"}</a>'
        f'<button type="button" aria-label="{"Ausblenden" if nav_de else "Hide"}" onclick="this.parentNode.hidden=true;try{{localStorage.setItem(\'fs.langbar\',\'1\')}}catch(e){{}}">✕</button></p>'
        f'<script>try{{if(!localStorage.getItem("fs.langbar")&&(navigator.language||"").toLowerCase().startsWith("de")!=={str(nav_de).lower()})document.getElementById("langbar").hidden=false}}catch(e){{}}</script>'
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
{langbar}
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
                '<p class="small">Die APK braucht Android 8.0 oder neuer und kein Konto. Updates holt sie sich selbst von GitHub. '
                'Die Web-App läuft auf iPhone, Android und am Rechner, ohne Installation; Bilder und Dateien verlassen das Gerät nicht.</p>')
    return (f'<p class="cta"><a class="btn" href="{APK}">Download the APK</a>'
            f'<a class="btn ghost" href="{WEBAPP}">Open in the browser</a></p>'
            '<p class="small">The APK needs Android 8.0 or newer and no account. It fetches its own updates from GitHub. '
            'The web app runs on iPhone, Android and desktop with nothing to install; pictures and files never leave the device.</p>')


def gallery(root):
    return f"""<div class="gallery">
<img src="{root}bilder/photo-map-ride.jpg" width="540" height="960" loading="lazy" alt="Map sticker: large route and a one-line summary over a view across the handlebars onto a country lane">
<img src="{root}bilder/photo-swim-lake.jpg" width="540" height="960" loading="lazy" alt="Swim stats in one row at the bottom of a lake photo at sunrise, darkened from the bottom edge">
<img src="{root}bilder/1-night.png" width="540" height="960" loading="lazy" alt="Night story: glowing route, distance, heart rate zones and a stats table">
</div>"""


# ------------------------------------------------------------------ shared

# Share button = what the app sends when you tap Share on a workout. Map = where a route file comes from.
# Menu names change between app versions, so the wording stays close to what the vendors document.
APPS_EN = [
    ("Mi Fitness", "Xiaomi Smart Band, Redmi Watch", "picture", "workout → Export → GPX, KML or TCX → ⋯ → FitShare; without GPS only TCX (numbers, no map)"),
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
    ("Mi Fitness", "Xiaomi Smart Band, Redmi Watch", "Bild", "Training → Exportieren → GPX, KML oder TCX → ⋯ → FitShare; ohne GPS nur TCX (Werte, keine Karte)"),
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

HOME_DESC = ("Turn a Mi Fitness, Garmin Connect or Strava workout into an Instagram story sticker with distance, time, "
             "route and heart rate. Free, Android app or web app.")
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
    ("Is it on Google Play?", "Not yet. Install the APK from GitHub Releases once. After that, FitShare finds new versions and installs them itself."),
    ("Does my data leave the phone?", "No. Text recognition runs on the phone. The app only goes online to ask GitHub whether there's a new version and sends nothing else."),
])
page("", "en", "FitShare: Instagram story stats sticker for any fitness app", HOME_DESC, f"""
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
<div><h3>iPhone and desktop too</h3><p>The <a href="app/">web app</a> does the same in Safari, Chrome or Firefox, without installing anything. Your files stay on the device.</p></div>
<div><h3>No subscription</h3><p>Elevation profile, splits and heart rate zones are free. Nothing in FitShare sits behind a paid plan.</p></div>
<div><h3>Stays on your phone</h3><p>Text recognition runs on the phone. No account, no analytics, no upload.</p></div>
</section>
<section>
<h2>Guides</h2>
<ul class="links">
<li><a href="fitness-app-instagram-sticker/">Every fitness app: share button and GPX export at a glance</a></li>
<li><a href="iphone-instagram-story-sticker/">Fitness stats sticker on iPhone, without an app</a></li>
<li><a href="garmin-connect-instagram-story/">Garmin Connect workouts as an Instagram story sticker</a></li>
<li><a href="mi-band-instagram-story/">Mi Fitness, Zepp and Mi Band workouts in your story</a></li>
<li><a href="gadgetbridge-instagram-story/">How to share a Gadgetbridge workout to Instagram</a></li>
<li><a href="strava-sticker-alternative/">A Strava stats sticker without Strava</a></li>
<li><a href="de/" hreflang="de">Auf Deutsch: FitShare für jede Fitness-App</a></li>
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
""", [howto_schema("Share a Gadgetbridge workout to Instagram stories", [s.replace("<b>", "").replace("</b>", "") for s in GB_STEPS]), gb_faq_schema],
     {"en": "gadgetbridge-instagram-story/", "de": "de/gadgetbridge-instagram-story/"})

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
<li>No account and no upload. The app only goes online to check for updates.</li>
</ul>
<h2>Where the data comes from</h2>
<p>From the share picture of Strava, Garmin Connect or Mi Fitness, from <a href="https://gadgetbridge.org/">Gadgetbridge</a> (FIT or JSON), from OpenTracks (KMZ or GPX), or from any GPX file, including one exported from Strava or Komoot. On an iPhone, use the <a href="../app/">web app</a>.</p>
{cta()}
{strava_faq}
<p class="small">Strava is a trademark of Strava, Inc. FitShare is an independent project.</p>
</article>
""", [app_schema("en", "Strava-style Instagram stats sticker without a Strava account."), strava_faq_schema],
     {"en": "strava-sticker-alternative/", "de": "de/strava-sticker-alternative/"})

MI_STEPS = [
    "In Mi Fitness, open the workout and tap <b>Share</b> → ⋯ → <b>FitShare</b>. It reads the numbers off the picture.",
    "For the map: open the workout again, tap <b>Export</b>, pick GPX, tap ⋯ and choose <b>FitShare</b>. Without GPS only TCX is offered; it carries the numbers but no route.",
    "Choose a layout, the stats and a colour, then tap <b>Save PNG</b>.",
    "In Instagram, open a story, tap Stickers → Photo and pick the PNG.",
]
band_faq, band_faq_schema = faq([
    ("Does it work with the Mi Fitness app?", "Yes. Open the workout in Mi Fitness, tap Share and pick FitShare. For the map too, use Export in the workout, pick GPX and send it to FitShare via the ⋯ button."),
    ("Does it work without Mi Fitness or Zepp?", "Yes, with Gadgetbridge. It syncs the band without the vendor's app and cloud, and FitShare reads its FIT export."),
    ("Why is the distance missing?", "Bands without GPS often don't store distance. FitShare takes distance, time and elevation from a route recorded on the phone, for example with OpenTracks."),
    ("Does it show heart rate zones?", "Yes, when the band records them. FIT exports from Gadgetbridge include the time in each zone and the heart rate over the workout."),
])
page("mi-band-instagram-story/", "en", "Mi Band and Amazfit workouts as Instagram story stickers",
     "Share Xiaomi Smart Band or Amazfit workouts from Mi Fitness, Zepp or Gadgetbridge as Instagram story stickers with time, heart rate and route.", f"""
<article>
<p class="kicker">XIAOMI · AMAZFIT</p>
<h1>Mi Band and Amazfit workouts in your Instagram story</h1>
<p class="lead">Mi Fitness and Zepp share a workout as a finished picture with their own layout. FitShare turns it into a sticker you style yourself. If you sync with Gadgetbridge instead, there is no share button at all; FitShare reads the FIT file.</p>
<h2>From Mi Fitness or Zepp</h2>
<p>Open the workout, tap <b>Share</b> and pick FitShare. It reads distance, time, average and max heart rate and calories off the picture. Mi Fitness has <b>Export</b> in the workout as well: pick GPX, KML or TCX, tap ⋯ and choose FitShare. With GPS the route comes along; without GPS only TCX is offered, which still carries the numbers. Zepp exports a GPX for watches with GPS.</p>
<p>On an iPhone, Mi Fitness and Zepp work the same way: save the share picture and open it in the <a href="../app/">web app</a>.</p>
{steps_html(MI_STEPS)}
<h2>From Gadgetbridge</h2>
<p>Gadgetbridge syncs the band without Xiaomi's or Zepp's app and has no picture to share. FitShare reads its FIT file instead.</p>
{steps_html(GB_STEPS)}
<h2>Bands without GPS</h2>
<p>Most Mi Bands measure heart rate but record no route. Two ways to still get a map on the sticker:</p>
<ul>
<li>Record the ride or run with OpenTracks on the phone and share the KMZ to FitShare after the FIT.</li>
<li>Or skip the map: the <b>heart rate graph</b> and <b>heart rate zones</b> make a good sticker on their own.</li>
</ul>
{cta()}
{band_faq}
</article>
""", [howto_schema("Share a Mi Fitness workout to Instagram stories", [s.replace("<b>", "").replace("</b>", "") for s in MI_STEPS]), band_faq_schema],
     {"en": "mi-band-instagram-story/", "de": "de/mi-band-instagram-story/"})

apps_faq, apps_faq_schema = faq([
    ("My app isn't in the list", "Try it. If the app's Share button sends a picture, FitShare shows up in the share sheet and reads what it can. Values it can't read stay off the sticker."),
    ("FitShare read a wrong number", "Text recognition works best on the app's own share picture, not on a cropped screenshot. Switch the stat off under Stats, or share the GPX, TCX or FIT file instead; files carry the exact values."),
    ("Why is there no map?", "A share picture only gives FitShare numbers. The route, elevation profile and km splits need a GPX, TCX, KML, KMZ or FIT file. The table above shows where each app exports one."),
])
page("fitness-app-instagram-sticker/", "en", "Share any fitness app workout as an Instagram story sticker",
     "Mi Fitness, Garmin Connect, Zepp, Huawei Health, Strava: tap Share, pick FitShare, get a transparent story sticker. Plus where each app exports GPX.", f"""
<article>
<p class="kicker">ALL APPS</p>
<h1>Any fitness app's workout as an Instagram story sticker</h1>
<p class="lead">Fitness apps share a workout as a picture in their own design, and Strava and Garmin put some charts behind a subscription. FitShare takes that picture, reads the numbers and lets you build your own transparent sticker. Free, on your phone.</p>
{steps_html(APP_STEPS)}
<h2>Which app sends what</h2>
{apps_table(APPS_EN)}
<p class="small">Menu names change between app versions. If FitShare is missing from the share sheet, update it to 1.7.1 or newer.</p>
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
""", [howto_schema("Share a Garmin Connect workout to Instagram stories", [s.replace("<b>", "").replace("</b>", "") for s in GARMIN_STEPS]), garmin_faq_schema],
     {"en": "garmin-connect-instagram-story/", "de": "de/garmin-connect-instagram-story/"})

IOS_APPS_EN = "Garmin Connect, Mi Fitness, Zepp, Huawei Health, Strava, Polar Flow and COROS"
IPHONE_STEPS = [
    "In your fitness app, open the workout, tap <b>Share</b> and choose <b>Save Image</b>.",
    "Open <b>brickystudios.github.io/FitShare/app</b> in Safari and pick the picture from Photos. FitShare reads distance, time, heart rate and calories off it.",
    "For the map, also pick the workout's GPX or TCX from the Files app (Garmin Connect and Strava export it on their websites).",
    "Choose a layout, the stats and a colour, tap <b>Share</b> and pick Instagram, or <b>Save Image</b>.",
    "In Instagram, open a story, tap Stickers → Photo and pick the PNG.",
]
ios_faq, ios_faq_schema = faq([
    ("Is FitShare in the App Store?", "No. The web app runs in Safari and needs no installation. Share → Add to Home Screen gives it an icon like an app."),
    ("Why doesn't FitShare show up in the iPhone share sheet?", "iOS only lists App Store apps there. Saving the picture first takes one extra tap."),
    ("Is my picture uploaded?", "No. Text recognition runs in Safari on your phone. The page loads about 8 MB of recognition data the first time and then works offline."),
    ("Which apps work?", f"Any app that shares a picture of the workout, for example {IOS_APPS_EN}. Gadgetbridge and Samsung Health are Android-only."),
])
page("iphone-instagram-story-sticker/", "en", "Fitness stats sticker for Instagram stories on iPhone",
     "Put Garmin Connect, Mi Fitness or Strava stats on your Instagram story from an iPhone. Free web app in Safari, nothing to install, nothing uploaded.", f"""
<article>
<p class="kicker">IPHONE · WEB APP</p>
<h1>A stats sticker for your Instagram story, on iPhone</h1>
<p class="lead">FitShare runs in Safari. Save the share picture from your fitness app, open it in the web app, and you get a transparent sticker with distance, time and heart rate. You don't need the App Store, an account or a subscription.</p>
<p class="cta"><a class="btn" href="../app/">Open the web app</a></p>
{steps_html(IPHONE_STEPS)}
<h2>Works with</h2>
<p>{IOS_APPS_EN}, and any other app whose Share button sends a picture. GPX, TCX, FIT, KML and KMZ files work too, for the route, elevation profile and km splits.</p>
{gallery("../")}
{ios_faq}
</article>
""", [howto_schema("Make an Instagram story stats sticker on iPhone", [re.sub(r"<[^>]+>", "", x) for x in IPHONE_STEPS]), ios_faq_schema],
     {"en": "iphone-instagram-story-sticker/", "de": "de/iphone-instagram-story/"})

# ------------------------------------------------------------------ German home

DE_DESC = ("Training aus Mi Fitness, Garmin Connect oder Strava als Instagram-Story-Sticker mit Distanz, Zeit, Route "
           "und Puls. Kostenlos, als Android- oder Web-App.")
GB_STEPS_DE = [
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
    ("Verlassen meine Daten das Handy?", "Nein. Die Texterkennung läuft auf dem Handy. Ins Netz geht die App nur, um bei GitHub nach einer neuen Version zu fragen, sonst schickt sie nichts."),
], "de")
page("de/", "de", "FitShare: Instagram-Story-Sticker für jede Fitness-App", DE_DESC, f"""
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
<p><a href="iphone-instagram-story/">Auf dem iPhone: so geht es mit der Web-App →</a></p>
</section>
<section class="grid">
<div><h3>Geht mit deiner App</h3><p>Mi Fitness, Garmin Connect, Zepp, Huawei Health, Samsung Health, Strava: Das Teilen-Bild reicht. Dazu Gadgetbridge-FIT und -JSON, Routen als GPX, TCX, KML oder KMZ.</p></div>
<div><h3>Vier Overlay-Layouts</h3><p>Stapel, Reihe, Karte und Route. Bis zu vier Werte in deiner Reihenfolge, in Weiß, Schwarz oder Sportfarbe.</p></div>
<div><h3>Kurven und Splits</h3><p>Höhenprofil, Pulskurve, Km-Splits mit dem schnellsten Kilometer, Pulszonen.</p></div>
<div><h3>Eigene Looks</h3><p>Einstellungen unter einem Namen speichern. Die letzten acht Exporte bleiben im Verlauf, ein Tippen überträgt sie aufs nächste Training.</p></div>
<div><h3>Auch iPhone und Rechner</h3><p>Die <a href="../app/">Web-App</a> kann dasselbe in Safari, Chrome oder Firefox, ohne Installation. Deine Dateien bleiben auf dem Gerät.</p></div>
<div><h3>Kein Abo</h3><p>Höhenprofil, Splits und Pulszonen kosten nichts. In FitShare liegt nichts hinter einer Bezahlschranke.</p></div>
<div><h3>Bleibt auf dem Handy</h3><p>Die Texterkennung läuft auf dem Handy. Kein Konto, keine Analyse, kein Upload.</p></div>
</section>
<section>
<h2>Anleitungen</h2>
<ul class="links">
<li><a href="fitness-app-instagram-story/">Jede Fitness-App: Teilen-Knopf und GPX-Export auf einen Blick</a></li>
<li><a href="iphone-instagram-story/">Trainings-Sticker auf dem iPhone, ohne App</a></li>
<li><a href="garmin-connect-instagram-story/">Garmin-Connect-Training als Instagram-Story-Sticker</a></li>
<li><a href="mi-band-instagram-story/">Mi Fitness, Zepp und Mi Band in der Story</a></li>
<li><a href="gadgetbridge-instagram-story/">Gadgetbridge-Training in die Instagram-Story</a></li>
<li><a href="strava-sticker-alternative/">Strava-Sticker ohne Strava</a></li>
</ul>
</section>
{de_faq}
""", [app_schema("de", DE_DESC), de_faq_schema, howto_schema("Training aus der Fitness-App in die Instagram-Story", [s.replace("<b>", "").replace("</b>", "") for s in APP_STEPS_DE])],
     {"en": "", "de": "de/", "x-default": ""})

de_apps_faq, de_apps_faq_schema = faq([
    ("Meine App steht nicht in der Liste", "Probier es aus. Schickt der Teilen-Knopf ein Bild, taucht FitShare im Teilen-Menü auf und liest, was es lesen kann."),
    ("FitShare hat eine Zahl falsch gelesen", "Am besten klappt es mit dem Teilen-Bild der App, nicht mit einem zugeschnittenen Screenshot. Den Wert unter Werte abwählen oder die GPX-, TCX- oder FIT-Datei nehmen; Dateien enthalten die genauen Werte."),
    ("Warum fehlt die Karte?", "Aus einem Bild bekommt FitShare nur Zahlen. Route, Höhenprofil und Km-Splits brauchen eine GPX-, TCX-, KML-, KMZ- oder FIT-Datei. Die Tabelle zeigt, wo jede App eine exportiert."),
], "de")
page("de/fitness-app-instagram-story/", "de", "Training aus jeder Fitness-App als Instagram-Story-Sticker",
     "Mi Fitness, Garmin Connect, Zepp, Huawei Health, Strava: auf Teilen tippen, FitShare wählen, Story-Sticker bekommen. Dazu: wo jede App GPX exportiert.", f"""
<article>
<p class="kicker">ALLE APPS</p>
<h1>Training aus jeder Fitness-App als Instagram-Story-Sticker</h1>
<p class="lead">Fitness-Apps teilen ein Training als fertiges Bild im eigenen Design, und bei Strava und Garmin gibt es manche Kurven nur im Abo. FitShare liest die Zahlen aus diesem Bild, und du baust daraus deinen eigenen transparenten Sticker. Kostenlos, auf dem Handy.</p>
{steps_html(APP_STEPS_DE)}
<h2>Welche App was schickt</h2>
{apps_table(APPS_DE, "de")}
<p class="small">Menünamen ändern sich mit App-Updates. Fehlt FitShare im Teilen-Menü, auf Version 1.7.1 oder neuer aktualisieren.</p>
<h2>Auf dem iPhone oder ohne APK</h2>
<p>In der Fitness-App auf Teilen tippen und das Bild sichern, dann in der <a href="../../app/">FitShare-Web-App</a> öffnen. Mit GPX-, TCX-, FIT-, KML- und KMZ-Dateien geht das genauso. Die Texterkennung läuft im Browser, hochgeladen wird nichts.</p>
{cta("de")}
{de_apps_faq}
<p class="small">Mi Fitness, Zepp, Garmin Connect, Huawei Health, Samsung Health, Strava, Polar Flow und COROS sind Marken ihrer Inhaber. FitShare ist ein unabhängiges Projekt.</p>
</article>
""", [howto_schema("Fitness-App-Training als Instagram-Story-Sticker teilen", [s.replace("<b>", "").replace("</b>", "") for s in APP_STEPS_DE]), de_apps_faq_schema],
     {"en": "fitness-app-instagram-sticker/", "de": "de/fitness-app-instagram-story/"})

IPHONE_STEPS_DE = [
    "In der Fitness-App das Training öffnen, auf <b>Teilen</b> tippen und <b>Bild sichern</b> wählen.",
    "<b>brickystudios.github.io/FitShare/app</b> in Safari öffnen und das Bild aus Fotos wählen. Distanz, Zeit, Puls und Kalorien liest FitShare vom Bild.",
    "Für die Karte zusätzlich die GPX- oder TCX-Datei aus der Dateien-App wählen (Garmin Connect und Strava exportieren sie auf ihren Websites).",
    "Layout, Werte und Farbe wählen, auf <b>Teilen</b> tippen und Instagram wählen, oder <b>Bild sichern</b>.",
    "In Instagram eine Story öffnen, Sticker → Foto und das PNG wählen.",
]
de_ios_faq, de_ios_faq_schema = faq([
    ("Gibt es FitShare im App Store?", "Nein. Die Web-App läuft in Safari ohne Installation. Über Teilen → Zum Home-Bildschirm bekommt sie ein Icon wie eine App."),
    ("Warum steht FitShare nicht im Teilen-Menü des iPhones?", "iOS zeigt dort nur Apps aus dem App Store. Das Bild vorher zu sichern kostet einen Tipp mehr."),
    ("Wird mein Bild hochgeladen?", "Nein. Die Texterkennung läuft in Safari auf dem Handy. Beim ersten Mal lädt die Seite etwa 8 MB Erkennungsdaten, danach geht sie auch offline."),
    ("Welche Apps gehen?", "Jede App, die ein Bild vom Training teilt, etwa Garmin Connect, Mi Fitness, Zepp, Huawei Health, Strava, Polar Flow und COROS. Gadgetbridge und Samsung Health gibt es nur für Android."),
], "de")
page("de/iphone-instagram-story/", "de", "Trainings-Sticker für die Instagram-Story auf dem iPhone",
     "Werte aus Garmin Connect, Mi Fitness oder Strava auf dem iPhone in die Instagram-Story bringen. Kostenlose Web-App in Safari, ohne Installation und Upload.", f"""
<article>
<p class="kicker">IPHONE · WEB-APP</p>
<h1>Trainings-Sticker für die Instagram-Story, auf dem iPhone</h1>
<p class="lead">FitShare läuft in Safari. Teilen-Bild aus der Fitness-App sichern, in der Web-App öffnen, und du hast einen transparenten Sticker mit Distanz, Zeit und Puls. App Store, Konto und Abo brauchst du nicht.</p>
<p class="cta"><a class="btn" href="../../app/">Web-App öffnen</a></p>
{steps_html(IPHONE_STEPS_DE)}
{gallery("../../")}
{de_ios_faq}
</article>
""", [howto_schema("Trainings-Sticker für die Instagram-Story auf dem iPhone", [re.sub(r"<[^>]+>", "", x) for x in IPHONE_STEPS_DE]), de_ios_faq_schema],
     {"en": "iphone-instagram-story-sticker/", "de": "de/iphone-instagram-story/"})


# ------------------------------------------------------------------ German intent pages (counterparts of the English ones)

de_gb_faq, de_gb_faq_schema = faq([
    ("Wo ist der Teilen-Knopf in Gadgetbridge?", "Das Training in der Aktivitätenliste öffnen, dann oben rechts das Drei-Punkte-Menü. <b>Fit-Datei teilen</b> schickt das ganze Training. <b>JSON-Details teilen</b> geht auch, hat aber keine Sportart und keine Pulskurve."),
    ("FitShare taucht im Teilen-Menü nicht auf", "FitShare auf 1.5.0 oder neuer aktualisieren. Ältere Versionen nahmen nur JSON an, und Gadgetbridge teilt FIT-Dateien als allgemeine Binärdatei."),
    ("Mein Sticker hat keine Karte", "Uhren ohne GPS, etwa die meisten Mi Bands, zeichnen nur den Puls auf. Die Route mit OpenTracks auf dem Handy aufzeichnen und die KMZ oder GPX ebenfalls an FitShare teilen. Sie kommt zum offenen Training dazu."),
], "de")
page("de/gadgetbridge-instagram-story/", "de", "Gadgetbridge-Training in die Instagram-Story teilen",
     "Gadgetbridge hat keinen Instagram-Knopf. So wird ein Training Schritt für Schritt zum Story-Sticker mit Route, Distanz und Puls.", f"""
<article>
<p class="kicker">ANLEITUNG</p>
<h1>Gadgetbridge-Training in die Instagram-Story</h1>
<p class="lead">Gadgetbridge hält deine Trainings auf dem Handy und hat keinen Instagram-Knopf. Teil die FIT-Datei an FitShare, und du bekommst einen transparenten Sticker mit Werten und Route für jedes Story-Foto.</p>
{steps_html(GB_STEPS_DE)}
<img class="shot" src="../../bilder/app-editor.png" width="540" height="1100" loading="lazy" alt="FitShare-Editor mit Overlay-Vorschau und den Reitern Looks, Layout, Werte, Farbe, Extras und Sport">
<h2>Tipps</h2>
<ul>
<li>Deine liebste Einstellung unter <b>Looks</b> speichern. Beim nächsten Training bekommt der Sticker mit einem Tippen dasselbe Design.</li>
<li><b>Km-Splits</b> einschalten bei Fahrten und Läufen mit GPS: ein Balken pro Kilometer, der schnellste ist markiert.</li>
<li>Für den Feed statt die Story: das Design Nacht oder Startnummer im Format 4:5.</li>
</ul>
{cta("de")}
{de_gb_faq}
</article>
""", [howto_schema("Gadgetbridge-Training in die Instagram-Story teilen", [s.replace("<b>", "").replace("</b>", "") for s in GB_STEPS_DE]), de_gb_faq_schema],
     {"en": "gadgetbridge-instagram-story/", "de": "de/gadgetbridge-instagram-story/"})

de_strava_faq, de_strava_faq_schema = faq([
    ("Sieht FitShare genauso aus wie der Strava-Sticker?", "Nein, FitShare hat eigene Designs. Die Idee ist dieselbe: Werte und Route auf transparentem Grund, den du auf dein Foto legst."),
    ("Kann ich eine Strava-Aktivität übernehmen?", "Ja. In der Strava-App bei der Aktivität auf Teilen tippen und FitShare wählen, die Zahlen liest FitShare vom Bild. Für die Route die GPX auf strava.com exportieren und ebenfalls teilen."),
    ("Warum nicht einfach Strava?", "Manche wollen ihre Trainings nicht auf einem Server haben. FitShare arbeitet komplett offline mit Dateien, die du schon hast."),
], "de")
page("de/strava-sticker-alternative/", "de", "Strava-Sticker ohne Strava: Werte für die Instagram-Story",
     "Ein Statistik-Sticker wie bei Strava, ohne Strava-Konto. FitShare zeichnet Distanz, Zeit, Tempo und Route als transparentes PNG, offline.", f"""
<article>
<p class="kicker">ALTERNATIVE</p>
<h1>Ein Sticker wie bei Strava, ohne Strava</h1>
<p class="lead">Der Statistik-Sticker von Strava legt Distanz, Zeit und Route auf eine Instagram-Story. Dafür brauchst du ein Strava-Konto, und das Training liegt auf Stravas Servern. FitShare zeichnet so einen Sticker auf dem Handy, aus dem Teilen-Bild deiner Fitness-App oder aus einer FIT-, GPX- oder KMZ-Datei.</p>
{gallery("../../")}
<h2>Was du bekommst</h2>
<ul>
<li>Ein transparentes PNG, auf den Inhalt zugeschnitten, das Instagram als verschiebbaren Foto-Sticker nimmt.</li>
<li>Distanz, Zeit, Geschwindigkeit oder Tempo, Höhenmeter, Puls, Kalorien, Schritte, Bahnen: bis zu vier davon.</li>
<li>Route, Höhenprofil, Pulskurve und Km-Splits als Extras.</li>
<li>Kein Konto und kein Upload. Ins Netz geht die App nur für Updates.</li>
</ul>
<h2>Woher die Werte kommen</h2>
<p>Aus dem Teilen-Bild von Strava, Garmin Connect oder Mi Fitness, aus <a href="https://gadgetbridge.org/">Gadgetbridge</a> (FIT oder JSON), aus OpenTracks (KMZ oder GPX) oder aus jeder GPX-Datei, auch einer aus Strava oder Komoot. Auf dem iPhone geht es mit der <a href="../../app/">Web-App</a>.</p>
{cta("de")}
{de_strava_faq}
<p class="small">Strava ist eine Marke der Strava, Inc. FitShare ist ein unabhängiges Projekt.</p>
</article>
""", [app_schema("de", "Instagram-Statistik-Sticker wie bei Strava, ohne Strava-Konto."), de_strava_faq_schema],
     {"en": "strava-sticker-alternative/", "de": "de/strava-sticker-alternative/"})

MI_STEPS_DE = [
    "In Mi Fitness das Training öffnen und <b>Teilen</b> → ⋯ → <b>FitShare</b> tippen. Die Zahlen liest FitShare vom Bild.",
    "Für die Karte: im Training auf <b>Exportieren</b> tippen, GPX wählen, ⋯ und <b>FitShare</b>. Ohne GPS bietet Mi Fitness nur TCX an, darin stehen die Werte, aber keine Route.",
    "Layout, Werte und Farbe wählen, dann <b>PNG speichern</b>.",
    "In Instagram eine Story öffnen, Sticker → Foto und das PNG wählen.",
]
de_band_faq, de_band_faq_schema = faq([
    ("Geht das mit der Mi-Fitness-App?", "Ja. Training öffnen, Teilen und FitShare wählen. Für die Karte im Training auf Exportieren tippen, GPX wählen und über ⋯ an FitShare schicken."),
    ("Geht es auch ohne Mi Fitness oder Zepp?", "Ja, mit Gadgetbridge. Es verbindet das Band ohne Hersteller-App und Cloud, und FitShare liest die FIT-Datei."),
    ("Warum fehlt die Distanz?", "Bänder ohne GPS speichern oft keine Distanz. FitShare nimmt Distanz, Zeit und Höhenmeter dann aus einer Route, die das Handy aufgezeichnet hat, etwa mit OpenTracks."),
    ("Zeigt FitShare Pulszonen?", "Ja, wenn das Band sie aufzeichnet. FIT-Dateien aus Gadgetbridge enthalten die Zeit in jeder Zone und die Pulskurve."),
], "de")
page("de/mi-band-instagram-story/", "de", "Mi Band und Amazfit: Training als Instagram-Story-Sticker",
     "Training vom Xiaomi Smart Band oder von Amazfit aus Mi Fitness, Zepp oder Gadgetbridge als Instagram-Story-Sticker mit Zeit, Puls und Route.", f"""
<article>
<p class="kicker">XIAOMI · AMAZFIT</p>
<h1>Mi Band und Amazfit in der Instagram-Story</h1>
<p class="lead">Mi Fitness und Zepp teilen ein Training als fertiges Bild in ihrem eigenen Layout. FitShare macht daraus einen Sticker, den du selbst gestaltest. Wer das Band mit Gadgetbridge verbindet, hat keinen Teilen-Knopf für Bilder, dann liest FitShare die FIT-Datei.</p>
<h2>Aus Mi Fitness oder Zepp</h2>
<p>Training öffnen, auf <b>Teilen</b> tippen und FitShare wählen. Distanz, Zeit, Durchschnitts- und Maximalpuls und Kalorien liest FitShare vom Bild. Mi Fitness hat im Training außerdem <b>Exportieren</b>: GPX, KML oder TCX wählen, auf ⋯ tippen und FitShare nehmen. Mit GPS kommt die Route mit, ohne GPS gibt es nur TCX, das immerhin die Werte enthält. Zepp exportiert bei Uhren mit GPS eine GPX.</p>
<p>Auf dem iPhone geht es genauso: Teilen-Bild sichern und in der <a href="../../app/">Web-App</a> öffnen.</p>
{steps_html(MI_STEPS_DE)}
<h2>Aus Gadgetbridge</h2>
<p>Gadgetbridge verbindet das Band ohne die App von Xiaomi oder Zepp und hat kein Bild zum Teilen. FitShare liest stattdessen die FIT-Datei.</p>
{steps_html(GB_STEPS_DE)}
<h2>Bänder ohne GPS</h2>
<p>Die meisten Mi Bands messen den Puls, zeichnen aber keine Route auf. Zwei Wege zur Karte auf dem Sticker:</p>
<ul>
<li>Die Fahrt oder den Lauf mit OpenTracks auf dem Handy aufzeichnen und die KMZ nach der FIT an FitShare teilen.</li>
<li>Oder ohne Karte: <b>Pulskurve</b> und <b>Pulszonen</b> geben allein schon einen guten Sticker.</li>
</ul>
{cta("de")}
{de_band_faq}
</article>
""", [howto_schema("Mi-Fitness-Training in die Instagram-Story teilen", [s.replace("<b>", "").replace("</b>", "") for s in MI_STEPS_DE]), de_band_faq_schema],
     {"en": "mi-band-instagram-story/", "de": "de/mi-band-instagram-story/"})

GARMIN_STEPS_DE = [
    "In Garmin Connect die Aktivität öffnen und auf das <b>Teilen</b>-Symbol tippen. Bietet Garmin mehrere Wege an, den nehmen, der ein Bild teilt. Auf dem iPhone das Bild sichern und in der <a href=\"../../app/\">Web-App</a> öffnen.",
    "<b>FitShare</b> wählen. Distanz, Zeit, Tempo oder Geschwindigkeit, Puls, Höhenmeter und Kalorien liest FitShare vom Bild.",
    "Für die Route dieselbe Aktivität auf connect.garmin.com öffnen, auf das Zahnrad klicken und als GPX exportieren. Die Datei auf dem Handy an FitShare teilen.",
    "Layout, Werte und Farbe wählen, dann <b>PNG speichern</b>.",
    "In Instagram eine Story öffnen, Sticker → Foto und das PNG wählen.",
]
de_garmin_faq, de_garmin_faq_schema = faq([
    ("Brauche ich Garmin Connect+?", "Nein. FitShare geht mit der kostenlosen Garmin-Connect-App. Es braucht nur das Bild, das der Teilen-Knopf ohnehin macht."),
    ("Warum fehlt ein Wert, den ich in Garmin Connect sehe?", "FitShare liest, was auf dem Teilen-Bild steht. Zeigt das Bild vier Werte, bekommst du diese vier. Die GPX bringt Route, Distanz, Zeit und Höhenmeter dazu."),
    ("Geht es mit einer Garmin, die Gadgetbridge verbindet?", "Ja. Dann statt des Bilds die FIT-Datei aus Gadgetbridge teilen."),
], "de")
page("de/garmin-connect-instagram-story/", "de", "Garmin-Connect-Training als Instagram-Story-Sticker",
     "Lauf oder Fahrt aus Garmin Connect als transparenter Instagram-Story-Sticker mit Distanz, Tempo, Puls und Route. Kostenlose Android-App, ohne Connect+.", f"""
<article>
<p class="kicker">GARMIN CONNECT</p>
<h1>Garmin-Connect-Training als Instagram-Story-Sticker</h1>
<p class="lead">Das Teilen-Bild von Garmin Connect hat Garmins Layout, und die Teile lassen sich nicht verschieben. FitShare liest die Zahlen daraus und zeichnet einen transparenten Sticker in deinem Stil. Ein Abo für Garmin Connect+ brauchst du nicht.</p>
{steps_html(GARMIN_STEPS_DE)}
{gallery("../../")}
{cta("de")}
{de_garmin_faq}
<p class="small">Garmin und Garmin Connect sind Marken der Garmin Ltd. FitShare ist ein unabhängiges Projekt.</p>
</article>
""", [howto_schema("Garmin-Connect-Training in die Instagram-Story teilen", [re.sub(r"<[^>]+>", "", x) for x in GARMIN_STEPS_DE]), de_garmin_faq_schema],
     {"en": "garmin-connect-instagram-story/", "de": "de/garmin-connect-instagram-story/"})

# ------------------------------------------------------------------ crawl files

slugs = ["", "app/", "iphone-instagram-story-sticker/", "fitness-app-instagram-sticker/", "garmin-connect-instagram-story/", "gadgetbridge-instagram-story/",
         "strava-sticker-alternative/", "mi-band-instagram-story/", "de/", "de/fitness-app-instagram-story/", "de/iphone-instagram-story/",
         "de/gadgetbridge-instagram-story/", "de/strava-sticker-alternative/", "de/mi-band-instagram-story/", "de/garmin-connect-instagram-story/"]
(DOCS / "sitemap.xml").write_text(
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    + "".join(f"  <url><loc>{BASE + s}</loc></url>\n" for s in slugs) + "</urlset>\n")
# no robots.txt: crawlers only read it at the domain root, submit the sitemap in Search Console instead
(DOCS / ".nojekyll").write_text("")  # serve files as they are; the markdown plans in docs/ stay unrendered
print("built", len(slugs), "pages")
