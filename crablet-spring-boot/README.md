# Crablet Spring Boot

Spring Boot integration for Crablet event sourcing library.

## Overview

Crablet Spring Boot provides Spring Boot auto-configuration for Crablet, including database setup, read replica support, and Spring integration.

## Features

- **Auto-Configuration**: Zero-configuration setup for Spring Boot applications
- **Read Replica Support**: Optional read replica routing for scaling reads
- **DataSource Configuration**: Easy configuration of primary and read DataSources
- **Component Scanning**: Automatic discovery of event store implementations

## Maven Coordinates

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-spring-boot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Dependencies

- crablet-core
- crablet-outbox
- Spring Boot JDBC
- Spring Boot Web

## Quick Start

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.crablet</groupId>
    <artifactId>crablet-spring-boot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Configure your database:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/crablet
spring.datasource.username=crablet
spring.datasource.password=crablet
```

Crablet will automatically configure:
- `EventStore` bean
- `DataSource` beans (primary and read)
- `JdbcTemplate` bean
- All necessary components

## Read Replicas

Optional read replica support for scaling read operations:

```properties
# Enable read replicas
crablet.eventstore.read-replicas.enabled=true

# Configure replica URLs
crablet.eventstore.read-replicas.urls=\
  jdbc:postgresql://replica1:5432/crablet,\
  jdbc:postgresql://replica2:5432/crablet

# Fallback to primary if replicas unavailable
crablet.eventstore.read-replicas.fallback-to-primary=true

# Connection pool settings
crablet.eventstore.read-replicas.hikari.maximum-pool-size=50
crablet.eventstore.read-replicas.hikari.minimum-idle=10
```

### How It Works

- **Write operations** (`append`, `appendIf`) use the primary DataSource
- **Read operations** (`project`) use the load-balanced read replicas
- Automatic fallback to primary if all replicas fail
- Round-robin load balancing across replicas

## Package Structure

All Spring integration classes are in the `com.crablet.spring` package:

- `com.crablet.spring.config` - Configuration classes
- `com.crablet.spring.datasource` - DataSource implementations
- `com.crablet.core.impl` - Core implementations (EventStoreImpl, etc.)

## Documentation

- [Read Replicas](docs/READ_REPLICAS.md) - Read replica configuration and setup
- [PgBouncer](docs/PGBOUNCER.md) - PgBouncer compatibility and deployment

## License

MIT

