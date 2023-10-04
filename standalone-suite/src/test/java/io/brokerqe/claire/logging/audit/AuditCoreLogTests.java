/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging.audit;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuditCoreLogTests extends AuditLogTests {
    @Test
    void checkBundledCoreAuditLogsTest() {
        int msgsExpected = 5;
        LOGGER.info("Test Bundled Core Messaging. Starting test for {} user.", ArtemisConstants.ADMIN_NAME);
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ADMIN_NAME)
                .withUsername(ArtemisConstants.ADMIN_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        MessagingClient bundledClient = new BundledCoreMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));
        checkAuditLogs(List.of(adminFormattedAuth, adminFormattedAddressCore, adminFormattedQueue, adminFormattedSent, adminFormattedRecv));

        LOGGER.info("Starting test for senders/receivers user: {}", ArtemisConstants.CHARLIE_NAME);
        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.CHARLIE_NAME)
                .withUsername(ArtemisConstants.CHARLIE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        bundledClient = new BundledCoreMessagingClient(options);
        sent = bundledClient.sendMessages();
        received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));
        checkAuditLogs(List.of(charlieFormattedAuth, charlieFormattedSent, charlieFormattedRecv));

        LOGGER.info("Starting test for senders user: {}", ArtemisConstants.ALICE_NAME);
        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ALICE_NAME)
                .withUsername(ArtemisConstants.ALICE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        BundledCoreMessagingClient bundledClientAlice = new BundledCoreMessagingClient(options);
        int sentAlice = bundledClientAlice.sendMessages();
        assertThrows(MessagingClientException.class, bundledClientAlice::receiveMessages);
        checkAuditLogs(List.of(aliceFormattedAuth, aliceFormattedSent, aliceFormattedRecv));

        LOGGER.info("Starting test for receivers user: {}", ArtemisConstants.BOB_NAME);
        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.BOB_NAME)
                .withUsername(ArtemisConstants.BOB_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        BundledCoreMessagingClient bundledClientBob = new BundledCoreMessagingClient(options);

        assertThrows(MessagingClientException.class, bundledClientBob::sendMessages);
        int receivedBob = bundledClientBob.receiveMessages();
        MatcherAssert.assertThat(sentAlice, equalTo(msgsExpected));
        MatcherAssert.assertThat(receivedBob, equalTo(msgsExpected));
        checkAuditLogs(List.of(bobFormattedAuth, bobFormattedSent, bobFormattedRecv));

        deleteQueue(artemisDeployableClient, queue);
        deleteAddress(artemisDeployableClient, address);
    }
}
