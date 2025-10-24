package crablet.unit.outbox.impl;

import com.crablet.outbox.impl.OutboxManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxManagementServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private OutboxManagementService service;

    @BeforeEach
    void setUp() {
        service = new OutboxManagementService(jdbcTemplate);
    }

    @Test
    void shouldPausePublisherSuccessfully() {
        // Given
        String publisherName = "CountDownLatchPublisher";
        when(jdbcTemplate.update(anyString(), eq(publisherName))).thenReturn(1);

        // When
        boolean result = service.pausePublisher(publisherName);

        // Then
        assertThat(result).isTrue();
        verify(jdbcTemplate).update(
            contains("UPDATE outbox_topic_progress SET status = 'PAUSED'"),
            eq(publisherName)
        );
    }

    @Test
    void shouldReturnFalseWhenPublisherNotFoundForPause() {
        // Given
        String publisherName = "NonExistentPublisher";
        when(jdbcTemplate.update(anyString(), eq(publisherName))).thenReturn(0);

        // When
        boolean result = service.pausePublisher(publisherName);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldResumePublisherSuccessfully() {
        // Given
        String publisherName = "CountDownLatchPublisher";
        when(jdbcTemplate.update(anyString(), eq(publisherName))).thenReturn(1);

        // When
        boolean result = service.resumePublisher(publisherName);

        // Then
        assertThat(result).isTrue();
        verify(jdbcTemplate).update(
            contains("UPDATE outbox_topic_progress SET status = 'ACTIVE'"),
            eq(publisherName)
        );
    }

    @Test
    void shouldResetPublisherSuccessfully() {
        // Given
        String publisherName = "CountDownLatchPublisher";
        when(jdbcTemplate.update(anyString(), eq(publisherName))).thenReturn(1);

        // When
        boolean result = service.resetPublisher(publisherName);

        // Then
        assertThat(result).isTrue();
        verify(jdbcTemplate).update(
            contains("UPDATE outbox_topic_progress"),
            eq(publisherName)
        );
    }

    @Test
    void shouldCheckPublisherExists() {
        // Given
        String publisherName = "CountDownLatchPublisher";
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(publisherName)))
            .thenReturn(1);

        // When
        boolean result = service.publisherExists(publisherName);

        // Then
        assertThat(result).isTrue();
        verify(jdbcTemplate).queryForObject(
            contains("SELECT COUNT(*) FROM outbox_topic_progress"),
            eq(Integer.class),
            eq(publisherName)
        );
    }
}
