package integration.api;

import com.wallets.features.deposit.DepositRequest;
import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletCommandsResponse;
import com.wallets.features.query.WalletHistoryResponse;
import com.wallets.features.transfer.TransferRequest;
import com.wallets.features.withdraw.WithdrawRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import testutils.AbstractCrabletTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for complete wallet workflow.
 * <p>
 * Tests the happy path scenario:
 * 1. Open wallet 1
 * 2. Open wallet 2
 * 3. Deposit to wallet 1
 * 4. Withdraw from wallet 1
 * 5. Transfer from wallet 1 to wallet 2
 * 6. Query events for both wallets
 * 7. Query commands for both wallets
 * <p>
 * Note: This test requires command persistence to be enabled.
 */
@TestPropertySource(properties = "crablet.eventstore.persist-commands=true")
class WalletWorkflowIT extends AbstractCrabletTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Complete wallet workflow: open, deposit, withdraw, transfer, query")
    void shouldCompleteWalletWorkflow() {
        // Generate unique wallet IDs for this test
        String wallet1Id = "workflow-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "workflow-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Open wallet 1
        OpenWalletRequest openWallet1Request = new OpenWalletRequest("user1", 1000);
        ResponseEntity<Void> openWallet1Response = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id,
                HttpMethod.PUT,
                new HttpEntity<>(openWallet1Request),
                Void.class
        );
        assertThat(openWallet1Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Open wallet 2
        OpenWalletRequest openWallet2Request = new OpenWalletRequest("user2", 500);
        ResponseEntity<Void> openWallet2Response = restTemplate.exchange(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id,
                HttpMethod.PUT,
                new HttpEntity<>(openWallet2Request),
                Void.class
        );
        assertThat(openWallet2Response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 3. Deposit to wallet 1
        DepositRequest depositRequest = new DepositRequest("deposit-" + UUID.randomUUID().toString().substring(0, 8), 200, "Initial deposit");
        ResponseEntity<Void> depositResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/deposit",
                depositRequest,
                Void.class
        );
        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 4. Withdraw from wallet 1
        WithdrawRequest withdrawRequest = new WithdrawRequest("withdraw-" + UUID.randomUUID().toString().substring(0, 8), 100, "Cash withdrawal");
        ResponseEntity<Void> withdrawResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/withdraw",
                withdrawRequest,
                Void.class
        );
        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 5. Transfer from wallet 1 to wallet 2
        TransferRequest transferRequest = new TransferRequest("transfer-" + UUID.randomUUID().toString().substring(0, 8), wallet1Id, wallet2Id, 150, "Transfer to wallet 2");
        ResponseEntity<Void> transferResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/wallets/transfer",
                transferRequest,
                Void.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 6. Query events for wallet 1
        ResponseEntity<WalletHistoryResponse> wallet1EventsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/events?page=0&size=20",
                WalletHistoryResponse.class
        );
        assertThat(wallet1EventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse wallet1Events = wallet1EventsResponse.getBody();
        assertThat(wallet1Events).isNotNull();
        assertThat(wallet1Events.events()).hasSize(4); // WalletOpened, DepositMade, WithdrawalMade, MoneyTransferred
        assertThat(wallet1Events.events().stream().map(e -> e.eventType()))
                .containsExactlyInAnyOrder("WalletOpened", "DepositMade", "WithdrawalMade", "MoneyTransferred");

        // 7. Query events for wallet 2
        ResponseEntity<WalletHistoryResponse> wallet2EventsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id + "/events?page=0&size=20",
                WalletHistoryResponse.class
        );
        assertThat(wallet2EventsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletHistoryResponse wallet2Events = wallet2EventsResponse.getBody();
        assertThat(wallet2Events).isNotNull();
        assertThat(wallet2Events.events()).hasSize(2); // WalletOpened, MoneyTransferred
        assertThat(wallet2Events.events().stream().map(e -> e.eventType()))
                .containsExactlyInAnyOrder("WalletOpened", "MoneyTransferred");

        // 8. Query commands for wallet 1
        ResponseEntity<WalletCommandsResponse> wallet1CommandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet1Id + "/commands?page=0&size=20",
                WalletCommandsResponse.class
        );
        assertThat(wallet1CommandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse wallet1Commands = wallet1CommandsResponse.getBody();
        assertThat(wallet1Commands).isNotNull();
        assertThat(wallet1Commands.commands()).hasSize(4); // open_wallet, deposit, withdraw, transfer_money
        assertThat(wallet1Commands.commands().stream().map(c -> c.commandType()))
                .containsExactlyInAnyOrder("open_wallet", "deposit", "withdraw", "transfer_money");

        // 9. Query commands for wallet 2
        ResponseEntity<WalletCommandsResponse> wallet2CommandsResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/wallets/" + wallet2Id + "/commands?page=0&size=20",
                WalletCommandsResponse.class
        );
        assertThat(wallet2CommandsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        WalletCommandsResponse wallet2Commands = wallet2CommandsResponse.getBody();
        assertThat(wallet2Commands).isNotNull();
        assertThat(wallet2Commands.commands()).hasSize(2); // open_wallet, transfer_money (as receiver)
        assertThat(wallet2Commands.commands().stream().map(c -> c.commandType()))
                .containsExactlyInAnyOrder("open_wallet", "transfer_money");

        // 10. Verify that each command has its corresponding events
        wallet1Commands.commands().forEach(command -> {
            assertThat(command.events()).isNotEmpty();
            // Verify event data is parsed as JSON, not Base64
            command.events().forEach(event -> {
                assertThat(event.data()).isInstanceOf(java.util.Map.class);
                assertThat(event.data()).isNotEmpty();
            });
        });

        wallet2Commands.commands().forEach(command -> {
            assertThat(command.events()).isNotEmpty();
            // Verify event data is parsed as JSON, not Base64
            command.events().forEach(event -> {
                assertThat(event.data()).isInstanceOf(java.util.Map.class);
                assertThat(event.data()).isNotEmpty();
            });
        });
    }
}
