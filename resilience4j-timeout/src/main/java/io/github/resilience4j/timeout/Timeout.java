package io.github.resilience4j.timeout;

import io.github.resilience4j.timeout.internal.DelegatingExecutors;
import io.github.resilience4j.timeout.internal.TimeoutContext;
import io.github.resilience4j.timeout.internal.TimeoutExecutors;
import io.vavr.control.Try;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * A Timeout decorator stops execution at a configurable rate.
 */
public interface Timeout {

    /**
     * Creates a Timeout decorator with a default TimeoutConfig configuration.
     *
     * @return The {@link Timeout}
     */
    static Timeout ofDefaults() {
        return new TimeoutContext(TimeoutConfig.ofDefaults());
    }

    /**
     * Creates a Timeout decorator with a TimeoutConfig configuration.
     *
     * @param timeoutConfig the TimeoutConfig
     * @return The {@link Timeout}
     */
    static Timeout of(TimeoutConfig timeoutConfig) {
        return new TimeoutContext(TimeoutConfig.ofDefaults());
    }

    /**
     * Creates a Timeout decorator with a timeout Duration.
     *
     * @param timeoutDuration the timeout Duration
     * @return The {@link Timeout}
     */
    static Timeout of(Duration timeoutDuration) {
        TimeoutConfig timeoutConfig = TimeoutConfig.custom()
                .timeoutDuration(timeoutDuration)
                .build();

        return new TimeoutContext(timeoutConfig);
    }

    /**
     * Creates a future which is restricted by a Timeout.
     *
     * @param timeout     the Timeout
     * @param future    the original callable
     * @param <T> the type of results supplied supplier
     * @param <F> the future type supplied
     * @return a future which is restricted by a Timeout.
     */
    static <T, F extends Future<T>> F decorateFuture(Timeout timeout, F future) {
        return waitForFuture(timeout, future);
    }

    /**
     * Get the TimeoutConfig of this Timeout decorator.
     *
     * @return the TimeoutConfig of this Timeout decorator
     */
    TimeoutConfig getTimeoutConfig();

    /**
     * Decorates and executes the decorated Future.
     *
     * @param future the original Future
     *
     * @return the result of the decorated Callable.
     * @param <T> the result type of the future
     * @param <F> the type of Future
     * @throws Exception if unable to compute a result
     */
    default <T, F extends Future<T>> T executeFuture(F future) throws Exception{
        return decorateFuture(this, future).get();
    }

    /**
     * Will wait for completion within default timeout duration.
     *
     * @param timeout     the Timeout
     * @param future      the original future
     * @param <T> the type of results supplied callable
     * @param <F> the type of Future
     * @throws TimeoutException if waiting time elapsed before executed completion.
     */
    static <T, F extends Future<T>> F waitForFuture(final Timeout timeout, final F future) throws TimeoutException {
        TimeoutConfig timeoutConfig = timeout.getTimeoutConfig();
        Duration timeoutDuration = timeoutConfig.getTimeoutDuration();
        Boolean cancelOnExecution = timeoutConfig.shouldCancelOnException();
        DelegatingExecutors.CompletableExecutorService executor = (DelegatingExecutors.CompletableExecutorService) timeoutConfig.getExecutor();

        Callable<T> callable = () -> Try.of(() -> future.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS))
                .getOrElseThrow(throwable -> {
                    if (cancelOnExecution && !future.isDone())
                        future.cancel(true);
                    return new TimeoutException(throwable);
                });

        return (F) executor.submit(callable);
    }
}
