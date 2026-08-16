/*
 * The desktop host for the YouTube.js engine.
 *
 * Deno already ships with this app for yt-dlp's challenge solver, so the whole
 * engine costs a script and a vendored library rather than another runtime.
 *
 * Run as:
 *   deno run --allow-net --allow-env --no-remote deno_main.mjs <videoId> [proxy]
 *
 * Prints one JSON object and exits. Errors are reported in that object rather
 * than thrown, so the caller has one thing to parse either way.
 */

import { Innertube, ClientType } from "./youtubei.bundle.mjs";
import { resolve } from "./engine.mjs";

const [videoId, proxy] = Deno.args;

if (!videoId) {
  console.log(JSON.stringify({ ok: false, streams: [], attempts: [],
                               error: "no video id given" }));
  Deno.exit(2);
}

// Deno reads a proxy from the environment rather than from an argument, and it
// is set here rather than inherited so this engine cannot silently take a
// different route from the download that follows it — one address asking for
// a stream and another fetching it is what YouTube answers with 403.
if (proxy) {
  Deno.env.set("HTTP_PROXY", proxy);
  Deno.env.set("HTTPS_PROXY", proxy);
}

try {
  const result = await resolve(Innertube, ClientType, videoId);
  console.log(JSON.stringify(result));
  Deno.exit(result.ok ? 0 : 1);
} catch (failure) {
  console.log(JSON.stringify({
    ok: false, streams: [], attempts: [],
    error: String(failure?.message ?? failure).slice(0, 300),
  }));
  Deno.exit(1);
}
