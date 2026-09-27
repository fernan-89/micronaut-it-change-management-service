package com.thinklab.infrastructure.adapter.out.integration.operationwindow;

import com.thinklab.domain.exception.SchedulingConflictException;
import com.thinklab.infrastructure.adapter.out.integration.operationwindow.OperationWindowServiceAdapter.InitiateOperationWindowApiRequest;
import com.thinklab.infrastructure.adapter.out.integration.operationwindow.OperationWindowServiceAdapter.OperationWindowApiResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationWindowServiceAdapterTest {

    @Mock private OperationWindowApiClient apiClient;

    private OperationWindowServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OperationWindowServiceAdapter(apiClient);
    }

    @Test
    @DisplayName("reserveImplementationWindow requests a DEPLOYMENT window and returns its id")
    void reserveImplementationWindow() {
        UUID organisationId = UUID.randomUUID();
        Set<UUID> assets = Set.of(UUID.randomUUID());
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        UUID windowId = UUID.randomUUID();
        when(apiClient.initiate(any(), any(), any())).thenReturn(Mono.just(new OperationWindowApiResponse(windowId)));

        StepVerifier.create(adapter.reserveImplementationWindow(organisationId, "Upgrade firmware", assets, start, end, "op-1"))
                .expectNext(windowId)
                .verifyComplete();

        ArgumentCaptor<InitiateOperationWindowApiRequest> captor = ArgumentCaptor.forClass(InitiateOperationWindowApiRequest.class);
        verify(apiClient).initiate(eq(organisationId.toString()), eq("op-1"), captor.capture());
        assertEquals("Upgrade firmware", captor.getValue().title());
        assertEquals("DEPLOYMENT", captor.getValue().windowType());
        assertEquals(assets, captor.getValue().targetAssetIds());
        assertEquals(start, captor.getValue().startAt());
        assertEquals(end, captor.getValue().endAt());
    }

    @Test
    @DisplayName("a 409 from operation-window-service translates into SchedulingConflictException")
    void reserveImplementationWindowCollision() {
        HttpClientResponseException collision = new HttpClientResponseException("Conflict",
                HttpResponse.status(HttpStatus.CONFLICT));
        when(apiClient.initiate(any(), any(), any())).thenReturn(Mono.error(collision));

        StepVerifier.create(adapter.reserveImplementationWindow(UUID.randomUUID(), "t", Set.of(UUID.randomUUID()),
                        Instant.now(), Instant.now().plusSeconds(3600), "op-1"))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(SchedulingConflictException.class, error);
                    assertTrue(error.getMessage().contains("collides with an existing reservation"));
                })
                .verify();
    }

    @Test
    @DisplayName("a non-409 HTTP error from operation-window-service is a dependency failure, not a scheduling conflict")
    void reserveImplementationWindowOtherHttpError() {
        HttpClientResponseException notFound = new HttpClientResponseException("Not Found",
                HttpResponse.status(HttpStatus.NOT_FOUND));
        when(apiClient.initiate(any(), any(), any())).thenReturn(Mono.error(notFound));

        StepVerifier.create(adapter.reserveImplementationWindow(UUID.randomUUID(), "t", Set.of(UUID.randomUUID()),
                        Instant.now(), Instant.now().plusSeconds(3600), "op-1"))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(IllegalStateException.class, error);
                    assertTrue(error.getMessage().contains("rejected the request"));
                })
                .verify();
    }

    @Test
    @DisplayName("a non-HTTP failure (e.g. connection refused) is a generic dependency-unavailable error")
    void reserveImplementationWindowInfrastructureFailure() {
        when(apiClient.initiate(any(), any(), any())).thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(adapter.reserveImplementationWindow(UUID.randomUUID(), "t", Set.of(UUID.randomUUID()),
                        Instant.now(), Instant.now().plusSeconds(3600), "op-1"))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(IllegalStateException.class, error);
                    assertTrue(error.getMessage().contains("currently unavailable"));
                })
                .verify();
    }
}
