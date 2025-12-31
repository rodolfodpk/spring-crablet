# E2E Test Plan for Wallets Example App

## Overview
End-to-end tests that hit the HTTP API endpoints and verify both API responses and view projections using `@Order` annotations for sequential test execution.

**Testing Style**: BDD (Behavior-Driven Development) with Given-When-Then structure
**Assertions**: AssertJ (`assertThat()`)

## Test Infrastructure

### 1. Base Test Class: `AbstractWalletsE2ETest`
- Extends `AbstractWalletsTest`
- Uses `SpringBootTest.WebEnvironment.RANDOM_PORT` to enable HTTP server
- Provides `WebTestClient` bean (modern Spring Boot 3.x HTTP client)
- Helper methods for API calls (fluent API with AssertJ):
  - `openWallet(OpenWalletRequest)` → `WebTestClient.ResponseSpec` (fluent assertions)
  - `deposit(String walletId, DepositRequest)` → `WebTestClient.ResponseSpec`
  - `withdraw(String walletId, WithdrawRequest)` → `WebTestClient.ResponseSpec`
  - `transfer(TransferRequest)` → `WebTestClient.ResponseSpec`
  - `getWallet(String walletId)` → `WebTestClient.ResponseSpec`
  - `getTransactions(String walletId, int page, int size)` → `WebTestClient.ResponseSpec`
  - `getSummary(String walletId)` → `WebTestClient.ResponseSpec`
- Helper methods for view verification:
  - `verifyBalanceView(String walletId, int expectedBalance)`
  - `verifyTransactionView(String walletId, int expectedCount)`
  - `verifySummaryView(String walletId, Map<String, Object> expectedValues)`
- Utility methods:
  - `waitForViewProjection()` - Wait for views to catch up (polling with timeout)

## Test Scenarios

### 2. `WalletLifecycleE2ETest` - Complete Wallet Lifecycle
**Scenario**: Open wallet → Deposit → Withdraw → Query views

| Order | Test Method | Given | When | Then |
|-------|-------------|-------|------|------|
| 1 | `shouldOpenWallet()` | No wallet exists | POST /api/wallets with walletId, owner, initialBalance=100 | Status 201, response contains walletId, owner, balance=100 |
| 2 | `shouldQueryWalletAfterOpening()` | Wallet "wallet-lifecycle-1" exists with balance=100 | GET /api/wallets/wallet-lifecycle-1 | Status 200, balance view shows balance=100, owner="John Doe" |
| 3 | `shouldDepositMoney()` | Wallet "wallet-lifecycle-1" exists with balance=100 | POST /api/wallets/wallet-lifecycle-1/deposits with amount=50 | Status 200, deposit successful |
| 4 | `shouldQueryWalletAfterDeposit()` | Deposit of 50 was made to wallet | GET /api/wallets/wallet-lifecycle-1 | Status 200, balance view shows balance=150 |
| 5 | `shouldWithdrawMoney()` | Wallet has balance=150 | POST /api/wallets/wallet-lifecycle-1/withdrawals with amount=30 | Status 200, withdrawal successful |
| 6 | `shouldQueryWalletAfterWithdrawal()` | Withdrawal of 30 was made | GET /api/wallets/wallet-lifecycle-1 | Status 200, balance view shows balance=120 |
| 7 | `shouldGetTransactionHistory()` | Wallet has 3 operations (open, deposit, withdraw) | GET /api/wallets/wallet-lifecycle-1/transactions | Status 200, transaction view contains 3 entries |
| 8 | `shouldGetWalletSummary()` | Wallet has completed operations | GET /api/wallets/wallet-lifecycle-1/summary | Status 200, summary shows totalDeposits=50, totalWithdrawals=30, currentBalance=120 |

### 3. `WalletDepositWithdrawalE2ETest` - Multiple Operations
**Scenario**: Open wallet → Multiple deposits → Multiple withdrawals → Verify balance

| Order | Test Method | Given | When | Then |
|-------|-------------|-------|------|------|
| 1 | `shouldOpenWalletWithInitialBalance()` | No wallet exists | POST /api/wallets with initialBalance=100 | Status 201, wallet created |
| 2 | `shouldMakeFirstDeposit()` | Wallet exists with balance=100 | POST /api/wallets/{id}/deposits with amount=50 | Status 200, balance view shows 150 |
| 3 | `shouldMakeSecondDeposit()` | Wallet has balance=150 | POST /api/wallets/{id}/deposits with amount=75 | Status 200, balance view shows 225 |
| 4 | `shouldMakeFirstWithdrawal()` | Wallet has balance=225 | POST /api/wallets/{id}/withdrawals with amount=30 | Status 200, balance view shows 195 |
| 5 | `shouldMakeSecondWithdrawal()` | Wallet has balance=195 | POST /api/wallets/{id}/withdrawals with amount=45 | Status 200, balance view shows 150 |
| 6 | `shouldVerifyFinalBalance()` | All operations completed | GET /api/wallets/{id} | Status 200, balance=150 |
| 7 | `shouldVerifyTransactionCount()` | Wallet has 5 operations | GET /api/wallets/{id}/transactions | Status 200, transaction count=5 |
| 8 | `shouldVerifySummaryTotals()` | Wallet has completed operations | GET /api/wallets/{id}/summary | Status 200, totalDeposits=125, totalWithdrawals=75, currentBalance=150 |

