/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.brokerqe.separator.TestSeparator;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

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

    /*******************************************************************************************************************
     *  ActiveMQArtemis Usage of generated typed API
     ******************************************************************************************************************/
    protected ActiveMQArtemis createArtemis(String namespace, String filePath) {
        return createArtemis(namespace, filePath, true);
    }

    protected ActiveMQArtemis createArtemis(String namespace, String filePath, boolean waitForDeployment) {
        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(filePath, ActiveMQArtemis.class);
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(artemisBroker).createOrReplace();
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", artemisBroker, namespace);
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, artemisBroker);
        }
        return artemisBroker;
    }

    protected ActiveMQArtemis createArtemisFromString(String namespace, InputStream yamlStream, boolean waitForDeployment) {
        LOGGER.trace("[{}] Deploying broker using stringYaml {}", namespace, yamlStream);
        ActiveMQArtemis brokerCR = ResourceManager.getArtemisClient().inNamespace(namespace).load(yamlStream).get();
        brokerCR = ResourceManager.getArtemisClient().inNamespace(namespace).resource(brokerCR).createOrReplace();
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, brokerCR);
        }
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", brokerCR, namespace);
        return brokerCR;
    }

    protected void deleteArtemis(String namespace, ActiveMQArtemis broker) {
        deleteArtemis(namespace, broker, true);
    }

    protected void deleteArtemis(String namespace, ActiveMQArtemis broker, boolean waitForDeletion) {
        String brokerName = broker.getMetadata().getName();
        ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).delete();
        if (waitForDeletion) {
            TestUtils.waitFor("StatefulSet to be removed", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
                StatefulSet ss = getClient().getStatefulSet(namespace, brokerName + "-ss");
                return ss == null && getClient().listPodsByPrefixInName(namespace, brokerName).size() == 0;
            });
        }
    }

    protected ActiveMQArtemisAddress createArtemisAddress(String namespace, String filePath) {
        ActiveMQArtemisAddress artemisAddress = TestUtils.configFromYaml(filePath, ActiveMQArtemisAddress.class);
        artemisAddress = ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(artemisAddress).createOrReplace();
        // TODO check it programmatically
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Created ActiveMQArtemisAddress {} in namespace {}", artemisAddress, namespace);
        return artemisAddress;
    }

    protected List<StatusDetails> deleteArtemisAddress(String namespace, String addressName) {
        throw new NotImplementedException();
    }
    protected void deleteArtemisAddress(String namespace, ActiveMQArtemisAddress activeMQArtemisAddress) {
        List<StatusDetails> status = ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(activeMQArtemisAddress).delete();
        LOGGER.info("Deleted ActiveMQArtemisAddress {} in namespace {}", activeMQArtemisAddress.getMetadata().getName(), namespace);
    }

    protected ActiveMQArtemisSecurity createArtemisSecurity(String namespace, String filePath) {
        ActiveMQArtemisSecurity artemisSecurity = TestUtils.configFromYaml(filePath, ActiveMQArtemisSecurity.class);
        artemisSecurity = ResourceManager.getArtemisSecurityClient().inNamespace(namespace).resource(artemisSecurity).createOrReplace();
        LOGGER.info("Created ActiveMQArtemisSecurity {} in namespace {}", artemisSecurity, namespace);
        return artemisSecurity;
    }

    protected List<StatusDetails> deleteArtemisSecurity(String namespace, ActiveMQArtemisSecurity artemisSecurity) {
        List<StatusDetails> status = ResourceManager.getArtemisSecurityClient().inNamespace(namespace).resource(artemisSecurity).delete();
        LOGGER.info("Deleted ActiveMQArtemisSecurity {} in namespace {}", artemisSecurity.getMetadata().getName(), namespace);
        return status;
    }

    protected void waitForBrokerDeployment(String namespace, ActiveMQArtemis brokerCR) {
        waitForBrokerDeployment(namespace, brokerCR, false);
    }
    protected void waitForBrokerDeployment(String namespace, ActiveMQArtemis brokerCR, boolean reloadExisting) {
        LOGGER.info("Waiting for creation of broker {} in namespace {}", brokerCR.getMetadata().getName(), namespace);
        String brokerName = brokerCR.getMetadata().getName();
        if (reloadExisting) {
            // TODO: make more generic and resource specific wait
            LOGGER.debug("[{}] Reloading existing broker {}, sleeping for some time", namespace, brokerCR.getMetadata().getName());
            TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        }
        TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE, () -> {
            StatefulSet ss = getClient().getStatefulSet(namespace, brokerName + "-ss");
            return ss != null && ss.getStatus().getReadyReplicas() != null && ss.getStatus().getReadyReplicas().equals(ss.getSpec().getReplicas());
        });
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

    protected ActiveMQArtemis addAcceptors(String namespace, List<Acceptors> acceptors, ActiveMQArtemis broker) {
        broker.getSpec().setAcceptors(acceptors);
        broker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).createOrReplace();
        waitForBrokerDeployment(namespace, broker, true);
        return broker;
    }
}
