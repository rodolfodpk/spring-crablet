package integration.api.controller;

import com.wallets.features.openwallet.OpenWalletRequest;
import com.wallets.features.query.WalletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import testutils.AbstractCrabletTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenWalletController using TestRestTemplate.
 * Tests wallet creation operations with real HTTP requests and database.
 */
class OpenWalletControllerIT extends AbstractCrabletTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/wallets";
    }

    @Test
    @DisplayName("Should create wallet successfully")
    void shouldCreateWalletSuccessfully() {
        // Arrange
        String walletId = "wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);

        // Act
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Verify wallet was created
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();

        WalletResponse wallet = walletResponse.getBody();
        assertThat(wallet.walletId()).isEqualTo(walletId);
        assertThat(wallet.owner()).isEqualTo("Alice");
        assertThat(wallet.balance()).isEqualTo(1000);
        assertThat(wallet.createdAt()).isNotNull();
        assertThat(wallet.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle wallet already exists gracefully")
    void shouldHandleWalletAlreadyExistsGracefully() {
        // Arrange
        String walletId = "idempotent-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000);

        // Act: Create wallet first time
        restTemplate.put(walletUrl, walletRequest);

        // Verify first creation
        ResponseEntity<WalletResponse> firstResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().walletId()).isEqualTo(walletId);
        assertThat(firstResponse.getBody().owner()).isEqualTo("Alice");
        assertThat(firstResponse.getBody().balance()).isEqualTo(1000);

        // Act: Try to create same wallet again (idempotent)
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Wallet should still exist with same data
        ResponseEntity<WalletResponse> secondResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().walletId()).isEqualTo(walletId);
        assertThat(secondResponse.getBody().owner()).isEqualTo("Alice");
        assertThat(secondResponse.getBody().balance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should validate request body")
    void shouldValidateRequestBody() {
        // Arrange
        String walletId = "validation-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;

        // Act: Try to create wallet with invalid data (empty owner, negative balance)
        OpenWalletRequest invalidRequest = new OpenWalletRequest("", -100);
        restTemplate.put(walletUrl, invalidRequest);

        // Assert: Wallet should not be created
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should handle zero initial balance")
    void shouldHandleZeroInitialBalance() {
        // Arrange
        String walletId = "zero-balance-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 0);

        // Act
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Wallet should be created with zero balance
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();

        WalletResponse wallet = walletResponse.getBody();
        assertThat(wallet.walletId()).isEqualTo(walletId);
        assertThat(wallet.owner()).isEqualTo("Alice");
        assertThat(wallet.balance()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large initial balance")
    void shouldHandleLargeInitialBalance() {
        // Arrange
        String walletId = "large-balance-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice", 1000000);

        // Act
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Wallet should be created with large balance
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();

        WalletResponse wallet = walletResponse.getBody();
        assertThat(wallet.walletId()).isEqualTo(walletId);
        assertThat(wallet.owner()).isEqualTo("Alice");
        assertThat(wallet.balance()).isEqualTo(1000000);
    }

    @Test
    @DisplayName("Should handle special characters in owner name")
    void shouldHandleSpecialCharactersInOwnerName() {
        // Arrange
        String walletId = "special-chars-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        OpenWalletRequest walletRequest = new OpenWalletRequest("Alice O'Connor-Smith", 1000);

        // Act
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Wallet should be created with special characters in owner name
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();

        WalletResponse wallet = walletResponse.getBody();
        assertThat(wallet.walletId()).isEqualTo(walletId);
        assertThat(wallet.owner()).isEqualTo("Alice O'Connor-Smith");
        assertThat(wallet.balance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should handle unicode characters in owner name")
    void shouldHandleUnicodeCharactersInOwnerName() {
        // Arrange
        String walletId = "unicode-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        OpenWalletRequest walletRequest = new OpenWalletRequest("张三", 1000);

        // Act
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Wallet should be created with unicode characters in owner name
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();

        WalletResponse wallet = walletResponse.getBody();
        assertThat(wallet.walletId()).isEqualTo(walletId);
        assertThat(wallet.owner()).isEqualTo("张三");
        assertThat(wallet.balance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should handle multiple wallet creations")
    void shouldHandleMultipleWalletCreations() {
        // Arrange
        String wallet1Id = "multi-wallet1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "multi-wallet2-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet3Id = "multi-wallet3-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 1000);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 2000);
        OpenWalletRequest wallet3Request = new OpenWalletRequest("Charlie", 3000);

        // Act: Create multiple wallets
        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);
        restTemplate.put(baseUrl + "/" + wallet3Id, wallet3Request);

        // Assert: All wallets should be created successfully
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet3Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet3Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet3Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet3Response.getBody()).isNotNull();

        assertThat(wallet1Response.getBody().walletId()).isEqualTo(wallet1Id);
        assertThat(wallet2Response.getBody().walletId()).isEqualTo(wallet2Id);
        assertThat(wallet3Response.getBody().walletId()).isEqualTo(wallet3Id);

        assertThat(wallet1Response.getBody().owner()).isEqualTo("Alice");
        assertThat(wallet2Response.getBody().owner()).isEqualTo("Bob");
        assertThat(wallet3Response.getBody().owner()).isEqualTo("Charlie");

        assertThat(wallet1Response.getBody().balance()).isEqualTo(1000);
        assertThat(wallet2Response.getBody().balance()).isEqualTo(2000);
        assertThat(wallet3Response.getBody().balance()).isEqualTo(3000);
    }

    @Test
    @DisplayName("Should handle wallet creation with different initial balances")
    void shouldHandleWalletCreationWithDifferentInitialBalances() {
        // Arrange
        String wallet1Id = "balance-test1-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet2Id = "balance-test2-" + UUID.randomUUID().toString().substring(0, 8);
        String wallet3Id = "balance-test3-" + UUID.randomUUID().toString().substring(0, 8);

        OpenWalletRequest wallet1Request = new OpenWalletRequest("Alice", 0);
        OpenWalletRequest wallet2Request = new OpenWalletRequest("Bob", 1);
        OpenWalletRequest wallet3Request = new OpenWalletRequest("Charlie", 999999);

        // Act: Create wallets with different balances
        restTemplate.put(baseUrl + "/" + wallet1Id, wallet1Request);
        restTemplate.put(baseUrl + "/" + wallet2Id, wallet2Request);
        restTemplate.put(baseUrl + "/" + wallet3Id, wallet3Request);

        // Assert: All wallets should be created with correct balances
        ResponseEntity<WalletResponse> wallet1Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet1Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet2Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet2Id, WalletResponse.class
        );
        ResponseEntity<WalletResponse> wallet3Response = restTemplate.getForEntity(
                baseUrl + "/" + wallet3Id, WalletResponse.class
        );

        assertThat(wallet1Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet2Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wallet3Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(wallet1Response.getBody()).isNotNull();
        assertThat(wallet2Response.getBody()).isNotNull();
        assertThat(wallet3Response.getBody()).isNotNull();

        assertThat(wallet1Response.getBody().balance()).isEqualTo(0);
        assertThat(wallet2Response.getBody().balance()).isEqualTo(1);
        assertThat(wallet3Response.getBody().balance()).isEqualTo(999999);
    }

    @Test
    @DisplayName("Should handle wallet creation with long owner names")
    void shouldHandleWalletCreationWithLongOwnerNames() {
        // Arrange
        String walletId = "long-name-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        String walletUrl = baseUrl + "/" + walletId;
        String longOwnerName = "Alice Marie Elizabeth O'Connor-Smith-Johnson-Williams-Brown-Davis-Miller-Wilson-Moore-Taylor-Anderson-Thomas-Jackson-White-Harris-Martin-Thompson-Garcia-Martinez-Robinson-Clark-Rodriguez-Lewis-Lee-Walker-Hall-Allen-Young-Hernandez-King-Wright-Lopez-Hill-Scott-Green-Adams-Baker-Gonzalez-Nelson-Carter-Mitchell-Perez-Roberts-Turner-Phillips-Campbell-Parker-Evans-Edwards-Collins-Stewart-Sanchez-Morris-Rogers-Reed-Cook-Morgan-Bell-Murphy-Bailey-Rivera-Cooper-Richardson-Cox-Howard-Ward-Torres-Peterson-Gray-Ramirez-James-Watson-Brooks-Kelly-Sanders-Price-Bennett-Wood-Barnes-Ross-Henderson-Coleman-Jenkins-Perry-Powell-Long-Patterson-Hughes-Flores-Washington-Butler-Simmons-Foster-Gonzales-Bryant-Alexander-Russell-Griffin-Diaz-Hayes";
        OpenWalletRequest walletRequest = new OpenWalletRequest(longOwnerName, 1000);

        // Act
        restTemplate.put(walletUrl, walletRequest);

        // Assert: Wallet should be created with long owner name
        ResponseEntity<WalletResponse> walletResponse = restTemplate.getForEntity(walletUrl, WalletResponse.class);
        assertThat(walletResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResponse.getBody()).isNotNull();

        WalletResponse wallet = walletResponse.getBody();
        assertThat(wallet.walletId()).isEqualTo(walletId);
        assertThat(wallet.owner()).isEqualTo(longOwnerName);
        assertThat(wallet.balance()).isEqualTo(1000);
    }
}
