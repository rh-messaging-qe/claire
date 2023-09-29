/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.ha.sharedstore;

import eu.rekawek.toxiproxy.Proxy;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.client.deployment.StJavaClientDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.NfsServerContainer;
import io.brokerqe.claire.container.ToxiProxyContainer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class FailoverSharedStoreTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailoverSharedStoreTests.class);
    private static final int SEND_CHUNK = 10;
    private static final int RECEIVE_CHUNK = 5;

    private NfsServerContainer nfsServer;
    private Proxy artemisPrimaryNfsProxy;
    private Proxy artemisBackupNfsProxy;
    private ArtemisContainer artemisPrimary;
    private ArtemisContainer artemisBackup;

    MessagingClient primaryMessagingClient;
    MessagingClient backupMessagingClient;
    String addressName;

    @BeforeAll
    void setupEnv() {
        LOGGER.info("Creating NFS sever");
        String exportDirName = "artemisData";
        nfsServer = getNfsServerInstance(exportDirName);

        ToxiProxyContainer tp = ResourceManager.getToxiProxyContainerInstance("toxiProxy");
        tp.start();
        artemisPrimaryNfsProxy = tp.getProxy("nfsProxy", "0.0.0.0:8666", nfsServer.getName() + ":" + "2049");
        artemisBackupNfsProxy = tp.getProxy("artemisBackupNfsProxy", "0.0.0.0:8667", nfsServer.getName() + ":" + "2049");

        LOGGER.info("Setting artemis NFS mount config");
        String artemisNfsMountDir = ArtemisContainer.ARTEMIS_INSTANCE_DIR + Constants.FILE_SEPARATOR + "shared_data_dir";
        String nfsMountPrimary = nfsServer.getNfsMountString(tp.getName(), "8666", exportDirName, artemisNfsMountDir, NfsServerContainer.DEFAULT_CLIENT_OPTIONS);
        Map<String, String> artemisPrimaryEnvVars = Map.of("NFS_MOUNTS", nfsMountPrimary);
        String nfsMountBackup = nfsServer.getNfsMountString(tp.getName(), "8667", exportDirName, artemisNfsMountDir, NfsServerContainer.DEFAULT_CLIENT_OPTIONS);
        Map<String, String> artemisBackupEnvVars = Map.of("NFS_MOUNTS", nfsMountBackup);


        String artemisPrimaryName = "artemisPrimary";
        LOGGER.info("Creating artemis instance: " + artemisPrimaryName);
        String primaryTuneFile = generateYacfgProfilesContainerTestDir("primary-tune.yaml.jinja2");
        List<String> yacfgOpts = List.of("--opt", "journal_base_data_dir=" + artemisNfsMountDir);
        artemisPrimary = getArtemisInstance(artemisPrimaryName, primaryTuneFile, yacfgOpts, artemisPrimaryEnvVars);

        String artemisBackupName = "artemisBackup";
        LOGGER.info("Creating artemis instance: " + artemisBackupName);
        String backupTuneFile = generateYacfgProfilesContainerTestDir("backup-tune.yaml.jinja2");
        artemisBackup = getArtemisInstance(artemisBackupName, backupTuneFile, yacfgOpts, artemisBackupEnvVars, true);

        LOGGER.info("Setting client configurations");
        DeployableClient stDeployableClient = new StJavaClientDeployment();

        addressName = getTestRandomName();
        Map<String, String> senderOpts = new HashMap<>(Map.of(
                "conn-username", ArtemisConstants.ADMIN_NAME,
                "conn-password", ArtemisConstants.ADMIN_PASS,
                "address", addressName,
                "count", String.valueOf(SEND_CHUNK)
        ));
        Map<String, String> receiverOpts = new HashMap<>(Map.of(
                "conn-username", ArtemisConstants.ADMIN_NAME,
                "conn-password", ArtemisConstants.ADMIN_PASS,
                "address", addressName,
                "count", String.valueOf(RECEIVE_CHUNK)
        ));
        String primaryAmqpHostAndPort = artemisPrimary.getInstanceNameAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        String backupAmqpHostAndPort = artemisBackup.getInstanceNameAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        primaryMessagingClient = new AmqpQpidClient(stDeployableClient, primaryAmqpHostAndPort, senderOpts, receiverOpts);
        backupMessagingClient = new AmqpQpidClient(stDeployableClient, backupAmqpHostAndPort, senderOpts, receiverOpts);
    }


    @ParameterizedTest(name = "{index} => stopAction=''{0}''")
    @EnumSource(value = ArtemisContainer.ArtemisProcessControllerActions.class, names = {"STOP", "FORCE_STOP"})
    void produceAndConsumeOnPrimaryAndOnBackupTest(ArtemisContainer.ArtemisProcessControllerActions stopAction) {
        LOGGER.info("Sending {} messages to broker primary {}", SEND_CHUNK, artemisPrimary.getName());
        int sent = primaryMessagingClient.sendMessages();

        LOGGER.info("Receiving {} messages from broker primary {}", RECEIVE_CHUNK, artemisPrimary.getName());
        int received = primaryMessagingClient.receiveMessages();

        artemisPrimary.artemisProcessController(stopAction);
        ensureBrokerIsLive(artemisBackup);

        LOGGER.info("Sending {} messages to broker backup {}", SEND_CHUNK, artemisBackup.getName());
        sent += backupMessagingClient.sendMessages();

        LOGGER.info("Receiving {} messages from broker backup {}", RECEIVE_CHUNK, artemisBackup.getName());
        received += backupMessagingClient.receiveMessages();

        artemisPrimary.artemisProcessController(ArtemisContainer.ArtemisProcessControllerActions.START);
        ensureBrokerIsLive(artemisPrimary);

        LOGGER.info("Receiving {} messages from broker primary {}", RECEIVE_CHUNK, artemisPrimary.getName());
        received += primaryMessagingClient.receiveMessages();
        LOGGER.info("Receiving {} messages from broker primary {}", RECEIVE_CHUNK, artemisPrimary.getName());
        received += primaryMessagingClient.receiveMessages();

        LOGGER.info("Ensure broker number of sent messages are equal received ones");
        assertThat(sent, equalTo(received));

        ensureQueueCount(artemisPrimary, addressName, addressName, RoutingType.ANYCAST, 0);
    }
}
