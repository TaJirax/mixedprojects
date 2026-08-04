"""Where downloads land, and where cookies come from. Shared by the GUI and
the standalone scripts."""

import contextlib
import json
import os
import re
import sys
import time
from pathlib import Path

ROOT_NAME = "BlueKnightdownloader"
SOURCES = {
    "spotify": "Spotify",
    "youtube": "YouTube",
    "tiktok": "TikTok",
    "instagram": "Instagram",
}

# yt-dlp browser name -> the profile directory that only exists once it is installed.
# Firefox first: on Windows, Chrome and Edge encrypt their cookie DB with DPAPI and
# yt-dlp often cannot decrypt it, while Firefox stores a plain SQLite file.
# Firefox first everywhere: it keeps cookies in a plain SQLite file that can be
# read while it runs. Every Chromium browser below holds an exclusive lock on
# its own database whenever it is open. Names are the ones yt-dlp accepts for
# --cookies-from-browser; the path is passed explicitly as `name:path` because
# yt-dlp only knows each browser's default location, and the interesting
# installs — Opera GX above all — do not live there.
_BROWSER_DIRS = {
    "win32": [
        ("firefox", "~/AppData/Roaming/Mozilla/Firefox"),
        ("librewolf", "~/AppData/Roaming/librewolf"),
        ("opera", "~/AppData/Roaming/Opera Software/Opera GX Stable"),
        ("opera", "~/AppData/Roaming/Opera Software/Opera Stable"),
        ("opera", "~/AppData/Roaming/Opera Software/Opera Crypto Stable"),
        ("brave", "~/AppData/Local/BraveSoftware/Brave-Browser/User Data"),
        ("vivaldi", "~/AppData/Local/Vivaldi/User Data"),
        ("edge", "~/AppData/Local/Microsoft/Edge/User Data"),
        ("chrome", "~/AppData/Local/Google/Chrome/User Data"),
        ("chromium", "~/AppData/Local/Chromium/User Data"),
        ("whale", "~/AppData/Local/Naver/Naver Whale/User Data"),
    ],
    "darwin": [
        ("firefox", "~/Library/Application Support/Firefox"),
        ("safari", "~/Library/Safari"),
        ("opera", "~/Library/Application Support/com.operasoftware.OperaGX"),
        ("opera", "~/Library/Application Support/com.operasoftware.Opera"),
        ("brave", "~/Library/Application Support/BraveSoftware/Brave-Browser"),
        ("vivaldi", "~/Library/Application Support/Vivaldi"),
        ("edge", "~/Library/Application Support/Microsoft Edge"),
        ("chrome", "~/Library/Application Support/Google/Chrome"),
        ("chromium", "~/Library/Application Support/Chromium"),
    ],
    "linux": [
        ("firefox", "~/.mozilla/firefox"),
        ("librewolf", "~/.librewolf"),
        ("opera", "~/.config/opera-gx"),
        ("opera", "~/.config/opera"),
        ("brave", "~/.config/BraveSoftware/Brave-Browser"),
        ("vivaldi", "~/.config/vivaldi"),
        ("edge", "~/.config/microsoft-edge"),
        ("chrome", "~/.config/google-chrome"),
        ("chromium", "~/.config/chromium"),
    ],
}

# How each install is labelled in the log, keyed by a folder-name fragment.
_BROWSER_LABELS = {"opera gx": "Opera GX", "opera crypto": "Opera Crypto",
                   "opera stable": "Opera", "librewolf": "LibreWolf"}


