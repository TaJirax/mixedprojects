#!/usr/bin/env python3
"""
Spotify Downloader - Modern Edition - Performance Optimized
- V2RayN/SOCKS5 proxy support
- Built-in local HTTP bridge for SOCKS5 proxies
- Optimized download speed matching CLI performance
"""

import os
import re
import sys
import json
import time
import queue
import contextlib
import shutil
import threading
import subprocess
import webbrowser
import zipfile
import urllib.request
import socket
import socketserver
import select
import tempfile
import ipaddress
from urllib.parse import quote, unquote, urlsplit
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

from blueknight_paths import (
    COOKIE_FAILURE, candidate_label, cookie_candidates, cookie_dir, download_dir,
    download_root, locked_by_browser, ytdlp_cookie_args)

import webview


APP_NAME = "Blue Knight Downloader"
APP_VERSION = "6.1"
CREATOR = "Blue Knight"
TELEGRAM_URL = "https://t.me/BlueKnight_Net"

SPOTDL_VERSION = "4.5.2"
SPOTDL_DOWNLOAD_URLS = (
    f"https://github.com/spotDL/spotify-downloader/releases/download/v{SPOTDL_VERSION}/spotdl-{SPOTDL_VERSION}-win32.exe",
    f"https://sourceforge.net/projects/spotdl.mirror/files/v{SPOTDL_VERSION}/spotdl-{SPOTDL_VERSION}-win32.exe/download",
)
FFMPEG_DOWNLOAD_URLS = (
    "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
    "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
)

YTDLP_VERSION = "2026.07.04"
# Independent hosts, tried in order. SourceForge is the escape hatch for
# networks that block GitHub release downloads.
YTDLP_DOWNLOAD_URLS = (
    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
    f"https://github.com/yt-dlp/yt-dlp/releases/download/{YTDLP_VERSION}/yt-dlp.exe",
    f"https://sourceforge.net/projects/yt-dlp.mirror/files/{YTDLP_VERSION}/yt-dlp.exe/download",
)

# Every downloadable tool is described once, so finding, downloading and
# updating are one code path instead of one per tool.
TOOLS = {
    "spotdl": {
        "label": "spotDL", "exe": "spotdl.exe", "urls": SPOTDL_DOWNLOAD_URLS,
        "api": "https://api.github.com/repos/spotDL/spotify-downloader/releases/latest",
    },
    "yt-dlp": {
        "label": "yt-dlp", "exe": "yt-dlp.exe", "urls": YTDLP_DOWNLOAD_URLS,
        "api": "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
    },
}

# yt-dlp format specs shared by the YouTube and TikTok pages.
MEDIA_FORMATS = {
    "best": "bv*+ba/b",
    "1080": "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
    "720": "bestvideo[height<=720]+bestaudio/best[height<=720]",
    "480": "bestvideo[height<=480]+bestaudio/best[height<=480]",
}
MEDIA_PATTERNS = {
    "youtube": re.compile(r"https?://(?:[\w-]+\.)*(?:youtube\.com|youtu\.be)/", re.I),
    "tiktok": re.compile(r"https?://(?:[\w-]+\.)*tiktok\.com/", re.I),
    "instagram": re.compile(r"https?://(?:[\w-]+\.)*instagram\.com/", re.I),
}
# Sources that will not serve anything useful to a logged-out client.
COOKIE_SOURCES = {"instagram"}
# Sources that usually work logged out, but sometimes demand a session. They get
# cookies only after asking for them, so the normal path stays cookie-free.
COOKIE_ON_DEMAND = {"youtube", "tiktok"}
# YouTube is rolling out "PO tokens", and a client that needs one cannot serve a
# download without it. Per yt-dlp's PO Token Guide the exceptions are android_vr,
# web_embedded and tv — so those are the ones worth retrying as. tv_simply,
# web_safari and mweb all need a token, which is why they answered the earlier
# attempts with "Requested format is not available" rather than a video.
YT_FALLBACK_CLIENTS = "android_vr,web_embedded,tv,default"
SIGNIN_DEMANDED = re.compile(
    r"not a bot|sign ?in to confirm|login required|account.*cookies|"
    r"this video is only available|use --cookies", re.I)
# yt-dlp loads the cookie jar before it touches the network, so a bogus URL is
# enough to find out whether a jar is readable — no request, no rate limit.
COOKIE_PROBE_URL = "blueknightprobe://cookies"
# The domain whose session makes a jar worth using, per source.
COOKIE_DOMAINS = {"instagram": "instagram.com", "youtube": "youtube.com", "tiktok": "tiktok.com"}

# Performance optimization: Batch UI updates
UI_UPDATE_INTERVAL = 0.1
LOG_BATCH_SIZE = 10


def asset_path(name):
    """Return a bundled/source asset path, or None if missing."""
    roots = [Path(getattr(sys, "_MEIPASS", "")) / "assets",
             Path(__file__).resolve().parent.parent / "assets",
             Path(__file__).resolve().parent / "assets"]
    for root in roots:
        candidate = root / name
        if candidate.is_file():
            return str(candidate)
    return None


def web_index():
    """Path to the glass UI document."""
    for root in (Path(getattr(sys, "_MEIPASS", "")), Path(__file__).resolve().parent):
        candidate = root / "web" / "index.html"
        if candidate.is_file():
            return str(candidate)
    raise FileNotFoundError("web/index.html is missing")


# Where a sign-in window starts, and which pages to read the session from.
#
# The browser-extension guides say to land on robots.txt, and for an extension
# that is right. Here it is not: WebView2 answers get_cookies() for the document
# it currently holds, and a text file yields an empty list — measured, 0 cookies
# on robots.txt against 5 on the homepage. Real pages only, and more than one,
# because a YouTube session is split across youtube.com and google.com.
SIGNIN_PAGES = {
    "youtube": ("https://accounts.google.com/ServiceLogin?service=youtube",
                ("https://www.youtube.com/", "https://myaccount.google.com/"),
                "www.youtube.com"),
    "instagram": ("https://www.instagram.com/accounts/login/",
                  ("https://www.instagram.com/",),
                  "www.instagram.com"),
    "tiktok": ("https://www.tiktok.com/login",
               ("https://www.tiktok.com/",),
               "www.tiktok.com"),
}


def write_netscape_jar(cookies, path):
    """Save browser cookies as the Netscape file yt-dlp reads. Returns the count."""
    lines = ["# Netscape HTTP Cookie File",
             "# Written by Blue Knight Downloader. Editing this is not needed.", ""]
    written = 0
    for cookie in cookies:
        for name, morsel in cookie.items():
            domain = morsel["domain"] or ""
            if not domain:
                continue
            expires = morsel["expires"]
            if expires:
                with contextlib.suppress(Exception):
                    expires = int(time.mktime(
                        time.strptime(expires, "%a, %d %b %Y %H:%M:%S %Z")))
            lines.append("\t".join([
                domain,
                "TRUE" if domain.startswith(".") else "FALSE",
                morsel["path"] or "/",
                "TRUE" if morsel["secure"] else "FALSE",
                str(expires or int(time.time()) + 60 * 60 * 24 * 180),
                name,
                morsel.value,
            ]))
            written += 1
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return written


def read_clipboard():
    """Windows clipboard text, empty when there is nothing to paste."""
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-Command", "Get-Clipboard -Raw"],
            capture_output=True, text=True, timeout=6,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        return out.stdout.strip()
    except Exception:
        return ""


class Var:
    """Minimal observable value, standing in for the old Tk variables."""

    def __init__(self, value=""):
        self._value = value

    def get(self):
        return self._value

    def set(self, value):
        self._value = value


def bundled_tool(name):
    """Find a packaged tool in both PyInstaller one-file and one-folder layouts."""
    roots = []
    bundle_root = getattr(sys, "_MEIPASS", None)
    if bundle_root:
        roots.append(Path(bundle_root))
    if getattr(sys, "frozen", False):
        executable_dir = Path(sys.executable).resolve().parent
        roots.extend((executable_dir, executable_dir / "_internal"))
    roots.append(Path(__file__).resolve().parent)

    seen = set()
    for root in roots:
        for candidate in (root / "tools" / name, root / name):
            key = str(candidate)
            if key not in seen and candidate.is_file():
                return str(candidate)
            seen.add(key)
    return None


