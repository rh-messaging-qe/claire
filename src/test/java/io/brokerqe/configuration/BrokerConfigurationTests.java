/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisSpecBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Env;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;


public class BrokerConfigurationTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerConfigurationTests.class);
    private final String testNamespace = getRandomNamespaceName("brkconfig-tests", 6);
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
    void initialVariableSettingTest() {
        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(ArtemisFileProvider.getArtemisSingleExampleFile().toFile(), ActiveMQArtemis.class);
        artemisBroker.setSpec(new ActiveMQArtemisSpecBuilder()
            .addAllToEnv(List.of(getEnvItem()))
            .build());
        artemisBroker = ResourceManager.createArtemis(testNamespace, artemisBroker);
        checkEnvVariables(artemisBroker);
    }

    @Test
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
        LOGGER.info("[{}] Checking for expected variables {}:{}", testNamespace, envItem.getName(), envItem.getValue());
        String processes = getClient().executeCommandInPod(testNamespace, brokerPod,  "ps ax | grep java", 10);
        assertThat(processes, containsString(val));
        assertThat(envItem, is(notNullValue()));
        assertThat(envItem.getValue(), is(val));
        // should be pre-populated
        assertThat(envVars.size(), greaterThan(1));
    }

}
