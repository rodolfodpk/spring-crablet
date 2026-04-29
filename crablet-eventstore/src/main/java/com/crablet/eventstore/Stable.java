package com.crablet.eventstore;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of the stable public API.
 * Types annotated with {@code @Stable} follow semantic versioning:
 * breaking changes will not be introduced without a MAJOR version bump.
 * <p>
 * Types without this annotation should be treated as subject to change.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Stable {
}
