/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper;

import io.brokerqe.claire.container.AbstractGenericContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

public class ContainerHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerHelper.class);

    public static void startContainersInParallel(AbstractGenericContainer... containers) {
        List<String> containerNames = Arrays.stream(containers).map(AbstractGenericContainer::getName).toList();
        LOGGER.debug(String.format("Starting multiple containers at same time: %s", containerNames));
        Arrays.stream(containers).parallel().forEach(AbstractGenericContainer::start);
    }

    public static void stopContainers(AbstractGenericContainer... containers) {
        Arrays.stream(containers).forEach(AbstractGenericContainer::stop);
    }
}
