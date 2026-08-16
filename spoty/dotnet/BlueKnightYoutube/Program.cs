using System.Globalization;
using System.Net;
using System.Text.Json;
using System.Text.Json.Nodes;
using YoutubeExplode;
using YoutubeExplode.Common;
using YoutubeExplode.Converter;
using YoutubeExplode.Exceptions;
using YoutubeExplode.Videos.ClosedCaptions;
using YoutubeExplode.Videos.Streams;

namespace BlueKnightYoutube;

/// <summary>
/// The desktop's second YouTube engine, spoken to over stdin/stdout as JSON.
///
/// One line of JSON per event so the Python side can read it as it arrives
/// rather than waiting for the process to end — progress has to be live, and a
/// download that only reports when it finishes is indistinguishable from one
/// that has hung.
/// </summary>
internal static class Program
{
    private static readonly JsonSerializerOptions Compact = new() { WriteIndented = false };

    private static async Task<int> Main(string[] args)
    {
        // Ctrl-C and a closed stdin both mean the same thing: the app that
        // started this is finished with it. Cancellation runs all the way
        // through YoutubeExplode's own calls rather than leaving a download
        // writing to a file nobody is waiting for.
        using var cancellation = new CancellationTokenSource();
        Console.CancelKeyPress += (_, eventArgs) =>
        {
            eventArgs.Cancel = true;
            cancellation.Cancel();
        };

        try
        {
            var options = CommandLine.Parse(args);
            var http = BuildHttpClient(options);
            var youtube = new YoutubeClient(http);

            return options.Command switch
            {
                "info" => await RunInfoAsync(youtube, options, cancellation.Token),
                "download" => await RunDownloadAsync(youtube, options, cancellation.Token),
                _ => Fail($"unknown command '{options.Command}'"),
            };
        }
        catch (OperationCanceledException)
        {
            // Not a failure, and specifically not one the caller should answer
            // by starting another engine.
            Emit(new { type = "cancelled" });
            return 130;
        }
        catch (Exception failure)
        {
            return Fail(Describe(failure), Classify(failure));
        }
    }

    /// <summary>
    /// The HTTP client every request goes through, including the ones
    /// YoutubeExplode makes for itself.
    /// </summary>
    private static HttpClient BuildHttpClient(CommandLine options)
    {
        var handler = new HttpClientHandler
        {
            AutomaticDecompression = DecompressionMethods.All,
            UseCookies = false,
        };

        if (!string.IsNullOrWhiteSpace(options.Proxy))
        {
            // The same proxy the rest of the app uses. A second engine leaving
            // by a different route than the first is how one address requests a
            // stream and another fetches it, which YouTube answers with 403.
            handler.Proxy = new WebProxy(options.Proxy);
            handler.UseProxy = true;
        }

        var http = new HttpClient(handler) { Timeout = TimeSpan.FromMinutes(30) };

        if (options.Cookies.Count > 0)
        {
            // Optional, and never assumed. Normal videos are fetched signed
            // out; a session is only carried when the caller passed one.
            handler.UseCookies = true;
            handler.CookieContainer = new CookieContainer();
            foreach (var cookie in options.Cookies)
                handler.CookieContainer.Add(cookie);
        }

        return http;
    }

    // -----------------------------------------------------------------------
    // info: what this video is, and everything it can be downloaded as
    // -----------------------------------------------------------------------
    private static async Task<int> RunInfoAsync(
        YoutubeClient youtube, CommandLine options, CancellationToken token)
    {
        var video = await Retry.RunAsync(
            () => youtube.Videos.GetAsync(options.Url, token).AsTask(), token);
        var manifest = await Retry.RunAsync(
            () => youtube.Videos.Streams.GetManifestAsync(options.Url, token).AsTask(), token);

        var formats = new JsonArray();
        foreach (var stream in manifest.GetVideoOnlyStreams())
            formats.Add(DescribeStream(stream));
        foreach (var stream in manifest.GetAudioOnlyStreams())
            formats.Add(DescribeStream(stream));
        foreach (var stream in manifest.GetMuxedStreams())
            formats.Add(DescribeStream(stream));

        Emit(new
        {
            type = "info",
            id = video.Id.Value,
            title = video.Title,
            author = video.Author.ChannelTitle,
            duration = video.Duration?.TotalSeconds ?? 0,
            thumbnail = video.Thumbnails.Count > 0
                ? video.Thumbnails.OrderByDescending(t => t.Resolution.Area).First().Url
                : null,
            formats,
        });
        return 0;
    }