### 4. `WalletTransferE2ETest` - Transfer Between Wallets
**Scenario**: Open two wallets → Transfer money → Verify both wallets

| Order | Test Method | Given | When | Then |
|-------|-------------|-------|------|------|
| 1 | `shouldOpenSourceWallet()` | No wallet exists | POST /api/wallets → wallet-transfer-1 with balance=200 | Status 201, wallet created |
| 2 | `shouldOpenDestinationWallet()` | wallet-transfer-1 exists | POST /api/wallets → wallet-transfer-2 with balance=50 | Status 201, wallet created |
| 3 | `shouldTransferMoney()` | Both wallets exist | POST /api/wallets/transfers → transfer 75 from wallet-transfer-1 to wallet-transfer-2 | Status 200, transfer successful |
| 4 | `shouldVerifySourceWalletBalance()` | Transfer completed | GET /api/wallets/wallet-transfer-1 | Status 200, balance=125 |
| 5 | `shouldVerifyDestinationWalletBalance()` | Transfer completed | GET /api/wallets/wallet-transfer-2 | Status 200, balance=125 |
| 6 | `shouldVerifySourceWalletTransactions()` | Transfer completed | GET /api/wallets/wallet-transfer-1/transactions | Status 200, contains transfer transaction |
| 7 | `shouldVerifyDestinationWalletTransactions()` | Transfer completed | GET /api/wallets/wallet-transfer-2/transactions | Status 200, contains transfer transaction |
| 8 | `shouldVerifySourceWalletSummary()` | Transfer completed | GET /api/wallets/wallet-transfer-1/summary | Status 200, transfers_out=75 |
| 9 | `shouldVerifyDestinationWalletSummary()` | Transfer completed | GET /api/wallets/wallet-transfer-2/summary | Status 200, transfers_in=75 |

### 5. `WalletErrorHandlingE2ETest` - Error Scenarios
**Scenario**: Test error responses and HTTP status codes

| Order | Test Method | Given | When | Then |
|-------|-------------|-------|------|------|
| 1 | `shouldReturn404ForNonExistentWallet()` | No wallet exists | GET /api/wallets/non-existent | Status 404, error response with walletId |
| 2 | `shouldReturn404WhenDepositingToNonExistentWallet()` | No wallet exists | POST /api/wallets/non-existent/deposits | Status 404, error response |
| 3 | `shouldReturn400WhenWithdrawingInsufficientFunds()` | Wallet exists with balance=100 | POST /api/wallets/{id}/withdrawals with amount=150 | Status 400, error response with currentBalance and requestedAmount |
| 4 | `shouldReturn400WhenTransferringInsufficientFunds()` | Source wallet exists with balance=50 | POST /api/wallets/transfers with amount=100 | Status 400, error response |
| 5 | `shouldReturn400ForInvalidRequest()` | - | POST /api/wallets with missing required fields | Status 400, validation errors in response |
| 6 | `shouldReturn409WhenOpeningDuplicateWallet()` | Wallet "wallet-1" already exists | POST /api/wallets with same walletId | Status 409, conflict error |
| 7 | `shouldReturn404ForSummaryOfNonExistentWallet()` | No wallet exists | GET /api/wallets/non-existent/summary | Status 404, error response |

### 6. `WalletIdempotencyE2ETest` - Idempotency Scenarios
**Scenario**: Verify idempotent operations (duplicate deposit/withdraw/transfer IDs)

