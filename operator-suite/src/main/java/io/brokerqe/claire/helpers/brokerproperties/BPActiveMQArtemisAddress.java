/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */


package io.brokerqe.claire.helpers.brokerproperties;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BPActiveMQArtemisAddress {

    // TODO consolidate this with AddressData etc so all stuff is accessible through it
    private List<String> queueNames = new ArrayList<>();
    private String addressName = "";

    private String routingType = "";

    private Map<String, List<String>> queueAdditionalProperties = new HashMap<>();


    BPActiveMQArtemisAddress() {
    }

    void setQueueNames(List<String> queueNames) {
        this.queueNames = queueNames;
    }

    void setAddressName(String addressName) {
        this.addressName = addressName;
    }

    void setRoutingType(String routingType) {
        this.routingType = routingType;
    }

    public String getAddressName() {
        return this.addressName;
    }
    public List<String> getQueueNames() {
        return this.queueNames;
    }

    public String getQueueName(int pos) {
        return this.queueNames.get(pos);
    }

    public String getSingularQueueName() {
        return getQueueName(0);
    }

    public String getRoutingType() {
        return this.routingType;
    }

    public Map<String, List<String>> getQueueAdditionalProperties() {
        return queueAdditionalProperties;
    }

    public List<String> getPropertiesList() {
        List<String> result = new ArrayList<>();
        result.add(String.format("addressConfigurations.%s.routingTypes=%s", addressName, routingType));
        for (String queueName: queueNames) {
            result.add(String.format("addressConfigurations.%s.queueConfigs.%s.routingType=%s", addressName, queueName, routingType));
            // Setting up queue config to belong to the address, otherwise queueName address would get created
            result.add(String.format("addressConfigurations.%s.queueConfigs.%s.address=%s", addressName, queueName, addressName));
            if (queueAdditionalProperties.containsKey(queueName)) {
                for (String property : queueAdditionalProperties.get(queueName)) {
                    result.add(String.format("addressConfigurations.%s.queueConfigs.%s.%s", addressName, queueName, property));
                }
            }
        }
        return result;
    }

    public JSONObject getPropertiesJson() {
        JSONObject result = new JSONObject();
        JSONObject addressConfigurations = new JSONObject();
        JSONObject queueArray = new JSONObject();
        for (String queueName: queueNames) {
            JSONObject queue = new JSONObject();
            queue.put("routingType", routingType);
            if (queueAdditionalProperties.containsKey(queueName)) {
                for (String property : queueAdditionalProperties.get(queueName)) {
                    String propertyName = property.split("=")[0];
                    String propertyValue = property.split("=")[1];
                    queue.put(propertyName, propertyValue);
                }
            }
            queueArray.put(queueName, queue);
        }
        JSONObject addressConfig = new JSONObject();
        addressConfig.put("queueConfigs", queueArray);
        addressConfigurations.put(addressName, addressConfig);
        result.put("addressConfigurations", addressConfigurations);
        return result;
    }

    void setQueueAdditionalProperties(Map<String, List<String>> queueAdditionalProperties) {
        this.queueAdditionalProperties = queueAdditionalProperties;
    }

}