def normalize_proxy_url(value, proxy_type="http"):
    """Validate a proxy URL and add the selected scheme when it is omitted."""
    value = value.strip()
    if not value:
        raise ValueError("Proxy URL is empty")
    if "://" not in value:
        value = f"{proxy_type}://{value}"

    parsed = urlsplit(value)
    scheme = parsed.scheme.lower()
    if scheme not in {"http", "https", "socks5", "socks5h"}:
        raise ValueError("Proxy scheme must be http, https, socks5, or socks5h")
    if not parsed.hostname:
        raise ValueError("Proxy URL must include a host")
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("Proxy URL has an invalid port") from exc
    if port is None:
        port = 1080 if scheme.startswith("socks5") else 8080

    host = parsed.hostname
    if ":" in host:
        host = f"[{host}]"
    auth = ""
    if parsed.username is not None:
        auth = quote(unquote(parsed.username), safe="")
        if parsed.password is not None:
            auth += ":" + quote(unquote(parsed.password), safe="")
        auth += "@"
    return f"{scheme}://{auth}{host}:{port}"


def display_proxy_url(value):
    """Hide proxy credentials before displaying the URL in the activity log."""
    parsed = urlsplit(value)
    host = parsed.hostname or ""
    if ":" in host:
        host = f"[{host}]"
    auth = "***@" if parsed.username is not None else ""
    return f"{parsed.scheme}://{auth}{host}:{parsed.port}"


def _recv_exact(sock, size):
    data = bytearray()
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("Proxy closed the connection")
        data.extend(chunk)
    return bytes(data)