| Order | Test Method | Given | When | Then |
|-------|-------------|-------|------|------|
| 1 | `shouldOpenWallet()` | No wallet exists | POST /api/wallets → wallet-idempotency-1 | Status 201, wallet created |
| 2 | `shouldAcceptFirstDeposit()` | Wallet exists | POST /api/wallets/{id}/deposits with depositId="dep-1", amount=100 | Status 200, balance=100 |
| 3 | `shouldIgnoreDuplicateDeposit()` | Wallet has balance=100 from dep-1 | POST /api/wallets/{id}/deposits with same depositId="dep-1", amount=100 | Status 200, balance still 100 (idempotent) |
| 4 | `shouldAcceptSecondDeposit()` | Wallet has balance=100 | POST /api/wallets/{id}/deposits with depositId="dep-2", amount=50 | Status 200, balance=150 |
| 5 | `shouldAcceptFirstWithdrawal()` | Wallet has balance=150 | POST /api/wallets/{id}/withdrawals with withdrawalId="wd-1", amount=30 | Status 200, balance=120 |
| 6 | `shouldIgnoreDuplicateWithdrawal()` | Wallet has balance=120 from wd-1 | POST /api/wallets/{id}/withdrawals with same withdrawalId="wd-1", amount=30 | Status 200, balance still 120 (idempotent) |
| 7 | `shouldOpenSecondWallet()` | wallet-idempotency-1 exists | POST /api/wallets → wallet-idempotency-2 | Status 201, wallet created |
| 8 | `shouldAcceptFirstTransfer()` | Both wallets exist | POST /api/wallets/transfers with transferId="tx-1", amount=20 | Status 200, transfer successful |
| 9 | `shouldIgnoreDuplicateTransfer()` | Transfer tx-1 already processed | POST /api/wallets/transfers with same transferId="tx-1", amount=20 | Status 200, balances unchanged (idempotent) |

### 7. `WalletViewProjectionE2ETest` - View Projection Verification
**Scenario**: Verify views are updated correctly after operations (with async projection waiting)

| Order | Test Method | Given | When | Then |
|-------|-------------|-------|------|------|
| 1 | `shouldProjectWalletBalanceViewOnOpen()` | No wallet exists | POST /api/wallets → wait for projection | wallet_balance_view contains wallet with correct balance |
| 2 | `shouldProjectWalletBalanceViewOnDeposit()` | Wallet exists | POST /api/wallets/{id}/deposits → wait | wallet_balance_view balance updated correctly |
| 3 | `shouldProjectWalletTransactionViewOnDeposit()` | Wallet exists | POST /api/wallets/{id}/deposits → wait | wallet_transaction_view contains deposit entry |
| 4 | `shouldProjectWalletSummaryViewOnDeposit()` | Wallet exists | POST /api/wallets/{id}/deposits → wait | wallet_summary_view totalDeposits incremented |
| 5 | `shouldProjectWalletBalanceViewOnWithdrawal()` | Wallet has balance | POST /api/wallets/{id}/withdrawals → wait | wallet_balance_view balance decreased correctly |
| 6 | `shouldProjectWalletTransactionViewOnWithdrawal()` | Wallet exists | POST /api/wallets/{id}/withdrawals → wait | wallet_transaction_view contains withdrawal entry |
| 7 | `shouldProjectWalletSummaryViewOnWithdrawal()` | Wallet exists | POST /api/wallets/{id}/withdrawals → wait | wallet_summary_view totalWithdrawals incremented |
| 8 | `shouldProjectBothWalletsOnTransfer()` | Two wallets exist | POST /api/wallets/transfers → wait | Both wallets in wallet_balance_view updated |
| 9 | `shouldProjectTransferInTransactionView()` | Two wallets exist | POST /api/wallets/transfers → wait | wallet_transaction_view contains transfer entries for both wallets |
| 10 | `shouldProjectTransferInSummaryView()` | Two wallets exist | POST /api/wallets/transfers → wait | wallet_summary_view shows transfers_out and transfers_in |

## Implementation Details

### Test Class Structure (BDD Style with WebTestClient)
```java
@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@AutoConfigureWebTestClient
@TestMethodOrder(OrderAnnotation.class)
public class WalletLifecycleE2ETest extends AbstractWalletsE2ETest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private WebTestClient webTestClient;
    
    @BeforeEach
    void setUp() {
        // Configure WebTestClient with base URL when using RANDOM_PORT
        webTestClient = WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }
    
    @Test
    @Order(1)
    @DisplayName("Should open a new wallet successfully")
    void shouldOpenWallet() {
        // Given
        OpenWalletRequest request = new OpenWalletRequest(
            "wallet-lifecycle-1",
            "John Doe",
            100
        );
        
        // When & Then
        webTestClient
            .post()
            .uri("/api/wallets")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(WalletResponse.class)
            .value(response -> {
                assertThat(response.walletId()).isEqualTo("wallet-lifecycle-1");
                assertThat(response.owner()).isEqualTo("John Doe");
                assertThat(response.balance()).isEqualTo(100);
            });
    }
    
    @Test
    @Order(2)
    @DisplayName("Should query wallet after opening")
    void shouldQueryWalletAfterOpening() {
        // Given - Wallet was created in previous test
        
        // When & Then
        webTestClient
            .get()
            .uri("/api/wallets/wallet-lifecycle-1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(WalletResponse.class)
            .value(response -> {
                assertThat(response.walletId()).isEqualTo("wallet-lifecycle-1");
                assertThat(response.owner()).isEqualTo("John Doe");
                assertThat(response.balance()).isEqualTo(100);
            });
    }
    
    // ... more tests
}
```

