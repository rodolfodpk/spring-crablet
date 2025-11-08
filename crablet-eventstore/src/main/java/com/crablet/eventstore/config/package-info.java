/**
 * EventStore configuration classes for datasource setup.
 * <p>
 * This package contains configuration classes for setting up primary (write) and
 * read replica datasources for the EventStore, enabling horizontal scaling of read operations.
 * <p>
 * <strong>Key Components:</strong>
 * <ul>
 *   <li>{@link com.crablet.eventstore.config.DataSourceConfig} - Configuration for primary and read replica datasources</li>
 *   <li>{@link com.crablet.eventstore.config.ReadReplicaProperties} - Properties for read replica configuration</li>
 * </ul>
 * <p>
 * <strong>Read Replica Support:</strong>
 * EventStore supports read replicas for horizontal scaling:
 * <ul>
 *   <li>Read operations (project) use read-only connections to read replicas</li>
 *   <li>Write operations (append, appendIf, storeCommand) use write connections to primary</li>
 *   <li>Transactions (executeInTransaction) use write connections as they may include writes</li>
 * </ul>
 * <p>
 * <strong>Configuration Properties:</strong>
 * Configuration is done via Spring Boot properties:
 * <ul>
 *   <li>Primary datasource: Standard Spring Boot properties ({@code spring.datasource.*})</li>
 *   <li>Read replica: Crablet-specific properties ({@code crablet.eventstore.read-replica.*})</li>
 * </ul>
 * <p>
 * <strong>Spring Integration:</strong>
 * Users must define datasource beans in their configuration:
 * <pre>{@code
 * @Bean
 * @Primary
 * @ConfigurationProperties(prefix = "spring.datasource")
 * public DataSource primaryDataSource() {
 *     return DataSourceBuilder.create().build();
 * }
 * 
 * @Bean
 * @Qualifier("readDataSource")
 * @ConfigurationProperties(prefix = "crablet.eventstore.read-replica")
 * public DataSource readDataSource() {
 *     return DataSourceBuilder.create().build();
 * }
 * }</pre>
 * <p>
 * <strong>Note:</strong>
 * For EventStore behavior configuration (command persistence, transaction isolation, etc.),
 * see {@link com.crablet.eventstore.store.EventStoreConfig} in the store package.
 *
 * @see com.crablet.eventstore.store.EventStore
 * @see com.crablet.eventstore.store.EventStoreConfig
 */
package com.crablet.eventstore.config;

