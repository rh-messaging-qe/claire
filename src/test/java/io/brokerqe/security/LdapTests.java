/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ArtemisVersion;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.clients.BundledCoreMessagingClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.clients.MessagingClientException;
import io.brokerqe.junit.TestValidSince;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class LdapTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapTests.class);
    private final String testNamespace = getRandomNamespaceName("ldap-tests", 6);
    private Openldap openldap;
    String secretName = "ldaplogin-jaas-config";
    String brokerName = "artemis";
    ActiveMQArtemis broker;
    ActiveMQArtemisAddress myAddress;
    Pod brokerPod;
    String allDefaultPort;

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        openldap = ResourceManager.getOpenldapInstance(testNamespace);
        openldap.deployLdap();
        setupEnvironment();
    }

    @AfterAll
    void teardownClusterOperator() {
        openldap.undeployLdap();
        teardownDefaultClusterOperator(testNamespace);
    }

    private void setupEnvironment() {
        Map<String, String> jaasData = Map.of(
            Constants.LOGIN_CONFIG_CONFIG_KEY, """
                activemq {
                    org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule sufficient
                        reload=false
                        org.apache.activemq.jaas.properties.user="artemis-users.properties"
                        org.apache.activemq.jaas.properties.role="artemis-roles.properties"
                        baseDir="/home/jboss/amq-broker/etc";

                     org.apache.activemq.artemis.spi.core.security.jaas.LDAPLoginModule sufficient
                         debug=true
                         initialContextFactory=com.sun.jndi.ldap.LdapCtxFactory
                         connectionURL="ldap://openldap:1389"
                         connectionUsername="cn=admin,dc=example,dc=org"
                         connectionPassword=admin
                         connectionProtocol=s
                         authentication=simple
                         userBase="ou=users,dc=example,dc=org"
                         userSearchMatching="(cn={0})"
                         userSearchSubtree=false
                         roleBase="ou=users,dc=example,dc=org"
                         roleName=cn
                         roleSearchMatching="(member=cn={1},ou=users,dc=example,dc=org)"
                         roleSearchSubtree=false
                         ;
                    };
                """
        );
        // create automagically mounted secret ldaplogin-jaas-config
        getClient().createSecretStringData(testNamespace, secretName, jaasData, true);

        ConfigMap debugLoggingConfigMap = new ConfigMapBuilder()
            .editOrNewMetadata()
            .withName("debug-logging-config")
            .endMetadata()
            .withData(Map.of(Constants.LOGGING_PROPERTIES_CONFIG_KEY, """
                appender.stdout.name = STDOUT
                appender.stdout.type = Console
                rootLogger = info, STDOUT
                logger.activemq.name=org.apache.activemq.artemis.spi.core.security.jaas
                logger.activemq.level=debug
                logger.activemq.netty.name=activemq-netty
                logger.activemq.netty.level=info
                """))
            .build();
        getKubernetesClient().configMaps().inNamespace(testNamespace).resource(debugLoggingConfigMap).createOrReplace();

        // reference secret in the broker CR spec.extraMounts.secrets
        broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withImage("placeholder")
                    .editOrNewExtraMounts()
                        .withSecrets(secretName)
                        .withConfigMaps("debug-logging-config")
                    .endExtraMounts()
                .endDeploymentPlan()
                .withBrokerProperties(List.of(
                    "securityRoles.#.producers.send=true",
                    "securityRoles.#.consumers.consume=true",
                    "securityRoles.#.producers.createAddress=true",
                    "securityRoles.#.producers.createNonDurableQueue=true"
                        ))
            .endSpec()
            .build();
        broker = ResourceManager.createArtemis(testNamespace, broker);
        myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        brokerPod = getClient().listPodsByPrefixInName(testNamespace, brokerName).get(0);
        allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
    }

    @Test
    public void testSenderReceiverLdapUsers() {
        int messages = 5;
        LOGGER.info("[{}] Trying to send messages as {} with {}", testNamespace, "alice", "alice");
        MessagingClient producerAlice = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(),
                messages, "alice", "alice");
        int sentAlice = producerAlice.sendMessages();

        LOGGER.info("[{}] Trying to receive messages as {} with {}", testNamespace, "alice", "alice");
        Throwable t = assertThrows(MessagingClientException.class, producerAlice::receiveMessages);
        assertThat(t.getMessage(), containsString("does not have permission='CONSUME' for queue"));

        LOGGER.info("[{}] Trying to receive messages as {} with {}", testNamespace, "bob", "bob");
        MessagingClient consumerBob = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(),
                messages, "bob", "bob");
        int consumedBob = consumerBob.receiveMessages();
        assertThat(sentAlice, equalTo(messages));
        assertThat(sentAlice, equalTo(consumedBob));

        LOGGER.info("[{}] Trying to send messages as {} with {}", testNamespace, "bob", "bob");
        t = assertThrows(MessagingClientException.class, consumerBob::sendMessages);
        assertThat(t.getMessage(), containsString("does not have permission='SEND' on address"));
    }

    @Test
    public void testSingleUserWithPermissions() {
        int messages = 5;
        LOGGER.info("[{}] Send & receive messages as {} with {}", testNamespace, "charlie", "charlie");
        MessagingClient clientCharlie = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(),
                messages, "charlie", "charlie");
        int sent = clientCharlie.sendMessages();
        int received = clientCharlie.receiveMessages();
        assertThat(sent, equalTo(messages));
        assertThat(sent, equalTo(received));
    }

    @Test
    public void testWrongUser() {
        int messages = 2;
        // invalid user
        LOGGER.info("[{}] Trying to connect as invalid user {} with {}", testNamespace, "lala", "lala");
        MessagingClient wrongUser = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(),
                messages, "lala", "lala");
        Throwable t = assertThrows(MessagingClientException.class, wrongUser::sendMessages);
        assertThat(t.getMessage(), containsString("Unable to validate user from"));

        // incorrect pass
        LOGGER.info("[{}] Trying to connect as {} with invalid password {}", testNamespace, "charlie", "lala");
        wrongUser = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(),
                messages, "charlie", "lala");
        t = assertThrows(MessagingClientException.class, wrongUser::sendMessages);
        assertThat(t.getMessage(), containsString("Unable to validate user from"));
    }
}
