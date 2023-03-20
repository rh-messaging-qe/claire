/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisSpecBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Env;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ArtemisVersion;
import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.junit.TestValidSince;
import io.brokerqe.operator.ArtemisFileProvider;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;


public class BrokerConfigurationTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerConfigurationTests.class);
    private final String testNamespace = getRandomNamespaceName("brkconfig-tests", 6);

    private final static String CONFIG_BROKER_NAME = "cfg-broker";
    private final String name = "JAVA_ARGS_APPEND";
    private final String val = "-XshowSettings:system";

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
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
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void variableAddedAfterDeployTest() {
        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, "env-broker");
        artemisBroker.setSpec(new ActiveMQArtemisSpecBuilder()
                .withEnv(List.of(getEnvItem()))
                .build());
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(artemisBroker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, artemisBroker, true);
        checkEnvVariables(artemisBroker);
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
        String processes = getClient().executeCommandInPod(testNamespace, brokerPod, "ps ax | grep java", 10);
        assertThat("List of processes inside container didn't have expected arguments", processes, containsString(val));
        assertThat("Found env item with matching name, but its value is not what was expected", envItem.getValue(), is(val));
        // should be pre-populated
        assertThat("EnvVars in the statefulset only had single value", envVars.size(), greaterThan(1));
    }


    @Test
    void verifyStatefulSetContainerPort() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        StatefulSet amqss = getClient().getDefaultArtemisStatefulSet(broker.getMetadata().getName());
        List<ContainerPort> amqPorts = amqss.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        Assertions.assertThat(amqPorts)
                .extracting(ContainerPort::getName)
                .containsExactly(Constants.WEBCONSOLE_URI_PREFIX);
        Assertions.assertThat(amqPorts)
                .filteredOn("name", Constants.WEBCONSOLE_URI_PREFIX)
                .extracting(ContainerPort::getContainerPort)
                .containsOnly(Constants.CONSOLE_PORT);
    }

    @Test
    void verifyServiceContainerPort() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(CONFIG_BROKER_NAME)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
            .endSpec()
            .build();
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        KubeClient kube = getClient();
        Service svc = kube.getService(testNamespace, broker.getMetadata().getName() + "-wconsj-0-svc");
        List<ServicePort> amqPorts = svc.getSpec().getPorts();
        assertThat(String.format("List of AMQ Ports did not consist of the expected items: %s", amqPorts),
            amqPorts, contains(
                hasProperty("name", is(Constants.WEBCONSOLE_URI_PREFIX)),
                hasProperty("name", is(Constants.WEBCONSOLE_URI_PREFIX + "-0"))));
        Assertions.assertThat(amqPorts)
                .filteredOn("name", Constants.WEBCONSOLE_URI_PREFIX)
                .extracting(ServicePort::getPort)
                .containsOnly(Constants.CONSOLE_PORT);
    }

}
