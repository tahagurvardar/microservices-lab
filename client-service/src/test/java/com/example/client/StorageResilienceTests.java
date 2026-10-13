package com.example.client;

import com.example.client.dto.ProcessedMessage;
import com.example.client.feign.StorageClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "resilience4j.retry.instances.storage.wait-duration=1ms")
@AutoConfigureMockMvc
class StorageResilienceTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CircuitBreakerRegistry circuits;
    @MockBean
    private StorageClient storage;
    private CircuitBreaker circuit;

    @BeforeEach
    void resetCircuit() {
        circuit = circuits.circuitBreaker("storage");
        circuit.reset();
    }

    @Test
    void normalCallSucceeds() throws Exception {
        when(storage.ping()).thenReturn(Map.of("status", "UP"));
        mvc.perform(get("/api/client/ping-storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageAnswer.status").value("UP"));
        verify(storage).ping();
        assertThat(circuit.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 429})
    void clientErrorsArePropagatedWithoutRetriesOrCircuitFailures(int code) throws Exception {
        when(storage.simulateError(code)).thenThrow(httpFailure(code));
        mvc.perform(get("/api/client/simulate-error/{status}", code))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.code").value("STORAGE_CLIENT_ERROR"))
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
        verify(storage).simulateError(code);
        assertThat(circuit.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void retryableFeignClientErrorIsStillNotRetried() throws Exception {
        when(storage.ping()).thenThrow(new RetryableException(429, "Rate limited",
                Request.HttpMethod.GET, (Long) null, request()));
        mvc.perform(get("/api/client/ping-storage")).andExpect(status().isTooManyRequests());
        verify(storage).ping();
        assertThat(circuit.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @ParameterizedTest
    @MethodSource("transientFailures")
    void transientFailureRetriesAndThenSucceeds(RuntimeException failure) throws Exception {
        when(storage.ping()).thenThrow(failure, failure).thenReturn(Map.of("status", "UP"));
        mvc.perform(get("/api/client/ping-storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageAnswer.status").value("UP"));
        verify(storage, times(3)).ping();
        assertThat(circuit.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @ParameterizedTest
    @MethodSource("transientFailures")
    void exhaustedTransientFailureMapsTo503(RuntimeException failure) throws Exception {
        when(storage.ping()).thenThrow(failure);
        mvc.perform(get("/api/client/ping-storage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Storage service is temporarily unavailable"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        verify(storage, times(3)).ping();
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void openCircuitReturns503WithoutCallingStorage() throws Exception {
        when(storage.ping()).thenThrow(httpFailure(500));
        for (int request = 0; request < 3; request++) {
            mvc.perform(get("/api/client/ping-storage"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("STORAGE_SERVICE_UNAVAILABLE"));
        }
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        mvc.perform(get("/api/client/ping-storage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_CIRCUIT_OPEN"));
        verify(storage, times(9)).ping();
    }

    @Test
    void halfOpenClosesAfterTwoSuccessfulTrials() throws Exception {
        circuit.transitionToOpenState();
        circuit.transitionToHalfOpenState();
        when(storage.ping()).thenReturn(Map.of("status", "UP"));
        mvc.perform(get("/api/client/ping-storage")).andExpect(status().isOk());
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        mvc.perform(get("/api/client/ping-storage")).andExpect(status().isOk());
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        verify(storage, times(2)).ping();
    }

    @Test
    void messageEndpointsAlsoRespectOpenCircuit() throws Exception {
        circuit.transitionToOpenState();
        mvc.perform(get("/api/messages"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_CIRCUIT_OPEN"));
        mvc.perform(post("/api/messages").contentType("application/json")
                        .content("{\"author\":\"Alice\",\"text\":\"hello world\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_CIRCUIT_OPEN"));
        verifyNoInteractions(storage);
    }

    @Test
    void messageCreationDoesNotReplayAnAmbiguousWrite() throws Exception {
        when(storage.saveMessage(any(ProcessedMessage.class)))
                .thenThrow(networkFailure(new SocketTimeoutException("Read timed out")));
        mvc.perform(post("/api/messages").contentType("application/json")
                        .content("{\"author\":\"Alice\",\"text\":\"hello world\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORAGE_SERVICE_UNAVAILABLE"));
        verify(storage).saveMessage(any(ProcessedMessage.class));
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    private static Stream<RuntimeException> transientFailures() {
        return Stream.of(httpFailure(500), httpFailure(503),
                networkFailure(new ConnectException("Connection refused")),
                networkFailure(new SocketTimeoutException("Read timed out")));
    }

    private static FeignException httpFailure(int code) {
        return FeignException.errorStatus("StorageClient#call", Response.builder()
                .request(request()).status(code).reason("Downstream error")
                .headers(Map.of()).build());
    }

    private static RetryableException networkFailure(Exception cause) {
        return new RetryableException(-1, "Storage I/O failure", Request.HttpMethod.GET,
                cause, (Long) null, request());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://storage-service:8082/api/storage/ping",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
