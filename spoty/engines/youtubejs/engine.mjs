/*
 * YouTube.js, as an engine the rest of the app can call.
 *
 * The same file runs in two places, which is the point of writing it in
 * JavaScript rather than twice:
 *
 *   Desktop — under the Deno that already ships for yt-dlp's challenge solver,
 *             so it costs a script rather than a runtime.
 *   Android — inside a WebView, which is a real browser and therefore a better
 *             host for this than the DOM emulators other tools carry around.
 *
 * It reports what it found and never fetches the media itself: the app already
 * has one downloader that handles proxies, progress, cancellation and part
 * files, and a second one would be a second set of those bugs. This resolves,
 * the app fetches.
 *
 * Output is one JSON object on a single line. Nothing else is printed, because
 * the caller parses stdout.
 */

// Cheapest first, where cheap means what the client has to prove.
//
// ANDROID_VR, TV and WEB_EMBEDDED are the three yt-dlp's PO Token Guide lists
// as needing no proof-of-origin token, and ANDROID_VR is the one that returned
// a full format list on a video every other client here refused. It leads.
//
// ANDROID and IOS are deliberately absent: they pass the flag that says no
// token is needed and then still want one to fetch a stream, which arrives as
// the 403 this list exists to avoid.
const CLIENTS = [
  "ANDROID_VR", "TV", "WEB_EMBEDDED", "TV_EMBEDDED", "MWEB", "WEB",
];

/**
 * Ask each client in turn and keep the first that actually offers streams.
 *
 * A client answering at all is not the same as a client answering usefully:
 * YouTube hands back a perfectly valid response carrying no formats when it
 * does not want to serve one, so the test is whether streams came back, not
 * whether the call threw.
 */
export async function resolve(Innertube, ClientType, videoId, options = {}) {
  const attempts = [];

  for (const name of CLIENTS) {
    if (!(name in ClientType)) continue;
    try {
      const youtube = await Innertube.create({
        retrieve_player: true,
        client_type: ClientType[name],
        ...(options.visitorData ? { visitor_data: options.visitorData } : {}),
      });
      const info = await youtube.getInfo(videoId);
      const status = info.playability_status?.status ?? "UNKNOWN";
      const adaptive = info.streaming_data?.adaptive_formats ?? [];
      const combined = info.streaming_data?.formats ?? [];

      if (adaptive.length === 0 && combined.length === 0) {
        attempts.push({
          client: name,
          status,
          reason: String(info.playability_status?.reason ?? "no formats"),
        });
        continue;
      }

      return {
        ok: true,
        client: name,
        title: String(info.basic_info?.title ?? ""),
        author: String(info.basic_info?.author ?? ""),
        duration: Number(info.basic_info?.duration ?? 0),
        streams: [...adaptive, ...combined].map(describe).filter(Boolean),
        attempts,
      };
    } catch (failure) {
      attempts.push({ client: name, status: "ERROR", reason: short(failure) });
    }
  }

  return { ok: false, streams: [], attempts };
}

/**
 * One format, in the shape every engine in this app reports.
 *
 * A format with no URL is dropped rather than passed on: those are the
 * server-side-streaming entries, and there is nothing at the other end of them
 * to fetch.
 */
function describe(format) {
  const url = format?.url ?? format?.decipher?.() ?? null;
  if (!url) return null;
  const mime = String(format.mime_type ?? "");
  return {
    url,
    format: containerOf(mime),
    video_codec: format.has_video ? codecOf(mime) : "",
    audio_codec: format.has_audio ? codecOf(mime) : "",
    height: Number(format.height ?? 0),
    width: Number(format.width ?? 0),
    fps: Number(format.fps ?? 0),
    bitrate: Number(format.bitrate ?? 0),
    file_size: Number(format.content_length ?? 0),
    video_only: Boolean(format.has_video && !format.has_audio),
    audio_only: Boolean(format.has_audio && !format.has_video),
    audio_language: String(format.language ?? format.audio_track?.id ?? ""),
    is_default_audio: Boolean(format.audio_track?.audio_is_default ?? false),
  };
}

function containerOf(mime) {
  const type = mime.split(";")[0].split("/")[1] ?? "";
  return type === "mp4" || type === "webm" ? type : (type || "mp4");
}

function codecOf(mime) {
  const match = /codecs="([^"]+)"/.exec(mime);
  return match ? match[1].split(",")[0].trim() : "";
}

/** A message without the stack, and without any URL it may have quoted. */
function short(failure) {
  const text = String(failure?.message ?? failure ?? "unknown");
  const marker = text.indexOf("https://");
  return (marker >= 0 ? text.slice(0, marker) + "<url>" : text).slice(0, 200);
}