    /// <summary>
    /// One stream, in the shape the rest of the app understands.
    ///
    /// The engine's own classes stop here on purpose: the interface and the
    /// fallback manager should not have to know what a YoutubeExplode object
    /// looks like to show a resolution.
    /// </summary>
    private static JsonObject DescribeStream(IStreamInfo stream)
    {
        var described = new JsonObject
        {
            ["engine"] = "youtube-explode",
            ["format_id"] = stream.GetType().Name + ":" + stream.Bitrate.BitsPerSecond,
            ["container"] = stream.Container.Name,
            ["bitrate"] = stream.Bitrate.BitsPerSecond,
            ["file_size"] = stream.Size.Bytes,
            ["has_video"] = stream is IVideoStreamInfo,
            ["has_audio"] = stream is IAudioStreamInfo,
        };

        if (stream is IVideoStreamInfo video)
        {
            described["video_codec"] = video.VideoCodec;
            described["width"] = video.VideoResolution.Width;
            described["height"] = video.VideoResolution.Height;
            described["fps"] = video.VideoQuality.Framerate;
            described["quality_label"] = video.VideoQuality.Label;
        }

        if (stream is IAudioStreamInfo audio)
        {
            described["audio_codec"] = audio.AudioCodec;
            // Kept even though only one track is downloaded today. Throwing it
            // away here is what makes alternative-language audio a rewrite
            // later rather than a setting.
            if (audio.AudioLanguage is { } language)
            {
                described["audio_language"] = language.Code;
                described["audio_language_name"] = language.Name;
                described["audio_is_default"] = audio.IsAudioLanguageDefault ?? false;
            }
        }

        return described;
    }

    // -----------------------------------------------------------------------
    // download: the whole attempt, owned end to end
    // -----------------------------------------------------------------------
    private static async Task<int> RunDownloadAsync(
        YoutubeClient youtube, CommandLine options, CancellationToken token)
    {
        var video = await Retry.RunAsync(
            () => youtube.Videos.GetAsync(options.Url, token).AsTask(), token);
        var manifest = await Retry.RunAsync(
            () => youtube.Videos.Streams.GetManifestAsync(options.Url, token).AsTask(), token);

        Emit(new
        {
            type = "manifest",
            video_streams = manifest.GetVideoOnlyStreams().Count(),
            audio_streams = manifest.GetAudioOnlyStreams().Count(),
            muxed_streams = manifest.GetMuxedStreams().Count(),
        });

        var selected = SelectStreams(manifest, options);
        if (selected.Count == 0)
            return Fail("no playable streams were offered for this video", "no-formats");

        var extension = options.AudioOnly
            ? selected.OfType<IAudioStreamInfo>().First().Container.Name
            : ChooseContainer(selected);
        var target = Path.Combine(
            options.Output, SafeName(video.Title) + "." + extension);
        Directory.CreateDirectory(options.Output);

        var progress = new Progress<double>(fraction =>
            Emit(new { type = "progress", percent = Math.Round(fraction * 100, 2) }));

        Emit(new { type = "target", path = Path.GetFileName(target) });

        // The library's own downloader, never the raw URL. Its stream handling
        // is where the missing Content-Length, the wrong Content-Length, the
        // 404 on a segment and the partial-request oddities are dealt with, and
        // those are not incidental — YouTube's CDN produces all of them.
        if (options.AudioOnly || selected.Count == 1)
        {
            await Retry.RunAsync(
                () => youtube.Videos.Streams.DownloadAsync(
                    selected[0], target, progress, token).AsTask(),
                token);
        }
        else
        {
            // Separate video and audio muxed by FFmpeg, copying rather than
            // re-encoding wherever the containers allow it. The best streams
            // YouTube offers are not muxed, so this is the normal path above
            // about 720p rather than a special case.
            var conversion = new ConversionRequestBuilder(target)
                .SetFFmpegPath(options.FFmpeg)
                .SetContainer(extension)
                .SetPreset(ConversionPreset.Medium)
                .Build();
            await Retry.RunAsync(
                () => youtube.Videos.DownloadAsync(
                    selected, conversion, progress, token).AsTask(),
                token);
        }

        await TryWriteSubtitlesAsync(youtube, options, target, token);

        Emit(new { type = "done", path = target });
        return 0;
    }

