"""What the Android shell talks to.

The engine and its Api class are shared with the desktop builds unchanged.
Three things they do cannot work here, and each is answered rather than
removed:

  * A file dialog is an Activity result, not a blocking call, so browse() and
    the converter pickers are handled in Kotlin and their answers arrive here
    afterwards. The page already expects those to report through the poll
    queue, so nothing about the interface changes.
  * A sign-in window is the shell's own WebView, and the session is read from
    Android's CookieManager instead of an installed browser's profile.
  * The clipboard has no command-line reader on Android.

Everything else — every download, every conversion, the proxy, the engine
ladder — is the same code path the Windows build runs.
"""

import contextlib
import json
import time
from pathlib import Path
from urllib.parse import urlsplit

import blueknight_paths
import spotify_downloader as engine
from blueknight_paths import cookie_dir, host_matches, record_jar

_app = None
_api = None
_activity = None

# A cookie taken from CookieManager arrives as "name=value; name=value" with no
# metadata, so the jar is written with the defaults yt-dlp accepts: host-wide,
# secure, and valid for the same six months the desktop jar uses.
_JAR_LIFETIME = 60 * 60 * 24 * 180


def boot(activity):
    """Start the engine once. Called from MainActivity.onCreate."""
    global _app, _api, _activity
    _activity = activity
    if _api is not None:
        return
    # Before the engine reads anything, so it never sees last run's session.
    _cleared = _start_signed_out()
    _app = engine.SpotifyDownloader()
    _api = engine.Api(_app)
    if _cleared:
        _app.log("Starting signed out — saved sign-ins are cleared each launch, so a "
                 "session that stops working cannot keep the app stuck. Sign in again "
                 "when a download asks for it.", "info")
    # The engine refreshes a stale login by re-reading the browser profile it
    # signed in through. On a phone that profile is the system CookieManager,
    # which only the shell can reach, so the way in is handed over here.
    engine.SESSION_REFRESHER = refresh_session
    # NewPipeExtractor is a JVM library. The shared Python engine sees only a
    # JSON-returning callback, and calls it only after yt-dlp has run out of
    # useful moves on Android.
    engine.NEWPIPE_RESOLVER = resolve_with_newpipe
    # The same YouTube.js script the desktop runs under Deno, hosted here by a
    # WebView instead. The engine sees one callback and cannot tell which.
    engine.YOUTUBEJS_RESOLVER = resolve_with_youtube_js
    # The token goes to yt-dlp, not to a fallback: it is the primary engine
    # that gets refused as a bot, and this is what that refusal asks for.
    engine.POTOKEN_MINTER = mint_po_token
    # The desktop Api reaches for a pywebview window to open dialogs with.
    # Those methods are intercepted in Kotlin before they ever get here, so the
    # attribute stays None and any missed path fails loudly rather than silently.
    _api._window = None


def resolve_with_newpipe(url, proxy_url=None):
    """Resolve direct stream candidates through the Android NewPipe adapter."""
    if _activity is None:
        raise RuntimeError("The Android extractor bridge is not ready.")
    payload = _activity.resolveWithNewPipe(str(url), str(proxy_url or ""))
    return json.loads(str(payload))


def resolve_with_youtube_js(url, proxy_url=None):
    """Resolve stream candidates by running YouTube.js inside a WebView."""
    if _activity is None:
        raise RuntimeError("The Android extractor bridge is not ready.")
    payload = _activity.resolveWithYouTubeJs(str(url), str(proxy_url or ""))
    return json.loads(str(payload))


def mint_po_token(proxy_url=None):
    """Mint a proof-of-origin token in the WebView, for yt-dlp to use."""
    if _activity is None:
        raise RuntimeError("The Android extractor bridge is not ready.")
    return json.loads(str(_activity.mintPoToken(str(proxy_url or ""))))


