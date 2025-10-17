package com.wallets.features.query;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for individual event in wallet history.
 */
public record WalletEventDTO(
    String eventType,
    Instant occurredAt,
    Map<String, Object> data
) {}
