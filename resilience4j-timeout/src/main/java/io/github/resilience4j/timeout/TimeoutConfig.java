package io.github.resilience4j.timeout;

import io.github.resilience4j.timeout.internal.TimeoutExecutors;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;

public class TimeoutConfig {
    private static final String TIMEOUT_DURATION_MUST_NOT_BE_NULL = "TimeoutDuration must not be null";
    private static final String CANCEL_ON_EXCEPTION_MUST_NOT_BE_NULL = "CancelOnExecution must not be null";
    private static final String EXECUTOR_SERVICE_MUST_NOT_BE_NULL = "ExecutorService must not be null";

    private Duration timeoutDuration =  Duration.ofSeconds(1);
    private Boolean cancelOnException = TRUE;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Executor executor = TimeoutExecutors.timed(executorService, timeoutDuration);

    private TimeoutConfig() {
    }

    /**
     * Returns a builder to create a custom TimeoutConfig.
     *
     * @return a {@link TimeoutConfig.Builder}
     */
    public static Builder custom() {
        return new Builder();
    }

    /**
     * Creates a default Timeout configuration.
     *
     * @return a default Timeout configuration.
     */
    public static TimeoutConfig ofDefaults(){
        return new Builder().build();
    }

    public Duration getTimeoutDuration() {
        return timeoutDuration;
    }

    public Boolean shouldCancelOnException() {
       return cancelOnException;
    }

    public Executor getExecutor() {
        return executor;
    }

    @Override public String toString() {
        return "TimeoutConfig{" +
                "timeoutDuration=" + timeoutDuration +
                "cancelOnException=" + cancelOnException +
                "executor=" + executor +
                '}';
    }

    public static class Builder {

        private TimeoutConfig config = new TimeoutConfig();

        /**
         * Builds a TimeoutConfig
         *
         * @return the TimeoutConfig
         */
        public TimeoutConfig build() {
            config.executor = TimeoutExecutors.timed(config.executorService, config.timeoutDuration);
            return config;
        }

        /**
         * Configures the thread execution timeout
         * Default value is 5 seconds.
         *
         * @param timeoutDuration the timeout Duration
         * @return the TimeoutConfig.Builder
         */
        public Builder timeoutDuration(final Duration timeoutDuration) {
            config.timeoutDuration = checkTimeoutDuration(timeoutDuration);
            return this;
        }

        /**
         * Configures canceling on Future thread execution
         * Default value is TRUE
         *
         * @param cancelOnException should cancel on exception
         * @return the TimeoutConfig.Builder
         */
        public Builder cancelOnException(final Boolean cancelOnException) {
            config.cancelOnException = checkCancelOnException(cancelOnException);
            return this;
        }

        /**
         * Configures a custom ExecutorService
         * Default is a DelegatingExecutor that handles DecoratedCallables
         *
         * @param executorService the executor service
         * @return the TimeoutConfig.Builder
         */
        public Builder executorService(final ExecutorService executorService) {
            config.executorService = checkExecutorService(executorService);
            return this;
        }

    }

    private static Duration checkTimeoutDuration(final Duration timeoutDuration) {
        return requireNonNull(timeoutDuration, TIMEOUT_DURATION_MUST_NOT_BE_NULL);
    }

    private static Boolean checkCancelOnException(final Boolean cancelOnException) {
        return requireNonNull(cancelOnException, CANCEL_ON_EXCEPTION_MUST_NOT_BE_NULL);
    }

    private static ExecutorService checkExecutorService(final ExecutorService executorService) {
        return requireNonNull(executorService, EXECUTOR_SERVICE_MUST_NOT_BE_NULL);
    }

}
