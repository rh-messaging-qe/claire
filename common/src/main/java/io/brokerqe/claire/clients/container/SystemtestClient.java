/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public abstract class SystemtestClient implements MessagingClient {

    protected DeployableClient deployableClient;
    protected Map<String, String> senderOptions = null;
    protected Map<String, String> receiverOptions = null;

    public boolean compareMessages(Object sentMessagesObject, Object receivedMessagesObject) {
        if (sentMessagesObject == null || receivedMessagesObject == null) {
            return false;
        }
        List<JSONObject> sentMessages = (List<JSONObject>) sentMessagesObject;
        List<JSONObject> receivedMessages = (List<JSONObject>) receivedMessagesObject;
        return compareMessages(sentMessages, receivedMessages);
    }

    public boolean compareMessages(List<JSONObject> sentMessages, List<JSONObject> receivedMessages) {
        // Method compares only number of sent and received messages and real comparison of messageIDs (if is present in other group)
        Logger logger = LoggerFactory.getLogger(MessagingClient.class);
        if (sentMessages.size() != receivedMessages.size()) {
            logger.warn("[{}] Sent {} and received {} messages are not same!", deployableClient.getContainerName(), sentMessages.size(), receivedMessages.size());
            return false;
        } else {
            try {
                // compare message IDs
                List<String> receivedIds = receivedMessages.stream().map(receivedMsg -> {
                    try {
                        return (String) receivedMsg.get("id");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
                for (JSONObject message : sentMessages) {
                    if (!receivedIds.contains(message.get("id"))) {
                        logger.warn("[{}] Unable to find/compare messageId {}", deployableClient.getContainerName(), message);
                        return false;
                    }
                }
            } catch (JSONException e) {
                logger.error("[{}] Unable to parse/compare messages! {}", deployableClient.getContainerName(), e.getMessage());
            }
            logger.debug("[{}] All messages are same. Good.", deployableClient.getContainerName());
            return true;
        }
    }
}
