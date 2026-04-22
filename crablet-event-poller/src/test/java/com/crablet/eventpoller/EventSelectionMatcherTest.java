package com.crablet.eventpoller;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EventSelectionMatcher}.
 * Each filter type is tested independently and in combination to verify parity
 * with the SQL WHERE logic in {@link EventSelectionSqlBuilder}.
 */
@DisplayName("EventSelectionMatcher Unit Tests")
class EventSelectionMatcherTest {

    // --- helpers ---

    private static StoredEvent event(String type, String... keyValuePairs) {
        return new StoredEvent(type, Tag.of(keyValuePairs), new byte[0], "tx1", 1L, Instant.now());
    }

    private static EventSelection selection(
            Set<String> types, Set<String> required, Set<String> anyOf, Map<String, String> exact) {
        return new EventSelection() {
            @Override
            public Set<String> getEventTypes()   { return types; }
            @Override
            public Set<String> getRequiredTags() { return required; }
            @Override
            public Set<String> getAnyOfTags()    { return anyOf; }
            @Override
            public Map<String, String> getExactTags() { return exact; }
        };
    }

    // --- empty selection ---

    @Test
    @DisplayName("Empty selection matches any event")
    void emptySelection_matchesAny() {
        var e = event("WalletOpened", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Empty selection matches event with no tags")
    void emptySelection_matchesEventWithNoTags() {
        var e = event("SomeEvent");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    // --- event type filter ---

    @Test
    @DisplayName("Type filter: matching type returns true")
    void typeFilter_match() {
        var e = event("WalletOpened", "wallet_id", "w1");
        var sel = selection(Set.of("WalletOpened"), Set.of(), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Type filter: non-matching type returns false")
    void typeFilter_noMatch() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of("WalletOpened"), Set.of(), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Type filter: multiple types, one matches")
    void typeFilter_multipleTypes_oneMatches() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of("WalletOpened", "DepositMade"), Set.of(), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    // --- required tag keys ---

    @Test
    @DisplayName("Required tag: key present returns true")
    void requiredTag_present() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of("wallet_id"), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Required tag: key absent returns false")
    void requiredTag_absent() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of("deposit_id"), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Required tags: all present returns true")
    void requiredTags_allPresent() {
        var e = event("DepositMade", "wallet_id", "w1", "deposit_id", "d1");
        var sel = selection(Set.of(), Set.of("wallet_id", "deposit_id"), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Required tags: one absent returns false")
    void requiredTags_oneAbsent() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of("wallet_id", "deposit_id"), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Required tag: key present with any value matches")
    void requiredTag_keyPresent_anyValueMatches() {
        var e = event("DepositMade", "wallet_id", "anything");
        var sel = selection(Set.of(), Set.of("wallet_id"), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    // --- any-of tag keys ---

    @Test
    @DisplayName("Any-of tags: one key present returns true")
    void anyOfTags_onePresent() {
        var e = event("MoneyTransferred", "from_wallet_id", "w1");
        var sel = selection(Set.of(), Set.of(), Set.of("from_wallet_id", "to_wallet_id"), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Any-of tags: both keys present returns true")
    void anyOfTags_bothPresent() {
        var e = event("MoneyTransferred", "from_wallet_id", "w1", "to_wallet_id", "w2");
        var sel = selection(Set.of(), Set.of(), Set.of("from_wallet_id", "to_wallet_id"), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Any-of tags: no key present returns false")
    void anyOfTags_nonePresent() {
        var e = event("MoneyTransferred", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of(), Set.of("from_wallet_id", "to_wallet_id"), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Empty any-of set does not filter")
    void anyOfTags_emptySet_doesNotFilter() {
        var e = event("WalletOpened", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    // --- exact tag key=value pairs ---

    @Test
    @DisplayName("Exact tag: matching key and value returns true")
    void exactTag_match() {
        var e = event("WalletOpened", "wallet_id", "alice");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of("wallet_id", "alice"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Exact tag: matching key but wrong value returns false")
    void exactTag_wrongValue() {
        var e = event("WalletOpened", "wallet_id", "alice");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of("wallet_id", "bob"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Exact tag: key absent returns false")
    void exactTag_keyAbsent() {
        var e = event("WalletOpened", "wallet_id", "alice");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of("owner_id", "alice"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Exact tags: all pairs match returns true")
    void exactTags_allMatch() {
        var e = event("DepositMade", "wallet_id", "w1", "deposit_id", "d1");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of("wallet_id", "w1", "deposit_id", "d1"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Exact tags: one pair wrong returns false")
    void exactTags_onePairWrong() {
        var e = event("DepositMade", "wallet_id", "w1", "deposit_id", "d1");
        var sel = selection(Set.of(), Set.of(), Set.of(), Map.of("wallet_id", "w1", "deposit_id", "d99"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    // --- combined filters ---

    @Test
    @DisplayName("Combined: type + exact tag, both match")
    void combined_typeAndExactTag_match() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of("DepositMade"), Set.of(), Set.of(), Map.of("wallet_id", "w1"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Combined: type matches but exact tag does not")
    void combined_typeMatchExactTagMismatch() {
        var e = event("DepositMade", "wallet_id", "w1");
        var sel = selection(Set.of("DepositMade"), Set.of(), Set.of(), Map.of("wallet_id", "w2"));
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    @Test
    @DisplayName("Combined: required tag + any-of, both match")
    void combined_requiredAndAnyOf_match() {
        var e = event("MoneyTransferred", "wallet_id", "w1", "from_wallet_id", "w1");
        var sel = selection(Set.of(), Set.of("wallet_id"), Set.of("from_wallet_id", "to_wallet_id"), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }

    @Test
    @DisplayName("Combined: required tag present but any-of absent")
    void combined_requiredPresentAnyOfAbsent() {
        var e = event("MoneyTransferred", "wallet_id", "w1");
        var sel = selection(Set.of(), Set.of("wallet_id"), Set.of("from_wallet_id", "to_wallet_id"), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isFalse();
    }

    // --- tag key case normalization ---

    @Test
    @DisplayName("Tag keys are case-insensitive (normalized to lowercase by Tag)")
    void tagKeys_caseInsensitive() {
        // Tag normalizes keys to lowercase; "WALLET_ID" key becomes "wallet_id"
        var e = new StoredEvent("WalletOpened",
                List.of(new Tag("WALLET_ID", "w1")),
                new byte[0], "tx1", 1L, Instant.now());
        var sel = selection(Set.of(), Set.of("wallet_id"), Set.of(), Map.of());
        assertThat(EventSelectionMatcher.matches(sel, e)).isTrue();
    }
}
