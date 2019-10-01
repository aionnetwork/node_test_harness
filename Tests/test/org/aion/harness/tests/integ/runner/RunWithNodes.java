package org.aion.harness.tests.integ.runner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.aion.harness.main.NodeFactory.NodeType;

/**
 * Specifies all of the nodes that the declaring test is eligible to be run against.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RunWithNodes {

    NodeType[] value();
}
