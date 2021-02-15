package uk.org.webcompere.completablefuture.retry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RetriesTest {

    private static final Duration WAIT_BETWEEN = Duration.ofMillis(20);

    @Mock
    private Supplier<String> operation;

    private Supplier<CompletableFuture<String>> attempter;

    @BeforeEach
    void beforeEach() {
        attempter = () -> CompletableFuture.supplyAsync(operation);
    }

    @Test
    void givenAWorkingAttempterThenTheResultIsProvided() throws Exception {
        given(operation.get())
                .willReturn("yes!!");

        var result = Retries.withRetries(attempter, t -> false, 3, WAIT_BETWEEN).get();

        assertThat(result).isEqualTo("yes!!");
    }

    @Test
    void givenAContinuouslyFailingAttempterThenTheResultIsError() throws Exception {
        given(operation.get())
                .willThrow(new RuntimeException("boom!"));

        assertThatThrownBy(() -> Retries.withRetries(attempter, t -> true, 3, WAIT_BETWEEN).get())
            .hasRootCauseMessage("boom!");
    }

    @Test
    void givenAContinuouslyFailingAttempterThenThereAreMultipleAttempts() throws Exception {
        given(operation.get())
                .willThrow(new RuntimeException("boom!"));

        assertThatThrownBy(() -> Retries.withRetries(attempter, t -> true, 3, WAIT_BETWEEN).get())
                .hasRootCauseMessage("boom!");

        then(operation)
                .should(times(3))
                .get();
    }

    @Test
    void givenAFailingThenSucceedingAttempterThenThereAreMultipleAttempts() throws Exception {
        given(operation.get())
                .willThrow(new RuntimeException("boom!"))
                .willThrow(new RuntimeException("boom!"))
                .willReturn("yes!!");

        var result = Retries.withRetries(attempter, t -> true, 3, WAIT_BETWEEN).get();

        assertThat(result).isEqualTo("yes!!");

        then(operation)
                .should(times(3))
                .get();
    }

    @Test
    void givenAnOperationWhichFailsWithAnUnhandleableExceptionThenFailsAtUnhandled() {
        given(operation.get())
                .willThrow(new RuntimeException("boom!"))
                .willThrow(new IllegalStateException("boom!"))
                .willReturn("yes!!");

        assertThatThrownBy(() -> Retries.withRetries(attempter, t -> t.getClass().equals(RuntimeException.class),
                3, WAIT_BETWEEN).get())
                .hasRootCauseMessage("boom!");

        then(operation)
                .should(times(2))
                .get();
    }

}