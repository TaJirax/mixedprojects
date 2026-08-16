using YoutubeExplode.Exceptions;

namespace BlueKnightYoutube;

/// <summary>
/// Bounded retries for the failures that are worth one.
///
/// A connection that dropped is worth asking again; a video that does not
/// exist is not, and retrying it only spends the caller's time before telling
/// them the same thing. Cancellation is never retried — it is an instruction,
/// not a fault.
/// </summary>
internal static class Retry
{
    private const int Attempts = 3;

    public static async Task RunAsync(Func<Task> work, CancellationToken token)
    {
        await RunAsync<object?>(async () => { await work(); return null; }, token);
    }

    public static async Task<T> RunAsync<T>(Func<Task<T>> work, CancellationToken token)
    {
        for (var attempt = 1; ; attempt++)
        {
            token.ThrowIfCancellationRequested();
            try
            {
                return await work();
            }
            catch (Exception failure) when (attempt < Attempts && IsWorthRetrying(failure))
            {
                // Short and widening. Long enough to outlast a blip, short
                // enough that three of them do not feel like a hang.
                await Task.Delay(TimeSpan.FromSeconds(attempt), token);
            }
        }
    }

    private static bool IsWorthRetrying(Exception failure) => failure switch
    {
        OperationCanceledException => false,
        // Gone, private, paid for, or region-locked: asking again changes none
        // of these.
        VideoUnavailableException => false,
        VideoUnplayableException => false,
        VideoRequiresPurchaseException => false,
        HttpRequestException => true,
        IOException => true,
        TimeoutException => true,
        _ => false,
    };
}
