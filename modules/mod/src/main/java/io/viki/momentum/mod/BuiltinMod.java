package io.viki.momentum.mod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a mod entrypoint supplied by the application classpath.
 *
 * <p>The class must be a concrete class implementing {@link Mod}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BuiltinMod {
}