### View Projection Waiting Strategy
Since views are updated asynchronously, we need to wait for projections:
- Use **Awaitility** library for polling assertions (recommended)
- Poll `wallet_balance_view` with timeout (e.g., 5 seconds)
- Fluent API: `await().atMost(5, SECONDS).until(() -> verifyBalance(...))`
- Alternative: Simple retry loop with `Thread.sleep()` (less ideal)

### Example with Awaitility
```java
// Given - Operation was performed
webTestClient.post().uri("/api/wallets/{id}/deposits", walletId)
    .bodyValue(depositRequest)
    .exchange()
    .expectStatus().isOk();

// When & Then - Wait for view projection
await().atMost(5, SECONDS).untilAsserted(() -> {
    webTestClient.get()
        .uri("/api/wallets/{id}", walletId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(WalletResponse.class)
        .value(response -> 
            assertThat(response.balance()).isEqualTo(expectedBalance)
        );
});
```

### Test Data Management
- Use unique wallet IDs per test class (e.g., `wallet-lifecycle-1`, `wallet-transfer-1`)
- Use unique operation IDs (depositId, withdrawalId, transferId) per test
- Clean database in `@BeforeEach` (inherited from `AbstractWalletsTest`)
- **Important**: Tests within a class share state (sequential execution), but test classes are isolated

### WebTestClient Configuration with RANDOM_PORT
When using `RANDOM_PORT`, `WebTestClient` needs to be configured with the actual port:
```java
@LocalServerPort
private int port;

@Autowired
private WebTestClient webTestClient;

@BeforeEach
void setUp() {
    // Reconfigure WebTestClient with actual port
    webTestClient = WebTestClient
        .bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
}
```

Alternatively, configure it in the base class `AbstractWalletsE2ETest`:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
public abstract class AbstractWalletsE2ETest extends AbstractWalletsTest {
    
    @LocalServerPort
    protected int port;
    
    protected WebTestClient webTestClient;
    
    @BeforeEach
    void setUpE2E() {
        webTestClient = WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }
}
```

## Dependencies Needed

### Required
- `spring-boot-starter-test` (already included) - provides test infrastructure
- `spring-boot-starter-web` (already included) - for REST API
- `spring-boot-starter-webflux` - **needs to be added** for `WebTestClient` support in MVC apps
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webflux</artifactId>
      <scope>test</scope>
  </dependency>
  ```

### Optional (Recommended)
- `awaitility` - for better polling assertions when waiting for view projections
  ```xml
  <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
  </dependency>
  ```

## Execution Order
- Tests within a class run in `@Order` sequence
- Test classes can run in parallel (each has isolated database state)
- Use `@TestMethodOrder(OrderAnnotation.class)` on test class

## Assertions (AssertJ with WebTestClient)
- **HTTP status codes**: `.expectStatus().isCreated()`, `.expectStatus().isOk()`, `.expectStatus().isBadRequest()`, etc.
- **Response body structure**: `.expectBody(WalletResponse.class)` or `.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})`
- **Response values**: `.value(response -> assertThat(...))` with AssertJ
- **JSON path assertions**: `.jsonPath("$.walletId").isEqualTo("wallet-1")` (alternative, but less type-safe)
- **View projection data**: Query views via `JdbcTemplate` and assert with AssertJ
- **Transaction counts**: Assert on transaction lists with `.hasSize()`, `.contains()`, etc.
- **Balance calculations**: Assert on numeric values with `.isEqualTo()`, `.isGreaterThan()`, etc.
- **Error responses**: Assert on error maps with `.get("error")`, `.get("status")`, etc.

### Example AssertJ Assertions in WebTestClient
```java
// Status code
.expectStatus().isCreated()

// Response body type
.expectBody(WalletResponse.class)

// AssertJ assertions on response
.value(response -> {
    assertThat(response.walletId()).isEqualTo("wallet-1");
    assertThat(response.balance()).isGreaterThan(0);
    assertThat(response.owner()).isNotBlank();
})

// JSON path alternative
.jsonPath("$.walletId").isEqualTo("wallet-1")
.jsonPath("$.balance").value(Matchers.greaterThan(0))

// Error response (GlobalExceptionHandler returns Map<String, Object>)
.expectStatus().isBadRequest()
.expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
.value(error -> {
    assertThat(error.get("error")).isEqualTo("Insufficient funds");
    assertThat(error.get("walletId")).isNotNull();
    assertThat(error.get("currentBalance")).isNotNull();
    assertThat(error.get("requestedAmount")).isNotNull();
})
```