def app_dir():
    """The folder the app actually lives in, frozen or not."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def download_root(base=None):
    return Path(base).expanduser() if base else app_dir() / ROOT_NAME


def download_dir(source, base=None):
    """Per-source folder, created on demand. source: a key of SOURCES."""
    folder = download_root(base) / SOURCES[source]
    folder.mkdir(parents=True, exist_ok=True)
    return folder


COOKIE_FAILURE = re.compile(
    r"could not copy .*cookie database|could not find .*cookies database|"
    r"failed to decrypt|unable to (?:read|open) .*cookie", re.I)


# "Get cookies.txt LOCALLY" exports as <domain>_cookies.txt; other extensions
# use cookies.txt or cookies-<site>.txt. Match the shape, not a fixed list.
COOKIE_FILE_GLOBS = ("cookies.txt", "*cookies*.txt")

# Each site keeps its own folder. One shared file meant an Instagram export could
# be offered to YouTube, where it can only fail; separate folders mean a site's
# cookies cannot reach another site even by accident.
COOKIE_DIR_NAME = "cookies"
SITE_DOMAINS = {"youtube": "youtube.com", "instagram": "instagram.com",
                "tiktok": "tiktok.com", "spotify": "spotify.com"}
# The cookie that means "signed in". Anything less is just a visitor.
SITE_SESSION_COOKIES = {
    "youtube": ("SID", "__Secure-3PSID", "SAPISID", "__Secure-1PSID"),
    "instagram": ("sessionid",),
    "tiktok": ("sessionid", "sessionid_ss"),
    "spotify": ("sp_dc", "sp_key"),
}
REGISTRY_NAME = "registry.json"


def cookie_dir(site=None):
    """Where the app keeps the jars it writes. One folder per site."""
    folder = app_dir() / COOKIE_DIR_NAME
    if site:
        folder = folder / site
    folder.mkdir(parents=True, exist_ok=True)
    return folder


def site_for_domain(domain):
    """Which site a domain belongs to, or None."""
    for site, site_domain in SITE_DOMAINS.items():
        if domain and site_domain in domain:
            return site
    return None


def jar_summary(path, site=None):
    """What is in a jar: (cookie count, has a session cookie, earliest expiry)."""
    names, count, expiry = set(), 0, None
    try:
        for line in Path(path).read_text("utf-8", "replace").splitlines():
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 7:
                continue
            count += 1
            names.add(parts[5])
            with contextlib.suppress(ValueError):
                stamp = int(parts[4])
                if stamp > 0 and (expiry is None or stamp < expiry):
                    expiry = stamp
    except OSError:
        return 0, False, None
    wanted = SITE_SESSION_COOKIES.get(site or "", ())
    return count, any(name in names for name in wanted), expiry


def registry_path():
    return cookie_dir() / REGISTRY_NAME


def read_registry():
    """What the app knows about the jars it has written."""
    try:
        return json.loads(registry_path().read_text("utf-8"))
    except Exception:
        return {}


def record_jar(site, path, source):
    """Remember a jar, so its state can be shown and its age judged later."""
    count, has_session, expiry = jar_summary(path, site)
    registry = read_registry()
    registry[site] = {
        "jar": str(path),
        "source": source,
        "cookies": count,
        "signed_in": has_session,
        "saved_at": int(time.time()),
        "expires_at": expiry,
    }
    with contextlib.suppress(OSError):
        registry_path().write_text(json.dumps(registry, indent=2), encoding="utf-8")
    return registry[site]


def registry_entry(site):
    """The recorded jar for a site, dropped if the file is gone or expired."""
    entry = read_registry().get(site)
    if not entry:
        return None
    if not Path(entry["jar"]).is_file():
        return None
    if entry.get("expires_at") and entry["expires_at"] < time.time():
        return None
    return entry


def cookie_files(domain=None):
    """Hand-exported cookie jars, newest first.

    Extensions drop these in Downloads under whatever name they like, and people
    leave them there, so the obvious places are searched rather than demanding
    an exact filename. With a domain given, only jars that actually carry that
    site are returned — a YouTube jar is no use to Instagram and vice versa.
    """
    site = site_for_domain(domain) if domain else None
    # The site's own folder first, then the shared places people drop exports.
    # Another site's folder is never searched, so its jars cannot leak across.
    folders = [cookie_dir(site)] if site else [cookie_dir()]
    folders += [app_dir(), download_root(), Path.home() / "Downloads"]

    seen, found = set(), []
    for folder in folders:
        for pattern in COOKIE_FILE_GLOBS:
            for path in sorted(folder.glob(pattern)):
                key = str(path).lower()
                if key in seen or not path.is_file():
                    continue
                seen.add(key)
                if domain and not jar_has_domain(path, domain):
                    continue
                found.append(path)
    return sorted(found, key=lambda p: p.stat().st_mtime, reverse=True)


def jar_has_domain(path, domain):
    """Does this Netscape jar hold cookies for the given site?"""
    try:
        for line in path.read_text("utf-8", "replace").splitlines():
            if line.strip() and not line.lstrip().startswith("#"):
                if domain in line.split("\t", 1)[0]:
                    return True
    except OSError:
        return False
    return False


def cookie_candidates(domain=None):
    """Every place cookies could come from, best first, as ('file'|'browser', value).

    A browser's `Cookies` file is a SQLite database, NOT the Netscape cookies.txt
    that yt-dlp's `cookiefile` expects — handing one to `cookiefile` fails or,
    worse, silently reads nothing. Only a real cookies.txt goes down that path;
    browsers go through `cookiesfrombrowser`, which knows how to read the DB.

    A list, not a single pick, because "installed" does not mean "readable": a
    Chromium browser that is currently running holds a lock on its cookie DB
    (yt-dlp #7271), and Chrome 127+ encrypts it app-bound so DPAPI cannot open it
    at all (yt-dlp #10927). Exported files come first for exactly that reason —
    they are the only source nothing else can take away.
    """
    found = [("file", str(path)) for path in cookie_files(domain)]
    for name, profile in _BROWSER_DIRS.get(sys.platform, _BROWSER_DIRS["linux"]):
        path = os.path.expanduser(profile)
        if os.path.isdir(path):
            found.append(("browser", name, path))
    return found


def candidate_label(candidate):
    """What to call this cookie source in a message a person reads."""
    if candidate[0] == "file":
        return os.path.basename(candidate[1])
    path = (candidate[2] if len(candidate) > 2 else "").lower()
    for fragment, label in _BROWSER_LABELS.items():
        if fragment in path:
            return label
    return candidate[1].title()


def locked_by_browser(candidate):
    """True when a running browser holds this profile's cookie database.

    Chromium keeps that file open exclusively, so no amount of retrying or
    permission juggling gets at it — the browser has to close first.
    """
    if candidate[0] != "browser" or len(candidate) < 3:
        return False
    root = Path(candidate[2])
    for db in (root / "Default" / "Network" / "Cookies", root / "Default" / "Cookies",
               root / "Network" / "Cookies", root / "Cookies"):
        if db.is_file():
            try:
                with open(db, "rb"):
                    return False
            except PermissionError:
                return True
            except OSError:
                return False
    return False


def cookie_source():
    """The first candidate, or None. For display and for one-shot callers."""
    found = cookie_candidates()
    return found[0] if found else None


def ytdlp_cookie_opts(candidate=None):
    """A candidate as yt-dlp Python options."""
    candidate = candidate or cookie_source()
    if not candidate:
        return {}
    if candidate[0] == "file":
        return {"cookiefile": candidate[1]}
    profile = candidate[2] if len(candidate) > 2 else None
    return {"cookiesfrombrowser": (candidate[1], profile)}


def ytdlp_cookie_args(candidate=None):
    """A candidate as yt-dlp command-line arguments."""
    candidate = candidate or cookie_source()
    if not candidate:
        return []
    if candidate[0] == "file":
        return ["--cookies", candidate[1]]
    spec = candidate[1]
    if len(candidate) > 2:
        # yt-dlp only knows each browser's default folder; Opera GX and friends
        # are somewhere else entirely, so the profile is named outright.
        spec = f"{spec}:{candidate[2]}"
    return ["--cookies-from-browser", spec]


if __name__ == "__main__":
    import tempfile

    with tempfile.TemporaryDirectory() as tmp:
        got = {name: download_dir(name, tmp) for name in SOURCES}
        assert all(p.is_dir() for p in got.values()), got
        assert len({str(p) for p in got.values()}) == len(SOURCES), got
        assert got["instagram"].parent == Path(tmp), got["instagram"]
    assert download_root().name == ROOT_NAME
    assert download_root().parent == app_dir()

    # Whatever the machine has, the two renderings must agree — and a browser must
    # never be handed to cookiefile.
    found, args, opts = cookie_source(), ytdlp_cookie_args(), ytdlp_cookie_opts()
    if found is None:
        assert args == [] and opts == {}, (args, opts)
    elif found[0] == "file":
        assert args == ["--cookies", found[1]] and opts == {"cookiefile": found[1]}
    else:
        assert args == ["--cookies-from-browser", f"{found[1]}:{found[2]}"], args
        assert opts == {"cookiesfrombrowser": (found[1], found[2])}, opts
    assert cookie_candidates()[:1] == ([] if found is None else [found])

    # A profile yt-dlp could never find on its own must still be addressable.
    gx = ("browser", "opera", r"C:\Users\x\AppData\Roaming\Opera Software\Opera GX Stable")
    assert ytdlp_cookie_args(gx) == ["--cookies-from-browser", f"opera:{gx[2]}"]
    assert candidate_label(gx) == "Opera GX", candidate_label(gx)
    assert candidate_label(("browser", "firefox", "/x/firefox")) == "Firefox"

    # A locked Chromium DB must be recognised as "try the next candidate", and an
    # ordinary download failure must not be.
    assert COOKIE_FAILURE.search("ERROR: Could not copy Chrome cookie database. See ...")
    assert COOKIE_FAILURE.search("ERROR: could not find firefox cookies database in ...")
    assert not COOKIE_FAILURE.search("ERROR: Restricted Video: Sign in to confirm your age")
    print("ok", cookie_candidates())
