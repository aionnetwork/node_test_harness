package org.aion.harness.tests.integ.runner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that any class which bears this annotation is to be run in isolation; that is, not
 * concurrently alongside other tests.
 *
 * Note that classes that declare this annotation must be responsible for managing their own node's
 * lifecycle. These are for tests that do not fit into the concurrent model of depending upon an
 * external node.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RunInIsolation {

}
