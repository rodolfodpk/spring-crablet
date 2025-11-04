package com.crablet.outbox.management;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for OutboxManagementController.
 * Tests all REST endpoints using MockMvc (standalone setup).
 */
@DisplayName("OutboxManagementController Unit Tests")
class OutboxManagementControllerTest {

    private MockMvc mockMvc;
    private OutboxManagementService outboxManagementService;

    private OutboxManagementService.PublisherStatus testStatus;

    @BeforeEach
    void setUp() {
        outboxManagementService = mock(OutboxManagementService.class);
        OutboxManagementController controller = new OutboxManagementController(outboxManagementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        
        testStatus = new OutboxManagementService.PublisherStatus(
                "test-publisher",
                "ACTIVE",
                100L,
                Instant.now(),
                0,
                null,
                Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/outbox/publishers should return 200 with list")
    void getAllPublisherStatus_ShouldReturn200WithList() throws Exception {
        // Given
        when(outboxManagementService.getAllPublisherStatus())
                .thenReturn(List.of(testStatus));

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].publisherName").value("test-publisher"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(outboxManagementService).getAllPublisherStatus();
    }

    @Test
    @DisplayName("GET /api/outbox/publishers should return 200 with empty list")
    void getAllPublisherStatus_ShouldReturn200WithEmptyList() throws Exception {
        // Given
        when(outboxManagementService.getAllPublisherStatus())
                .thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(outboxManagementService).getAllPublisherStatus();
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/{name} should return 200 when found")
    void getPublisherStatus_ShouldReturn200_WhenFound() throws Exception {
        // Given
        when(outboxManagementService.getPublisherStatus("test-publisher"))
                .thenReturn(testStatus);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/test-publisher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisherName").value("test-publisher"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(outboxManagementService).getPublisherStatus("test-publisher");
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/{name} should return 404 when not found")
    void getPublisherStatus_ShouldReturn404_WhenNotFound() throws Exception {
        // Given
        when(outboxManagementService.getPublisherStatus("non-existent"))
                .thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/non-existent"))
                .andExpect(status().isNotFound());

        verify(outboxManagementService).getPublisherStatus("non-existent");
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/lag should return 200 with map")
    void getPublisherLag_ShouldReturn200WithMap() throws Exception {
        // Given
        Map<String, Long> lag = Map.of("publisher1", 10L, "publisher2", 5L);
        when(outboxManagementService.getPublisherLag()).thenReturn(lag);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/lag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher1").value(10))
                .andExpect(jsonPath("$.publisher2").value(5));

        verify(outboxManagementService).getPublisherLag();
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/leaders should return 200 with map")
    void getCurrentLeaders_ShouldReturn200WithMap() throws Exception {
        // Given
        Map<String, String> leaders = Map.of("publisher1", "instance-1", "publisher2", "instance-2");
        when(outboxManagementService.getCurrentLeaders()).thenReturn(leaders);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/leaders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher1").value("instance-1"))
                .andExpect(jsonPath("$.publisher2").value("instance-2"));

        verify(outboxManagementService).getCurrentLeaders();
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/backoff should return 200 with map")
    void getBackoffInfo_ShouldReturn200WithMap() throws Exception {
        // Given
        OutboxManagementService.BackoffInfo info = new OutboxManagementService.BackoffInfo(5, 3);
        Map<String, OutboxManagementService.BackoffInfo> backoffInfo = Map.of("publisher1", info);
        when(outboxManagementService.getBackoffInfo()).thenReturn(backoffInfo);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/backoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisher1.emptyPollCount").value(5))
                .andExpect(jsonPath("$.publisher1.currentSkipCounter").value(3));

        verify(outboxManagementService).getBackoffInfo();
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/{topic}/{publisher}/backoff should return 200 when found")
    void getPublisherBackoffInfo_ShouldReturn200_WhenFound() throws Exception {
        // Given
        OutboxManagementService.BackoffInfo info = new OutboxManagementService.BackoffInfo(5, 3);
        when(outboxManagementService.getBackoffInfo("topic1", "publisher1"))
                .thenReturn(info);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/topic1/publisher1/backoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emptyPollCount").value(5))
                .andExpect(jsonPath("$.currentSkipCounter").value(3));

        verify(outboxManagementService).getBackoffInfo("topic1", "publisher1");
    }

    @Test
    @DisplayName("GET /api/outbox/publishers/{topic}/{publisher}/backoff should return 404 when not found")
    void getPublisherBackoffInfo_ShouldReturn404_WhenNotFound() throws Exception {
        // Given
        when(outboxManagementService.getBackoffInfo("topic1", "non-existent"))
                .thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/outbox/publishers/topic1/non-existent/backoff"))
                .andExpect(status().isNotFound());

        verify(outboxManagementService).getBackoffInfo("topic1", "non-existent");
    }

    @Test
    @DisplayName("POST /api/outbox/publishers/{name}/pause should return 200 when success")
    void pausePublisher_ShouldReturn200_WhenSuccess() throws Exception {
        // Given
        when(outboxManagementService.pausePublisher("test-publisher"))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/outbox/publishers/test-publisher/pause"))
                .andExpect(status().isOk());

        verify(outboxManagementService).pausePublisher("test-publisher");
    }

    @Test
    @DisplayName("POST /api/outbox/publishers/{name}/pause should return 404 when not found")
    void pausePublisher_ShouldReturn404_WhenNotFound() throws Exception {
        // Given
        when(outboxManagementService.pausePublisher("non-existent"))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/outbox/publishers/non-existent/pause"))
                .andExpect(status().isNotFound());

        verify(outboxManagementService).pausePublisher("non-existent");
    }

    @Test
    @DisplayName("POST /api/outbox/publishers/{name}/resume should return 200 when success")
    void resumePublisher_ShouldReturn200_WhenSuccess() throws Exception {
        // Given
        when(outboxManagementService.resumePublisher("test-publisher"))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/outbox/publishers/test-publisher/resume"))
                .andExpect(status().isOk());

        verify(outboxManagementService).resumePublisher("test-publisher");
    }

    @Test
    @DisplayName("POST /api/outbox/publishers/{name}/resume should return 404 when not found")
    void resumePublisher_ShouldReturn404_WhenNotFound() throws Exception {
        // Given
        when(outboxManagementService.resumePublisher("non-existent"))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/outbox/publishers/non-existent/resume"))
                .andExpect(status().isNotFound());

        verify(outboxManagementService).resumePublisher("non-existent");
    }

    @Test
    @DisplayName("POST /api/outbox/publishers/{name}/reset should return 200 when success")
    void resetPublisher_ShouldReturn200_WhenSuccess() throws Exception {
        // Given
        when(outboxManagementService.resetPublisher("test-publisher"))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/outbox/publishers/test-publisher/reset"))
                .andExpect(status().isOk());

        verify(outboxManagementService).resetPublisher("test-publisher");
    }

    @Test
    @DisplayName("POST /api/outbox/publishers/{name}/reset should return 404 when not found")
    void resetPublisher_ShouldReturn404_WhenNotFound() throws Exception {
        // Given
        when(outboxManagementService.resetPublisher("non-existent"))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/outbox/publishers/non-existent/reset"))
                .andExpect(status().isNotFound());

        verify(outboxManagementService).resetPublisher("non-existent");
    }
}

