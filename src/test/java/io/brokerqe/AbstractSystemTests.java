/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.amq.broker.v1alpha1.ActiveMQArtemisSecurity;
import io.amq.broker.v2alpha5.ActiveMQArtemis;
import io.amq.broker.v2alpha3.ActiveMQArtemisAddress;
import io.brokerqe.operator.ActiveMQArtemisClusterOperator;
import io.brokerqe.separator.TestSeparator;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AbstractSystemTests implements TestSeparator {

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractSystemTests.class);

    private KubeClient client;

    protected ActiveMQArtemisClusterOperator operator;

    public KubeClient getClient() {
        return this.client;
    }
    public KubernetesClient getKubernetesClient() {
        return this.client.getKubernetesClient();
    }

    public String getRandomNamespaceName(String nsPrefix, int randomLength) {
        return nsPrefix + "-" + getRandomString(randomLength);
    }

    public static String getRandomString(int length) {
        if (length > 96) {
            LOGGER.warn("Trimming to max size of 96 chars");
            length = 96;
        }
        StringBuilder randomStr = new StringBuilder(UUID.randomUUID().toString());
        while (randomStr.length() < length) {
            randomStr.append(UUID.randomUUID().toString());
        }
        randomStr = new StringBuilder(randomStr.toString().replace("-", ""));
        return randomStr.substring(0, length);
    }

    @BeforeAll
    void setupTestEnvironment() {
        setupLoggingLevel();
        client = new KubeClient("default");
        ResourceManager resourceManager = ResourceManager.getInstance();
    }

    void setupLoggingLevel() {
        String envLogLevel = System.getenv("TEST_LOG_LEVEL");
        if (envLogLevel == null || envLogLevel.equals("")) {
            LOGGER.debug("Not setting log level at all.");
        } else {
            Level envLevel = Level.toLevel(envLogLevel.toUpperCase(Locale.ROOT));
            LOGGER.info("All logging changed to level: {}", envLevel.levelStr);
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            List<ch.qos.logback.classic.Logger> loggerList = loggerContext.getLoggerList();
            loggerList.stream().forEach(tmpLogger -> tmpLogger.setLevel(envLevel));
        }
    }



    /*******************************************************************************************************************
     *  TYPED API - bug, but fixed and will be available in 6.3x (works in local build)
     ******************************************************************************************************************/
    @Disabled("Does not work yet! Use Typeless way https://github.com/fabric8io/kubernetes-client/pull/4612")
    protected ActiveMQArtemis createArtemisTyped(String namespace, String filePath, boolean waitForDeployment) {
        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(filePath, ActiveMQArtemis.class);
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(artemisBroker).createOrReplace();
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", artemisBroker, namespace);
        if (waitForDeployment) {
            LOGGER.info("Waiting for creation of broker {} in namespace {}", artemisBroker.getMetadata().getName(), namespace);
//            GenericKubernetesResource finalBrokerCR = brokerCR;
            String brokerName = artemisBroker.getMetadata().getName();
            TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
                StatefulSet ss = getClient().getStatefulSet(namespace, brokerName + "-ss");
                return ss.getStatus().getReadyReplicas().equals(ss.getSpec().getReplicas())
                        && ResourceManager.getArtemisClient().inNamespace(namespace).withName(brokerName).get()
                        .getStatus().getPodStatus().getReady().size() == ss.getSpec().getReplicas();
            });
        }
        return artemisBroker;
    }

    protected void deleteArtemisTyped(String namespace, ActiveMQArtemis broker, boolean waitForDeletion) {
        String brokerName = broker.getMetadata().getName();
        ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).delete();
        if (waitForDeletion) {
            TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
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
    protected List<StatusDetails> deleteArtemisAddress(String namespace, ActiveMQArtemisAddress activeMQArtemisAddress) {
        List<StatusDetails> status = ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(activeMQArtemisAddress).delete();
        LOGGER.info("Deleted ActiveMQArtemisAddress {} in namespace {}", activeMQArtemisAddress.getMetadata().getName(), namespace);
        return status;
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

    /*******************************************************************************************************************
     *  TYPELESS API - used only until Typed API is fixed
     ******************************************************************************************************************/
    protected GenericKubernetesResource createArtemisTypeless(String namespace, String filePath) {
        return createArtemisTypeless(namespace, filePath, true);
    }

    protected GenericKubernetesResource createArtemisTypelessFromString(String namespace, InputStream yamlStream, boolean waitForDeployment) {
        CustomResourceDefinitionContext brokerCrdContextFromCrd = getArtemisCRDContext(Constants.CRD_ACTIVEMQ_ARTEMIS);
//        LOGGER.debug("[{}] Deploying broker using stringYaml {}", namespace, stringYaml);
        GenericKubernetesResource brokerCR = getKubernetesClient().genericKubernetesResources(brokerCrdContextFromCrd).load(yamlStream).get();
        brokerCR = getKubernetesClient().genericKubernetesResources(brokerCrdContextFromCrd).inNamespace(namespace).resource(brokerCR).createOrReplace();
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, brokerCR);
        }
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", brokerCR, namespace);
        return brokerCR;
    }

    protected GenericKubernetesResource createArtemisTypeless(String namespace, String filePath, boolean waitForDeployment) {
        CustomResourceDefinitionContext brokerCrdContextFromCrd = getArtemisCRDContext(Constants.CRD_ACTIVEMQ_ARTEMIS);
        LOGGER.debug("[{}] Deploying broker using file {}", namespace, filePath);
        GenericKubernetesResource brokerCR = getKubernetesClient().genericKubernetesResources(brokerCrdContextFromCrd).load(filePath).get();
        brokerCR = getKubernetesClient().genericKubernetesResources(brokerCrdContextFromCrd).inNamespace(namespace).resource(brokerCR).createOrReplace();
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, brokerCR);
        }
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", brokerCR, namespace);
        return brokerCR;
    }

    private void waitForBrokerDeployment(String namespace, GenericKubernetesResource brokerCR) {
        LOGGER.info("Waiting for creation of broker {} in namespace {}", brokerCR.getMetadata().getName(), namespace);
        String brokerName = brokerCR.getMetadata().getName();
        TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            StatefulSet ss = getClient().getStatefulSet(namespace, brokerName + "-ss");
            return ss.getStatus().getReadyReplicas().equals(ss.getSpec().getReplicas());
        });
    }

    protected List<StatusDetails> deleteArtemisTypeless(String namespace, String brokerName) {
        return deleteArtemisTypeless(namespace, brokerName, true);
    }
    protected List<StatusDetails> deleteArtemisTypeless(String namespace, String brokerName, boolean waitForDeletion) {
        CustomResourceDefinitionContext brokerCrdContextFromCrd = getArtemisCRDContext(Constants.CRD_ACTIVEMQ_ARTEMIS);
        List<StatusDetails> status = getKubernetesClient().genericKubernetesResources(brokerCrdContextFromCrd).inNamespace(namespace).withName(brokerName).delete();
        if (waitForDeletion) {
            LOGGER.info("Waiting on deletion of ActiveMQArtemis StatefulSet {} from namespace {}", brokerName, namespace);
            TestUtils.waitFor("StatefulSet to be removed", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES,
                    () -> getClient().getStatefulSet(namespace, brokerName + "-ss") == null
                            &&  getClient().listPodsByPrefixInName(namespace, brokerName).size() == 0
            );
        }
        LOGGER.info("Deleted ActiveMQArtemis {} from namespace {}", brokerName, namespace);
        return status;
    }

    protected CustomResourceDefinitionContext getArtemisCRDContext(String crdName) {
        return CustomResourceDefinitionContext.fromCrd(getKubernetesClient().apiextensions().v1().customResourceDefinitions().withName(crdName).get());
    }
}
