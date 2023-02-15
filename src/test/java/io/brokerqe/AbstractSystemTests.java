/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisSpecBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.broker.Permissions;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.broker.PermissionsBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.management.authorisation.RoleAccess;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.management.authorisation.RoleAccessBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.management.authorisation.roleaccess.AccessListBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.clients.AmqpQpidClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.junit.TestSeparator;
import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith({TestDataCollector.class})
public class AbstractSystemTests implements TestSeparator {

    static final Logger LOGGER = LoggerFactory.getLogger(AbstractSystemTests.class);

    private KubeClient client;

    protected ArtemisCloudClusterOperator operator;

    protected Environment testEnvironment;

    public KubeClient getClient() {
        if (ResourceManager.getKubeClient() != null) {
            client = ResourceManager.getKubeClient();
        } else {
            LOGGER.error("KubeClient not initialized!");
        }
        return client;
    }
    public KubernetesClient getKubernetesClient() {
        return this.client.getKubernetesClient();
    }

    public String getRandomNamespaceName(String nsPrefix, int randomLength) {
        testEnvironment = ResourceManager.getEnvironment();
        if (testEnvironment.isDisabledRandomNs()) {
            return nsPrefix;
        } else {
            return nsPrefix + "-" + TestUtils.getRandomString(randomLength);
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

    protected List<Permissions> allAdminPermissions = List.of(
            new PermissionsBuilder().withOperationType("createAddress").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("deleteAddress").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("createDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("deleteDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("createNonDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("deleteNonDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("send").withRoles("admin", "sender").build(),
            new PermissionsBuilder().withOperationType("consume").withRoles("admin", "consumer").build(),
            new PermissionsBuilder().withOperationType("browse").withRoles("admin").build()
    );
    protected List<Permissions> activemqManagementAllAdminPermissions = List.of(
            new PermissionsBuilder().withOperationType("createAddress").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("deleteAddress").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("createDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("deleteDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("createNonDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("deleteNonDurableQueue").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("send").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("manage").withRoles("admin").build(),
            new PermissionsBuilder().withOperationType("consume").withRoles("admin").build()
    );

    protected RoleAccess roleAccess = new RoleAccessBuilder()
            .withDomain("org.apache.activemq.artemis")
            .withAccessList(List.of(
                    new AccessListBuilder().withMethod("get*").withRoles("admin", "viewer").build(),
                    new AccessListBuilder().withMethod("is*").withRoles("admin", "viewer").build(),
                    new AccessListBuilder().withMethod("set*").withRoles("admin").build(),
                    new AccessListBuilder().withMethod("browse*").withRoles("admin").build(),
                    new AccessListBuilder().withMethod("count*").withRoles("admin").build(),
                    new AccessListBuilder().withMethod("*").withRoles("admin").build()
            )).build();


    // TODO: Move these methods to some more appropriate location?
    protected Acceptors createAcceptor(String name, String protocols, int port) {
        return createAcceptor(name, protocols, port, false, false, null, false);
    }

    protected Acceptors createAcceptor(String name, String protocols, int port, boolean expose) {
        return createAcceptor(name, protocols, port, expose, false, null, false);
    }
    protected Acceptors createAcceptor(String name, String protocols, int port, boolean expose, boolean sslEnabled, String sslSecretName, boolean needClientAuth) {
        Acceptors acceptors = new Acceptors();
        acceptors.setName(name);
        acceptors.setProtocols(protocols);
        acceptors.setPort(port);
        acceptors.setExpose(expose);
        if (sslEnabled) {
            acceptors.setSslEnabled(sslEnabled);
            if (sslSecretName != null) {
                acceptors.setSslSecret(sslSecretName);
            }
            acceptors.setNeedClientAuth(needClientAuth);

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

    public void testTlsMessaging(String namespace, Pod brokerPod, ActiveMQArtemisAddress myAddress,
                                 String externalBrokerUri, String saslMechanism, String secretName,
                                 String clientKeyStore, String clientKeyStorePassword, String clientTrustStore, String clientTrustStorePassword) {
        Deployment clients = ResourceManager.deploySecuredClientsContainer(namespace, List.of(secretName));
        Pod clientsPod = getClient().getFirstPodByPrefixName(namespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
        int msgsExpected = 10;
        int sent = -1;
        int received = 0;

        // Publisher - Receiver
        MessagingClient messagingClient = new AmqpQpidClient(clientsPod, externalBrokerUri, myAddress, msgsExpected, saslMechanism,
                "/etc/" + secretName + "/" + clientKeyStore, clientKeyStorePassword,
                "/etc/" + secretName + "/" + clientTrustStore, clientTrustStorePassword);
        sent = messagingClient.sendMessages();
        received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));
        ResourceManager.undeployClientsContainer(namespace, clients);
    }

}
