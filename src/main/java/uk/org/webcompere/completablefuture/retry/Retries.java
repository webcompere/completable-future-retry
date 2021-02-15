package uk.org.webcompere.completablefuture.retry;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Build retry logic for a {@link java.util.concurrent.CompletableFuture}
 */
public class Retries {

    /**
     * Used to manage waiting before a retry is attempted
     */
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    /**
     * Compose a {@link CompletableFuture} using the <code>attempter</code> to create the first
     * attempt and any retries permitted by the <code>shouldRetry</code> predicate. All retries wait
     * for the <code>waitBetween</code> before going again, up to a maximum number of attempts
     * @param attempter produce an attempt as a {@link CompletableFuture}
     * @param shouldRetry determines whether a {@link Throwable} is retryable
     * @param attempts the number of attempts to make before allowing failure
     * @param waitBetween the duration of waiting between attempts
     * @param <T> the type of value the future will return
     * @return a composite {@link CompletableFuture} that runs until success or total failure
     */
    public static <T> CompletableFuture<T> withRetries(Supplier<CompletableFuture<T>> attempter,
                                                       Predicate<Throwable> shouldRetry,
                                                       int attempts, Duration waitBetween) {
        // retries run via the scheduler
        Executor scheduler = runnable -> SCHEDULER.schedule(runnable, waitBetween.toMillis(), TimeUnit.MILLISECONDS);

        // start with the first link in the chain
        CompletableFuture<T> firstAttempt = attempter.get();

        return flatten(firstAttempt.thenApply(CompletableFuture::completedFuture)
                .exceptionally(throwable -> retry(attempter, 1, throwable, shouldRetry, attempts, scheduler)));
    }

    private static <T> CompletableFuture<T> retry(Supplier<CompletableFuture<T>> attempter,
                                                  int attemptsSoFar,
                                                  Throwable throwable,
                                                  Predicate<Throwable> shouldRetry,
                                                  int maxAttempts,
                                                  Executor scheduler) {
        // cannot retry if we're at the max attempts or the predicate doesn't like the error
        int nextAttempt = attemptsSoFar + 1;
        if (nextAttempt > maxAttempts || !shouldRetry.test(throwable.getCause())) {
            return CompletableFuture.failedFuture(throwable);
        }

        return flatten(flatten(CompletableFuture.supplyAsync(attempter, scheduler))
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally(nextThrowable ->
                        retry(attempter, nextAttempt, nextThrowable, shouldRetry, maxAttempts, scheduler)));
    }

    private static <T> CompletableFuture<T> flatten(CompletableFuture<CompletableFuture<T>> completableCompletable) {
        return completableCompletable.thenCompose(Function.identity());
    }
}
