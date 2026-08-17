/*
 * Mint a proof-of-origin token, for yt-dlp rather than for ourselves.
 *
 * This is the one piece of the fallback machinery that helps the *primary*
 * engine. yt-dlp is refused as a bot before any fallback gets a turn, and a
 * token is what that refusal is asking for — so producing one here and handing
 * it to yt-dlp fixes the run that matters instead of the third attempt at it.
 *
 * Two ways to get one, cheapest first:
 *
 *   Cold start — derived from the visitor id alone. No BotGuard, no VM, no
 *                network beyond fetching the visitor id itself. Weaker than an
 *                attested token and enough for a great deal.
 *   Attested   — BotGuard's challenge actually run. Costs a round trip and a
 *                JavaScript VM, which is why it is second and not first.
 *
 * Runs unmodified under Deno and inside Android's WebView, like the resolver
 * beside it: the hosts differ, the engine does not.
 */

/**
 * @returns {Promise<{visitorData: string, poToken: string, method: string}>}
 */
export async function mint(Innertube, webpo, botguard = null) {
  // A session with no player fetch: all that is wanted here is the visitor id,
  // and asking for the player as well would spend a request on nothing.
  const session = await Innertube.create({ retrieve_player: false });
  const visitorData = session.session?.context?.client?.visitorData ?? "";
  if (!visitorData) {
    throw new Error("no visitor data came back");
  }

  if (botguard) {
    try {
      const attested = await attest(visitorData, webpo, botguard);
      if (attested) {
        return { visitorData, poToken: attested, method: "attested" };
      }
    } catch {
      // An attested token is the better one, not the required one. Falling
      // back to cold start is worth more than reporting no token at all.
    }
  }

  return {
    visitorData,
    poToken: webpo.createColdStartToken(visitorData),
    method: "cold-start",
  };
}

/**
 * Run BotGuard's challenge and mint against the integrity token it returns.
 *
 * Kept separate and optional because it is the part that needs a browser-like
 * host; the cold-start path above works anywhere.
 */
async function attest(visitorData, webpo, botguard) {
  const challenge = await botguard.getChallenge();
  if (!challenge) return null;

  const client = await botguard.BotGuardClient.create({
    program: challenge.program,
    globalName: challenge.globalName,
    globalObj: globalThis,
  });

  const result = await client.snapshot({ webPoSignalOutput: [] });
  if (!result) return null;

  const minter = await webpo.WebPoMinter.create(
    { integrityToken: result }, []);
  return minter.mintAsWebsafeString(visitorData);
}
