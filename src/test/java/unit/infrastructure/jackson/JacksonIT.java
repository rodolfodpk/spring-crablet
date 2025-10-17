package unit.infrastructure.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WalletEvent;
import com.wallets.domain.event.WalletOpened;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import testutils.AbstractCrabletTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Jackson configuration.
 * Tests Jackson ObjectMapper with Spring Boot context and sealed interface deserialization.
 */
@TestPropertySource(properties = {
    "spring.flyway.enabled=false"
})
public class JacksonIT extends AbstractCrabletTest {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @DisplayName("Should serialize and deserialize WalletEvent correctly")
    public void testWalletEventSerialization() throws Exception {
        // Create a WalletOpened event
        WalletOpened event = WalletOpened.of("wallet1", "Alice", 1000);
        
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(event);
        System.out.println("Serialized JSON: " + json);
        
        // Deserialize back to WalletEvent
        WalletEvent deserialized = objectMapper.readValue(json, WalletEvent.class);
        
        // Verify it's the correct type
        assertThat(deserialized).isInstanceOf(WalletOpened.class);
        WalletOpened walletOpened = (WalletOpened) deserialized;
        assertThat(walletOpened.walletId()).isEqualTo("wallet1");
        assertThat(walletOpened.owner()).isEqualTo("Alice");
        assertThat(walletOpened.initialBalance()).isEqualTo(1000);
    }
}
