# Spotify Downloader Portable

## Run

1. Extract the entire ZIP. Do not run the executable from inside the ZIP.
2. Open `SpotifyDownloader.exe`.
3. Paste a Spotify track, album, or playlist URL.

The package is built for 64-bit Windows 10 and Windows 11. Python, spotDL,
and FFmpeg do not need to be installed separately. Bundled tools are reused on
every launch and are never downloaded again. If a tool is missing from a custom
build, the app caches it under `%LOCALAPPDATA%\SpotifyDownloader` and tries an
independent fallback host (GitHub/SourceForge for spotDL and BtbN/gyan.dev for
FFmpeg). Internet access is still required to resolve metadata and download audio.

For local proxies, open **Advanced settings**, select the matching HTTP or
SOCKS5 type, enter the listening address, and use **Test Proxy** before a
download. Common v2rayN defaults are HTTP `127.0.0.1:10809` and SOCKS5
`127.0.0.1:10808`.

## Source

The original Python source and proxy tests are included in the `source`
folder. Third-party licensing and source links are in
`THIRD_PARTY_NOTICES.txt`.