    /// <summary>Best video for the cap, plus the audio it needs.</summary>
    private static IReadOnlyList<IStreamInfo> SelectStreams(
        StreamManifest manifest, CommandLine options)
    {
        var audio = manifest.GetAudioOnlyStreams()
            .OrderByDescending(s => s.IsAudioLanguageDefault ?? false)
            .ThenByDescending(s => s.Bitrate.BitsPerSecond)
            .FirstOrDefault();

        if (options.AudioOnly)
            return audio is null ? Array.Empty<IStreamInfo>() : new IStreamInfo[] { audio };

        var video = manifest.GetVideoOnlyStreams()
            .Where(s => options.MaxHeight <= 0 || s.VideoResolution.Height <= options.MaxHeight)
            .OrderByDescending(s => s.VideoResolution.Height)
            .ThenByDescending(s => s.VideoQuality.Framerate)
            .FirstOrDefault();

        // A cap that matches nothing must not lose the download. Take the
        // smallest thing on offer rather than reporting no formats.
        video ??= manifest.GetVideoOnlyStreams()
            .OrderBy(s => s.VideoResolution.Height)
            .FirstOrDefault();

        if (video is not null && audio is not null)
            return new IStreamInfo[] { video, audio };

        var muxed = manifest.GetMuxedStreams()
            .Where(s => options.MaxHeight <= 0 || s.VideoResolution.Height <= options.MaxHeight)
            .OrderByDescending(s => s.VideoResolution.Height)
            .FirstOrDefault() ?? manifest.GetMuxedStreams().FirstOrDefault();

        if (muxed is not null)
            return new IStreamInfo[] { muxed };
        if (video is not null)
            return new IStreamInfo[] { video };
        return Array.Empty<IStreamInfo>();
    }

    /// <summary>mp4 when everything in it is mp4-shaped, mkv when it is not.</summary>
    private static string ChooseContainer(IReadOnlyList<IStreamInfo> streams)
    {
        var names = streams.Select(s => s.Container.Name.ToLowerInvariant()).ToList();
        if (names.All(n => n is "mp4" or "m4a"))
            return "mp4";
        if (names.All(n => n is "webm"))
            return "webm";
        return "mkv";
    }

    /// <summary>
    /// Subtitles are a bonus, so they never cost the video.
    /// </summary>
    private static async Task TryWriteSubtitlesAsync(
        YoutubeClient youtube, CommandLine options, string target, CancellationToken token)
    {
        if (!options.Subtitles)
            return;
        try
        {
            var tracks = await youtube.Videos.ClosedCaptions
                .GetManifestAsync(options.Url, token);
            foreach (var track in tracks.Tracks.Where(t => !t.IsAutoGenerated).Take(4))
            {
                var path = Path.ChangeExtension(target, $".{track.Language.Code}.srt");
                await youtube.Videos.ClosedCaptions.DownloadAsync(track, path, null, token);
                Emit(new { type = "subtitle", language = track.Language.Code });
            }
        }
        catch (OperationCanceledException)
        {
            throw;                      // stopping is not a subtitle problem
        }
        catch (Exception failure)
        {
            Emit(new { type = "warning", message = "subtitles unavailable: " + Describe(failure) });
        }
    }

    // -----------------------------------------------------------------------
    private static string SafeName(string title)
    {
        var cleaned = new string(title
            .Select(c => Path.GetInvalidFileNameChars().Contains(c) ? '_' : c)
            .ToArray()).Trim().TrimEnd('.');
        if (cleaned.Length > 120)
            cleaned = cleaned[..120].Trim();
        return string.IsNullOrWhiteSpace(cleaned) ? "video" : cleaned;
    }

    /// <summary>
    /// What the caller needs to decide whether another engine is worth trying.
    /// A video that does not exist is not a reason to run the next extractor.
    /// </summary>
    private static string Classify(Exception failure) => failure switch
    {
        VideoRequiresPurchaseException => "unavailable",
        VideoUnavailableException => "unavailable",
        VideoUnplayableException => "unplayable",
        RequestLimitExceededException => "rate-limited",
        YoutubeExplodeException => "extraction",
        HttpRequestException => "network",
        IOException => "network",
        _ => "error",
    };

    /// <summary>
    /// The message, with anything sensitive kept out of it. A signed media URL
    /// carries a session and an address in its query string, so it is never
    /// what gets printed.
    /// </summary>
    private static string Describe(Exception failure)
    {
        var message = failure.Message.Replace("\r", " ").Replace("\n", " ");
        var marker = message.IndexOf("https://", StringComparison.OrdinalIgnoreCase);
        if (marker >= 0)
            message = message[..marker] + "<url>";
        return message.Length > 400 ? message[..400] : message;
    }

    private static int Fail(string message, string reason = "error")
    {
        Emit(new { type = "error", reason, message });
        return 1;
    }

    /// <summary>One event, one line, flushed — the caller reads as it goes.</summary>
    private static void Emit(object payload)
    {
        Console.Out.WriteLine(JsonSerializer.Serialize(payload, Compact));
        Console.Out.Flush();
    }
}
