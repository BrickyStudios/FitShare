#!/usr/bin/env python3
"""Builds the GitHub Pages site into docs/ (served at brickystudios.github.io/FitShare).

One home page per language plus one page per search intent. Run: python3 tools/build_site.py
"""
import html
import json
from pathlib import Path

BASE = "https://brickystudios.github.io/FitShare/"
REPO = "https://github.com/BrickyStudios/FitShare"
APK = REPO + "/releases/latest"
DOCS = Path(__file__).resolve().parent.parent / "docs"
IMG = BASE + "bilder/social-preview.png"


def page(slug, lang, title, desc, body, schema, alternates=None):
    """slug '' = home. Relative paths keep the site working under any sub-path."""
    root = "../" * slug.count("/") if slug else ""
    url = BASE + slug
    alt = "".join(f'<link rel="alternate" hreflang="{l}" href="{BASE + s}">' for l, s in (alternates or {}).items())
    nav_de = lang == "de"
    nav = (
        f'<a href="{root}de/">Anleitung</a><a href="{REPO}">GitHub</a>' if nav_de else
        f'<a href="{root}gadgetbridge-instagram-story/">Guide</a><a href="{root}de/">Deutsch</a><a href="{REPO}">GitHub</a>'
    )
    foot = (
        "FitShare ist freie Software unter GPL-3.0. Kein Projekt von Gadgetbridge, Strava, Instagram oder Meta."
        if nav_de else
        "FitShare is free software under GPL-3.0. Not affiliated with Gadgetbridge, Strava, Instagram or Meta."
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
        "step": [{"@type": "HowToStep", "position": i + 1, "text": s} for i, s in enumerate(steps)],
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
                f'<a class="btn ghost" href="{REPO}">Auf GitHub ansehen</a></p>'
                '<p class="small">Android 8.0 oder neuer. Kostenlos, ohne Konto, ohne Internetzugriff. Updates bequem über Obtainium.</p>')
    return (f'<p class="cta"><a class="btn" href="{APK}">Download the APK</a>'
            f'<a class="btn ghost" href="{REPO}">View on GitHub</a></p>'
            '<p class="small">Android 8.0 or newer. Free, no account, no internet permission. Get updates through Obtainium.</p>')


def gallery(root):
    return f"""<div class="gallery">
<img src="{root}bilder/0-overlay-stack.png" width="540" height="960" loading="lazy" alt="Stack overlay: distance, time and speed above the route on a sunset photo">
<img src="{root}bilder/0-overlay-card.png" width="540" height="960" loading="lazy" alt="Card overlay: translucent card with route, four stats and an elevation profile">
<img src="{root}bilder/1-night.png" width="540" height="960" loading="lazy" alt="Night story: glowing route, distance, heart rate zones and a stats table">
</div>"""


# ------------------------------------------------------------------ English home

HOME_DESC = ("Turn Gadgetbridge workouts into Instagram story stickers like Strava's: a transparent PNG with "
             "distance, time, route and heart rate. Free Android app, offline, no account.")