def _start_signed_out():
    """Throw away the sessions this app wrote, at every launch.

    A stored login is not always the better position on YouTube. Some of what
    it withholds it withholds because of the account — the server-side
    streaming experiment is chosen per account, and one inside it is served no
    downloadable stream on any client, on every video, until the session goes
    away. A session that can only be cleared by knowing it is the problem is a
    session that wedges the app, so the phone starts each launch signed out and
    signs in again when something actually needs it.

    Only the jars this app wrote are cleared. A cookies.txt exported from a
    desktop browser and dropped in the app's own folder is the documented way
    round a site that refuses to sign in here at all, so it lives outside the
    cookie folder and survives this on purpose.
    """
    removed = 0
    with contextlib.suppress(Exception):
        for jar in cookie_dir().rglob("*.txt"):
            with contextlib.suppress(OSError):
                jar.unlink()
                removed += 1
    with contextlib.suppress(Exception):
        registry = blueknight_paths.registry_path()
        if registry.is_file():
            registry.unlink()
    # The WebView keeps its own copy, and it is the one a re-login reads back.
    with contextlib.suppress(Exception):
        if _activity is not None:
            _activity.clearBrowserSession()
    return removed


def call(method, args_json):
    """Answer one page call. Returns JSON, or an empty string for no result."""
    args = json.loads(args_json or "[]")
    handler = _LOCAL.get(method)
    if handler is None:
        handler = getattr(_api, method, None)
    if handler is None:
        return json.dumps({"error": f"unknown method {method}"})
    try:
        result = handler(*args)
    except Exception as failure:      # a bridge call must never kill the page
        with contextlib.suppress(Exception):
            _app.log(f"{method} failed: {failure}", "error")
        return json.dumps({"error": str(failure)[:300]})
    return "" if result is None else json.dumps(result)


# ---------------------------------------------------------------------------
# What the shell needs to know to host a login
# ---------------------------------------------------------------------------
def signin_url(kind):
    pages = engine.SIGNIN_PAGES.get(kind)
    return pages[0] if pages else None


def signin_domains(kind):
    """The pages a session for this source is spread across.

    A YouTube login puts half its cookies on google.com, so reading one host
    would save a jar that looks complete and is not.
    """
    pages = engine.SIGNIN_PAGES.get(kind)
    return list(pages[1]) if pages else []


def browser_ua():
    return engine.BROWSER_UA


def _build_jar(kind, jar_json):
    """Render harvested cookies as jar lines. Touches nothing on disk.

    Split out from the write because the sign-in window asks after every page
    whether the session exists yet, and answering must not cost anything. Doing
    that with the writing version would overwrite a perfectly good stored
    session with a half-finished one on the way through a re-login, and losing
    it if the person then backed out.
    """
    site = engine.cookie_site(kind)
    harvested = json.loads(jar_json or "[]")
    expiry = int(time.time()) + _JAR_LIFETIME

    lines = ["# Netscape HTTP Cookie File",
             "# Written by Blue Knight Downloader. Editing this is not needed.", ""]
    written = 0
    seen = set()
    for url, header in harvested:
        host = (url.split("//", 1)[-1].split("/", 1)[0]).strip()
        if not host or not header:
            continue
        # A cookie set for www.youtube.com is sent to youtube.com too, and the
        # leading dot is what tells yt-dlp's jar reader that.
        domain = host if host.count(".") < 2 else "." + host.split(".", 1)[1]
        for pair in header.split(";"):
            name, _, value = pair.strip().partition("=")
            if not name or (domain, name) in seen:
                continue
            seen.add((domain, name))
            lines.append("\t".join([domain, "TRUE", "/", "TRUE",
                                    str(expiry), name, value]))
            written += 1

    session_names = set(engine.SITE_SESSION_COOKIES.get(site, ()))
    signed_in = any(name in session_names for _, name in seen)
    return lines, written, signed_in


