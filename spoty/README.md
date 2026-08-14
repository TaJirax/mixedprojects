# Blue Knight Downloader

A single-file Windows app that downloads from **Spotify, YouTube, YouTube
Music, TikTok, Instagram, SoundCloud, X, PDF/ebook documents, and other
yt-dlp-supported video sites**, sorts what it fetches, and handles restricted
networks and common sign-in walls.

No install, no Python, no command line. ffmpeg, spotDL, yt-dlp, Deno, Streamlink,
gallery-dl and the document conversion libraries ship inside the executable.
The **Update tools** button checks each bundled downloader and converter, verifies
Python wheels against PyPI's SHA-256 digest, and activates those updates on restart.

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
| **YouTube Music** | song · album · playlist | MP3 or FLAC, tagged with artist, album and cover art |
| **TikTok** | video link · `@username` · hashtag page | MP4 |
| **Instagram** | reel · post · `@username` | source quality, photo or video |
| **SoundCloud** | track · set · profile | MP3 or FLAC |
| **X** | `x.com` or `twitter.com` post - video, photo, GIF, multi-image | MP4, or JPG/PNG at full resolution |
| **Video** | public video page or direct media URL, excluding YouTube | MP4 or audio-only |
| **Documents & Ebooks** | documents, Office files, ebooks, comic archives, or an index page | original format |
| **Manga / manhwa** | public or account-authorized chapter page | PDF and CBZ |

Playlist and profile downloads take an optional item limit.

### More than one engine

yt-dlp only understands video. A post that is a photo, a GIF or a mix of both
is not a failure - it is a job for a different engine, so the X and **Video**
pages fall through automatically:

1. **yt-dlp** - video, everywhere it has an extractor.
2. **Streamlink** - live and HLS streams on its supported services.
3. **HTML5 resolver** - standard video/audio tags and social media metadata.
4. **gallery-dl** - photos, GIFs, galleries and mixed posts.
5. **direct download** - the link treated as the file itself, using neither
   yt-dlp nor ffmpeg.

The hand-off is silent, and only happens when yt-dlp reports that there is no
video at the link. A genuine dead post or DRM failure stops where it happened
rather than being retried pointlessly by every engine in turn. The engines use
the app's proxy and Stop button.

For a General-site bot check, CAPTCHA, or HTTP 401/403 response, yt-dlp gets a
separate challenge ladder before engine hand-off: the bundled Deno runtime,
then a browser-impersonated request, then a signed-in browser or exported jar
only when it contains cookies applicable to that exact hostname. Impersonation
is not forced on normal downloads because yt-dlp documents a speed and stability
cost. YouTube's player-client and PO-token arguments remain exclusive to the
YouTube pages and are never sent to other sites.

### What it cannot do

**Netflix, Crunchyroll, Disney+ and similar services are not supported and
cannot be added.** Every stream they serve is encrypted with Widevine DRM, and
downloading one means breaking that encryption. yt-dlp refuses these sites
outright, and so does this app. The same applies to individual protected
tracks elsewhere — SoundCloud Go+, for instance — which report `DRM protected`
and stop. Unprotected media on those same sites still works.

### Documents

The **Documents & Ebooks** page takes either a direct file link, or a page that
links to documents — a course page, a manual index, a journal issue — and collects
validated PDF, EPUB, MOBI, AZW/AZW3, FB2, Office, OpenDocument, RTF, text, DjVu,
CHM and CBZ files. Each link is checked by reading the file's first
bytes rather than trusting its name, so pages that serve documents from
`/download?id=…` work, and an HTML error page never lands on disk as a broken
`.pdf`. Sites that build their viewer in JavaScript hide the real file from the
page source; there, open the PDF in a browser and paste that address.

Switch the same page to **Manga / manhwa** for a public or account-authorized
chapter URL. gallery-dl is tried first, then the built-in HTML image resolver.
The extractor/HTML reading sequence is preserved and saved permanently as
`00001`, `00002`, ... inside a chapter folder; PDF and CBZ are built from that
same ordered list. It does not bypass DRM, subscriptions, paywalls, or site
access controls.

## Where files go

Everything lands next to the app, one folder per source:

```
BlueKnightdownloader/
├─ Spotify/
├─ YouTube/
├─ YouTube Music/
├─ TikTok/
├─ Instagram/
├─ SoundCloud/
├─ X/
├─ Video/
└─ Documents & Ebooks/
```

Change the parent folder in **Download settings**; the per-source folders follow.

## Local conversion

**Download settings** includes a local media converter for MP3, FLAC, WAV, M4A,
OGG, AAC, MP4, MKV, WebM, MOV and AVI. Video conversion offers H.264, H.265 and
VP9, three quality profiles, original/preset/custom dimensions, source/24/30/60
FPS, and selectable audio bitrate. FFmpeg performs a real transcode and writes
the result beside the selected source without overwriting it.

The document converter creates PDF from images, CBZ archives and PDF using the
bundled libraries. **Choose image folder** collects up to 500 images recursively,
uses numeric-aware ordering (`page2` before `page10`), and creates one PDF beside
the folder. Office and OpenDocument conversion uses LibreOffice when installed;
ebook conversion uses Calibre when installed. DRM-protected inputs are not
decrypted or bypassed.

## Sign-in and cookies

Some things are simply not served to a logged-out client — most Instagram
content, and YouTube whenever it decides your IP looks like a robot.

**The easy way.** Press **Sign in** on the YouTube, X, or Instagram page. The app
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
offered to YouTube. General-site cookie retries are challenge-triggered and
domain checked; unrelated cookies in a browser or export are not sent to the
requested host.

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

The app bundles Deno so yt-dlp can execute its EJS JavaScript challenge solver,
then retries the current token-free and HLS-capable clients before asking for a
fresh signed-in session. Playlist requests are spaced out to reduce session
rate limits.

If YouTube still requires proof-of-origin attestation, **Connection settings**
accepts an `mweb.gvs+TOKEN` value in yt-dlp's official format. It is kept in
memory only. These tokens are session/video-bound and expire, so this is an
advanced fallback rather than a permanent credential. A signed-in throwaway
account or a different IP may still be required.

## Keeping it current

**Check for updates** refreshes spotDL, yt-dlp, Deno and the Python engines from mirrored hosts (GitHub,
with SourceForge as the escape hatch for blocked networks), and reports whether
a usable Instagram session is on file. Sites change their defences constantly;
an outdated yt-dlp is the single most common cause of a download that used to
work.

## Building from source

```powershell
cd spoty
.\build_portable.ps1
```

Needs Python 3.12, PyInstaller, pywebview, `vendor/spotdl.exe`,
`vendor/yt-dlp.exe`, `vendor/deno.exe` plus an ffmpeg build. The result is
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
