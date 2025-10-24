package com.wallets.features.query;

import java.util.List;

/**
 * Response DTO for paginated wallet history.
 */
public record WalletHistoryResponse(
        List<WalletEventDTO> events,
        int page,
        int size,
        long totalEvents,
        boolean hasNext
) {
}
