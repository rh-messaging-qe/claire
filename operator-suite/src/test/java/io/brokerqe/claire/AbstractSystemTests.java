/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisSpecBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.clients.BundledClientDeployment;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.junit.TestSeparator;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperator;
import io.brokerqe.claire.security.CertificateManager;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({OperatorTestDataCollector.class})
public abstract class AbstractSystemTests implements TestSeparator {

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractSystemTests.class);

    private KubeClient client;

    protected ArtemisCloudClusterOperator operator;

    protected EnvironmentOperator testEnvironmentOperator;
    protected TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
        ResourceManager.setTestInfo(testInfo);
        CertificateManager.setCertificateTestDirectory(TestUtils.getTestName(testInfo));
    }

    @AfterEach
    void cleanAfterTest() {
        if (TestUtils.isEmptyDirectory(CertificateManager.getCurrentTestDirectory())) {
            TestUtils.deleteFile(Path.of(CertificateManager.getCurrentTestDirectory()));
        }
    }

    protected void cleanResourcesAfterTest(String namespace) {
        LOGGER.info("[{}] Cleaning environment between tests", namespace);
        for (ActiveMQArtemis broker : ResourceManager.getArtemisClient().inNamespace(namespace).list().getItems()) {
            LOGGER.warn("[{}] Undeploying broker {}", namespace, broker.getMetadata().getName());
            ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).delete();
        }

        for (ActiveMQArtemisAddress address : ResourceManager.getArtemisAddressClient().inNamespace(namespace).list().getItems()) {
            LOGGER.warn("[{}] Removing address {}", namespace, address.getMetadata().getName());
            ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(address).delete();
        }

        List<Deployment> clientDeployments = getClient().getDeploymentByPrefixName(namespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
        for (Deployment clientDeployment : clientDeployments) {
            LOGGER.warn("[{}] Undeploying forgotten clients deployment {}", namespace, clientDeployment.getMetadata().getName());
            ResourceManager.undeployClientsContainer(namespace, clientDeployment);
        }

        List<Pod> clientsPods = getClient().listPodsByPrefixName(namespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
        for (Pod pod : clientsPods) {
            LOGGER.warn("[{}] Deleting forgotten clients pods {}", namespace, pod.getMetadata().getName());
            getClient().deletePod(namespace, pod);
        }
    }

    public KubeClient getClient() {
        if (ResourceManager.getKubeClient() == null) {
            ResourceManager.getInstance(testEnvironmentOperator);
        }
        client = ResourceManager.getKubeClient();
        return client;
    }

    public void setClient(KubeClient kubeClient) {
        ResourceManager.setKubeClient(kubeClient);
        client = kubeClient;
    }

    public KubernetesClient getKubernetesClient() {
        return this.client.getKubernetesClient();
    }

    public String getRandomNamespaceName(String nsPrefix, int randomLength) {
        testEnvironmentOperator = ResourceManager.getEnvironment();
        if (testEnvironmentOperator.isDisabledRandomNs()) {
            return nsPrefix;
        } else {
            return getRandomName(nsPrefix, randomLength);
        }
    }

    public String getRandomName(String prefix, int randomLength) {
        return prefix + "-" + TestUtils.getRandomString(randomLength);
    }

    public String getExpectedVersion() {
        if (testEnvironmentOperator.isOlmInstallation()) {
            return operator.getOperatorOLMVersion(true);
        } else {
            return testEnvironmentOperator.getArtemisVersion();
        }
    }

    public void containExpectedSelectors(Map<String, String> selectors, Map<String, String> expectedSelectors) {
        expectedSelectors.entrySet().forEach(e -> {
            assertThat("Selectors do not contain expected key", selectors.containsKey(e.getKey()));
            assertThat("Selectors do not contain expected value", selectors.containsValue(e.getValue()));
        });
    }

    public void containExpectedLabels(HasMetadata resource, Map<String, String> expectedLabels) {
        Map<String, String> labels = resource.getMetadata().getLabels();
        expectedLabels.entrySet().forEach(e -> {
            assertThat("Selectors do not contain expected key", labels.containsKey(e.getKey()));
            assertThat("Selectors do not contain expected value", labels.containsValue(e.getValue()));
        });
    }

    /******************************************************************************************************************
     *  Default setup and teardown methods
     ******************************************************************************************************************/
    protected void setupDefaultClusterOperator(String testNamespace) {
        getClient().createNamespace(testNamespace, true);
        operator = ResourceManager.deployArtemisClusterOperator(testNamespace);
    }

    protected void teardownDefaultClusterOperator(String testNamespace) {
        if (ResourceManager.isClusterOperatorManaged()) {
            if (operator == null) {
                LOGGER.warn("[{}] Skipping teardown of cluster Operator as it is null! (Already removed?)", testNamespace);
            } else {
                ResourceManager.undeployArtemisClusterOperator(operator);
            }
        }
        getClient().deleteNamespace(testNamespace);
    }

    /******************************************************************************************************************
     *  Helper methods
     ******************************************************************************************************************/
    // TODO: Move these methods to some more appropriate location?
    protected Acceptors createAcceptor(String name, String protocols, int port) {
        return createAcceptor(name, protocols, port, false, false, null, false);
    }

    protected Acceptors createAcceptor(String name, String protocols, int port, boolean expose) {
        return createAcceptor(name, protocols, port, expose, false, null, false);
    }

    protected Acceptors createAcceptor(String name, String protocols, int port, boolean expose, boolean sslEnabled,
                                       String sslSecretName, boolean needClientAuth) {
        return createAcceptor(name, protocols, port, expose, sslEnabled, sslSecretName, needClientAuth, null);
    }

    protected Acceptors createAcceptor(String name, String protocols, int port, boolean expose, boolean sslEnabled,
                                       String sslSecretName, boolean needClientAuth, String sslProvider) {
        Acceptors acceptors = new Acceptors();
        acceptors.setName(name);
        acceptors.setProtocols(protocols);
        acceptors.setPort(port);
        acceptors.setExpose(expose);
        if (sslEnabled) {
            acceptors.setSslEnabled(true);
            if (sslSecretName != null) {
                acceptors.setSslSecret(sslSecretName);
            }
            acceptors.setNeedClientAuth(needClientAuth);
            if (sslProvider != null) {
                acceptors.setSslProvider(sslProvider);
            }
        }
        return acceptors;
    }

    protected ActiveMQArtemis addAcceptorsWaitForPodReload(String namespace, List<Acceptors> acceptors, ActiveMQArtemis broker) {
        List<String> acceptorNames = acceptors.stream().map(Acceptors::getName).collect(Collectors.toList());
        LOGGER.info("[{}] Adding acceptors {} to broker {}", namespace, acceptorNames, broker.getMetadata().getName());
        if (broker.getSpec() == null) {
            broker.setSpec(new ActiveMQArtemisSpecBuilder().withAcceptors(acceptors).build());
        } else {
            broker.getSpec().setAcceptors(acceptors);
        }
        broker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).createOrReplace();
        String brokerName = broker.getMetadata().getName();
        client.waitForPodReload(namespace, getClient().getFirstPodByPrefixName(namespace, brokerName), brokerName);
        return broker;
    }

    protected Service getArtemisServiceHdls(String namespace, ActiveMQArtemis broker) {
        return getClient().getServiceByName(namespace, broker.getMetadata().getName() + "-hdls-svc");
    }

    protected String getServicePortNumber(String namespace, Service service, String portName) {
        return getServicePort(namespace, service, portName).getTargetPort().getValue().toString();
    }

    protected ServicePort getServicePort(String namespace, Service service, String portName) {
        return service.getSpec().getPorts().stream().filter(port -> {
            return port.getName().equals(portName);
        }).toList().get(0);
    }

    protected ActiveMQArtemis doArtemisScale(String namespace, ActiveMQArtemis broker, int previousSize, int newSize) {
        LOGGER.info("[{}] Starting Broker scaledown {} -> {}", namespace, previousSize, newSize);
        broker.getSpec().getDeploymentPlan().setSize(newSize);
        broker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).createOrReplace();
        long waitTime = Math.abs(previousSize - newSize) * Constants.DURATION_2_MINUTES;

        if (previousSize > newSize && newSize != 0 && broker.getSpec().getDeploymentPlan().getMessageMigration()) {
            waitForScaleDownDrainer(namespace, operator.getOperatorName(),
                    broker.getMetadata().getName(), waitTime, previousSize, newSize);
        } else {
            boolean reload = previousSize != 0;
            ResourceManager.waitForBrokerDeployment(namespace, broker, reload, null, waitTime);
            ResourceManager.waitForBrokerPodsExpectedCount(namespace, broker, newSize, waitTime);
        }
        List<Pod> brokers = getClient().listPodsByPrefixName(namespace, broker.getMetadata().getName());
        assertEquals(brokers.size(), newSize);
        LOGGER.info("[{}] Performed Broker scaledown {} -> {}", namespace, previousSize, newSize);
        return broker;
    }

    /**
     * We are looking for a log 'Drain pod my-broker-ss-1 finished.' which is present in ArtemisClusterOperator.
     * It has to be present N times (based on scaledown factor (from 3 brokers to 1 broker -> 2)
     */
    public void waitForScaleDownDrainer(String namespace, String operatorName, String brokerName, long maxTimeout, int previousSize, int newSize) {
        // Drain pod my-broker-ss-1 finished.
        // Deleting drain pod my-broker-ss-1
        int expectedDrainPodsCount = previousSize - newSize;
        Instant now = Instant.now().minus(Duration.ofSeconds(5));
        Pod operatorPod = getClient().getFirstPodByPrefixName(namespace, operatorName);
        Pattern pattern = Pattern.compile("Drain pod " + brokerName + ".* finished");

        TestUtils.waitFor("Drain pod to finish", Constants.DURATION_2_SECONDS, maxTimeout, () -> {
            String log = getClient().getLogsFromPod(operatorPod, now);
            Matcher matcher = pattern.matcher(log);
            int count = 0;
            while (matcher.find()) {
                for (int i = previousSize - 1; i >= newSize; i--) {
                    if (matcher.group().contains(String.valueOf(i))) {
                        count++;
                        LOGGER.debug("Found drain: " + matcher.group());
                        break;
                    }
                }
            }
            LOGGER.info("[{}] Scaledown in progress. Finished Drainer {}/{}", namespace, count, expectedDrainPodsCount);
            return count == expectedDrainPodsCount;
        });
        // Wait for drainPods to disappear

    }

    protected String maybeStripBrokerName(String testBrokerName, String testNamespace) {
        return maybeStripBrokerName(testBrokerName, testNamespace, "wconsj");
    }

    protected String maybeStripBrokerName(String testBrokerName, String testNamespace, String routePart) {
        //testBrokerName = CONFIG_BROKER_NAME + "-" + testInfo.getTestMethod().orElseThrow().getName().toLowerCase(Locale.ROOT);
        //configmap naming scheme is configmap-${ssname}-9 digits
        //this has a limit of length = 63, otherwise configmap won't get created and whole deployment would fail
        String configMapName = "configmap-" + testBrokerName + "-123456789";
        int diff = 0;
        if (configMapName.length() > 63) {
            diff = configMapName.length() - 63;
        }

        // spec.host in Route has limit of 63 characters.
        // If route would spill out and be longer than 63 characters, tests would silently fail: no routes would be created,
        // but this would only be indicated in operator log

        String urlHost = testBrokerName + "-" + routePart + "-0-svc-rte-" + testNamespace;
        if (urlHost.length() > 63) {
            if (urlHost.length() > configMapName.length()) {
                diff = urlHost.length() - 63;
            }
        }

        if (diff != 0) {
            String newBrokerName = testBrokerName.substring(0, testBrokerName.length() - diff);
            LOGGER.warn("[{}] Stripping broker name from {} to {} due to length limitations", testNamespace,
                    testBrokerName, newBrokerName);
            testBrokerName = newBrokerName;
        }
        return testBrokerName;
    }

    public void waitConsoleReady(String url, long pool, long timeout) {
        TestUtils.waitFor("wait for console be ready", pool, timeout,
                () -> isHttpResponse(TestUtils.makeInsecureHttpsRequest(url), HttpURLConnection.HTTP_OK, "hawtio-login"));
    }

    public boolean isHttpResponse(URLConnection connection, int expectedCode, String expectedString) {
        InputStream response;
        try {
            response = connection.getInputStream();
            assertThat(((HttpURLConnection) connection).getResponseCode(), equalTo(expectedCode));
            Scanner scanner = new Scanner(response);
            String responseBody = scanner.useDelimiter("\\A").next();
            response.close();
            return responseBody.contains(expectedString);
        } catch (IOException e) {
            // carry on with execution, we've got expected exception
            if (e.getMessage().contains(String.valueOf(expectedCode))) {
                assertThat(e.getMessage(), containsString(String.valueOf(expectedCode)));
            } else {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public void checkHttpResponse(URLConnection connection, int expectedCode, String expectedString) {
        InputStream response = null;
        try {
            response = connection.getInputStream();
            assertThat(((HttpURLConnection) connection).getResponseCode(), equalTo(expectedCode));
            Scanner scanner = new Scanner(response);
            String responseBody = scanner.useDelimiter("\\A").next();
            assertThat(responseBody, containsString(expectedString));
            response.close();
        } catch (IOException e) {
            // carry on with execution, we've got expected exception
            if (e.getMessage().contains(String.valueOf(expectedCode))) {
                assertThat(e.getMessage(), containsString(String.valueOf(expectedCode)));
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    protected void patchRouteTls(HasMetadata service, String edgeTerminationPolicy, String termination) {
        if (getClient().getKubernetesPlatform().equals(KubernetesPlatform.OPENSHIFT)) {
            Route route = (Route) service;
            route.getSpec().setTls(new TLSConfigBuilder().withInsecureEdgeTerminationPolicy(edgeTerminationPolicy).withTermination(termination).build());
            route.getMetadata().setManagedFields(null);
            ((OpenShiftClient) getKubernetesClient()).routes().withName(route.getMetadata().getName()).patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY), route);
            TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        } else {
            // Once supported, remove Openshift limitation
            throw new ClaireNotImplementedException("Ingress is not supported yet!");
        }
    }

    // Messaging methods
    public void checkMessageCount(String namespace, Pod brokerPod, String queue, int expectedMessageCount) {
        int actual = getMessageCount(namespace, brokerPod, queue);
        assertThat("Unexpected amount of messages in queue", actual, equalTo(expectedMessageCount));
    }

    public int getMessageCount(String namespace, Pod brokerPod, String queue) {
        Map<String, Map<String, String>> queueStats = getMessageCount(namespace, brokerPod, null, queue);
        return Integer.parseInt(queueStats.get(queue).get("message_count"));
    }

    public Map<String, Map<String, String>> getMessageCount(String namespace, Pod brokerPod, Map<String, String> queueStatOptions, String queueName) {
        if (queueStatOptions == null) {
            queueStatOptions = new HashMap<>(Map.of(
                    "maxColumnSize", "-1",
                    "maxRows", "1000",
                    "url", "tcp://" + brokerPod.getMetadata().getName() + ":61616"
            ));
        }
        if (queueName != null) {
            queueStatOptions.put("queueName", queueName);
        }
        BundledArtemisClient bac = new BundledArtemisClient(new BundledClientDeployment(namespace, brokerPod), ArtemisCommand.QUEUE_STAT, queueStatOptions);
        Map<String, Map<String, String>> queueStats = (Map<String, Map<String, String>>) bac.executeCommand();
        return queueStats;
    }

    public void testMessaging(String namespace, Pod brokerPod, ActiveMQArtemisAddress address, int messages) {
        testMessaging(ClientType.BUNDLED_CORE, namespace, brokerPod, address, messages, null, null);
    }

    public void testMessaging(ClientType clientType, String namespace, Pod brokerPod, ActiveMQArtemisAddress address, int messages) {
        testMessaging(clientType, namespace, brokerPod, address, messages, null, null);
    }

    public void testMessaging(ClientType clientType, String namespace, Pod brokerPod, ActiveMQArtemisAddress address, int messages, String username, String password) {
        Deployment clients = null;
        MessagingClient messagingClient;
        if (clientType.equals(ClientType.BUNDLED_AMQP)) {
            messagingClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod, "5672", address, messages, username, password);
        } else if (clientType.equals(ClientType.BUNDLED_CORE)) {
            messagingClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod, "61616", address, messages, username, password);
        } else if (clientType.equals(ClientType.ST_AMQP_QPID_JMS)) {
            Pod clientsPod = getClient().getFirstPodByPrefixName(namespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
            if (clientsPod == null) {
                clients = ResourceManager.deployClientsContainer(namespace);
                clientsPod = getClient().getFirstPodByPrefixName(namespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
            }
            messagingClient = ResourceManager.createMessagingClient(ClientType.ST_AMQP_QPID_JMS, clientsPod, brokerPod.getStatus().getPodIP(), "5672", address, messages, username, password);
        } else {
            throw new ClaireRuntimeException("Unknown/Unsupported client type!" + clientType);
        }

        messagingClient.subscribe();
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();

        assertThat(sent, equalTo(messages));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));
        if (clients != null) {
            ResourceManager.undeployClientsContainer(namespace, clients);
        }
    }

    public void testTlsMessaging(String namespace, ActiveMQArtemisAddress address,
                                 String externalBrokerUri, String saslMechanism, String secretName,
                                 String clientKeyStore, String clientKeyStorePassword, String clientTrustStore, String clientTrustStorePassword) {
        testTlsMessaging(namespace, address, externalBrokerUri, saslMechanism, secretName,
                null, clientKeyStore, clientKeyStorePassword, clientTrustStore, clientTrustStorePassword);
    }

    public void testTlsMessaging(String namespace, ActiveMQArtemisAddress address,
                                 String externalBrokerUri, String saslMechanism, String secretName, Pod clientsPod,
                                 String clientKeyStore, String clientKeyStorePassword, String clientTrustStore, String clientTrustStorePassword) {
        Deployment clients = null;
        if (clientsPod == null) {
            clients = ResourceManager.deploySecuredClientsContainer(namespace, List.of(secretName));
            clientsPod = getClient().getFirstPodByPrefixName(namespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
        }
        int msgsExpected = 10;
        int sent = -1;
        int received = 0;

        // Publisher - Receiver
        MessagingClient messagingClient = ResourceManager.createMessagingClientTls(clientsPod, externalBrokerUri, address, msgsExpected, saslMechanism,
                "/etc/" + secretName + "/" + clientKeyStore, clientKeyStorePassword,
                "/etc/" + secretName + "/" + clientTrustStore, clientTrustStorePassword);
        sent = messagingClient.sendMessages();
        received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));
        if (clientsPod == null) {
            ResourceManager.undeployClientsContainer(namespace, clients);
        }
    }
}
