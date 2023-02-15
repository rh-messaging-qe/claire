/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.activemqartemisspec.Env;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ResourceManager;
import io.brokerqe.executor.Executor;
import io.brokerqe.smoke.SmokeTests;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("env-tests", 6);
    private final String name = "JDK_JAVA_ARGS";
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
        item.setName(val);
        return item;
    }

    private String getPs(String namespace, String podname) {
        try (Executor ex = new Executor()) {
            return ex.execCommandOnPod(podname, namespace, 10, "ps", "ax", "|", "grep", "java");
        }
    }

    @Test
    void initialVariableSettingTest() {
        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, "env-broker");
        artemisBroker.getSpec().getEnv().add(getEnvItem());
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(artemisBroker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, artemisBroker);
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, artemisBroker.getMetadata().getName());
        List<EnvVar> envVars = brokerPod.getSpec().getContainers().get(0).getEnv();
        EnvVar envItem = null;
        for (int i = 0; i < envVars.size(); i++) {
            if (envVars.get(i).getName().equals(name)) {
                envItem = envVars.get(i);
                break;
            }
        }

        String processes = getPs(testNamespace, artemisBroker.getMetadata().getName());
        assertThat(processes, containsString(val));
        assertThat(envItem, is(notNullValue()));
        assertThat(envItem.getValue(), is(val));

        // should be pre-populated
        assertThat(envVars.size(), greaterThan(1));
    }

    @Test
    void variableAddedAfterDeployTest() {
        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, "env-broker");
        artemisBroker.getSpec().getEnv().add(getEnvItem());
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(artemisBroker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, artemisBroker);

        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, artemisBroker.getMetadata().getName());
        List<EnvVar> envVars = brokerPod.getSpec().getContainers().get(0).getEnv();
        EnvVar envItem = null;
        for (EnvVar envVar : envVars) {
            if (envVar.getName().equals(name)) {
                envItem = envVar;
                break;
            }
        }

        String processes = getPs(testNamespace, artemisBroker.getMetadata().getName());
        assertThat(processes, containsString(val));
        assertThat(envItem, is(notNullValue()));
        assertThat(envItem.getValue(), is(val));
        // should be pre-populated
        assertThat(envVars.size(), greaterThan(1));
    }


}