class Socks5HttpBridge:
    """Expose a local HTTP CONNECT proxy backed by a SOCKS5 proxy."""

    def __init__(self, proxy_url):
        parsed = urlsplit(normalize_proxy_url(proxy_url, "socks5"))
        if not parsed.scheme.startswith("socks5"):
            raise ValueError("The local bridge requires a SOCKS5 proxy")
        self.host = parsed.hostname
        self.port = parsed.port
        self.username = unquote(parsed.username) if parsed.username else None
        self.password = unquote(parsed.password) if parsed.password else ""
        self.server = None
        self.thread = None

    def _open_target(self, target_host, target_port):
        sock = socket.create_connection((self.host, self.port), timeout=15)
        methods = b"\x00\x02" if self.username is not None else b"\x00"
        sock.sendall(b"\x05" + bytes([len(methods)]) + methods)
        version, method = _recv_exact(sock, 2)
        if version != 5 or method == 0xFF:
            sock.close()
            raise ConnectionError("SOCKS5 proxy rejected authentication methods")
        if method == 2:
            if self.username is None:
                sock.close()
                raise ConnectionError("SOCKS5 proxy requires a username and password")
            user = self.username.encode("utf-8")
            password = self.password.encode("utf-8")
            if len(user) > 255 or len(password) > 255:
                sock.close()
                raise ValueError("SOCKS5 credentials are too long")
            sock.sendall(b"\x01" + bytes([len(user)]) + user + bytes([len(password)]) + password)
            if _recv_exact(sock, 2)[1] != 0:
                sock.close()
                raise ConnectionError("SOCKS5 authentication failed")
        elif method != 0:
            sock.close()
            raise ConnectionError("SOCKS5 proxy selected an unsupported authentication method")

        try:
            address = ipaddress.ip_address(target_host)
            atyp = 1 if address.version == 4 else 4
            encoded_host = address.packed
        except ValueError:
            encoded_host = target_host.encode("idna")
            if len(encoded_host) > 255:
                sock.close()
                raise ValueError("Target hostname is too long")
            atyp = 3
            encoded_host = bytes([len(encoded_host)]) + encoded_host

        sock.sendall(b"\x05\x01\x00" + bytes([atyp]) + encoded_host + target_port.to_bytes(2, "big"))
        version, result, _, bound_type = _recv_exact(sock, 4)
        if version != 5 or result != 0:
            sock.close()
            raise ConnectionError(f"SOCKS5 connection failed (code {result})")
        if bound_type == 1:
            _recv_exact(sock, 4)
        elif bound_type == 4:
            _recv_exact(sock, 16)
        elif bound_type == 3:
            _recv_exact(sock, _recv_exact(sock, 1)[0])
        else:
            sock.close()
            raise ConnectionError("SOCKS5 proxy returned an invalid address")
        _recv_exact(sock, 2)
        sock.settimeout(None)
        return sock

    @staticmethod
    def _split_authority(authority, default_port):
        parsed = urlsplit("//" + authority)
        if not parsed.hostname:
            raise ValueError("Invalid proxy request target")
        return parsed.hostname, parsed.port or default_port

    @staticmethod
    def _relay(client, upstream):
        sockets = (client, upstream)
        while True:
            readable, _, _ = select.select(sockets, [], [], 60)
            if not readable:
                return
            for source in readable:
                data = source.recv(65536)
                if not data:
                    return
                (upstream if source is client else client).sendall(data)

    def start(self):
        bridge = self

        class Handler(socketserver.BaseRequestHandler):
            def handle(self):
                self.request.settimeout(15)
                header = bytearray()
                while b"\r\n\r\n" not in header:
                    chunk = self.request.recv(4096)
                    if not chunk:
                        return
                    header.extend(chunk)
                    if len(header) > 65536:
                        raise ValueError("HTTP proxy request headers are too large")

                head, body = bytes(header).split(b"\r\n\r\n", 1)
                lines = head.split(b"\r\n")
                method, target, version = lines[0].decode("latin-1").split(" ", 2)
                if method.upper() == "CONNECT":
                    host, port = bridge._split_authority(target, 443)
                    upstream = bridge._open_target(host, port)
                    self.request.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
                else:
                    parsed = urlsplit(target)
                    if not parsed.hostname:
                        raise ValueError("HTTP proxy requests must use an absolute URL")
                    host = parsed.hostname
                    port = parsed.port or (443 if parsed.scheme == "https" else 80)
                    upstream = bridge._open_target(host, port)
                    path = parsed.path or "/"
                    if parsed.query:
                        path += "?" + parsed.query
                    clean_headers = [line for line in lines[1:] if not line.lower().startswith(b"proxy-connection:")]
                    request_head = f"{method} {path} {version}\r\n".encode("latin-1")
                    upstream.sendall(request_head + b"\r\n".join(clean_headers) + b"\r\n\r\n" + body)
                try:
                    bridge._relay(self.request, upstream)
                finally:
                    upstream.close()

        class Server(socketserver.ThreadingTCPServer):
            allow_reuse_address = True
            daemon_threads = True

            def handle_error(self, request, client_address):
                pass

        self.server = Server(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        return f"http://127.0.0.1:{self.server.server_address[1]}"

    def close(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            self.server = None


class SpotifyDownloader:
    """Download engine. The window talks to it through the js_api bridge."""

    def __init__(self):
        self.window = None

        # Performance optimization: Batching
        self.ui_queue = queue.Queue()
        self.log_batch = []
        self.last_ui_update = 0
        self.process = None
        self.is_downloading = False
        self.is_converting = False
        self.closing = False
        self.setup_running = False
        self.executor = ThreadPoolExecutor(max_workers=3)

        # Store FFmpeg in app data directory
        local_app_data = os.environ.get("LOCALAPPDATA")
        self.app_data = (Path(local_app_data) if local_app_data else Path.home() / "AppData" / "Local") / "SpotifyDownloader"
        self.ffmpeg_dir = self.app_data / "ffmpeg"
        self.ffmpeg_exe = self.ffmpeg_dir / "bin" / "ffmpeg.exe"
        self.tools_dir = self.app_data / "tools"
        self.spotdl_exe = self.tools_dir / "spotdl.exe"

        # Proxy settings with proper defaults for v2rayN
        self.download_format = Var("mp3")
        self.quality = Var("320")
        self.use_proxy = Var(False)
        self.proxy_type = Var("http")  # http or socks5
        self.proxy_url = Var("http://127.0.0.1:10809")  # v2rayN HTTP default
        self.save_folder = Var(str(download_root()))
        self.status = Var("Preparing")

        self.spotdl_cmd = None
        self.ffmpeg_cmd = None
        self.ytdlp_cmd = None
        self.updating = False

        # Pre-compile regex patterns for performance
        self._re_url = re.compile(r"https?://open\.spotify\.com/(track|album|playlist)/", re.I)
        self._re_found_songs = re.compile(r"Found\s+(\d+)\s+songs", re.I)
        self._re_downloading = re.compile(r"Downloading", re.I)
        self._re_downloaded = re.compile(r"Downloaded", re.I)
        self._re_brackets = re.compile(r"\[.*?\]")
        self._re_error = re.compile(r"(ERROR|Error|error)")
        self._re_warning = re.compile(r"(WARNING|Warning|warning)")
        self._re_request_error = re.compile(r"(RequestError|Failed to complete request)")
        self._re_ytdlp_item = re.compile(r"Downloading item (\d+) of (\d+)", re.I)

        self.hello_sent = False
        self._session_cookies = set()
        self._login_window = None
        self._login_kind = None
        self._media_done = 0
        self._media_total = None

    # ------------------------------------------------------------------
    # State the page reads through the bridge
    # ------------------------------------------------------------------
    def snapshot(self):
        return {
            "format": self.download_format.get(),
            "quality": self.quality.get(),
            "proxy_enabled": bool(self.use_proxy.get()),
            "proxy_type": self.proxy_type.get(),
            "proxy_url": self.proxy_url.get(),
            "folder": self.save_folder.get(),
        }

    def begin(self):
        """First contact from the page: greet, then start the dependency check."""
        if self.hello_sent:
            return
        self.hello_sent = True
        self.log("Welcome. Paste a Spotify link to begin.", "info")
        self.log("Behind a filter? Set a proxy in Connection settings.", "info")
        threading.Thread(target=self.start_automatic_setup, daemon=True).start()

    def drain(self):
        """Drain queued events for the page. Called on a short interval."""
        events = []
        while True:
            try:
                action, args = self.ui_queue.get_nowait()
            except queue.Empty:
                break
            if action == "batch_log":
                events.append(["log", [list(pair) for pair in args]])
            elif action == "finished":
                self.download_finished()
                events.append(["finished", []])
            else:
                events.append([action, list(args)])
        return events

    def set_option(self, name, value):
        options = {
            "format": self.download_format,
            "quality": self.quality,
            "proxy_type": self.proxy_type,
            "proxy_url": self.proxy_url,
            "proxy_enabled": self.use_proxy,
            "folder": self.save_folder,
        }
        var = options.get(name)
        if var is not None:
            var.set(value)

    def notify(self, message, kind="info"):
        self.ui("toast", message, kind)

    def open_folder(self):
        folder = Path(self.save_folder.get()).expanduser()
        folder.mkdir(parents=True, exist_ok=True)
        try:
            if sys.platform == "win32":
                os.startfile(str(folder))
            elif sys.platform == "darwin":
                subprocess.Popen(["open", str(folder)])
            else:
                subprocess.Popen(["xdg-open", str(folder)])
        except Exception as exc:
            self.notify(f"Could not open the folder: {exc}", "err")

    # ------------------------------------------------------------------
    # Proxy helpers
    # ------------------------------------------------------------------
    def set_proxy_preset(self, proxy_type, url):
        """Set proxy to a preset configuration"""
        self.proxy_type.set(proxy_type)
        self.proxy_url.set(url)
        self.use_proxy.set(True)
        self.log(f"Proxy preset set: {proxy_type.upper()} - {url}", "info")
        
        if proxy_type == "socks5":
            self.log("SOCKS5 will use the built-in local bridge.", "info")
        else:
            self.log("HTTP proxy ready for spotDL", "success")

    def test_proxy(self):
        """Make an HTTPS request through the configured proxy."""
        try:
            proxy = normalize_proxy_url(self.proxy_url.get(), self.proxy_type.get())
        except ValueError as exc:
            self.notify(str(exc), "err")
            return

        self.log(f"Testing proxy: {display_proxy_url(proxy)}...", "info")

        def do_test():
            bridge = None
            try:
                effective_proxy = proxy
                if urlsplit(proxy).scheme.startswith("socks5"):
                    bridge = Socks5HttpBridge(proxy)
                    effective_proxy = bridge.start()
                handler = urllib.request.ProxyHandler({"http": effective_proxy, "https": effective_proxy})
                opener = urllib.request.build_opener(handler)
                request = urllib.request.Request(
                    "https://api.ipify.org?format=json",
                    headers={"User-Agent": f"{APP_NAME}/{APP_VERSION}"},
                )
                with opener.open(request, timeout=15) as response:
                    result = response.read().decode("utf-8", "replace")
                self.log(f"Proxy test succeeded: {result[:120]}", "success")
                self.notify("Proxy connection works.", "ok")
            except Exception as exc:
                detail = str(exc)[:200]
                self.log(f"Proxy test failed: {detail}", "error")
                self.notify(
                    f"Proxy test failed: {detail}\n\n"
                    "Check that the proxy app is running and that the type and port match its local settings.",
                    "err",
                )
            finally:
                if bridge:
                    bridge.close()

        threading.Thread(target=do_test, daemon=True).start()

    # ------------------------------------------------------------------
    # Setup (unchanged)
    # ------------------------------------------------------------------
    def start_automatic_setup(self):
        if self.setup_running:
            return
        self.setup_running = True
        threading.Thread(target=self.setup_dependencies, daemon=True).start()

    def setup_dependencies(self):
        try:
            self.ui("setup", "Checking FFmpeg...", "This is required for audio conversion")
            self.ffmpeg_cmd = self.get_or_download_ffmpeg()
            
            if not self.ffmpeg_cmd:
                raise RuntimeError("Failed to setup FFmpeg")

            self.ui("setup", "Checking spotDL...", "This downloads Spotify tracks")
            self.spotdl_cmd = self.get_or_download_tool("spotdl")
            if not self.spotdl_cmd:
                raise RuntimeError("spotDL was installed, but its command could not be located.")

            self.ui("setup", "Checking yt-dlp...", "This downloads YouTube, TikTok and Instagram media")
            self.ytdlp_cmd = self.get_or_download_tool("yt-dlp")
            if not self.ytdlp_cmd:
                raise RuntimeError("yt-dlp was installed, but its command could not be located.")

            self.ui("setup_done", "Ready to download", "Everything is installed and up to date.")
            self.ui("components", self.component_summary())
            self.log("✓ FFmpeg: " + str(self.ffmpeg_cmd), "success")
            self.log("✓ spotDL: " + str(self.spotdl_cmd), "success")
            self.log("✓ yt-dlp: " + str(self.ytdlp_cmd), "success")
            self.log("💡 Use 'V2RayN HTTP' preset for proxy support", "info")

        except Exception as exc:
            self.ui("setup_error", "Automatic setup needs attention", str(exc))
        finally:
            self.setup_running = False

    @contextlib.contextmanager
    def _net_opener(self):
        """A urllib opener that honours the configured proxy.

        Fetching tools is exactly the traffic a restricted network blocks, so
        it goes through the same proxy the downloads themselves use.
        """
        bridge = None
        try:
            if self.use_proxy.get() and self.proxy_url.get().strip():
                proxy = normalize_proxy_url(self.proxy_url.get(), self.proxy_type.get())
                if urlsplit(proxy).scheme.startswith("socks5"):
                    bridge = Socks5HttpBridge(proxy)
                    proxy = bridge.start()
                yield urllib.request.build_opener(
                    urllib.request.ProxyHandler({"http": proxy, "https": proxy}))
            else:
                yield urllib.request.build_opener()
        finally:
            if bridge:
                bridge.close()

    def _download_with_fallbacks(self, urls, destination, label):
        """Download atomically, trying independent hosts in order."""
        destination.parent.mkdir(parents=True, exist_ok=True)
        partial = destination.with_suffix(destination.suffix + ".part")
        errors = []
        with self._net_opener() as opener:
            for attempt, url in enumerate(urls, 1):
                try:
                    partial.unlink(missing_ok=True)
                    host = urlsplit(url).hostname or "download server"
                    self.ui("setup", f"Downloading {label}...", f"Trying {host} ({attempt}/{len(urls)})")
                    self.log(f"Downloading {label} from {host} ({attempt}/{len(urls)})…", "info")
                    request = urllib.request.Request(url, headers={"User-Agent": f"{APP_NAME}/{APP_VERSION}"})
                    with opener.open(request, timeout=45) as response, partial.open("wb") as output:
                        shutil.copyfileobj(response, output, length=1024 * 1024)
                    if partial.stat().st_size == 0:
                        raise RuntimeError("server returned an empty file")
                    os.replace(partial, destination)
                    return destination
                except Exception as exc:
                    errors.append(f"{urlsplit(url).hostname}: {exc}")
                    self.log(f"{label} source {attempt} failed; trying the next source.", "warning")
        partial.unlink(missing_ok=True)
        raise RuntimeError(f"All {label} download sources failed: " + " | ".join(errors))

    def latest_version(self, name):
        """Published version for a tool, or None when the check cannot run."""
        api = TOOLS[name].get("api")
        if not api:
            return None
        try:
            with self._net_opener() as opener:
                request = urllib.request.Request(api, headers={
                    "User-Agent": f"{APP_NAME}/{APP_VERSION}",
                    "Accept": "application/vnd.github+json",
                })
                with opener.open(request, timeout=20) as response:
                    tag = json.loads(response.read().decode("utf-8", "replace")).get("tag_name", "")
            return tag.strip().lstrip("vV") or None
        except Exception:
            return None

    @staticmethod
    def _is_windows_executable(path, minimum_size=1024 * 1024):
        try:
            path = Path(path)
            with path.open("rb") as handle:
                return path.stat().st_size >= minimum_size and handle.read(2) == b"MZ"
        except OSError:
            return False

    def find_tool(self, name):
        """Locate a tool, newest copy first.

        A downloaded copy in app data only exists after an update, so it always
        wins over the copy shipped inside the bundle.
        """
        spec = TOOLS[name]
        downloaded = self.tools_dir / spec["exe"]
        if self._is_windows_executable(downloaded):
            return str(downloaded)
        packaged = bundled_tool(spec["exe"])
        if packaged:
            return packaged
        return shutil.which(name)

    def get_or_download_tool(self, name, force=False):
        """Return a usable tool path, downloading from the mirrors when needed."""
        spec = TOOLS[name]
        if not force:
            existing = self.find_tool(name)
            if existing:
                return existing

        target = self.tools_dir / spec["exe"]
        candidate = target.with_name(spec["exe"] + ".new")
        self._download_with_fallbacks(spec["urls"], candidate, spec["label"])
        if not self._is_windows_executable(candidate):
            candidate.unlink(missing_ok=True)
            raise RuntimeError(f"The downloaded {spec['label']} file is not a valid Windows executable.")
        target.parent.mkdir(parents=True, exist_ok=True)
        os.replace(candidate, target)
        return str(target)

    def tool_version(self, path):
        try:
            result = subprocess.run(
                [path, "--version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, encoding="utf-8", errors="replace", timeout=45,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            return result.stdout.strip().splitlines()[-1].strip() if result.stdout.strip() else "unknown"
        except Exception:
            return "unknown"

    def update_tools(self):
        """Refresh spotDL and yt-dlp from their mirrors, then report per tool."""
        if self.updating or self.is_downloading:
            self.notify("Wait for the current job to finish.", "err")
            return
        self.updating = True
        threading.Thread(target=self._update_tools, daemon=True).start()

    def probe_cookie(self, candidate, domain=None):
        """Read this jar and see what is in it. Returns (ok, detail). Offline.

        Two separate things can be wrong, and only one of them looks like an
        error. The jar may be unreadable — a Chromium browser holds a lock while
        it runs, and Chrome 127+ encrypts app-bound so DPAPI never opens it. Or
        the jar reads perfectly and simply holds no session for the site, which
        yt-dlp reports much later as an empty response or a bot check. Asking
        yt-dlp to export the jar answers both, with a bogus URL and no request.
        """
        if candidate[0] == "file":
            # Already a Netscape jar: read it. Handing it to yt-dlp only to have
            # it written back out would mean two --cookies flags, and the second
            # would win — which is how an exported jar came back empty.
            return self._probe_cookie_file(Path(candidate[1]), domain)

        if not self.ytdlp_cmd:
            return False, "yt-dlp is not installed yet"

        jar = Path(tempfile.gettempdir()) / f"blueknight_cookies_{os.getpid()}.txt"
        jar.unlink(missing_ok=True)
        try:
            out = subprocess.run(
                [self.ytdlp_cmd, "--simulate", "--no-warnings",
                 *ytdlp_cookie_args(candidate), "--cookies", str(jar), COOKIE_PROBE_URL],
                capture_output=True, text=True, timeout=40,
                encoding="utf-8", errors="replace",
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            text = (out.stdout or "") + (out.stderr or "")
            failure = COOKIE_FAILURE.search(text)
            if failure:
                return False, failure.group(0)[:120]

            lines = []
            if jar.is_file():
                lines = [ln for ln in jar.read_text("utf-8", "replace").splitlines()
                         if ln.strip() and not ln.startswith("#")]
            if not lines:
                return False, "no cookies read"
            if domain and not any(domain in ln for ln in lines):
                return False, f"{len(lines)} cookies, none for {domain}"
            return True, (f"{len(lines)} cookies, {domain} session" if domain
                          else f"{len(lines)} cookies")
        except Exception as exc:
            return False, str(exc)[:120]
        finally:
            jar.unlink(missing_ok=True)

    @staticmethod
    def _probe_cookie_file(path, domain=None):
        """What is inside an exported cookies.txt."""
        try:
            lines = [ln for ln in path.read_text("utf-8", "replace").splitlines()
                     if ln.strip() and not ln.lstrip().startswith("#")]
        except Exception as exc:
            return False, str(exc)[:120]
        if not lines:
            return False, "the file is empty"
        if domain and not any(domain in ln.split("\t", 1)[0] for ln in lines):
            return False, f"{len(lines)} cookies, none for {domain}"
        return True, (f"{len(lines)} cookies, {domain} session" if domain
                      else f"{len(lines)} cookies")

    def usable_cookies(self, domain=None, require_session=False):
        """Cookie candidates that pass the probe, ones holding a session first.

        require_session drops jars that read fine but carry nothing for the
        site — retrying a signed-out jar against a sign-in demand is a loop.
        """
        good, readable = [], []
        self._session_cookies = set()
        for candidate in cookie_candidates(domain):
            ok, detail = self.probe_cookie(candidate, domain)
            if not ok and locked_by_browser(candidate):
                # yt-dlp calls every Chromium browser "Chrome" here, which reads
                # as nonsense when the browser in question is Opera GX.
                detail = "open right now — close it to use its cookies"
            self._batch_log(f"Cookies · {candidate_label(candidate)}: {detail}", "success" if ok else "warning")
            if ok:
                good.append(candidate)
                self._session_cookies.add(candidate)
            elif domain and detail.endswith(f"none for {domain}"):
                # Readable but signed out. Worth a try if nothing better exists.
                readable.append(candidate)
        self._flush_logs()
        return good if require_session else good + readable

    def _update_tools(self):
        self.ui("status", "Updating")
        self.ui("updating", True)
        changed, failed = [], []
        try:
            for name, spec in TOOLS.items():
                label = spec["label"]
                try:
                    path = self.find_tool(name)
                    current = self.tool_version(path) if path else None

                    if path:
                        # Never re-download what is already current: ask first,
                        # and only spend the bandwidth when there is a newer one.
                        self.log(f"Checking {label} (have {current})…", "info")
                        latest = self.latest_version(name)
                        if latest is None:
                            self.log(f"{label}: could not reach the update server. "
                                     f"Keeping {current}.", "warning")
                            self._remember_tool(name, path)
                            continue
                        if latest == current:
                            self.log(f"{label} is already current ({current}).", "success")
                            self._remember_tool(name, path)
                            continue
                        self.log(f"{label} {current} → {latest} available.", "info")
                    else:
                        self.log(f"{label} is missing. Installing…", "warning")

                    path = self.get_or_download_tool(name, force=True)
                    after = self.tool_version(path)
                    self._remember_tool(name, path)
                    changed.append(f"{label} {current or 'missing'} → {after}")
                    self.log(f"✓ {label} is now {after}", "success")
                except Exception as exc:
                    failed.append(f"{label}: {str(exc)[:160]}")
                    self.log(f"{label} update failed: {str(exc)[:200]}", "error")

            if not self.ffmpeg_cmd:
                try:
                    self.ffmpeg_cmd = self.get_or_download_ffmpeg()
                    changed.append("FFmpeg installed")
                except Exception as exc:
                    failed.append(f"FFmpeg: {str(exc)[:160]}")

            # Instagram needs a login, so a readable cookie jar is a component
            # like any other. Checked here because yt-dlp must exist first.
            self.log("Checking cookies for Instagram…", "info")
            working = self.usable_cookies(COOKIE_DOMAINS["instagram"])
            if working and working[0] in self._session_cookies:
                self.log(f"Instagram session found in {candidate_label(working[0])}.", "success")
            elif working:
                self.log(f"{candidate_label(working[0])} cookies are readable but hold no Instagram session — "
                         "log into Instagram there, or export a cookies.txt.", "warning")
            elif cookie_candidates(COOKIE_DOMAINS['instagram']):
                self.log("No cookie jar could be read. Close Edge and Chrome, use Firefox, or "
                         "drop a cookies.txt beside the app.", "warning")
            else:
                self.log("No cookie source found. Instagram needs a cookies.txt beside the app, "
                         "in your Downloads, or in the download folder.", "warning")

            self.ui("components", self.component_summary())
            if failed:
                self.notify(
                    "Some updates failed:\n" + "\n".join(failed)
                    + "\n\nThe versions you already have still work. On a restricted network, "
                      "turn on a proxy in Connection settings and try again.",
                    "err",
                )
            elif changed:
                self.notify("Updated:\n" + "\n".join(changed), "ok")
            else:
                self.notify("Everything is already up to date.", "ok")
        finally:
            self.updating = False
            self.ui("updating", False)
            self.status.set("Ready")
            self.ui("status", "Ready")

    def _remember_tool(self, name, path):
        if name == "spotdl":
            self.spotdl_cmd = path
        else:
            self.ytdlp_cmd = path

    def component_summary(self):
        return (f"spotDL: {self.spotdl_cmd}\n"
                f"yt-dlp: {self.ytdlp_cmd}\n"
                f"FFmpeg: {self.ffmpeg_cmd}")

    def get_or_download_ffmpeg(self):
        packaged_ffmpeg = bundled_tool("ffmpeg.exe")
        if packaged_ffmpeg:
            return packaged_ffmpeg

        if self._is_windows_executable(self.ffmpeg_exe):
            return str(self.ffmpeg_exe)
        
        system_ffmpeg = shutil.which("ffmpeg")
        if system_ffmpeg:
            return system_ffmpeg
        
        try:
            import imageio_ffmpeg
            binary = imageio_ffmpeg.get_ffmpeg_exe()
            if binary and Path(binary).exists():
                return binary
        except Exception:
            pass
        
        try:
            self.app_data.mkdir(parents=True, exist_ok=True)
            zip_path = self.app_data / "ffmpeg.zip"
            self._download_with_fallbacks(FFMPEG_DOWNLOAD_URLS, zip_path, "FFmpeg")
            
            self.ui("setup", "Extracting FFmpeg...", "Setting up audio converter")
            with zipfile.ZipFile(zip_path, "r") as archive:
                members = archive.namelist()
                ffmpeg_member = next((name for name in members if name.replace("\\", "/").lower().endswith("/bin/ffmpeg.exe")), None)
                ffprobe_member = next((name for name in members if name.replace("\\", "/").lower().endswith("/bin/ffprobe.exe")), None)
                if not ffmpeg_member:
                    raise RuntimeError("Could not find ffmpeg.exe in the downloaded archive")
                self.ffmpeg_exe.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(ffmpeg_member) as source, self.ffmpeg_exe.open("wb") as target:
                    shutil.copyfileobj(source, target)
                if ffprobe_member:
                    ffprobe_path = self.ffmpeg_dir / "bin" / "ffprobe.exe"
                    with archive.open(ffprobe_member) as source, ffprobe_path.open("wb") as target:
                        shutil.copyfileobj(source, target)
            zip_path.unlink(missing_ok=True)
            if not self._is_windows_executable(self.ffmpeg_exe):
                self.ffmpeg_exe.unlink(missing_ok=True)
                raise RuntimeError("The downloaded FFmpeg file is not a valid Windows executable")
            return str(self.ffmpeg_exe)
        except Exception as e:
            raise RuntimeError(f"FFmpeg setup failed: {e}")

    def find_spotdl(self):
        return self.find_tool("spotdl")

    # ------------------------------------------------------------------
    # OPTIMIZED DOWNLOAD - With HTTP and SOCKS5 proxy support
    # ------------------------------------------------------------------
    def start_download(self, url, fmt=None, quality=None):
        if fmt:
            self.download_format.set(fmt)
        if quality:
            self.quality.set(quality)

        if self.setup_running or not self.spotdl_cmd:
            self.notify("Still finishing setup. Try again in a moment.", "err")
            self.ui("finished")
            return
        if self.is_downloading:
            return

        url = (url or "").strip()
        if not self._re_url.search(url):
            self.notify("That is not a Spotify track, album, or playlist link.", "err")
            self.ui("finished")
            return

        try:
            output = download_dir("spotify", self.save_folder.get().strip())
        except Exception as exc:
            self.notify(f"Could not create the download folder.\n\n{exc}", "err")
            self.ui("finished")
            return

        self.is_downloading = True
        self.status.set("Downloading")
        options = {
            "format": self.download_format.get(),
            "quality": self.quality.get(),
            "proxy_enabled": self.use_proxy.get(),
            "proxy_url": self.proxy_url.get().strip(),
            "proxy_type": self.proxy_type.get(),
        }
        threading.Thread(
            target=self.download_with_spotdl,
            args=(url, output, options),
            daemon=True,
        ).start()

    def _prepare_proxy(self, proxy_url, proxy_type):
        """Return an HTTP proxy URL accepted by spotDL and an optional bridge."""
        proxy = normalize_proxy_url(proxy_url, proxy_type)
        if urlsplit(proxy).scheme.startswith("socks5"):
            bridge = Socks5HttpBridge(proxy)
            local_proxy = bridge.start()
            self._batch_log(f"SOCKS5 bridge listening at {local_proxy}", "info")
            return local_proxy, bridge
        return proxy, None

    def download_with_spotdl(self, url, output, options):
        """Optimized download with proxy support"""
        fmt = options["format"]
        output_template = output / "{artists} - {title}.{output-ext}"
        cmd = [self.spotdl_cmd, "download", url, "--output", str(output_template)]

        # Format settings
        if fmt == "mp3":
            cmd += ["--format", "mp3", "--bitrate", f"{options['quality']}k"]
        elif fmt == "flac":
            cmd += ["--format", "flac"]

        proxy_enabled = options["proxy_enabled"]
        proxy_url = options["proxy_url"]
        proxy_type = options["proxy_type"]
        bridge = None
        env = os.environ.copy()

        if proxy_enabled and proxy_url:
            try:
                effective_proxy, bridge = self._prepare_proxy(proxy_url, proxy_type)
            except Exception as exc:
                self.log(f"Proxy setup failed: {exc}", "error")
                self.ui("failure", f"Proxy setup failed:\n\n{exc}")
                self.ui("finished")
                return
            cmd += ["--proxy", effective_proxy]
            for name in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"):
                env[name] = effective_proxy
            self._batch_log(
                f"Using proxy: {display_proxy_url(normalize_proxy_url(proxy_url, proxy_type))}",
                "info",
            )
        else:
            self._batch_log("No proxy configured - using direct connection", "info")

        # FFmpeg setup (minimal)
        if self.ffmpeg_cmd:
            ffmpeg_dir = str(Path(self.ffmpeg_cmd).parent)
            if ffmpeg_dir not in env.get("PATH", ""):
                env["PATH"] = ffmpeg_dir + os.pathsep + env.get("PATH", "")
            env["FFMPEG_PATH"] = self.ffmpeg_cmd
            cmd += ["--ffmpeg", self.ffmpeg_cmd]

        self._batch_log(f"Starting {fmt.upper()} download...", "download")

        try:
            # Use larger buffer for better I/O performance
            self.process = subprocess.Popen(
                cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=8192, encoding="utf-8", errors="replace", env=env
            )
            
            total = None
            count = 0
            started = time.time()
            last_progress_update = 0
            error_lines = []
            
            for raw in self.process.stdout:
                if not self.is_downloading:
                    break
                    
                line = raw.strip()
                if not line:
                    continue

                # Check for important lines only
                if self._re_found_songs.search(line):
                    total = int(self._re_found_songs.search(line).group(1))
                    self.ui("total", total)
                    self._batch_log(f"Found {total} song(s)", "success")
                    continue

                if self._re_downloading.search(line):
                    count += 1
                    track = line.split(":", 1)[-1].strip() if ":" in line else line
                    track = self._re_brackets.sub("", track).strip()
                    
                    current_time = time.time()
                    if current_time - last_progress_update > 0.5:
                        self.ui("track", count, total, track[:90])
                        last_progress_update = current_time
                    
                    if count % 5 == 0:
                        self._batch_log(f"[{count}/{total or '?'}] {track[:80]}", "download")
                    continue

                if self._re_downloaded.search(line):
                    self._batch_log("✓ Downloaded", "success")
                    continue

                if self._re_error.search(line) and len(line) < 200:
                    error_lines.append(line)
                    self._batch_log(line[:200], "error")
                elif self._re_request_error.search(line):
                    error_lines.append(line)
                    self._batch_log(line[:200], "error")
                elif "Invalid proxy" in line or "proxy server" in line.lower():
                    error_lines.append(line)
                    self._batch_log(f"PROXY ERROR: {line[:200]}", "error")

            self.process.wait()
            elapsed = max(time.time() - started, 0.1)
            code = self.process.returncode

            if not self.is_downloading:
                self._flush_logs()
                self.log("Download stopped.", "warning")
                return

            if code == 0:
                self._flush_logs()
                self.ui("success", elapsed)
            else:
                error_text = "\n".join(error_lines[-10:]) if error_lines else "Unknown error"
                self._flush_logs()
                
                # Enhanced proxy troubleshooting
                if "Invalid proxy" in error_text or "proxy server" in error_text.lower():
                    troubleshooting = (
                        "\n\n🔧 PROXY ISSUE DETECTED:"
                        "\n• Confirm that the selected HTTP/SOCKS5 type matches the local port"
                        "\n• v2rayN commonly uses HTTP 10809 and SOCKS5 10808"
                        "\n• Test the proxy from Advanced settings"
                        "\n• Make sure the local proxy application is running"
                    )
                elif proxy_enabled and self._re_request_error.search(error_text):
                    troubleshooting = (
                        "\n\n🔧 PROXY CONNECTION ISSUE:"
                        "\n• Verify v2rayN is running"
                        "\n• Check the proxy port (HTTP: 10809, SOCKS5: 10808)"
                        "\n• Try 'Test Proxy' button"
                        "\n• Ensure your VPN server is working"
                        "\n• Try a different proxy server"
                    )
                else:
                    troubleshooting = self._get_troubleshooting(error_text, proxy_enabled)

                self.log(f"spotDL exited with code {code}", "error")
                self.log(f"Error: {error_text[:300]}", "error")
                self.log(troubleshooting, "warning")
                
                self.ui("failure", f"Exit code {code}\n\n{error_text[:300]}{troubleshooting}")
        except FileNotFoundError:
            self._flush_logs()
            self.log("spotDL not found. Try 'Update spotDL'.", "error")
            self.ui("failure", "spotDL could not be launched.")
        except Exception as exc:
            self._flush_logs()
            self.log(f"Download error: {str(exc)[:300]}", "error")
            self.ui("failure", f"Download failed: {str(exc)[:300]}")
        finally:
            if bridge:
                bridge.close()
            self.ui("finished")

    # ------------------------------------------------------------------
    # YouTube / TikTok / Instagram, through yt-dlp
    # ------------------------------------------------------------------
    def start_media_download(self, kind, url, quality="best", audio_only=False, limit=None):
        if self.is_downloading:
            return
        if self.updating or not self.ytdlp_cmd or not self.ffmpeg_cmd:
            self.notify("Still finishing setup. Try again in a moment.", "err")
            self.ui("finished")
            return

        url = (url or "").strip()
        if kind == "tiktok" and url.startswith("@"):
            url = f"https://www.tiktok.com/{url}"
        if kind == "instagram" and url.startswith("@"):
            url = f"https://www.instagram.com/{url[1:].strip('/')}/"
        if not MEDIA_PATTERNS[kind].search(url):
            self.notify(f"That is not a {kind.title()} link.", "err")
            self.ui("finished")
            return

        try:
            output = download_dir(kind, self.save_folder.get().strip())
        except Exception as exc:
            self.notify(f"Could not create the download folder.\n\n{exc}", "err")
            self.ui("finished")
            return

        self.is_downloading = True
        self.status.set("Downloading")
        threading.Thread(
            target=self.download_with_ytdlp,
            args=(kind, url, output, quality, audio_only, limit),
            daemon=True,
        ).start()

    def _ytdlp_cmd_for(self, kind, url, output, quality, audio_only, limit, candidate=None,
                       clients=None):
        cmd = [
            self.ytdlp_cmd, url,
            "--output", str(output / "%(title).150B.%(ext)s"),
            "--newline", "--no-warnings", "--ignore-errors",
            # Partials stay in .part files and are renamed on success. Writing
            # straight to the final name leaves a truncated file behind when a
            # run fails, and the next run tries to resume it — which Instagram
            # answers with HTTP 416.
            "--retries", "10", "--socket-timeout", "30",
            "--ffmpeg-location", str(Path(self.ffmpeg_cmd).parent),
            # A machine-readable progress line beats scraping the pretty bar.
            "--progress-template", "PROGRESS %(progress._percent_str)s %(info.title).80s",
        ]
        if audio_only:
            codec = self.download_format.get() if self.download_format.get() in ("mp3", "flac") else "mp3"
            cmd += ["--extract-audio", "--audio-format", codec, "--audio-quality", f"{self.quality.get()}K"]
        elif kind == "instagram":
            # Posts are as often stills as video: a height-capped video selector
            # matches nothing on a photo, and merging into mp4 makes no sense.
            cmd += ["--format", "best"]
        else:
            selector = MEDIA_FORMATS.get(quality, MEDIA_FORMATS["best"])
            if clients:
                # The token-free clients carry a thinner format list, so a strict
                # height cap can match nothing. Take the cap, or whatever exists.
                selector += "/best"
            cmd += ["--format", selector, "--merge-output-format", "mp4"]
        if limit:
            cmd += ["--playlist-end", str(limit)]
        if candidate:
            cmd += ytdlp_cookie_args(candidate)
        if clients:
            # Each YouTube client is checked for bots differently. When the
            # default is challenged, another one usually is not.
            cmd += ["--extractor-args", f"youtube:player_client={clients}"]
        if self.use_proxy.get() and self.proxy_url.get().strip():
            # yt-dlp speaks SOCKS5 directly, so the HTTP bridge is not needed here.
            cmd += ["--proxy", normalize_proxy_url(self.proxy_url.get(), self.proxy_type.get())]
        return cmd

    def download_with_ytdlp(self, kind, url, output, quality, audio_only, limit):
        self._media_done = 0
        self._media_total = None
        started = time.time()
        self._batch_log(f"{kind.title()}: {url}", "download")

        # A cookie source that exists is not a cookie source that reads: a running
        # Chromium browser locks its DB, and Chrome 127+ encrypts it app-bound.
        # Probing costs a second and tells us before the download instead of after.
        cookies = [None]
        if kind in COOKIE_SOURCES:
            cookies = self.usable_cookies(COOKIE_DOMAINS.get(kind)) or [None]
            if cookies == [None]:
                self._batch_log(
                    "No readable cookies — log into Instagram in Firefox, or drop a cookies.txt "
                    "beside the app. Trying anyway.", "warning")

        # Each attempt is a cookie jar, a set of YouTube player clients, and a
        # proxy override. The ladder is climbed only when a run says why it
        # failed, and every rung is used at most once — otherwise a jar that can
        # never satisfy the site gets retried forever.
        plan = [{"cookies": c, "clients": None} for c in cookies]
        spent = set()

        try:
            while plan:
                attempt = plan.pop(0)
                candidate = attempt["cookies"]
                if candidate:
                    self._batch_log(f"Cookies: {candidate_label(candidate)}", "info")
                verdict = self._run_ytdlp(
                    kind, url, output, quality, audio_only, limit, candidate, started,
                    may_borrow_cookies=(kind in COOKIE_ON_DEMAND),
                    clients=attempt["clients"])

                if verdict is None:
                    return

                if verdict == "signin":
                    nxt = self._next_signin_step(kind, attempt, spent)
                    if nxt:
                        plan.insert(0, nxt)
                        continue
                    self._flush_logs()
                    self._report_signin_failure(kind)
                    return

                # verdict == "cookies": that jar is unreadable.
                if plan:
                    self._batch_log(
                        f"Could not read cookies from {candidate_label(candidate)} — trying the next source.",
                        "warning")
                    self._flush_logs()
                    continue
                self._flush_logs()
                self._report_ytdlp_failure(kind, f"Could not read cookies from {candidate_label(candidate)}.")
                return
        finally:
            self.ui("finished")

    def _next_signin_step(self, kind, attempt, spent):
        """The next thing worth trying against a sign-in demand, or None.

        Cheapest first: another player client costs nothing, a cookie jar needs
        an account. Each rung is spent once, so a jar that can never satisfy the
        site is not retried forever.
        """
        if kind == "youtube" and "clients" not in spent:
            spent.add("clients")
            self._batch_log("YouTube asked for a bot check — retrying as a different client.",
                            "warning")
            self._flush_logs()
            return {**attempt, "clients": YT_FALLBACK_CLIENTS}

        if "cookies" not in spent:
            spent.add("cookies")
            # Only a jar that actually holds this site's session can help here.
            session = self.usable_cookies(COOKIE_DOMAINS.get(kind), require_session=True)
            if session:
                self._batch_log(f"Retrying with the {candidate_label(session[0])} session.", "warning")
                self._flush_logs()
                return {**attempt, "cookies": session[0]}
            self._batch_log("No browser holds a session for this site — skipping cookies.",
                            "warning")

        return None

    # ------------------------------------------------------------------
    # Signing in, without an extension or a text file
    # ------------------------------------------------------------------
    def sign_in(self, kind):
        """Open a real login window. Its cookies become the jar we download with.

        This is the same thing the browser extension does, minus the extension:
        the app owns this window, so nothing locks the database and nothing has
        to be exported by hand.
        """
        page = SIGNIN_PAGES.get(kind)
        if not page:
            self.notify(f"{kind.title()} does not need a sign-in.", "err")
            return
        if self._login_window is not None:
            self.notify("A sign-in window is already open.", "err")
            return

        login_url = page[0]
        self._login_kind = kind
        self._login_window = webview.create_window(
            f"Sign in to {kind.title()} · Blue Knight",
            url=login_url, width=980, height=800, min_size=(600, 560),
            background_color="#05080d",
        )
        self._login_window.events.closed += self._login_closed
        self.ui("signin", kind)
        self.log(f"Sign in to {kind.title()} in the window that opened, then press "
                 f"“Finish sign-in”.", "info")

    def _login_closed(self):
        """The window went away. If cookies were never taken, say so."""
        if self._login_window is not None:
            self._login_window = None
            self._login_kind = None
            self.ui("signin", None)
            self.log("Sign-in window closed before the cookies were saved.", "warning")

    def finish_sign_in(self):
        """Harvest the session from the login window and write it as a jar."""
        window, kind = self._login_window, self._login_kind
        if not window or not kind:
            self.notify("No sign-in window is open.", "err")
            return
        _, harvest_urls, site = SIGNIN_PAGES[kind]

        try:
            # Visit each page the session lives on and merge what comes back;
            # one page only ever holds its own domain's cookies.
            harvested, seen = [], set()
            for url in harvest_urls:
                try:
                    window.load_url(url)
                    time.sleep(4.0)
                    for cookie in window.get_cookies() or []:
                        for name, morsel in cookie.items():
                            key = (morsel["domain"], name)
                            if key not in seen:
                                seen.add(key)
                                harvested.append(cookie)
                except Exception as exc:
                    self.log(f"Sign-in: {url} gave nothing ({str(exc)[:90]})", "warning")
            jar = cookie_dir() / f"{site}_cookies.txt"
            count = write_netscape_jar(harvested, jar)
        except Exception as exc:
            self.log(f"Could not read the sign-in cookies: {str(exc)[:200]}", "error")
            self.notify(f"Could not read the cookies.\n\n{str(exc)[:200]}", "err")
            return
        finally:
            self._login_window = None
            self._login_kind = None
            self.ui("signin", None)
            with contextlib.suppress(Exception):
                window.destroy()

        if not count:
            self.log(f"No cookies came back from {kind.title()}. Was the sign-in "
                     f"completed?", "warning")
            self.notify("No cookies were found. Finish signing in, then try again.", "err")
            return

        self.log(f"Saved {count} {kind.title()} cookies to {jar.name}.", "success")
        ok, detail = self._probe_cookie_file(jar, COOKIE_DOMAINS.get(kind))
        if ok:
            self.notify(f"Signed in to {kind.title()}. {detail}.", "ok")
        else:
            self.log(f"{jar.name}: {detail}", "warning")
            self.notify(f"Cookies saved, but {detail}. Sign in fully, then repeat.", "err")

    def cookie_export_recipe(self, kind):
        """The export that actually survives, spelled out.

        A jar copied from a normal window dies when the site rotates the
        session; one taken in a private window and never used again does not.
        """
        site = {"youtube": "www.youtube.com", "instagram": "www.instagram.com",
                "tiktok": "www.tiktok.com"}.get(kind, "the site")
        return (f"\n\n🍪 Export a cookies.txt — this lasts:"
                "\n1. Install the “Get cookies.txt LOCALLY” extension"
                "\n2. Open a private/incognito window and sign in"
                f"\n3. In that same tab, go to https://{site}/robots.txt"
                "\n4. Export as Netscape format"
                f"\n5. Save it in the app's cookies folder, or your Downloads"
                "\n\nThe app picks up any *cookies*.txt in those folders and uses the one"
                " holding this site's session. Do not open that private window again —"
                " closing it is what keeps the session alive.")

    def _report_signin_failure(self, kind):
        detail = f"{kind.title()} wants a signed-in session and no usable cookies were found."
        self.log(f"{kind.title()} download failed: {detail}", "error")

        # A browser that is merely running is the most common — and most easily
        # fixed — reason we have no session, so name it instead of listing options.
        locked = [candidate_label(c) for c in cookie_candidates(COOKIE_DOMAINS.get(kind))
                  if locked_by_browser(c)]
        opening = ""
        if locked:
            names = " and ".join(dict.fromkeys(locked))
            opening = (f"\n\n🔧 {names} is open, and a running browser keeps its cookies locked."
                       f"\n• Close {names} completely — check the tray — then press Download again")
        self.ui("failure", detail + opening + self.cookie_export_recipe(kind))

    def _report_ytdlp_failure(self, kind, detail):
        self.log(f"{kind.title()} download failed: {detail}", "error")
        self.ui("failure", detail +
                "\n\n🔧 Cookies are the sticking point:"
                "\n• A running Edge/Chrome/Brave locks its cookie database — close it"
                "\n• Chrome 127 and newer encrypt it app-bound; yt-dlp cannot open that at all"
                "\n• Log in with Firefox, which stays readable"
                "\n• Or export a cookies.txt into this folder, your Downloads, or the download"
                " folder — an exported file wins over every browser")

    def _run_ytdlp(self, kind, url, output, quality, audio_only, limit, candidate, started,
                   may_borrow_cookies=False, clients=None):
        """One yt-dlp run.

        Returns None when the job is over (success, stop, or a failure already
        reported), or a verdict the caller can act on: "cookies" for an unreadable
        jar, "signin" for a site that wants a session.
        """
        error_lines = []
        try:
            cmd = self._ytdlp_cmd_for(kind, url, output, quality, audio_only, limit, candidate,
                                      clients)
            self.process = subprocess.Popen(
                cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=8192, encoding="utf-8", errors="replace",
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            for raw in self.process.stdout:
                if not self.is_downloading:
                    break
                line = raw.strip()
                if not line:
                    continue

                if line.startswith("PROGRESS "):
                    now = time.time()
                    if now - self.last_ui_update < UI_UPDATE_INTERVAL:
                        continue
                    self.last_ui_update = now
                    parts = line.split(None, 2)
                    percent = parts[1].rstrip("%") if len(parts) > 1 else ""
                    title = parts[2] if len(parts) > 2 else ""
                    try:
                        self.ui("bytes", float(percent), title)
                    except ValueError:
                        pass
                    continue

                item = self._re_ytdlp_item.search(line)
                if item:
                    self._media_done, self._media_total = int(item.group(1)), int(item.group(2))
                    self.ui("total", self._media_total)
                    self.ui("track", self._media_done, self._media_total, "")
                    continue

                if "[download] Destination:" in line or "has already been downloaded" in line:
                    self._batch_log(line[:200], "success")
                elif self._re_error.search(line):
                    error_lines.append(line)
                    self._batch_log(line[:200], "error")

            self.process.wait()
            code = self.process.returncode

            if not self.is_downloading:
                self._flush_logs()
                self.log("Download stopped.", "warning")
                return None
            self._flush_logs()
            if code == 0:
                self.ui("success", time.time() - started)
                return None
            joined = "\n".join(error_lines[-6:])
            # Say nothing yet when the caller still has a move to make.
            if candidate and COOKIE_FAILURE.search(joined):
                return "cookies"
            if may_borrow_cookies and SIGNIN_DEMANDED.search(joined):
                return "signin"
            raise RuntimeError(joined or f"yt-dlp exited with code {code}")
        except FileNotFoundError:
            self._flush_logs()
            self.log("yt-dlp could not be launched. Run Check for updates.", "error")
            self.ui("failure", "yt-dlp could not be launched.")
        except Exception as exc:
            detail = str(exc)[:300]
            self._flush_logs()
            self.log(f"{kind.title()} download failed: {detail}", "error")
            hint = ""
            if SIGNIN_DEMANDED.search(detail):
                hint = ("\n\n🔧 The site asked for a signed-in session."
                        "\n• Log in with Firefox, or drop a cookies.txt beside the app"
                        "\n• A proxy in Connection settings also helps when it is region bait")
            elif kind == "tiktok":
                hint = "\n\n🔧 TikTok rate-limits hard. Enable a proxy and retry in a minute."
            elif kind == "instagram":
                hint = ("\n\n🔧 Instagram serves almost nothing logged out."
                        "\n• Log into Instagram in Firefox, Edge, or Chrome, then retry"
                        "\n• A running Chromium browser locks its cookies — close Edge/Chrome,"
                        " or use Firefox"
                        "\n• Or export a cookies.txt and drop it beside the app"
                        "\n• Private accounts need the logged-in user to follow them")
            self.ui("failure", detail + hint)
        finally:
            # "finished" belongs to the caller: a cookie retry is still the same job.
            self.process = None
        return None

    def _batch_log(self, message, level="info"):
        """Batch log messages"""
        self.log_batch.append((message, level))
        if len(self.log_batch) >= LOG_BATCH_SIZE:
            self._flush_logs()

    def _flush_logs(self):
        """Write batched logs to UI"""
        if not self.log_batch:
            return
        self.ui_queue.put(("batch_log", self.log_batch.copy()))
        self.log_batch.clear()

    def _get_troubleshooting(self, error_text, proxy_enabled):
        """Generate troubleshooting"""
        if proxy_enabled and "RequestError" in error_text:
            return (
                "\n🔧 PROXY TROUBLESHOOTING:"
                "\n• Verify proxy is running"
                "\n• Verify that HTTP or SOCKS5 matches the configured port"
                "\n• Try a matching v2rayN preset"
                "\n• Test proxy with 'Test Proxy' button"
            )
        elif "RequestError" in error_text:
            return (
                "\n🔧 TROUBLESHOOTING:"
                "\n• Check internet connection"
                "\n• Try VPN/proxy if region-blocked"
                "\n• Use 'V2RayN HTTP' preset for proxy"
                "\n• Update spotDL in Advanced settings"
            )
        elif "blocked by YouTube" in error_text.lower():
            return (
                "\n🔧 TROUBLESHOOTING:"
                "\n• YouTube Music blocked connection"
                "\n• Enable proxy (V2RayN HTTP preset)"
                "\n• Try 'best' format instead"
            )
        else:
            return (
                "\n🔧 TROUBLESHOOTING:"
                "\n• Check internet connection"
                "\n• Update spotDL"
                "\n• Verify Spotify link"
            )

    def stop_download(self):
        # spotDL runs as a child process; yt-dlp runs in-process and stops via its hook.
        if self.is_downloading:
            self.is_downloading = False
            if self.process:
                try:
                    self.process.terminate()
                except Exception:
                    pass
            self._flush_logs()
            self.log("Download stopped.", "warning")
            self.status.set("Ready")
            self.ui("status", "Stopped")

    # ------------------------------------------------------------------
    # Event queue drained by the page
    # ------------------------------------------------------------------
    def ui(self, action, *args):
        self.ui_queue.put((action, args))

    def log(self, message, level="info"):
        self.ui_queue.put(("batch_log", [(message, level)]))

    def download_finished(self):
        self.is_downloading = False
        self.process = None
        if self.status.get() != "Error":
            self.status.set("Ready")

    # ------------------------------------------------------------------
    # Utilities
    # ------------------------------------------------------------------
    def check_setup(self):
        self.log("Checking components...", "info")

        packaged_ffmpeg = bundled_tool("ffmpeg.exe")
        if packaged_ffmpeg:
            self.ffmpeg_cmd = packaged_ffmpeg
        elif self.ffmpeg_exe.exists():
            self.ffmpeg_cmd = str(self.ffmpeg_exe)
        else:
            self.ffmpeg_cmd = shutil.which("ffmpeg")

        self.spotdl_cmd = self.find_tool("spotdl")
        self.ytdlp_cmd = self.find_tool("yt-dlp")

        missing = [label for label, found in (
            ("spotDL", self.spotdl_cmd), ("yt-dlp", self.ytdlp_cmd), ("FFmpeg", self.ffmpeg_cmd),
        ) if not found]
        self.ui("components", self.component_summary())
        if not missing:
            self.status.set("Ready")
            self.log("All components ready.", "success")
            self.notify("Everything is installed and ready.", "ok")
        else:
            self.notify("Missing: " + ", ".join(missing) + ". Run Check for updates, or restart the app.", "err")

    # ------------------------------------------------------------------
    # Conversion
    # ------------------------------------------------------------------
    def convert_single_flac(self, path):
        if self.is_converting:
            self.notify("A conversion is already running.", "err")
            return
        self.is_converting = True
        source = Path(path)
        target = source.with_suffix(".flac")
        try:
            self.log(f"Converting {source.name} → FLAC…", "info")
            cmd = [self.ffmpeg_cmd, "-i", str(source), "-ar", "44100", "-c:a", "flac", "-compression_level", "8", "-y", str(target)]
            result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
            if result.returncode == 0 and target.exists():
                self.log(f"✓ Converted: {target.name}", "success")
                self.notify(f"Created {target.name}", "ok")
            else:
                self.log("FLAC conversion failed.", "error")
                self.notify("FFmpeg could not convert that file.", "err")
        except Exception as exc:
            self.log(f"Conversion error: {str(exc)[:250]}", "error")
            self.notify("FFmpeg could not convert that file.", "err")
        finally:
            self.is_converting = False

    def on_close(self):
        self.closing = True
        if self.process:
            try:
                self.process.terminate()
            except Exception:
                pass
        try:
            self.executor.shutdown(wait=False, cancel_futures=True)
        except Exception:
            pass


class Api:
    """The js_api surface: only these methods reach the page.

    pywebview walks every public attribute of js_api into the JS bridge, so the
    engine, the window, and anything unserialisable stay underscored.
    """

    def __init__(self, app):
        self._app = app
        self._window = None

    # -- lifecycle ------------------------------------------------------
    def hello(self):
        self._app.begin()
        return self._app.snapshot()

    def poll(self):
        return self._app.drain()

    def set_option(self, name, value):
        self._app.set_option(name, value)

    # -- window chrome --------------------------------------------------
    def minimize(self):
        self._window.minimize()

    def toggle_maximize(self):
        self._window.toggle_fullscreen()

    def close(self):
        self._app.on_close()
        self._window.destroy()

    # -- actions --------------------------------------------------------
    def start_download(self, url, fmt, quality):
        self._app.start_download(url, fmt, quality)

    def start_media(self, kind, url, quality, audio_only, limit):
        self._app.start_media_download(kind, url, quality, audio_only, limit or None)

    def stop_download(self):
        self._app.stop_download()

    def set_preset(self, proxy_type, url):
        self._app.set_proxy_preset(proxy_type, url)
        self._app.ui("proxy", proxy_type, url, True)

    def test_proxy(self):
        self._app.test_proxy()

    def check_setup(self):
        threading.Thread(target=self._app.check_setup, daemon=True).start()

    def sign_in(self, kind):
        self._app.sign_in(kind)

    def finish_sign_in(self):
        threading.Thread(target=self._app.finish_sign_in, daemon=True).start()

    def update_tools(self):
        self._app.update_tools()

    def open_folder(self):
        self._app.open_folder()

    def open_telegram(self):
        webbrowser.open(TELEGRAM_URL)

    def clipboard(self):
        return read_clipboard()

    def about(self):
        self._app.notify(
            f"{APP_NAME} {APP_VERSION}\n"
            f"Created by {CREATOR}\n"
            f"{TELEGRAM_URL}\n\n"
            "Spotify, YouTube, TikTok and Instagram.\n"
            "FFmpeg, spotDL and yt-dlp ship inside the app and update from\n"
            "mirrored sources. V2RayN compatible.",
            "info",
        )

    def browse(self):
        picked = self._window.create_file_dialog(webview.FOLDER_DIALOG)
        if picked:
            folder = picked[0]
            self._app.set_option("folder", folder)
            self._app.ui("folder", folder)

    def convert(self):
        if not self._app.ffmpeg_cmd:
            self._app.notify("FFmpeg is not ready yet.", "err")
            return
        picked = self._window.create_file_dialog(
            webview.OPEN_DIALOG, file_types=("MP3 files (*.mp3)", "All files (*.*)")
        )
        if picked:
            threading.Thread(target=self._app.convert_single_flac, args=(picked[0],), daemon=True).start()


def main():
    app = SpotifyDownloader()
    api = Api(app)
    window = webview.create_window(
        f"{APP_NAME} {APP_VERSION}",
        url=web_index(),
        js_api=api,
        width=1180,
        height=800,
        min_size=(980, 680),
        frameless=True,
        easy_drag=False,
        background_color="#05080d",
        text_select=False,
    )
    api._window = window
    # private_mode is pywebview's default, and it throws the browser profile away
    # on exit — which is why a sign-in never survived a restart. Give the webview
    # a folder of its own and the account stays signed in, like any browser.
    webview.start(private_mode=False,
                  storage_path=str(Path(os.environ.get("LOCALAPPDATA", Path.home()))
                                   / "SpotifyDownloader" / "browser"))
    app.on_close()
    return 0


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        spotdl = bundled_tool("spotdl.exe")
        ffmpeg = bundled_tool("ffmpeg.exe")
        ytdlp = bundled_tool("yt-dlp.exe")
        if not spotdl or not ffmpeg or not ytdlp:
            raise SystemExit(2)
        assert MEDIA_PATTERNS["youtube"].search("https://youtu.be/abc")
        assert MEDIA_PATTERNS["tiktok"].search("https://www.tiktok.com/@u/video/1")
        assert not MEDIA_PATTERNS["tiktok"].search("https://open.spotify.com/track/1")
        assert not MEDIA_PATTERNS["youtube"].search("https://nyoutube.com.evil.tld/x")
        # Every tool must be reachable through the same generic lookup.
        for _name in TOOLS:
            assert bundled_tool(TOOLS[_name]["exe"]), _name
            assert len(TOOLS[_name]["urls"]) >= 2, f"{_name} needs a fallback mirror"
        checks = ([spotdl, "--version"], [ffmpeg, "-version"], [ytdlp, "--version"])
        for command in checks:
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
            if result.returncode != 0:
                raise SystemExit(3)
        raise SystemExit(0)
    else:
        main()
