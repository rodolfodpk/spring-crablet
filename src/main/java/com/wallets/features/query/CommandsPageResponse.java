package com.wallets.features.query;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response object for paginated commands.
 */
@Schema(description = "Paginated commands response")
public record CommandsPageResponse(
        @Schema(description = "List of commands with their events")
        List<CommandResponse> commands,

        @Schema(description = "Next cursor for pagination", example = "98765")
        String nextCursor,

        @Schema(description = "Whether there are more commands available")
        boolean hasMore
) {
}