def _write_jar(kind, jar_json):
    """Save the harvested cookies as the Netscape jar yt-dlp reads.

    Returns (jar path, cookies written, whether a session cookie was among
    them). Shared by the sign-in flow and the silent mid-download refresh, so
    the two can never disagree about what a saved session looks like.
    """
    lines, written, signed_in = _build_jar(kind, jar_json)
    jar = cookie_dir(kind) / f"{engine.cookie_site(kind)}_cookies.txt"
    jar.parent.mkdir(parents=True, exist_ok=True)
    # Only replace a working jar once there is something to replace it with; a
    # refresh that reads nothing must not delete the session it was checking.
    if written:
        jar.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return jar, written, signed_in


def webview_proxy():
    """Where the sign-in window should send its traffic.

    "" for none, "!" and a scheme when the proxy cannot be used, otherwise
    "host:port". A plain string because the shell only has to act on it.

    This exists because a per-app proxy is not a tunnel. --proxy is handed to
    the downloader and to nothing else, so the sign-in window went out over the
    ordinary connection while the download went out through the proxy — and a
    session minted at one address and spent at another is one of the things
    sites like YouTube look for. A device-wide VPN never showed this, because
    it carried the login and the download alike.

    Android's WebView proxy override speaks HTTP only. A SOCKS proxy is
    reported back rather than silently ignored, because "the login is not going
    where the download goes" is the whole problem and it should not be
    invisible.
    """
    if _app is None or not _app.use_proxy.get():
        return ""
    raw = _app.proxy_url.get().strip()
    if not raw:
        return ""
    try:
        parsed = urlsplit(engine.normalize_proxy_url(raw, _app.proxy_type.get()))
    except ValueError:
        return ""
    if parsed.scheme.startswith("socks"):
        _app.log("The sign-in window cannot use a SOCKS proxy — Android only lets an "
                 "app redirect its browser through an HTTP one. Downloads still use "
                 "the proxy, so the session will be created on this connection and "
                 "used from another, which is what sites read as a bot. Point the "
                 "proxy at your client's HTTP port instead if it has one.", "warning")
        return "!" + parsed.scheme
    return f"{parsed.hostname}:{parsed.port}"


def signin_ready(kind, url, jar_json):
    """True once this page is the far side of a completed sign-in.

    The shell asks after every page the login window finishes, so nobody has to
    know when to press anything: the window closes itself the moment the
    session exists.

    Two things have to be true together. The session cookie must be present —
    which is the same test the save uses, so "ready" and "saved" can never
    disagree. And the page must be back on the source's own host: a YouTube
    login starts on accounts.google.com and Google sets some of these names
    part-way through, so the cookie alone would close the window while the
    password box was still on screen. Landing back on the site is what says
    the flow ran to the end.

    Every source with a login goes through this one path — YouTube, Instagram,
    TikTok, SoundCloud and X — because the test is about the session, not about
    any one site's redirects.
    """
    pages = engine.SIGNIN_PAGES.get(kind)
    if not pages:
        return False
    host = (str(url or "").split("//", 1)[-1].split("/", 1)[0]).split(":")[0].strip()
    home = pages[2]
    if home.startswith("www."):
        home = home[4:]
    if not host or not host_matches(host, home):
        return False
    _, written, signed_in = _build_jar(kind, jar_json)
    return bool(written and signed_in)


def refresh_session(kind):
    """Re-read a live session from CookieManager, mid-download and unattended.

    Installed as the engine's SESSION_REFRESHER. The engine calls this when a
    stored login stops working, which on YouTube happens routinely because it
    rotates cookies on any open tab.
    """
    if _activity is None:
        return 0, False
    jar, written, signed_in = _write_jar(kind, _activity.harvestCookies(kind))
    if written and signed_in:
        record_jar(kind, jar, "refresh")
    return written, signed_in


