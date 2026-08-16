/* Offline check of the shaping logic: given what YouTube.js hands back, does
   this engine report it the way the rest of the app expects? */
import { resolve } from "./engine.mjs";

const ClientType = { ANDROID: 1, TV: 2, WEB_EMBEDDED: 3, MWEB: 4, WEB: 5 };
const fmt = (o) => ({ mime_type: 'video/mp4; codecs="avc1.640028"', ...o });

// First client offers nothing, second offers real streams: the engine must
// walk past the empty one and keep the useful one.
let created = 0;
const Innertube = {
  create: async () => {
    created++;
    const empty = created === 1;
    return {
      getInfo: async () => ({
        playability_status: { status: empty ? "LOGIN_REQUIRED" : "OK",
                              reason: empty ? "Sign in to confirm" : "" },
        basic_info: { title: "Example", author: "Chan", duration: 12 },
        streaming_data: empty ? { adaptive_formats: [], formats: [] } : {
          adaptive_formats: [
            fmt({ url: "https://x/v", height: 1080, width: 1920, fps: 30,
                  bitrate: 900, content_length: 5, has_video: true, has_audio: false }),
            { mime_type: 'audio/mp4; codecs="mp4a.40.2"', url: "https://x/a",
              bitrate: 128, has_video: false, has_audio: true, language: "en",
              audio_track: { audio_is_default: true } },
            // No URL: server-side streaming, nothing to fetch. Must be dropped.
            fmt({ height: 2160, has_video: true, has_audio: false }),
          ],
          formats: [],
        },
      }),
    };
  },
};

const out = await resolve(Innertube, ClientType, "abc");
const checks = [
  ["walked past the empty client", out.client === "TV"],
  ["reported the useful one",      out.ok === true],
  ["dropped the url-less format",  out.streams.length === 2],
  ["kept resolution",              out.streams[0].height === 1080],
  ["parsed the video codec",       out.streams[0].video_codec === "avc1.640028"],
  ["marked video-only",            out.streams[0].video_only === true],
  ["parsed the audio codec",       out.streams[1].audio_codec === "mp4a.40.2"],
  ["kept the audio language",      out.streams[1].audio_language === "en"],
  ["kept default-audio flag",      out.streams[1].is_default_audio === true],
  ["recorded the failed attempt",  out.attempts.length === 1],
];
let bad = 0;
for (const [name, pass] of checks) {
  if (!pass) bad++;
  console.log(`  ${pass ? "ok " : "BAD"} ${name}`);
}
Deno.exit(bad);
