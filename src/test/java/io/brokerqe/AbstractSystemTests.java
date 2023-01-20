/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.brokerqe.junit.TestSeparator;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({TestDataCollector.class})
public class AbstractSystemTests implements TestSeparator {

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractSystemTests.class);

    private KubeClient client;

    protected ArtemisCloudClusterOperator operator;

    protected Environment testEnvironment;
    protected ResourceManager resourceManager;

    public KubeClient getClient() {
        return client;
    }
    public KubernetesClient getKubernetesClient() {
        return this.client.getKubernetesClient();
    }

    public String getRandomNamespaceName(String nsPrefix, int randomLength) {
        if (testEnvironment == null) {
            testEnvironment = new Environment();
        }
        if (testEnvironment.isDisabledRandomNs()) {
            return nsPrefix;
        } else {
            return nsPrefix + "-" + TestUtils.getRandomString(randomLength);
        }
    }

    @BeforeAll
    void setupTestEnvironment() {
        if (testEnvironment == null) {
            testEnvironment = new Environment();
        }
        setupLoggingLevel();
        resourceManager = ResourceManager.getInstance(testEnvironment);
        client = ResourceManager.getKubeClient();
        // Following log is added for debugging purposes, when OkHttpClient leaks connection
        java.util.logging.Logger.getLogger(OkHttpClient.class.getName()).setLevel(java.util.logging.Level.FINE);
    }

    @AfterAll
    void teardownTestEnvironment() {
        ResourceManager.undeployAllResources();
    }

    void setupLoggingLevel() {
        String envLogLevel = testEnvironment.getTestLogLevel();
        if (envLogLevel == null || envLogLevel.equals("")) {
            LOGGER.debug("Not setting log level at all.");
        } else {
            Level envLevel = Level.toLevel(envLogLevel.toUpperCase(Locale.ROOT));
            LOGGER.info("All logging changed to level: {}", envLevel.levelStr);
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            List<ch.qos.logback.classic.Logger> loggerList = loggerContext.getLoggerList();
            loggerList.stream().forEach(
                    tmpLogger -> {
                        // Do not set `ROOT` and `io` logger, as it would set it on all used components, not just this project.
                        if (!List.of("ROOT", "io").contains(tmpLogger.getName())) {
                            tmpLogger.setLevel(envLevel);
                        }
                    });
        }
    }

    /******************************************************************************************************************
     *  Default setup and teardown methods
     ******************************************************************************************************************/
    protected void setupDefaultClusterOperator(String testNamespace) {
        getClient().createNamespace(testNamespace, true);
        operator = ResourceManager.deployArtemisClusterOperator(testNamespace);
    }

    protected void teardownDefaultClusterOperator(String testNamespace) {
        ResourceManager.undeployArtemisClusterOperator(operator);
        if (!ResourceManager.isClusterOperatorManaged()) {
            getClient().deleteNamespace(testNamespace);
        }
    }

    /******************************************************************************************************************
     *  Helper methods
     ******************************************************************************************************************/
    protected Acceptors createAcceptor(String name, String protocols) {
        Acceptors acceptors = new Acceptors();
        acceptors.setName(name);
        acceptors.setProtocols(protocols);
        acceptors.setPort(5672);
        return acceptors;
    }

    protected ActiveMQArtemis addAcceptorsWaitForPodReload(String namespace, List<Acceptors> acceptors, ActiveMQArtemis broker) {
        broker.getSpec().setAcceptors(acceptors);
        broker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).createOrReplace();
        String brokerName = broker.getMetadata().getName();
        client.waitForPodReload(namespace, getClient().getFirstPodByPrefixName(namespace, brokerName), brokerName);
        return broker;
    }

    protected Service getArtemisServiceHdls(String namespace, ActiveMQArtemis broker) {
        return getClient().getServiceByNames(namespace, broker.getMetadata().getName() + "-hdls-svc");
    }

    protected String getServicePortNumber(String namespace, Service service, String portName) {
        return getServicePort(namespace, service, portName).getTargetPort().getValue().toString();
    }

    protected ServicePort getServicePort(String namespace, Service service, String portName) {
        return service.getSpec().getPorts().stream().filter(port -> {
            return port.getName().equals(portName);
        }).collect(Collectors.toList()).get(0);
    }
}
