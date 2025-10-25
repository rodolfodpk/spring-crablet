# Shared Wallet Domain

Shared wallet event DTOs for serialization contracts between microservices.

## Overview

This module contains only event Data Transfer Objects (DTOs) that are shared between:
- `wallet-eventstore-service` - writes these events
- `wallet-outbox-service` - reads and publishes these events

## Contents

### Event Classes

- `WalletEvent` - Base interface with Jackson polymorphic type info
- `WalletOpened` - Event for wallet creation
- `DepositMade` - Event for money deposits
- `WithdrawalMade` - Event for money withdrawals
- `MoneyTransferred` - Event for transfers between wallets

## Design Principles

1. **No Spring Dependencies** - Pure Java POJOs
2. **No Business Logic** - Only data structures for serialization
3. **No Infrastructure** - No EventStore, Outbox, or database dependencies
4. **Immutable** - All events are immutable records

## Usage

### Maven Dependency

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>shared-wallet-domain</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Jackson Configuration

Events use `@JsonTypeInfo` and `@JsonSubTypes` for polymorphic deserialization:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = WalletOpened.class, name = "WalletOpened"),
    @JsonSubTypes.Type(value = DepositMade.class, name = "DepositMade"),
    @JsonSubTypes.Type(value = WithdrawalMade.class, name = "WithdrawalMade"),
    @JsonSubTypes.Type(value = MoneyTransferred.class, name = "MoneyTransferred")
})
public interface WalletEvent { }
```

## Microservices Architecture

In the microservices deployment:
- **EventStore Service** uses these events when writing to the database
- **Outbox Service** uses these events when deserializing from the database

Both services must use the same version of this module to ensure serialization compatibility.