GB_STEPS = [
    "In Gadgetbridge, open the workout and tap ⋮ → <b>Share FIT File</b>.",
    "Pick <b>FitShare</b> in the share sheet.",
    "No GPS in the file? Share the route from OpenTracks (KMZ or GPX) to FitShare too.",
    "Choose a layout, the stats and a colour, then tap <b>Save PNG</b>.",
    "In Instagram, open a story, tap Stickers → Photo and pick the PNG.",
]
home_faq, home_faq_schema = faq([
    ("Is FitShare free?", "Yes. It is open source under GPL-3.0, with no ads and no in-app purchases."),
    ("Do I need Strava?", "No. FitShare reads the files Gadgetbridge exports and draws the sticker on your phone."),
    ("Which watches work?", "Any watch Gadgetbridge syncs workouts from, for example Xiaomi Smart Band, Amazfit and Garmin. FitShare reads the FIT or JSON export, not the watch."),
    ("Is there an iPhone version?", "No. Gadgetbridge is Android-only, and so is FitShare (Android 8.0 or newer)."),
    ("Is it on Google Play?", "Not yet. Install the APK from GitHub Releases, or add the repository to Obtainium for automatic updates."),
    ("Does my data leave the phone?", "No. The app has no internet permission, so it cannot upload anything."),
])
page("", "en", "FitShare: Gadgetbridge workouts as Instagram story stickers", HOME_DESC, f"""
<section class="hero">
<p class="kicker">FREE ANDROID APP · OPEN SOURCE</p>
<h1>Your workout.<br><span>As a sticker.</span></h1>
<p class="lead">FitShare turns a Gadgetbridge workout into a transparent PNG for your Instagram story: distance, time, route, heart rate. Like Strava's stats sticker, without Strava and without an account.</p>
{cta()}
</section>
{gallery("")}
<section>
<h2>From watch to story in five taps</h2>
{steps_html(GB_STEPS)}
<p><a href="gadgetbridge-instagram-story/">Step-by-step guide with tips →</a></p>
</section>
<section class="grid">
<div><h3>Reads what Gadgetbridge exports</h3><p>FIT files (sport, heart rate, zones, GPS if recorded) and the JSON details. Routes from GPX, KML or KMZ, so OpenTracks tracks work as they are.</p></div>
<div><h3>Four overlay layouts</h3><p>Stack, Row, Card and Map. Pick up to four stats in your order, white, black or the sport's colour.</p></div>
<div><h3>Elevation, heart rate, splits</h3><p>Elevation profile, heart rate graph, km splits with your fastest kilometre, heart rate zones.</p></div>
<div><h3>Your look, one tap away</h3><p>Save a setup as a named look. The last eight exports stay in a history, so the next workout looks like the last one.</p></div>
<div><h3>Full stories and feed posts</h3><p>Two finished designs, Night and Race bib, as a 9:16 story or a 4:5 feed post.</p></div>
<div><h3>Stays on your phone</h3><p>No account, no analytics, no internet permission. Files stay on your phone.</p></div>
</section>
<section>
<h2>Guides</h2>
<ul class="links">
<li><a href="gadgetbridge-instagram-story/">How to share a Gadgetbridge workout to Instagram</a></li>
<li><a href="strava-sticker-alternative/">A Strava stats sticker without Strava</a></li>
<li><a href="mi-band-instagram-story/">Mi Band and Amazfit workouts in your Instagram story</a></li>
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
    ("Can I import a Strava activity?", "Export it as GPX from Strava's website and open the GPX in FitShare. You get the route, distance, elevation and time from the track."),
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
    ("Does it work without the Mi Fitness or Zepp app?", "Yes. That is the point of Gadgetbridge: it syncs the band without the vendor's app and cloud. FitShare then reads Gadgetbridge's export."),
    ("Why is the distance missing?", "Bands without GPS often don't store distance. FitShare takes distance, time and elevation from a route recorded on the phone, for example with OpenTracks."),
    ("Does it show heart rate zones?", "Yes, when the band records them. FIT exports from Gadgetbridge include the time in each zone and the heart rate over the workout."),
])
page("mi-band-instagram-story/", "en", "Mi Band and Amazfit workouts as Instagram stories (Gadgetbridge)",
     "Share Xiaomi Smart Band or Amazfit workouts synced with Gadgetbridge as Instagram story stickers: heart rate graph, zones, time and route. Free, offline Android app.", f"""
<article>
<p class="kicker">XIAOMI · AMAZFIT</p>
<h1>Mi Band and Amazfit workouts in your Instagram story</h1>
<p class="lead">If you sync your Xiaomi Smart Band or Amazfit with Gadgetbridge instead of the vendor app, you lose the share button. FitShare brings it back: share the FIT file from Gadgetbridge and get a story sticker with time, heart rate and calories.</p>
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

# ------------------------------------------------------------------ German home

DE_DESC = ("Gadgetbridge-Trainings als Instagram-Story-Sticker wie bei Strava: transparentes PNG mit Distanz, Zeit, "
           "Route und Puls. Kostenlose Android-App, offline, ohne Konto.")
DE_STEPS = [
    "In Gadgetbridge das Training öffnen, ⋮ → <b>Fit-Datei teilen</b>.",
    "Im Teilen-Menü <b>FitShare</b> wählen.",
    "Kein GPS in der Datei? Die Route aus OpenTracks (KMZ oder GPX) ebenfalls an FitShare teilen.",
    "Layout, Werte und Farbe wählen, dann <b>PNG speichern</b>.",
    "In Instagram eine Story öffnen, Sticker → Foto und das PNG wählen.",
]
de_faq, de_faq_schema = faq([
    ("Kostet FitShare etwas?", "Nein. Die App ist Open Source unter GPL-3.0, ohne Werbung und ohne In-App-Käufe."),
    ("Brauche ich Strava?", "Nein. FitShare liest die Dateien, die Gadgetbridge exportiert, und zeichnet den Sticker auf dem Handy."),
    ("Welche Uhren gehen?", "Alle, von denen Gadgetbridge Trainings synchronisiert, zum Beispiel Xiaomi Smart Band, Amazfit und Garmin."),
    ("Gibt es FitShare für das iPhone?", "Nein. Gadgetbridge gibt es nur für Android, FitShare auch (ab Android 8.0)."),
    ("Verlassen meine Daten das Handy?", "Nein. Die App hat keine Internet-Berechtigung und kann nichts hochladen."),
], "de")
page("de/", "de", "FitShare: Gadgetbridge-Training als Instagram-Story-Sticker", DE_DESC, f"""
<section class="hero">
<p class="kicker">KOSTENLOSE ANDROID-APP · OPEN SOURCE</p>
<h1>Dein Training.<br><span>Als Sticker.</span></h1>
<p class="lead">FitShare macht aus einem Gadgetbridge-Training ein transparentes PNG für deine Instagram-Story: Distanz, Zeit, Route, Puls. Wie der Statistik-Sticker von Strava, nur ohne Strava und ohne Konto.</p>
{cta("de")}
</section>
{gallery("../")}
<section>
<h2>So kommt das Training in die Story</h2>
{steps_html(DE_STEPS)}
</section>
<section class="grid">
<div><h3>Liest Gadgetbridge-Exporte</h3><p>FIT-Dateien (Sportart, Puls, Pulszonen, GPS falls aufgezeichnet) und JSON. Routen als GPX, KML oder KMZ, OpenTracks-Strecken gehen direkt.</p></div>
<div><h3>Vier Overlay-Layouts</h3><p>Stapel, Reihe, Karte und Route. Bis zu vier Werte in deiner Reihenfolge, in Weiß, Schwarz oder Sportfarbe.</p></div>
<div><h3>Kurven und Splits</h3><p>Höhenprofil, Pulskurve, Km-Splits mit dem schnellsten Kilometer, Pulszonen.</p></div>
<div><h3>Eigene Looks</h3><p>Einstellungen unter einem Namen speichern. Die letzten acht Exporte bleiben im Verlauf, ein Tippen überträgt sie aufs nächste Training.</p></div>
<div><h3>Story oder Feed</h3><p>Zwei fertige Designs, Nacht und Startnummer, als 9:16-Story oder 4:5-Beitrag.</p></div>
<div><h3>Bleibt auf dem Handy</h3><p>Kein Konto, keine Analyse, keine Internet-Berechtigung.</p></div>
</section>
{de_faq}
""", [app_schema("de", DE_DESC), de_faq_schema, howto_schema("Gadgetbridge-Training in die Instagram-Story", [s.replace("<b>", "").replace("</b>", "") for s in DE_STEPS])],
     {"en": "", "de": "de/", "x-default": ""})

# ------------------------------------------------------------------ crawl files

slugs = ["", "gadgetbridge-instagram-story/", "strava-sticker-alternative/", "mi-band-instagram-story/", "de/"]
(DOCS / "sitemap.xml").write_text(
    '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
    + "".join(f"  <url><loc>{BASE + s}</loc></url>\n" for s in slugs) + "</urlset>\n")
# no robots.txt: crawlers only read it at the domain root, submit the sitemap in Search Console instead
(DOCS / ".nojekyll").write_text("")  # serve files as they are; the markdown plans in docs/ stay unrendered
print("built", len(slugs), "pages")
