using System.Net;

namespace BlueKnightYoutube;

/// <summary>
/// What the caller asked for.
///
/// Cookies arrive as a file path rather than on the command line, because a
/// command line is visible to every other process on the machine and a session
/// is exactly the kind of thing that must not be.
/// </summary>
internal sealed class CommandLine
{
    public string Command { get; private init; } = "info";
    public string Url { get; private init; } = "";
    public string Output { get; private init; } = ".";
    public string FFmpeg { get; private init; } = "ffmpeg";
    public string? Proxy { get; private init; }
    public int MaxHeight { get; private init; }
    public bool AudioOnly { get; private init; }
    public bool Subtitles { get; private init; }
    public IReadOnlyList<Cookie> Cookies { get; private init; } = Array.Empty<Cookie>();

    public static CommandLine Parse(string[] args)
    {
        if (args.Length < 2)
            throw new ArgumentException("usage: blueknight-youtube <info|download> <url> [options]");

        string? Value(string name)
        {
            var index = Array.IndexOf(args, name);
            return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
        }

        var height = 0;
        var quality = Value("--quality");
        if (!string.IsNullOrWhiteSpace(quality) && quality != "best")
            int.TryParse(new string(quality.TakeWhile(char.IsDigit).ToArray()), out height);

        var jar = Value("--cookies");

        return new CommandLine
        {
            Command = args[0],
            Url = args[1],
            Output = Value("--output") ?? ".",
            FFmpeg = Value("--ffmpeg") ?? "ffmpeg",
            Proxy = Value("--proxy"),
            MaxHeight = height,
            AudioOnly = args.Contains("--audio-only"),
            Subtitles = args.Contains("--subtitles"),
            Cookies = string.IsNullOrWhiteSpace(jar) ? Array.Empty<Cookie>() : ReadJar(jar),
        };
    }

    /// <summary>
    /// Read a Netscape cookies.txt, which is the shape the rest of the app
    /// already stores sessions in.
    ///
    /// A malformed line is skipped rather than thrown on: a jar is often
    /// hand-exported, and one bad row should not cost a download that the
    /// other rows would have allowed.
    /// </summary>
    private static IReadOnlyList<Cookie> ReadJar(string path)
    {
        var cookies = new List<Cookie>();
        foreach (var raw in File.ReadLines(path))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line.StartsWith('#'))
                continue;
            var parts = line.Split('\t');
            if (parts.Length < 7)
                continue;
            try
            {
                var domain = parts[0].TrimStart('.');
                cookies.Add(new Cookie(parts[5], parts[6], parts[2], domain));
            }
            catch (CookieException)
            {
                // A name or value .NET will not accept. Skip that one row.
            }
        }
        return cookies;
    }
}
