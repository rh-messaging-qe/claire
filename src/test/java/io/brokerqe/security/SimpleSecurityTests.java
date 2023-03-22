/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;


import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurityBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.loginmodules.PropertiesLoginModulesBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.loginmodules.propertiesloginmodules.UsersBuilder;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.clients.BundledCoreMessagingClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class SimpleSecurityTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSecurityTests.class);
    private final String testNamespace = getRandomNamespaceName("simple-sec-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    void userPropertiesSecretTest() {
        String brokerName = "my-test-artemis";
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, brokerName);
        String secretName = "security-properties-prop-module";
        Map<String, String> users = Map.of(
            "secureUser1", "password123",
            "secureUser2", "Pass!$%/r/n//");
        getClient().createSecretStringData(testNamespace, secretName, users, true);

        ActiveMQArtemisSecurity artemisSecurity = new ActiveMQArtemisSecurityBuilder()
            .editOrNewMetadata()
                .withName("amq-broker-security")
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .withApplyToCrNames("*")
                .editOrNewLoginModules()
                    .withPropertiesLoginModules(new PropertiesLoginModulesBuilder()
                        .withName("prop-module")
                        .withUsers(List.of(
                            new UsersBuilder()
                                .withName("alice")
                                .withRoles("admin")
                                .withPassword("alicesecret")
                                .build(),
                            new UsersBuilder()
                                .withName("bob")
                                .withRoles("admin")
                                .withPassword("bobsecret")
                                .build(),
                            new UsersBuilder()
                                .withName("secureUser1")
                                .withRoles("admin")
                                .build(),
                            new UsersBuilder()
                                .withName("secureUser2")
                                .withRoles("admin")
                                .build()
                        ))
                        .build())
                .endV1beta1LoginModules()
                .editOrNewSecurityDomains()
                    .editOrNewBrokerDomain()
                    .withName("activemq")
                        .addNewBrokerdomainLoginModule()
                            .withName("prop-module")
                            .withFlag("sufficient")
                        .endBrokerdomainLoginModule()
                    .endV1beta1BrokerDomain()
                .endV1beta1SecurityDomains()
            .endSpec()
            .build();

        String cmd = "cat " + Constants.CONTAINER_BROKER_HOME_ETC_DIR + "artemis-users.properties";
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        String usersPropertiesFile = getClient().executeCommandInPod(testNamespace, brokerPod, cmd, Constants.DURATION_10_SECONDS);
        assertThat(usersPropertiesFile, not(containsString("alice")));
        assertThat(usersPropertiesFile, not(containsString("bob")));
        assertThat(usersPropertiesFile, not(containsString("secureUser1")));
        assertThat(usersPropertiesFile, not(containsString("secureUser2")));

        LOGGER.info("[{}] Creating artemis security {} with secret: {}",
            testNamespace, artemisSecurity.getMetadata().getName(), secretName);
        ResourceManager.createArtemisSecurity(testNamespace, artemisSecurity);
        ResourceManager.waitForBrokerDeployment(testNamespace, broker);

        brokerPod = getClient().waitForPodReload(testNamespace, brokerPod, brokerPod.getMetadata().getName());
        usersPropertiesFile = getClient().executeCommandInPod(testNamespace, brokerPod, cmd, Constants.DURATION_10_SECONDS);
        assertThat("artemis-users.properties didn't contain \"alice\".", usersPropertiesFile, containsString("alice = ENC("));
        assertThat("artemis-users.properties didn't contain \"bob\").", usersPropertiesFile, containsString("bob = ENC("));
        assertThat("artemis-users.properties didn't contain \"secureUser1\".", usersPropertiesFile, containsString("secureUser1 = ENC("));
        assertThat("artemis-users.properties didn't contain \"secureUser2\".", usersPropertiesFile, containsString("secureUser2 = ENC("));

        // If passwords are encrypted it shouldn't contain passwords in plaintext
        assertThat("artemis-users.properties contained unmasked password.", usersPropertiesFile, not(containsString("alicesecret")));
        assertThat("artemis-users.properties contained unmasked password.", usersPropertiesFile, not(containsString("bobsecret")));
        assertThat("artemis-users.properties contained unmasked password.", usersPropertiesFile, not(containsString("password123")));
        assertThat("artemis-users.properties contained unmasked password.", usersPropertiesFile, not(containsString("Pass!$%/r/n//")));

        LOGGER.info("[{}] Check for sending an receiving messages", testNamespace);
        Map<String, String> testMsgUsers = Map.of(
            "alice", "alicesecret",
            "bob", "bobsecret",
            "secureUser1", "password123",
            "secureUser2", "Pass!$%/r/n//");
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
        int msgsExpected = 10;

        for (Map.Entry<String, String> entry : testMsgUsers.entrySet()) {
            String username = entry.getKey();
            String password = entry.getValue();
            MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(),
                msgsExpected, username, password);
            int sent = messagingClientCore.sendMessages();
            int received = messagingClientCore.receiveMessages();
            LOGGER.info("[{}] User: {}, Sent {} - Received {}",
                testNamespace, username, sent, received);
            assertThat(sent, equalTo(msgsExpected));
            assertThat(sent, equalTo(received));
            assertThat("Sent and received messages weren't the same.", messagingClientCore.compareMessages(), is(true));
        }

        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisSecurity(testNamespace, artemisSecurity);
    }

}