def save_cookies(kind, jar_json):
    """Write the shell's harvested cookies after an interactive sign-in."""
    jar, written, signed_in = _write_jar(kind, jar_json)

    _app._login_kind = None
    _app.ui("signin", None)
    if not written:
        # Some sites refuse to sign in inside an embedded browser at all, and no
        # amount of retrying changes that — so the way round it is named here
        # rather than left for someone to find out. The app's own folder is
        # reachable from any file manager, and a jar dropped there is picked up
        # as a cookie source exactly like one this app wrote.
        folder = blueknight_paths.app_dir()
        site = engine.MEDIA_LABELS.get(kind, kind.title())
        _app.log(f"No cookies came back from {site}. Either the sign-in was not finished, "
                 f"or {site} refused to sign in inside an embedded browser.", "warning")
        _app.log(f"You can also export {site} cookies from a desktop browser and put the "
                 f"file in {folder} — any name ending in cookies.txt works.", "info")
        _app.notify("No cookies were found. Finish signing in, or drop an exported "
                    "cookies.txt in the app folder.", "err")
        return {"cookies": 0}

    entry = record_jar(kind, jar, "sign-in")
    _app.log(f"Signed in to {kind.title()}: {written} cookies saved to "
             f"{jar.parent.name}/{jar.name}.", "success")
    _app.ui("cookie_status", _app.cookie_status())
    if not signed_in:
        _app.notify(f"Saved {written} cookies, but no {kind.title()} session cookie. "
                    f"Complete the login, then press Finish sign-in again.", "err")
    else:
        _app.notify(f"Signed in to {kind.title()}. {entry['cookies']} cookies kept.", "ok")
    return {"cookies": written, "signed_in": signed_in}


# ---------------------------------------------------------------------------
# Activity results, arriving after the page has already moved on
# ---------------------------------------------------------------------------
def android_set_folder(tree_uri):
    """Remember a folder the user picked with the system picker.

    A tree URI is not a filesystem path and the engine writes with open(), so
    the download itself keeps going to the app's own external folder, which
    needs no permission on any API level. The picked tree is where finished
    files are handed to afterwards.
    """
    _app.save_folder.set(str(blueknight_paths.download_root()))
    public_name = "BlueKnight Downloader"
    _app.ui("folder", public_name)
    _app.notify(f"Downloads will be sorted inside {public_name}.", "ok")
    return {"folder": public_name}


def android_picked_file(path):
    """Start document conversion after Kotlin materialises the content URI."""
    local = Path(path)
    _app.start_document_conversion(str(local))
    return {"path": str(local)}


def android_picked_media(path, options):
    """Start media conversion with the options already selected in the page."""
    local = Path(path)
    _app.start_media_conversion(str(local), options)
    return {"path": str(local)}


def android_picked_image_folder(path):
    """Start the ordered-image conversion on the imported SAF tree."""
    local = Path(path)
    _app.start_image_folder_conversion(str(local))
    return {"path": str(local)}


def android_import_failed(message):
    _app.notify(str(message or "The selected item could not be opened."), "err")


def js_runtime_hint():
    """Report which JavaScript engine yt-dlp will get, for the components panel."""
    name, path = (_app.js_runtime() if _app else (None, None))
    return {"name": name, "path": path}


def is_working():
    """Whether anything is in flight, so the service knows to keep the app alive.

    Setup counts: fetching a tool over a slow connection is exactly when being
    killed for being in the background is most likely and most annoying.
    """
    if _app is None:
        return False
    return bool(_app.is_downloading or _app.is_converting
                or _app.updating or _app.setup_running)


_LOCAL = {
    "signin_url": signin_url,
    "signin_domains": signin_domains,
    "browser_ua": browser_ua,
    "save_cookies": save_cookies,
    "android_set_folder": android_set_folder,
    "android_picked_file": android_picked_file,
    "android_picked_media": android_picked_media,
    "android_picked_image_folder": android_picked_image_folder,
    "android_import_failed": android_import_failed,
    "js_runtime_hint": js_runtime_hint,
    "webview_proxy": webview_proxy,
}
