/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client;

import io.brokerqe.claire.Constants;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AmqpUtil {

    private AmqpUtil() {
        super();
    }

    public static String buildAmqpUrl(String hostAndPort) {
        return Constants.AMQP_URL_PREFIX + hostAndPort;
    }

    public static String buildAmqFailoverUrl(String failoverOptions, String... hostAndPort) {
        String servers = Stream.of(hostAndPort).map(AmqpUtil::buildAmqpUrl).collect(Collectors.joining(","));
        return "failover:(" +  servers + ")" + (Objects.isNull(failoverOptions) ? "" : "?" + failoverOptions);
    }

}
