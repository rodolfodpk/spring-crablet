package com.wallets.features.query;

import com.crablet.core.EventStore;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.WalletQueryPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WalletQueryService handles read operations with optimized queries.
 * <p>
 * This service handles:
 * - Build queries for read-only operations
 * - Coordinate event fetching and state projection
 * - Pagination and DTO conversion for history endpoint
 * - Uses WalletStateProjector for read-specific projections
 */
@Service
public class WalletQueryService {

    private static final Logger log = LoggerFactory.getLogger(WalletQueryService.class);

    private final ObjectMapper objectMapper;
    private final WalletQueryRepository queryRepository;
    private final EventStore eventStore;
    private final WalletStateProjector walletStateProjector;

    public WalletQueryService(ObjectMapper objectMapper, WalletQueryRepository queryRepository, EventStore eventStore, WalletStateProjector walletStateProjector) {
        this.objectMapper = objectMapper;
        this.queryRepository = queryRepository;
        this.eventStore = eventStore;
        this.walletStateProjector = walletStateProjector;
    }

    /**
     * Get current wallet state (current balance).
     *
     * @param walletId The wallet ID
     * @return WalletResponse or null if wallet doesn't exist
     */
    public WalletResponse getWalletState(String walletId) {
        try {
            // Use the full decision model query that includes MoneyTransferred events
            Query query = WalletQueryPatterns.singleWalletDecisionModel(walletId);
            
            // Use new signature: query, cursor, stateType, projectors
            ProjectionResult<WalletState> result = 
                eventStore.project(query, com.crablet.core.Cursor.zero(), WalletState.class, List.of(walletStateProjector));
            
            WalletState walletState = result.state();
            if (walletState == null || walletState.isEmpty()) {
                return null;
            }
            
            return WalletResponse.from(walletState);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get historical wallet events with pagination.
     *
     * @param walletId  The wallet ID
     * @param timestamp The target timestamp (events up to this time)
     * @param page      The page number (0-based)
     * @param size      The page size
     * @return WalletHistoryResponse with paginated events
     */
    public WalletHistoryResponse getWalletHistory(String walletId, Instant timestamp, int page, int size) {
        try {
            // Database-level pagination and filtering for efficiency
            List<EventResponse> eventResponses = queryRepository.getWalletEvents(
                    walletId,
                    timestamp,      // SQL WHERE filtering (not in-memory)
                    size + 1,       // Fetch one extra to detect hasNext
                    page * size     // SQL OFFSET
            );

            log.debug("Query for wallet {} returned {} events, timestamp: {}", walletId, eventResponses.size(), timestamp);

            // Calculate pagination metadata
            boolean hasNext = eventResponses.size() > size;
            if (hasNext) {
                eventResponses = eventResponses.subList(0, size);
            }

            // Get total count for pagination metadata
            long totalEvents = queryRepository.getWalletEventsCount(walletId, timestamp);

            // Convert to DTOs (EventResponse.data() is Object, cast to String)
            List<WalletEventDTO> eventDTOs = eventResponses.stream()
                    .map(e -> new WalletEventDTO(
                            e.type(),
                            e.occurredAt(),
                            parseEventData((String) e.data())
                    ))
                    .collect(Collectors.toList());

            return new WalletHistoryResponse(
                    eventDTOs,
                    page,
                    size,
                    (int) totalEvents,
                    hasNext
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get wallet history", e);
        }
    }

    /**
     * Get wallet commands with pagination and timestamp filtering.
     *
     * @param walletId  The wallet ID
     * @param timestamp Filter commands after this timestamp (optional)
     * @param page      Page number (0-based)
     * @param size      Page size
     * @return WalletCommandsResponse with paginated commands
     */
    public WalletCommandsResponse getWalletCommands(String walletId, Instant timestamp, int page, int size) {
        try {
            // Query commands for this wallet using repository
            List<CommandResponse> commands = queryRepository.getCommandsForWallet(walletId, timestamp, page, size);

            // Calculate pagination info (repository returns size+1 to detect hasNext)
            boolean hasNext = commands.size() > size;
            if (hasNext) {
                commands = commands.subList(0, size); // Remove the extra item
            }

            // Convert to DTOs
            List<WalletCommandDTO> commandDTOs = convertToCommandDTOs(commands);
            long totalCommands = queryRepository.getTotalCommandsForWallet(walletId, timestamp);

            return new WalletCommandsResponse(
                    commandDTOs,
                    page,
                    size,
                    totalCommands,
                    hasNext
            );

        } catch (Exception e) {
            log.error("Error getting wallet commands for wallet: {}", walletId, e);
            throw new RuntimeException("Failed to get wallet commands", e);
        }
    }


    /**
     * Convert CommandResponse list to WalletCommandDTO list with parsed JSON data.
     */
    private List<WalletCommandDTO> convertToCommandDTOs(List<CommandResponse> commands) {
        return commands.stream()
                .map(command -> {
                    try {
                        // Parse the JSON data - data is a String, parse it to Map
                        Map<String, Object> dataMap;
                        if (command.data() instanceof String) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> parsed = objectMapper.readValue((String) command.data(), Map.class);
                            dataMap = parsed;
                        } else {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> castedMap = (Map<String, Object>) command.data();
                            dataMap = castedMap;
                        }

                        // Convert events to DTOs
                        List<WalletEventDTO> eventDTOs = command.events().stream()
                                .map(eventResponse -> new WalletEventDTO(
                                        eventResponse.type(),
                                        eventResponse.occurredAt(),
                                        parseEventData((String) eventResponse.data())
                                ))
                                .collect(Collectors.toList());

                        return new WalletCommandDTO(
                                command.transactionId(),
                                command.type(),
                                dataMap,
                                command.occurredAt(),
                                eventDTOs
                        );
                    } catch (Exception e) {
                        // Fallback to raw data if JSON parsing fails
                        return new WalletCommandDTO(
                                command.transactionId(),
                                command.type(),
                                Map.of("data", command.data()),
                                command.occurredAt(),
                                List.of()
                        );
                    }
                })
                .collect(Collectors.toList());
    }


    /**
     * Parse event data from JSON string to Map.
     *
     * @param jsonData JSON string representation of event data
     * @return Parsed event data as Map
     * @throws RuntimeException if parsing fails
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEventData(String jsonData) {
        try {
            return objectMapper.readValue(jsonData, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse event data", e);
        }
    }

}
