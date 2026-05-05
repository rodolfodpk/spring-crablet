package com.crablet.eventstore.config;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.ReadDataSource;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.eventstore.notify.EventAppendNotifier;
import com.crablet.eventstore.notify.PostgresNotifyEventAppendNotifier;
import com.crablet.eventstore.query.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EventStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventStoreAutoConfiguration.class))
            .withBean("dataSource", DataSource.class, () -> mock(DataSource.class))
            .withBean(tools.jackson.databind.ObjectMapper.class,
                    () -> JsonMapper.builder().build());

    @Test
    void defaultContextCreatesAllCoreBeansOfExpectedTypes() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(EventStore.class);
            assertThat(ctx).hasSingleBean(EventRepository.class);
            assertThat(ctx).hasSingleBean(WriteDataSource.class);
            assertThat(ctx).hasSingleBean(ReadDataSource.class);
            assertThat(ctx).hasSingleBean(JdbcTemplate.class);
            assertThat(ctx).hasSingleBean(PlatformTransactionManager.class);
            assertThat(ctx).hasSingleBean(EventAppendNotifier.class);
            assertThat(ctx).hasSingleBean(ClockProvider.class);
        });
    }

    @Test
    void defaultNotifierIsPostgresNotifyEventAppendNotifier() {
        runner.run(ctx ->
                assertThat(ctx.getBean(EventAppendNotifier.class))
                        .isInstanceOf(PostgresNotifyEventAppendNotifier.class));
    }

    @Test
    void defaultReadDataSourceWrapsSameInstanceAsWriteDataSource() {
        runner.run(ctx -> {
            WriteDataSource write = ctx.getBean(WriteDataSource.class);
            ReadDataSource read = ctx.getBean(ReadDataSource.class);
            assertThat(read.dataSource()).isSameAs(write.dataSource());
        });
    }

    @Test
    void replicaEnabledWithoutUrlFailsFast() {
        runner.withPropertyValues("crablet.eventstore.read-replicas.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    Throwable t = ctx.getStartupFailure();
                    while (t.getCause() != null) t = t.getCause();
                    assertThat(t)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("no replica URL configured");
                });
    }

    @Test
    void replicaEnabledWithBlankUrlFailsFast() {
        runner.withPropertyValues(
                        "crablet.eventstore.read-replicas.enabled=true",
                        "crablet.eventstore.read-replicas.url=   ")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    Throwable t = ctx.getStartupFailure();
                    while (t.getCause() != null) t = t.getCause();
                    assertThat(t)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("no replica URL configured");
                });
    }
}
