# Blue Knight Downloader

A single-file Windows app that downloads from **Spotify, YouTube, TikTok and
Instagram**, sorts what it fetches, and handles the two things that actually
break downloaders in practice: **restricted networks** and **sign-in walls**.

No install, no Python, no command line. ffmpeg, spotDL and yt-dlp ship inside
the executable and update themselves.

<sub>Telegram: <a href="https://t.me/BlueKnight_Net">@BlueKnight_Net</a></sub>

---

## Run it

1. Extract the whole folder. Do not run the `.exe` from inside the ZIP.
2. Open `SpotifyDownloader.exe`.
3. Pick a source in the sidebar, paste a link, press **Download**.

Built for 64-bit Windows 10 and 11. Internet access is still needed to resolve
metadata and fetch media.

## What each source takes

| Source | Accepts | Output |
| --- | --- | --- |
| **Spotify** | track · album · playlist | MP3 320 kbps or FLAC, tagged |
| **YouTube** | video · Short · playlist | MP4 up to best/1080/720/480, or audio-only as MP3 |
| **TikTok** | video link · `@username` · hashtag page | MP4 |
| **Instagram** | reel · post · `@username` | source quality, photo or video |

Playlist and profile downloads take an optional item limit.

## Where files go

Everything lands next to the app, one folder per source:

```
BlueKnightdownloader/
├─ Spotify/
├─ YouTube/
├─ TikTok/
└─ Instagram/
```

Change the parent folder in **Download settings**; the per-source folders follow.

## Sign-in and cookies

Some things are simply not served to a logged-out client — most Instagram
content, and YouTube whenever it decides your IP looks like a robot.

**The easy way.** Press **Sign in** on the YouTube or Instagram page. The app
opens a real login window of its own, you sign in normally, and it keeps the
session itself. That login survives restarts, and the app quietly re-reads it
when a site rotates its cookies — which YouTube does constantly.

**The manual way.** Export a `cookies.txt` (Netscape format — the
*Get cookies.txt LOCALLY* extension writes one) and drop it in the app folder,
your `Downloads`, or the download folder. Any `*cookies*.txt` is picked up.
For a jar that lasts, follow yt-dlp's own advice: sign in using a **private
window**, visit `robots.txt` in that same tab, export, then close the window
and never reopen it.

Cookie jars are kept **per site** in `cookies/<site>/`, with a small registry
recording what each holds and when it expires. An Instagram jar is never
offered to YouTube.

> Use a throwaway account, not your main one. yt-dlp's wiki warns that
> accounts used for downloading can be banned.

The app can also read a browser you are already signed into (Firefox,
Opera GX, Brave, Vivaldi, Edge, Chrome, Chromium, LibreWolf, Whale) — but a
running Chromium browser holds an exclusive lock on its cookie database, so it
has to be **closed** first. Firefox stays readable while open. Chrome 127+
encrypts its jar app-bound and cannot be read at all; use the in-app sign-in or
an exported file instead.

## Restricted networks

Open **Connection**, switch the proxy on, and pick a preset:

| Preset | Address |
| --- | --- |
| V2RayN HTTP | `http://127.0.0.1:10809` |
| V2RayN SOCKS5 | `socks5://127.0.0.1:10808` |
| Clash | `http://127.0.0.1:7890` |

**Test proxy** checks it before you commit to a download. SOCKS5 is handled
through a temporary local HTTP bridge, because spotDL only speaks HTTP — and
note that spotDL reads `HTTP_PROXY`/`HTTPS_PROXY`, which the app sets for it.
If Spotify resolves but never downloads, the proxy toggle is the first thing
to check.

## When YouTube asks for a bot check

YouTube is rolling out "PO tokens", and most player clients now require one.
When it challenges a download, the app retries as the clients that do not need
a token — `android_vr`, `web_embedded`, `tv` — before asking for cookies. That
alone clears it most of the time, with no account involved.

If it keeps failing: sign in (throwaway account), or route through a proxy —
the check follows your IP, and datacenter ranges are flagged hardest.

## Keeping it current

**Check for updates** refreshes spotDL and yt-dlp from mirrored hosts (GitHub,
with SourceForge as the escape hatch for blocked networks), and reports whether
a usable Instagram session is on file. Sites change their defences constantly;
an outdated yt-dlp is the single most common cause of a download that used to
work.

## Building from source

```powershell
cd spoty
.\build_portable.ps1
```

Needs Python 3.12, PyInstaller, pywebview, and `vendor/spotdl.exe`,
`vendor/yt-dlp.exe` plus an ffmpeg build. The result is
`release/SpotifyDownloader.exe`.

| File | Role |
| --- | --- |
| `spotify_downloader.py` | engine, download logic, the bridge the page talks to |
| `web/index.html` | the entire interface — markup, style and script |
| `blueknight_paths.py` | download folders, cookie discovery, the jar registry |
| `instagram.py`, `youtubedl.py`, `tiktok.py` | standalone CLI versions |
| `build_portable.ps1` | one-file build |

`blueknight_paths.py` runs its own checks with `python blueknight_paths.py`.

## Licensing

Released under **GPL-3.0** (`GPL-3.0.txt`). Bundled tools keep their own
licences — spotDL (MIT), yt-dlp (Unlicense), FFmpeg (GPL) — with sources and
full notices in `THIRD_PARTY_NOTICES.txt`.

Download only what you have the right to download. Respect each platform's
terms and the rights of the people who made the work.
