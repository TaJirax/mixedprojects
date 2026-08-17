/*
 * The desktop host for the token minter. Prints one JSON object.
 *   deno run --allow-net --allow-env --no-remote potoken_main.mjs [proxy]
 */
import { Innertube } from "./youtubei.bundle.mjs";
import * as webpo from "./bgutils-webpo.mjs";
import { mint } from "./potoken.mjs";

const [proxy] = Deno.args;
if (proxy) {
  Deno.env.set("HTTP_PROXY", proxy);
  Deno.env.set("HTTPS_PROXY", proxy);
}

try {
  // BotGuard is not passed here: Deno has no DOM for it, and the cold-start
  // path needs none. Android passes it, because a WebView has one.
  console.log(JSON.stringify(await mint(Innertube, webpo, null)));
} catch (failure) {
  console.log(JSON.stringify({ error: String(failure?.message ?? failure).slice(0, 200) }));
  Deno.exit(1);
}
