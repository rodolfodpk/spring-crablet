package com.crablet.eventstore;

import com.crablet.eventstore.query.ProjectionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventStore public API contract")
class PublicApiContractTest {

    @Test
    @DisplayName("EventStore should not expose command-layer internals")
    void eventStore_ShouldNotExposeCommandLayerInternals() {
        assertThat(methodNames(EventStore.class))
                .doesNotContain("storeCommand", "hasConflict");
    }

    @Test
    @DisplayName("ProjectionResult should remain a passive projection value")
    void projectionResult_ShouldRemainPassiveValue() {
        Method[] publicDeclaredMethods = Arrays.stream(ProjectionResult.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        assertThat(methodNames(ProjectionResult.class))
                .doesNotContain("appendCommutative", "appendNonCommutative", "appendIdempotent");

        assertThat(publicDeclaredMethods)
                .allSatisfy(method -> assertThat(method.getParameterTypes())
                        .doesNotContain(EventStore.class, AppendEvent.class));
    }

    private static String[] methodNames(Class<?> type) {
        return Arrays.stream(type.getMethods())
                .map(Method::getName)
                .toArray(String[]::new);
    }
}
