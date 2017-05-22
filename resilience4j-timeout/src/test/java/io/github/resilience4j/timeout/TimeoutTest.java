package io.github.resilience4j.timeout;

import io.github.resilience4j.timeout.internal.DecoratedFuture;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;

import static io.github.resilience4j.timeout.SleepStubber.doSleep;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TimeoutTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofNanos(1);
    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration SLEEP_DURATION = Duration.ofSeconds(1);

    private static final java.util.concurrent.TimeoutException JAVA_TIMEOUT_EXCEPTION = new java.util.concurrent.TimeoutException();

    private TimeoutConfig shortConfig;
    private TimeoutConfig longConfig;
    private Timeout timeout;

    @Before
    public void init() {
        shortConfig = TimeoutConfig.custom()
                .timeoutDuration(SHORT_TIMEOUT)
                .build();
        longConfig = TimeoutConfig.custom()
                .timeoutDuration(LONG_TIMEOUT)
                .build();
        timeout = mock(Timeout.class);
    }

    @Test
    public void construction() throws Exception {
        Timeout timeout = Timeout.of(shortConfig);
        then(timeout).isNotNull();
    }

    @Test
    public void decorateFuture() throws Throwable {
        when(timeout.getTimeoutConfig()).thenReturn(shortConfig);

        CompletableFuture future = mock(CompletableFuture.class);
        CompletableFuture decorated = Timeout.decorateFuture(timeout, future);

        doSleep(SLEEP_DURATION).when(future).get();
        doReturn(true).when(future).cancel(true);

        Try decoratedResult = Try.success(decorated).mapTry(Future::get);
        then(decoratedResult.isFailure()).isTrue();
        then(decoratedResult.getCause()).isInstanceOf(TimeoutException.class);
        then(decoratedResult.getCause()).hasCause(JAVA_TIMEOUT_EXCEPTION);
        verify(decorated, times(1)).cancel(any());

        when(timeout.getTimeoutConfig())
                .thenReturn(longConfig);

        Try secondResult = Try.success(decorated).mapTry(Future::get);
        then(secondResult.isSuccess()).isTrue();
    }
}
