package net.blueknight.newpipe;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

/**
 * The desktop's NewPipeExtractor engine.
 *
 * Prints one JSON object and exits, in the same shape the Android adapter
 * produces — so everything above this point in the app is shared between the
 * two platforms rather than written twice. This resolves only; the app's own
 * downloader fetches, because it already knows about proxies, progress, part
 * files and stopping.
 *
 * Usage: java -jar blueknight-newpipe.jar &lt;url&gt; [proxy]
 */
public final class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println(failure("no url given"));
            System.exit(2);
        }
        final String url = args[0];
        final String proxy = args.length > 1 ? args[1] : "";

        try {
            NewPipe.init(new SimpleDownloader(proxy), new Localization("en", "US"));
            final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
            System.out.println(describe(info).toString());
            System.exit(0);
        } catch (Throwable problem) {
            System.out.println(failure(problem.getClass().getSimpleName()
                    + ": " + String.valueOf(problem.getMessage())));
            System.exit(1);
        }
    }

    /** The same keys the Android adapter emits, so one reader serves both. */
    private static JSONObject describe(StreamInfo info) {
        final JSONArray audio = new JSONArray();
        for (AudioStream stream : info.getAudioStreams()) {
            if (stream.getContent() == null || stream.getContent().isEmpty()) continue;
            audio.put(new JSONObject()
                    .put("url", stream.getContent())
                    .put("format", stream.getFormat() == null
                            ? "" : stream.getFormat().getSuffix())
                    .put("bitrate", stream.getBitrate())
                    .put("delivery", stream.getDeliveryMethod().name()));
        }

        final JSONArray video = new JSONArray();
        final List<VideoStream> all = new ArrayList<>(info.getVideoOnlyStreams());
        all.addAll(info.getVideoStreams());
        for (VideoStream stream : all) {
            if (stream.getContent() == null || stream.getContent().isEmpty()) continue;
            video.put(new JSONObject()
                    .put("url", stream.getContent())
                    .put("format", stream.getFormat() == null
                            ? "" : stream.getFormat().getSuffix())
                    .put("resolution", String.valueOf(stream.getResolution()))
                    .put("height", stream.getHeight())
                    .put("width", stream.getWidth())
                    .put("fps", stream.getFps())
                    .put("bitrate", stream.getBitrate())
                    .put("video_only", stream.isVideoOnly()));
        }

        final JSONArray errors = new JSONArray();
        for (Throwable problem : info.getErrors()) {
            errors.put(String.valueOf(problem.getMessage()));
        }

        return new JSONObject()
                .put("title", info.getName())
                .put("duration", info.getDuration())
                .put("service", "YouTube")
                .put("hls", info.getHlsUrl() == null ? "" : info.getHlsUrl())
                .put("dash", info.getDashMpdUrl() == null ? "" : info.getDashMpdUrl())
                .put("audio", audio)
                .put("video", video)
                .put("errors", errors);
    }

    private static String failure(String message) {
        return new JSONObject()
                .put("title", "")
                .put("audio", new JSONArray())
                .put("video", new JSONArray())
                .put("errors", new JSONArray().put(message))
                .toString();
    }

    /**
     * The smallest downloader the extractor will accept.
     *
     * It takes the app's proxy so this engine cannot leave by a different route
     * than the download that follows it — one address asking for a stream and
     * another fetching it is what YouTube answers with 403.
     */
    private static final class SimpleDownloader extends Downloader {
        private final Proxy proxy;

        SimpleDownloader(String proxyUrl) {
            Proxy resolved = Proxy.NO_PROXY;
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                try {
                    final URI parsed = URI.create(proxyUrl);
                    final int port = parsed.getPort() > 0 ? parsed.getPort() : 8080;
                    final Proxy.Type type = String.valueOf(parsed.getScheme())
                            .startsWith("socks") ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
                    resolved = new Proxy(type,
                            new InetSocketAddress(parsed.getHost(), port));
                } catch (RuntimeException ignored) {
                    // An unusable proxy string must not stop the engine from
                    // trying directly; the app reports the proxy separately.
                }
            }
            this.proxy = resolved;
        }

        @Override
        public Response execute(Request request) throws IOException {
            final HttpURLConnection connection =
                    (HttpURLConnection) new URL(request.url()).openConnection(proxy);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(30_000);
            connection.setRequestMethod(request.httpMethod());
            for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
                for (String value : header.getValue()) {
                    connection.addRequestProperty(header.getKey(), value);
                }
            }
            if (request.dataToSend() != null) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(request.dataToSend());
            }

            final int status = connection.getResponseCode();
            // An error body is still a body, and the extractor reads it to find
            // out what the site actually said.
            try (InputStream stream = status >= 400
                    ? connection.getErrorStream() : connection.getInputStream()) {
                final String body = stream == null ? ""
                        : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return new Response(status, connection.getResponseMessage(),
                        connection.getHeaderFields(), body, connection.getURL().toString());
            }
        }
    }
}
