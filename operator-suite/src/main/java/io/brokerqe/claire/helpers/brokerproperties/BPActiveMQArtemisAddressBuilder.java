/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.brokerqe.claire.helpers.brokerproperties;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BPActiveMQArtemisAddressBuilder {

    private String addressName;
    private List<String> queueNames;
    private String routingType;
    private Map<String, List<String>> queueAdditionalProperties;

    private void maybeInitQueueNames() {
        if (queueNames == null) {
            queueNames = new ArrayList<>();
        }
    }

    private void maybeInitProperties() {
        if (queueAdditionalProperties == null) {
            queueAdditionalProperties = new HashMap<>();
        }
    }

    public BPActiveMQArtemisAddress build() {
        BPActiveMQArtemisAddress address = new BPActiveMQArtemisAddress();
        maybeInitProperties();
        maybeInitQueueNames();
        address.setAddressName(addressName);
        address.setRoutingType(routingType);
        address.setQueueNames(queueNames);
        address.setQueueAdditionalProperties(queueAdditionalProperties);
        return address;
    }

    public BPActiveMQArtemisAddressBuilder withAddressName(String addressName) {
        this.addressName = addressName;
        return this;
    }

    public BPActiveMQArtemisAddressBuilder addQueueName(String queueName) {
        maybeInitQueueNames();
        queueNames.add(queueName);
        return this;
    }

    public BPActiveMQArtemisAddressBuilder withQueueName(String queueName) {
        queueNames = new ArrayList<>();
        queueNames.add(queueName);
        return this;
    }

    public BPActiveMQArtemisAddressBuilder addQueueNames(List<String> queueNames) {
        maybeInitQueueNames();
        this.queueNames.addAll(queueNames);
        return this;
    }

    public BPActiveMQArtemisAddressBuilder withRoutingType(String routingType) {
        this.routingType = routingType;
        return this;
    }

    public BPActiveMQArtemisAddressBuilder addQueueProperty(String queueName, String property, String value) {
        maybeInitProperties();
        List<String> properties = new ArrayList<>();
        if (queueAdditionalProperties.containsKey(queueName)) {
            properties = queueAdditionalProperties.get(queueName);
        }
        properties.add(String.format("%s=%s", property, value));
        this.queueAdditionalProperties.put(queueName, properties);
        return this;
    }

    public BPActiveMQArtemisAddressBuilder fromBPAddress(BPActiveMQArtemisAddress address) {
        this.addressName = address.getAddressName();
        this.queueNames = address.getQueueNames();
        this.queueAdditionalProperties = address.getQueueAdditionalProperties();
        this.routingType = address.getRoutingType();
        return this;
    }

}
