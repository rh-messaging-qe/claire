/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisSpecBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.amq.broker.v1beta1.activemqartemisspec.Env;
import io.amq.broker.v1beta1.activemqartemisspec.deploymentplan.Resources;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.exception.WaitException;
import io.brokerqe.claire.helpers.JMXHelper;
import io.brokerqe.claire.helpers.AddressData;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddressBuilder;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.api.model.Route;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class BrokerConfigurationTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerConfigurationTests.class);
    private final String testNamespace = getRandomNamespaceName("brkconfig-tests", 3);
    private String testBrokerName;

    private final static String CONFIG_BROKER_NAME = "cfg-broker";
    private final static String AMQ_ACCEPTOR_NAME = "acceptor";
    private final String name = "JAVA_ARGS_APPEND";
    private final String val = "-XshowSettings:system";

    private Acceptors acceptor;

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
        testBrokerName = CONFIG_BROKER_NAME + "-" + testInfo.getTestMethod().orElseThrow().getName().toLowerCase(Locale.ROOT);
        testBrokerName = maybeStripBrokerName(testBrokerName, testNamespace);
        cleanResourcesAfterTest(testNamespace);
    }

    private Env getEnvItem() {
        Env item = new Env();
        item.setName(name);
        item.setValue(val);
        return item;
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void initialVariableSettingTest() {
        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(ArtemisFileProvider.getArtemisSingleExampleFile().toFile(), ActiveMQArtemis.class);
        artemisBroker.setSpec(new ActiveMQArtemisSpecBuilder()
                .addAllToEnv(List.of(getEnvItem()))
                .build());
        artemisBroker = ResourceManager.createArtemis(testNamespace, artemisBroker);
        checkEnvVariables(artemisBroker);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void variableAddedAfterDeployTest() {
        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, testBrokerName);
        artemisBroker.setSpec(new ActiveMQArtemisSpecBuilder()
                .withEnv(List.of(getEnvItem()))
                .build());
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisBroker.getMetadata().getName());
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(artemisBroker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, artemisBroker, true, artemisPod);
        checkEnvVariables(artemisBroker);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    private void checkEnvVariables(ActiveMQArtemis broker) {
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, broker.getMetadata().getName());
        List<EnvVar> envVars = brokerPod.getSpec().getContainers().get(0).getEnv();
        EnvVar envItem = null;
        for (EnvVar envVar : envVars) {
            if (envVar.getName().equals(name)) {
                envItem = envVar;
                break;
            }
        }
        assertThat("EnvVar is null", envItem, is(notNullValue()));
        LOGGER.info("[{}] Checking for expected variables {}:{}", testNamespace, envItem.getName(), envItem.getValue());
        String processes = getClient().executeCommandInPod(brokerPod, "ps ax | grep java", 10);
        assertThat("List of processes inside container didn't have expected arguments", processes, containsString(val));
        assertThat("Found env item with matching name, but its value is not what was expected", envItem.getValue(), is(val));
        // should be pre-populated
        assertThat("EnvVars in the statefulset only had single value", envVars.size(), greaterThan(1));
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void verifyStatefulSetContainerPort() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        StatefulSet amqss = getClient().getDefaultArtemisStatefulSet(broker.getMetadata().getName());
        List<ContainerPort> amqPorts = amqss.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        Assertions.assertThat(amqPorts)
                .extracting(ContainerPort::getName)
                .containsExactly(ArtemisConstants.WEBCONSOLE_URI_PREFIX);
        Assertions.assertThat(amqPorts)
                .filteredOn("name", ArtemisConstants.WEBCONSOLE_URI_PREFIX)
                .extracting(ContainerPort::getContainerPort)
                .containsOnly(ArtemisConstants.CONSOLE_PORT);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void verifyServiceContainerPort() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(getRandomName(testBrokerName, 3))
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
            .endSpec()
            .build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        Service svc = getClient().getService(testNamespace, broker.getMetadata().getName() + "-wconsj-0-svc");
        List<ServicePort> amqPorts = svc.getSpec().getPorts();
        assertThat(String.format("List of AMQ Ports did not consist of the expected items: %s", amqPorts),
            amqPorts, contains(
                hasProperty("name", Matchers.is(ArtemisConstants.WEBCONSOLE_URI_PREFIX)),
                hasProperty("name", is(ArtemisConstants.WEBCONSOLE_URI_PREFIX + "-0"))));
        Assertions.assertThat(amqPorts)
                .filteredOn("name", ArtemisConstants.WEBCONSOLE_URI_PREFIX)
                .extracting(ServicePort::getPort)
                .containsOnly(ArtemisConstants.CONSOLE_PORT);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    private void verifyResourceRequestValues(String type, Map<String, Quantity> reportedResources, IntOrString cpuValue, IntOrString memValue) {
        assertThat(String.format("CPU %s by pod was not %s: %s", type, cpuValue.getStrVal(), reportedResources.get("cpu")),
                reportedResources.get("cpu").getAmount() + reportedResources.get("cpu").getFormat(), equalTo(cpuValue.getStrVal()));
        assertThat(String.format("Memory %s by pod was not %s : %s", type, memValue.getStrVal(), reportedResources.get("memory")),
                reportedResources.get("memory").getAmount() + reportedResources.get("memory").getFormat(), equalTo(memValue.getStrVal()));
    }

    @Test
    void verifyResourceRequest() {
        Map<String, IntOrString> requestedResources = new HashMap<>();
        IntOrString cpuValue = new IntOrString("400m");
        IntOrString memValue = new IntOrString("512M");
        requestedResources.put("cpu", cpuValue);
        requestedResources.put("memory", memValue);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .editOrNewResources()
                        .withRequests(requestedResources)
                    .endDeploymentplanResources()
                .endDeploymentPlan()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker, true);
        Pod brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        Map<String, Quantity> requests = brokerPod.getSpec().getContainers().get(0).getResources().getRequests();
        verifyResourceRequestValues("request", requests, cpuValue, memValue);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void verifyResourceLimits() {
        getClient().printKubernetesNodesMetrics();
        getClient().printKubernetesPodsMetrics(testNamespace);
        Map<String, IntOrString> requestedResourceLimits = new HashMap<>();
        IntOrString cpuValue = new IntOrString("300m");
        IntOrString memValue = new IntOrString("300Mi");
        requestedResourceLimits.put("cpu", cpuValue);
        requestedResourceLimits.put("memory", memValue);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .editOrNewResources()
                        .withLimits(requestedResourceLimits)
                    .endDeploymentplanResources()
                .endDeploymentPlan()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);
        Pod brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        Map<String, Quantity> limits = brokerPod.getSpec().getContainers().get(0).getResources().getLimits();
        verifyResourceRequestValues("limit", limits, cpuValue, memValue);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }


    @Test
    void verifyResourceUpdates() {
        getClient().printKubernetesNodesMetrics();
        Map<String, IntOrString> requestedResources = new HashMap<>();
        IntOrString cpuValue = new IntOrString("300m");
        IntOrString memValue = new IntOrString("350Mi");
        requestedResources.put("cpu", cpuValue);
        requestedResources.put("memory", memValue);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .editOrNewResources()
                        .withLimits(requestedResources)
                        .withRequests(requestedResources)
                    .endDeploymentplanResources()
                .endDeploymentPlan()
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);
        Pod brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        Map<String, Quantity> limits = brokerPod.getSpec().getContainers().get(0).getResources().getLimits();
        Map<String, Quantity> requests = brokerPod.getSpec().getContainers().get(0).getResources().getRequests();
        verifyResourceRequestValues("limit", limits, cpuValue, memValue);
        verifyResourceRequestValues("request", requests, cpuValue, memValue);
        getClient().printKubernetesPodsMetrics(testNamespace);

        cpuValue = new IntOrString("400m");
        memValue = new IntOrString("400Mi");
        requestedResources.put("cpu", cpuValue);
        requestedResources.put("memory", memValue);
        broker.getSpec().getDeploymentPlan().getResources().setLimits(requestedResources);
        broker.getSpec().getDeploymentPlan().getResources().setRequests(requestedResources);

        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, true, brokerPod);

        brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        limits = brokerPod.getSpec().getContainers().get(0).getResources().getLimits();
        requests = brokerPod.getSpec().getContainers().get(0).getResources().getRequests();
        verifyResourceRequestValues("limit", limits, cpuValue, memValue);
        verifyResourceRequestValues("request", requests, cpuValue, memValue);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void verifyDefaultResourceRequests() {
        getClient().printKubernetesNodesMetrics();
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, testBrokerName);
        Pod brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        Map<String, Quantity> limits = brokerPod.getSpec().getContainers().get(0).getResources().getLimits();
        Map<String, Quantity> requests = brokerPod.getSpec().getContainers().get(0).getResources().getRequests();

        assertThat(String.format("Resource limits were applied by default: %s", limits), limits, aMapWithSize(0));
        assertThat(String.format("Resource requests were applied by default: %s", requests), requests, aMapWithSize(0));

        getClient().printKubernetesPodsMetrics(testNamespace);
        Map<String, IntOrString> requestedResources = new HashMap<>();
        IntOrString cpuValue = new IntOrString("300m");
        IntOrString memValue = new IntOrString("350Mi");
        requestedResources.put("cpu", cpuValue);
        requestedResources.put("memory", memValue);
        Resources resources = new Resources();
        resources.setLimits(requestedResources);
        resources.setRequests(requestedResources);
        broker.getSpec().getDeploymentPlan().setResources(resources);
        ResourceManager.updateArtemis(broker, true);

        brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        limits = brokerPod.getSpec().getContainers().get(0).getResources().getLimits();
        requests = brokerPod.getSpec().getContainers().get(0).getResources().getRequests();
        verifyResourceRequestValues("limit", limits, cpuValue, memValue);
        verifyResourceRequestValues("request", requests, cpuValue, memValue);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void smallVolumeTest() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withPersistenceEnabled()
                    .editOrNewStorage()
                        .withSize("1Gi")
                    .endStorage()
                    .withSize(1)
                .endDeploymentPlan()
            .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        PersistentVolumeClaim pvc = getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace).withName(testBrokerName + "-" + testBrokerName + "-ss-0").get();
        Quantity pvcSize = pvc.getSpec().getResources().getRequests().get("storage");
        assertThat(String.format("PVC requested wrong size: %s %s", pvcSize.getAmount(), pvcSize.getFormat()), pvcSize.getAmount(), is(equalTo("1")));
        assertThat("PVC requested wrong size (unit format): %s", pvcSize.getFormat(), is(equalTo("Gi")));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void volumeSizeWithNoUnitTest() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withPersistenceEnabled()
                    .editOrNewStorage()
                        .withSize("1")
                    .endStorage()
                    .withSize(1)
                .endDeploymentPlan()
            .endSpec().build();

        boolean waitForDeployment = !getClient().isMicroshiftPlatform();
        broker = ResourceManager.createArtemis(testNamespace, broker, waitForDeployment);
        if (!waitForDeployment) {
            TestUtils.waitFor("Waiting for pvc creation", Constants.DURATION_1_SECOND, Constants.DURATION_3_MINUTES,
                    () -> getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace)
                            .withName(testBrokerName + "-" + testBrokerName + "-ss-0").get() != null);
        }
        PersistentVolumeClaim pvc = getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace).withName(testBrokerName + "-" + testBrokerName + "-ss-0").get();
        Quantity pvcSize = pvc.getSpec().getResources().getRequests().get("storage");
        assertThat(String.format("PVC requested wrong size: %s %s", pvcSize.getAmount(), pvcSize.getFormat()), pvcSize.getAmount(), is(equalTo("1")));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void defaultVolumeSizeTest() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withPersistenceEnabled()
                    .withSize(1)
                .endDeploymentPlan()
            .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        PersistentVolumeClaim pvc = getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace).withName(testBrokerName + "-" + testBrokerName + "-ss-0").get();
        Quantity pvcSize = pvc.getSpec().getResources().getRequests().get("storage");
        assertThat(String.format("PVC requested wrong size: %s %s", pvcSize.getAmount(), pvcSize.getFormat()), pvcSize.getAmount(), is(equalTo("2")));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void volumeSizeChangeTest() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(testBrokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withPersistenceEnabled()
                    .editOrNewStorage()
                        .withSize("1Gi")
                    .endStorage()
                    .withSize(1)
                .endDeploymentPlan()
            .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, broker.getMetadata().getName());
        StatefulSet ss = getClient().getStatefulSet(testNamespace, testBrokerName + "-ss");
        broker.getSpec().getDeploymentPlan().getStorage().setSize("3Gi");
        broker.getSpec().getDeploymentPlan().setSize(2);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, true, brokerPod, Constants.DURATION_2_MINUTES, ss);
        PersistentVolumeClaim pvc = getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace).withName(testBrokerName + "-" + testBrokerName + "-ss-0").get();
        Quantity pvcDefaultSize = pvc.getSpec().getResources().getRequests().get("storage");
        PersistentVolumeClaim bigPvc = getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace).withName(testBrokerName + "-" + testBrokerName + "-ss-1").get();
        Quantity pvcBigSize = bigPvc.getSpec().getResources().getRequests().get("storage");

        // Storage claim/PV are not updated to avoid data loss - additional manual steps are required for properly migrating data between PVs
        assertThat(String.format("Storage claim was updated for the first broker: %s %s", pvcDefaultSize.getAmount(), pvcDefaultSize.getFormat()), pvcDefaultSize.getAmount(), is(equalTo("1")));
        assertThat(String.format("Storage claim is incorrect for second broker: %s %s", pvcBigSize.getAmount(), pvcBigSize.getFormat()), pvcBigSize.getAmount(), is(equalTo("3")));

        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    // Expects list of unique per-route postfixes.
    private void verifyRoutes(String brokerName, List<String> expectedPostfixes, Integer expectedCount) {
        List<Route> routes = getClient().getRouteByPrefixName(testNamespace, brokerName);
        assertThat("Amount of Routes is different from expected", routes.size(), equalTo(expectedCount));
        Integer countMatched = 0;
        for (Route route: routes) {
            String host = route.getSpec().getHost();
            for (String postfix: expectedPostfixes) {
                if (host.contains(postfix)) {
                    countMatched++;
                }
            }
        }
        assertThat("Amount of matched hosts isn't equal to expected", countMatched, equalTo(expectedCount));
    }

    private void verifySingleRoute(String brokerName, String expectedPostfix) {
        verifyRoutes(brokerName, List.of(expectedPostfix), 1);
    }

    @Test
    void noExposureTest() {
        acceptor = createAcceptor(AMQ_ACCEPTOR_NAME,
                "amqp",
                5672,
                false,
                false,
                null,
                false);

        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(acceptor))
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);

        List<Route> routes = getClient().getRouteByPrefixName(testNamespace, testBrokerName);
        assertThat("Route was created despite exposure = false", routes.size(), equalTo(0));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
    
    @Test
    void multiRouteTest() {
        Acceptors amqAcceptor1 = createAcceptor(AMQ_ACCEPTOR_NAME + "-one",
                "amqp",
                5672,
                true,
                false,
                null,
                false);
        Acceptors amqAcceptor2 = createAcceptor(AMQ_ACCEPTOR_NAME + "-two",
                "amqp",
                5671,
                true,
                false,
                null,
                false);

        List<Acceptors> acceptors = List.of(amqAcceptor1, amqAcceptor2);

        String brokerName = getRandomName(CONFIG_BROKER_NAME, 3);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(acceptors)
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);

        verifyRoutes(brokerName, List.of(amqAcceptor1.getName(), amqAcceptor2.getName()), 2);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
    
    @Test
    void routeCreationTest() {
        acceptor = createAcceptor(AMQ_ACCEPTOR_NAME,
                "amqp",
                5672,
                true,
                false,
                null,
                false);

        String brokerName = getRandomName(CONFIG_BROKER_NAME, 3);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(acceptor))
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);

        verifySingleRoute(brokerName, AMQ_ACCEPTOR_NAME);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void routeDeletionTest() {
        acceptor = createAcceptor(AMQ_ACCEPTOR_NAME,
                "amqp",
                5672,
                true,
                false,
                null,
                false);

        String brokerName = getRandomName(CONFIG_BROKER_NAME, 3);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(acceptor))
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);

        verifySingleRoute(brokerName, AMQ_ACCEPTOR_NAME);
        broker.getSpec().getAcceptors().clear();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, broker.getMetadata().getName());
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, true, brokerPod, Constants.DURATION_2_MINUTES);

        List<Route> routes = getClient().getRouteByPrefixName(testNamespace, brokerName);
        assertThat("Route was not removed when expected", routes.size(), equalTo(0));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void routeModificationTest() {
        acceptor = createAcceptor(AMQ_ACCEPTOR_NAME,
                "amqp",
                5672,
                true,
                false,
                null,
                false);

        String brokerName = getRandomName(CONFIG_BROKER_NAME, 3);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(acceptor))
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);

        verifySingleRoute(brokerName, AMQ_ACCEPTOR_NAME);
        broker.getSpec().getAcceptors().get(0).setName(brokerName);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, false, null, Constants.DURATION_2_MINUTES);
        verifySingleRoute(brokerName, brokerName);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
    
    @Test
    void routeCreationAfterBrokerTest() {
        acceptor = createAcceptor(AMQ_ACCEPTOR_NAME,
                "amqp",
                5672,
                true,
                false,
                null,
                false);

        String brokerName = getRandomName(CONFIG_BROKER_NAME, 3);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        broker.getSpec().setAcceptors(List.of(acceptor));
        ResourceManager.updateArtemis(broker, true);

        verifySingleRoute(brokerName, AMQ_ACCEPTOR_NAME);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void invalidRouteNameTest() {
        acceptor = createAcceptor(AMQ_ACCEPTOR_NAME,
                "amqp",
                5672,
                true,
                false,
                null,
                false);

        long startTime = System.nanoTime();
        String brokerName = getRandomName(CONFIG_BROKER_NAME + "-too-long-broker-name-with-short-domain", 20);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(acceptor))
                .endSpec().build();
        Throwable t = assertThrows(WaitException.class, () -> ResourceManager.createArtemis(testNamespace, broker, true, Constants.DURATION_10_SECONDS));
        assertThat(t.getMessage(), containsString("waiting for StatefulSet to be ready"));
        StatefulSet ss = getClient().getStatefulSet(testNamespace, brokerName + "-ss");
        assertNull(ss, "Statefulset is not null!");

        LOGGER.info("[{}] Checking for 'invalid route name' in CO log", testNamespace);
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod, (int) Duration.ofNanos(System.nanoTime() - startTime).toSeconds() + 5);
        assertThat(operatorLog, allOf(
                containsString("Failed to create new *v1.Route"),
                containsString(brokerName),
                containsString("must be no more than 63 characters, metadata.labels: Invalid value")
        ));
        boolean logStatus = ResourceManager.getArtemisStatus(testNamespace, broker, ArtemisConstants.CONDITION_TYPE_DEPLOYED,
                ArtemisConstants.CONDITION_REASON_RESOURCE_ERROR, "must be no more than 63 characters, metadata.labels: Invalid value: \\\"" + brokerName);
        assertThat("Artemis ready condition does not match", logStatus);

        boolean readyCondition = ResourceManager.getArtemisStatus(testNamespace, broker, ArtemisConstants.CONDITION_TYPE_READY,
                ArtemisConstants.CONDITION_REASON_WAITING_FOR_ALL_CONDITIONS, "Some conditions are not met");
        assertThat("Artemis ready condition does not match", readyCondition);
        
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    private ActiveMQArtemis validateAcceptorClash(Acceptors amq1, Acceptors amq2) {
        List<Acceptors> acceptors = List.of(amq1, amq2);
        String brokerName = "tralala";
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withPersistenceEnabled()
                    .withSize(1)
                .endDeploymentPlan()
                .withAcceptors(acceptors)
            .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, false);
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, broker, ArtemisConstants.CONDITION_TYPE_VALID, ArtemisConstants.CONDITION_REASON_ACCEPTOR_DUPLICATE, Constants.DURATION_5_MINUTES, false);
        StatefulSet amqss = getClient().getDefaultArtemisStatefulSet(broker.getMetadata().getName());
        assertNull(amqss, "Statefulset was unexpectedly created");
        return broker;
    }

    @Test
    void filterApplicationTest() {
        Acceptors amqpAcceptors = createAcceptor(AMQ_ACCEPTOR_NAME, "amqp", 5672, true, false, null, true);
        amqpAcceptors.setBindToAllInterfaces(true);
        String brokerName = "filter-test";

        String addressName = "queuewithfilter";
        String queueName = "queuewithfilter";
        BPActiveMQArtemisAddress address = new BPActiveMQArtemisAddressBuilder()
                .withAddressName(addressName)
                .withQueueName(queueName)
                .withRoutingType(ArtemisConstants.ROUTING_TYPE_ANYCAST)
                .build();

        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(2)
                        .withManagementRBACEnabled()
                    .endDeploymentPlan()
                .withAcceptors(amqpAcceptors)
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
                .withBrokerProperties(address.getPropertiesList())
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        int msgsExpected = 50;
        Service amqp = getClient().getFirstServiceBrokerAcceptor(testNamespace, brokerName, AMQ_ACCEPTOR_NAME);
        Integer amqpPort = amqp.getSpec().getPorts().get(0).getPort();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod brokerPod1 = brokerPods.get(0);
        Pod brokerPod2 = brokerPods.get(1);

        //     public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String port, String address, String queue, int messages, String username, String password) {
        MessagingClient messagingClientAmqp1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod1,
                amqpPort.toString(), addressName, queueName, msgsExpected);
        MessagingClient messagingClientAmqp2 = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod2,
                amqpPort.toString(), addressName, queueName, msgsExpected);
        messagingClientAmqp1.sendMessages();
        messagingClientAmqp2.sendMessages();
        JMXHelper jmx = new JMXHelper().withKubeClient(getClient());

        AddressData address1 = jmx.getAddressQueue(brokerName, address, 0);
        AddressData address2 = jmx.getAddressQueue(brokerName, address, 1);

        assertThat(String.format("Address %s on pod %s has %d messages after filter update, expected %d.",
                        addressName, brokerPod1.getMetadata().getName(), address1.getMsgCount(), msgsExpected),
                address1.getMsgCount(), equalTo(msgsExpected));
        assertThat(String.format("Address %s on pod %s has %d messages after filter update, expected %d.",
                        addressName, brokerPod2.getMetadata().getName(), address2.getMsgCount(), msgsExpected),
                address2.getMsgCount(), equalTo(msgsExpected));
        address = new BPActiveMQArtemisAddressBuilder()
                .fromBPAddress(address)
                .addQueueProperty(queueName, "filterString", "color = 'red'").build();
        broker.getSpec().setBrokerProperties(address.getPropertiesList());
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();

        ResourceManager.waitForArtemisStatusUpdateSequential(testNamespace, broker,
                ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED, ArtemisConstants.CONDITION_REASON_OUT_OF_SYNC,
                ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED, ArtemisConstants.CONDITION_REASON_APPLIED, Constants.DURATION_5_MINUTES);

        MessagingClient messagingClientAmqpUpdated1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod1,
                amqpPort.toString(), address, msgsExpected);
        MessagingClient messagingClientAmqpUpdated2 = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod2,
                amqpPort.toString(), address, msgsExpected);
        int sentUpdated1 = messagingClientAmqpUpdated1.sendMessages();
        int sentUpdated2 = messagingClientAmqpUpdated2.sendMessages();

        AddressData addressUpdated1 = jmx.getAddressQueue(brokerName, addressName, queueName, ArtemisConstants.ROUTING_TYPE_ANYCAST, 0);
        AddressData addressUpdated2 = jmx.getAddressQueue(brokerName, addressName, queueName, ArtemisConstants.ROUTING_TYPE_ANYCAST, 1);

        assertThat(String.format("Address %s on pod %s has %d messages after filter update, expected %d.",
                addressName, brokerPod1.getMetadata().getName(), addressUpdated1.getMsgCount(), msgsExpected),
                addressUpdated2.getMsgCount(), equalTo(msgsExpected));
        assertThat(String.format("Address %s on pod %s has %d messages after filter update, expected %d.",
                addressName, brokerPod2.getMetadata().getName(), addressUpdated2.getMsgCount(), msgsExpected),
                addressUpdated2.getMsgCount(), equalTo(msgsExpected));

       // ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @Disabled
    //see ENTMQBR-8586
    void acceptorNameClashTest() {
        Acceptors acceptor1 = createAcceptor(AMQ_ACCEPTOR_NAME + "-original",
                "amqp",
                5672,
                true,
                false,
                null,
                false);
        Acceptors acceptor2 = createAcceptor(AMQ_ACCEPTOR_NAME + "-original",
                "amqp",
                5673,
                true,
                false,
                null,
                false);
        ActiveMQArtemis broker = validateAcceptorClash(acceptor1, acceptor2);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void acceptorPortClashTest() {
        Acceptors acceptor1 = createAcceptor(AMQ_ACCEPTOR_NAME + "-original",
                "amqp1",
                5672,
                true,
                false,
                null,
                false);
        Acceptors acceptor2 = createAcceptor(AMQ_ACCEPTOR_NAME + "-original",
                "amqp2",
                5672,
                true,
                false,
                null,
                false);
        ActiveMQArtemis broker = validateAcceptorClash(acceptor1, acceptor2);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @Disabled
    void routeCreationNonAscii() {
        String nonAsciiAcceptorName = AMQ_ACCEPTOR_NAME + "Ж";
        acceptor = createAcceptor(nonAsciiAcceptorName,
                "amqp",
                5672,
                true,
                false,
                null,
                false);

        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withPersistenceEnabled()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(acceptor))
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker, false);
        // This is speculative expectation, to be fixed in future.
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, broker, ArtemisConstants.CONDITION_TYPE_VALID, ArtemisConstants.CONDITION_FALSE, Constants.DURATION_5_MINUTES, false);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void verifyMultipleLabels() {
        Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            labels.put("label" + i, "value" + i);
        }
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withLabels(labels)
                    .endDeploymentPlan()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker, true);

        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();
        String updateTime = broker.getMetadata().getManagedFields().get(1).getTime();

        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        ActiveMQArtemis newbroker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();
        String newUpdateTime = newbroker.getMetadata().getManagedFields().get(1).getTime();
        assertEquals(updateTime, newUpdateTime, "CR Status was updated when its not expected to be");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void verifyLabels() {
        Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            labels.put("label" + i, "value" + i);
        }
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withLabels(labels)
                    .endDeploymentPlan()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker, false);

        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();

        Pod brokerPod = getClient().getPod(testNamespace, testBrokerName + "-ss-0");
        for (String item: labels.keySet()) {
            assertThat("Pod doesn't have expected label applied", brokerPod.getMetadata().getLabels(), hasEntry(item, labels.get(item)));
        }
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    void testResourceTemplateWithKindSelector() {
        String annotationKey = "my-test-annotation-name";
        String annotationValue = "my-test-annotation";
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                .addNewResourceTemplate()
                    .withNewResourcetemplatesSelector()
                        .withKind("Service")
                    .endResourcetemplatesSelector()
                    .addToAnnotations(annotationKey, annotationValue)
                .endResourceTemplate()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                    .endDeploymentPlan()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker);
        List<Service> services = ResourceManager.getKubeClient().getServicesInNamespace(testNamespace);
        Assertions.assertThat(services)
                .withFailMessage("No specified annotations found on services")
                .extracting(e -> e.getMetadata().getAnnotations().get(annotationKey).equals(annotationValue))
                .contains(true);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    void testResourceTemplateWithPatch() {
        String amqpAcceptorName = "amqp-acceptor";
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .addNewResourceTemplate()
                        .withNewResourcetemplatesSelector()
                            .withKind("Service")
                        .endResourcetemplatesSelector()
                        .withNewPatch()
                            .addToAdditionalProperties(Map.of(
                                "kind", "Service",
                                "spec", Map.of("publishNotReadyAddresses", false)
                            ))
                        .endPatch()
                    .endResourceTemplate()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                    .endDeploymentPlan()
                    .addNewAcceptor()
                        .withExpose(true)
                        .withProtocols("amqp")
                        .withPort(5672)
                        .withSslEnabled(false)
                        .withNeedClientAuth(false)
                        .withName(amqpAcceptorName)
                    .endAcceptor()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker);
        Service service = ResourceManager.getKubeClient().getService(testNamespace, testBrokerName + "-" + amqpAcceptorName + "-0-svc");
        Assertions.assertThat(Boolean.TRUE.equals(service.getSpec().getPublishNotReadyAddresses()))
                .withFailMessage("publishNotReadyAddresses is not false. Patch may not have worked")
                .isFalse();
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    void testAcceptorWithPublishNotReadyAddressAsFalseByDefault() {
        String amqpAcceptorName = "amqp-acceptor";
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(testBrokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                    .endDeploymentPlan()
                    .addNewAcceptor()
                        .withExpose(true)
                        .withProtocols("amqp")
                        .withPort(5672)
                        .withSslEnabled(false)
                        .withNeedClientAuth(false)
                        .withName(amqpAcceptorName)
                    .endAcceptor()
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker);
        Service service = ResourceManager.getKubeClient().getService(testNamespace, testBrokerName + "-" + amqpAcceptorName + "-0-svc");
        Assertions.assertThat(service.getSpec().getPublishNotReadyAddresses())
                .withFailMessage("publishNotReadyAddresses should be true by default")
                .isTrue();
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    void testOperatorSelector() {
        String brokerName = "brk-selector";
        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, brokerName);
        Deployment operatorDeployment = getClient().getDeployment(testNamespace, operator.getOperatorName());
        ReplicaSet operatorRS = getClient().getReplicaSetsWithPrefix(testNamespace, operator.getOperatorName()).get(0);
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());

        LOGGER.info("[{}] Check selectors in Operator Deployment and ReplicaSet", testNamespace);
        // amq-broker-operator --> operator.getOperatorOldName();
        Map<String, String> expectedSelectors = Map.of("name", operator.getOperatorOldName());
        containExpectedSelectors(operatorDeployment.getSpec().getSelector().getMatchLabels(), expectedSelectors);
        containExpectedSelectors(operatorRS.getSpec().getSelector().getMatchLabels(), expectedSelectors);

        LOGGER.info("[{}] Check labels in deployed operator Pod {}", testNamespace, operatorPod.getMetadata().getName());
        containExpectedLabels(operatorPod, expectedSelectors);

        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }
}
