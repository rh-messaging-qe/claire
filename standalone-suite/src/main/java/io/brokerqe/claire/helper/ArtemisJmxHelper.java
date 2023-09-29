/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.container.ArtemisContainer;
import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.MalformedURLException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * This class was based on
 * <a href=
 * "https://github.com/apache/activemq-artemis/blob/main/tests/artemis-test-support/src/main/java/org/apache/activemq/artemis/tests/util/Jmx.java">
 * ActiveMQ Artemis Jmx.class</a>
 */
public final class ArtemisJmxHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisJmxHelper.class);
    private static final String JMX_URL_BASE = "service:jmx:rmi:///jndi/rmi://";
    private static final String JMX_URL_SUFFIX = "/jmxrmi";

    private ArtemisJmxHelper() {
        super();
    }

    @FunctionalInterface
    public interface ThrowableFunction<T, R> {

        R apply(T t) throws Exception;
    }

    public static Long getQueueCount(ArtemisContainer artemisContainer, String address, String queue,
                                     RoutingType routingType, long expectedResult, long retries, long pollMs) {
        LOGGER.debug("[Container {}] - Checking address {} and queue {} for number of messages", artemisContainer.getName(),
                address, queue);
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        try {
            ObjectName ob = getObjectBuilder(artemisContainer).getQueueObjectName(
                    SimpleString.toSimpleString(address),
                    SimpleString.toSimpleString(queue), routingType);
            return TimeHelper.retry(() -> queryControl(serviceURI, ob, QueueControl::getMessageCount,
                    QueueControl.class, throwable -> null).orElse(null), expectedResult, retries, pollMs);
        }  catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

    public static Long getAddressPageCount(ArtemisContainer artemisContainer, String address, long expectedResult,
                                           long retries, long pollMs) {
        LOGGER.debug("[Container {}] - Checking address {} number of pages", artemisContainer.getName(), address);
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        try {
            ObjectName ob = getObjectBuilder(artemisContainer).getAddressObjectName(SimpleString.toSimpleString(address));
            return TimeHelper.retry(() -> queryControl(serviceURI, ob, AddressControl::getNumberOfPages,
                    AddressControl.class, throwable -> null).orElse(null), expectedResult, retries, pollMs);
        }  catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

    public static boolean isPaging(ArtemisContainer artemisContainer, String address, boolean expectedResult,
                                   long retries, long pollMs) {
        LOGGER.debug("[Container {}] - Checking if address {} is paging", artemisContainer.getName(), address);
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        try {
            ObjectName ob = getObjectBuilder(artemisContainer).getAddressObjectName(SimpleString.toSimpleString(address));
            return TimeHelper.retry(() -> queryControl(serviceURI, ob, AddressControl::isPaging, AddressControl.class,
                    throwable -> null).orElse(false), expectedResult, retries, pollMs);
        }  catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

    private static ObjectName getArtemisObjectName(ArtemisContainer artemisContainer) {
        ObjectName objectName;
        try {
            // Use default `artemis` name, if profile is forced
            String artemisConfig = EnvironmentStandalone.getInstance().getProvidedArtemisConfig();
            if (artemisConfig != null) {
                String artemisName = TestUtils.getElementByXpathFromXml("/configuration/core/name", artemisConfig + "/broker.xml");
                objectName = getObjectBuilder(artemisName).getActiveMQServerObjectName();
            } else {
                objectName = getObjectBuilder(artemisContainer).getActiveMQServerObjectName();
            }
        } catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
        return objectName;
    }

    public static boolean isStarted(ArtemisContainer artemisContainer, boolean expectedResult, long retries,
                                    long timeoutInMs) {
        LOGGER.debug("[Container {}] - Checking if is started", artemisContainer.getName());
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        try {
            ObjectName objectName = getArtemisObjectName(artemisContainer);
            return TimeHelper.retry(() -> queryControl(serviceURI, objectName, ActiveMQServerControl::isStarted,
                    ActiveMQServerControl.class, t -> false).orElse(false), expectedResult, retries, timeoutInMs);
        } catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

    public static boolean isLive(ArtemisContainer artemisContainer, boolean expectedResult, long retries, long timeoutInMs) {
        LOGGER.debug("[Container {}] - Checking if is the live", artemisContainer.getName());
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        ObjectName objectName = getArtemisObjectName(artemisContainer);
        return TimeHelper.retry(() -> queryControl(serviceURI, objectName, ActiveMQServerControl::isActive,
                ActiveMQServerControl.class, t -> false).orElse(false), expectedResult, retries, timeoutInMs);
    }

    public static boolean isBackup(ArtemisContainer artemisContainer, boolean expectedResult, long retries,
                                   long timeoutInMs) {
        LOGGER.debug("[Container {}] - Checking if is the backup", artemisContainer.getName());
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        try {
            ObjectName objectName = getArtemisObjectName(artemisContainer);
            return TimeHelper.retry(() -> queryControl(serviceURI, objectName, ActiveMQServerControl::isBackup,
                    ActiveMQServerControl.class, throwable -> null).orElse(false), expectedResult, retries,
                    timeoutInMs);
        } catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

    public static boolean isReplicaInSync(ArtemisContainer artemisContainer, boolean expectedResult, long retries,
                                          long timeoutInMs) {
        LOGGER.debug("[Container {}] - Checking if has replica in sync", artemisContainer.getName());
        JMXServiceURL serviceURI = getJmxUrl(artemisContainer);
        try {
            ObjectName objectName = getArtemisObjectName(artemisContainer);
            return TimeHelper.retry(() -> queryControl(serviceURI, objectName, ActiveMQServerControl::isReplicaSync,
                    ActiveMQServerControl.class, throwable -> null).orElse(false), expectedResult, retries,
                    timeoutInMs);
        } catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

    private static <C, T> Optional<T> queryControl(JMXServiceURL serviceURI, ObjectName objectName,
                                                   ArtemisJmxHelper.ThrowableFunction<C, T> queryControl,
                                                   Class<C> controlClass, Function<Throwable, T> onThrowable) {
        try {
            LOGGER.trace("Connecting using JMX on {}", serviceURI);
            try (JMXConnector jmx = JMXConnectorFactory.connect(serviceURI)) {
                final C control = MBeanServerInvocationHandler.newProxyInstance(jmx.getMBeanServerConnection(),
                        objectName, controlClass, false);
                return Optional.ofNullable(queryControl.apply(control));
            }
        } catch (Exception e) {
            Optional<T> exceptionHandler = Optional.ofNullable(onThrowable.apply(e));
            if (exceptionHandler.isEmpty()) {
                Throwable ex = e;
                if (e instanceof UndeclaredThrowableException) {
                    ex = ((UndeclaredThrowableException) e).getUndeclaredThrowable();
                }
                String errMsg = String.format("Error on getting JMX info: %s", ex.getMessage());
                Objects.requireNonNull(LOGGER).error(errMsg);
                throw new ClaireRuntimeException(ex.getMessage(), ex);
            }
            return exceptionHandler;
        }
    }

    private static JMXServiceURL getJmxUrl(ArtemisContainer artemisContainer) {
        JMXServiceURL url;
        String hostAndPort = artemisContainer.getHostAndPort(ArtemisConstants.DEFAULT_JMX_PORT);
        try {
            url = new JMXServiceURL(JMX_URL_BASE + hostAndPort + JMX_URL_SUFFIX);
        } catch (MalformedURLException e) {
            String errMsg = String.format("Error on getting JMX url: %s", e.getMessage());
            LOGGER.error(errMsg, e);
            throw new ClaireRuntimeException(errMsg, e);
        }
        return url;
    }

    private static ObjectNameBuilder getObjectBuilder(ArtemisContainer artemisContainer) {
        return ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), artemisContainer.getName(),
                true);
    }

    private static ObjectNameBuilder getObjectBuilder(String artemisName) {
        return ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), artemisName,
                true);
    }

}
