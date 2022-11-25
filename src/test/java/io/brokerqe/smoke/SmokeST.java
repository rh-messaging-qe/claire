/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.brokerqe.AbstractST;
import io.fabric8.kubernetes.api.model.Namespace;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;

public class SmokeST extends AbstractST {

    @Test
    void getNamespaceTest() {
        Namespace ns = getClient().getClient().namespaces().withName("default").get();
        assertThat(ns, is(notNullValue()));
    }
}
