package com.crablet.eventstore.internal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresNotifyPayloadTest {

    @Test
    void encodePayloadTypesOnly() {
        assertThat(PostgresNotifyPayload.encodePayload(Set.of("WalletCreated"), Set.of()))
                .isEqualTo("WalletCreated");
    }

    @Test
    void encodePayloadTypesAndTagKeys() {
        assertThat(PostgresNotifyPayload.encodePayload(
                Set.of("WalletUpdated", "WalletDeposited"), Set.of("wallet_id", "region")))
                .isEqualTo("WalletDeposited,WalletUpdated|region,wallet_id");
    }

    @Test
    void encodePayloadEmptyTypesIsWildcard() {
        assertThat(PostgresNotifyPayload.encodePayload(Set.of(), Set.of("wallet_id")))
                .isEqualTo("*");
    }

    @Test
    void encodePayloadCombinedTooLongFallsBackToTypesOnly() {
        String hugeTag = "y".repeat(8000);
        assertThat(PostgresNotifyPayload.encodePayload(Set.of("T"), Set.of(hugeTag)))
                .isEqualTo("T");
    }

    @Test
    void encodePayloadTypesPartTooLongFallsBackToWildcard() {
        String hugeType = "x".repeat(8000);
        assertThat(PostgresNotifyPayload.encodePayload(Set.of(hugeType), Set.of()))
                .isEqualTo("*");
    }
}
