/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import io.brokerqe.claire.CommandResult;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.executor.Executor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

class AmqpProtonCppClientTest {

    @Test
    void sendMessages() {
        Executor executor = Mockito.mock();
        DeployableClient deployableClient = Mockito.mock();
        Mockito.when(deployableClient.getExecutor()).thenReturn(executor);
        Mockito.when(executor.executeCommand(Mockito.anyLong(), Mockito.any(String[].class))).thenReturn(new CommandResult(0, "{}", "{}"));

        AmqpProtonCppClient c = new AmqpProtonCppClient(deployableClient, "someUrl", "5672", "someAddress", "someAddress", 1);
        int sent = c.sendMessages();
        Mockito.verify(executor).executeCommand(Mockito.anyLong(), Mockito.eq(List.of(
                "aac3_sender",
                "--broker-url=amqp://@someUrl:5672/someAddress::someAddress",
                "--log-msgs=json",
                "--count=1"
        ).toArray(String[]::new)));
        Assertions.assertThat(sent).isEqualTo(1);
        Mockito.clearInvocations(executor);

        int received = c.receiveMessages();
        Mockito.verify(executor).executeCommand(Mockito.anyLong(), Mockito.eq(List.of(
                "aac3_receiver",
                "--broker-url=amqp://@someUrl:5672/someAddress::someAddress",
                "--log-msgs=json",
                "--count=1"
        ).toArray(String[]::new)));
        Assertions.assertThat(received).isEqualTo(1);
        Mockito.clearInvocations(executor);
    }
}
