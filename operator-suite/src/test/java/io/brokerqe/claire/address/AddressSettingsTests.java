/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.brokerqe.claire.address;


import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.addresssettings.AddressSetting;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.helpers.JolokiaHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.function.Function;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class AddressSettingsTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddressSettingsTests.class);
    private final String testNamespace = getRandomNamespaceName("address-tests", 3);
    private final String brokerName = "brkr-addr";
    private final static String KNOWN_GOOD_RESPONSE = "addressettings/configured.json";

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    // Non string values should be .toString()
    private boolean verifyAddressSettingFromResource(JSONObject valueReceived, String verificationFile) {
        final InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(verificationFile);
        StringBuilder sb = new StringBuilder();
        try {
            Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            int c = 0;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
        } catch (IOException ioe) {
            LOGGER.error(String.format("Exception reading resource file %s: %s", verificationFile, ioe.getMessage()));
            ioe.printStackTrace();
            throw new RuntimeException(ioe.getMessage());
        }
        JSONObject valueExpected = new JSONObject(sb.toString());
        if (valueExpected.equals(valueReceived)) {
            return true;
        } else {
            Set<String> irregularKeys = new HashSet<>();
            Set<String> wrongValueKeys = new HashSet<>();
            Set<String> expectedKeys = valueExpected.keySet();
            Set<String> receivedKeys = valueReceived.keySet();
            Collection<String> allKeys = CollectionUtils.union(expectedKeys, receivedKeys);
            if (!expectedKeys.equals(receivedKeys)) {
                Collection<String> keyDifference = CollectionUtils.disjunction(expectedKeys, receivedKeys);
                irregularKeys.addAll(keyDifference);
            }
            for (String key: allKeys) {
                if (!irregularKeys.contains(key)) {
                    if (!valueExpected.get(key).equals(valueReceived.get(key))) {
                        wrongValueKeys.add(key);
                    }
                }
            }
            for (String key: wrongValueKeys) {
                LOGGER.warn("[{}] AddressSetting {}: expected value {}, received value {}", testNamespace, key, valueExpected.get(key), valueReceived.get(key));
            }
            for (String key: irregularKeys) {
                if (expectedKeys.contains(key)) {
                    LOGGER.warn("[{}] Key exists in expected, but not in actually received data: {}", testNamespace, key);
                } else if (receivedKeys.contains(key)) {
                    LOGGER.warn("[{}] Unexpected key exists in received data: {}", testNamespace, key);
                }
            }
            return wrongValueKeys.isEmpty() && irregularKeys.isEmpty();
        }
    }

    private AddressSetting getDefaultAddressSetting(String match) {
        AddressSetting setting = new AddressSetting();
        setting.setAutoDeleteAddresses(true);
        setting.setAutoCreateAddresses(true);
        setting.setAddressFullPolicy(ArtemisConstants.ADDRESSSETTINGS_POLICY_DROP);
        setting.setAutoCreateDeadLetterResources(true);
        setting.setAutoCreateExpiryResources(true);
        setting.setAutoCreateJmsQueues(true);
        setting.setAutoCreateJmsTopics(true);
        setting.setAutoCreateQueues(true);
        setting.setAutoDeleteAddressesDelay(100);
        setting.setAutoDeleteCreatedQueues(true);
        setting.setAutoDeleteJmsQueues(true);
        setting.setAutoDeleteJmsTopics(true);
        setting.setAutoDeleteQueues(true);
        setting.setAutoDeleteQueuesDelay(100);
        setting.setAutoDeleteQueuesMessageCount(100);
        setting.setAutoCreateQueues(true);
        setting.setAutoCreateJmsTopics(true);
        setting.setAutoCreateJmsQueues(true);
        setting.setAutoCreateExpiryResources(true);
        setting.setAutoCreateDeadLetterResources(true);
        setting.setConfigDeleteAddresses(ArtemisConstants.ADDRESSSETTING_FORCE);
        setting.setConfigDeleteDiverts(ArtemisConstants.ADDRESSSETTING_FORCE);
        setting.setConfigDeleteQueues(ArtemisConstants.ADDRESSSETTING_FORCE);
        setting.setDeadLetterAddress("deadlq");
        setting.setDeadLetterQueuePrefix("deadlq");
        setting.setDeadLetterQueueSuffix("deadlq");
        setting.setDefaultAddressRoutingType(ArtemisConstants.ROUTING_TYPE_MULTICAST);
        setting.setDefaultConsumersBeforeDispatch(100);
        setting.setDefaultConsumerWindowSize(100);
        setting.setDefaultGroupBuckets(100);
        setting.setDefaultDelayBeforeDispatch(100);
        setting.setDefaultExclusiveQueue(true);
        setting.setDefaultGroupRebalance(true);
        setting.setDefaultGroupRebalancePauseDispatch(true);
        setting.setDefaultGroupFirstKey("key");
        setting.setDefaultLastValueKey("key");
        setting.setDefaultLastValueQueue(true);
        setting.setDefaultMaxConsumers(100);
        setting.setDefaultNonDestructive(true);
        setting.setDefaultPurgeOnNoConsumers(true);
        setting.setDefaultQueueRoutingType(ArtemisConstants.ROUTING_TYPE_MULTICAST);
        setting.setDefaultRingSize(100);
        /*
        * This parameter is bugged starting 7.10.0
         */
        // setting.setEnableIngressTimestamp(true);
        setting.setEnableMetrics(true);
        setting.setExpiryDelay(100);
        setting.setExpiryQueuePrefix("pref");
        setting.setExpiryQueueSuffix("suff");
        setting.setExpiryAddress("expiryaddr");
        setting.setLastValueQueue(true);
        setting.setManagementBrowsePageSize(100);
        setting.setMaxExpiryDelay(100);
        setting.setManagementMessageAttributeSizeLimit(100);
        setting.setMaxDeliveryAttempts(100);
        setting.setMaxSizeBytes("200");
        setting.setMaxRedeliveryDelay(100);
        setting.setMaxSizeBytesRejectThreshold(100);
        setting.setMaxSizeBytesRejectThreshold(100);
        setting.setMaxSizeMessages(200L);
        setting.setMessageCounterHistoryDayLimit(100);
        setting.setMinExpiryDelay(100);
        setting.setPageMaxCacheSize(100);
        setting.setPageSizeBytes("100");
        setting.setRedeliveryDelay(100);
        setting.setRedistributionDelay(100);
        setting.setRetroactiveMessageCount(100);
        setting.setSendToDlaOnNoRoute(true);
        setting.setSlowConsumerCheckPeriod(100);
        setting.setSlowConsumerPolicy(ArtemisConstants.ADDRESSETTINGS_POLICY_NOTIFY);
        setting.setSlowConsumerThreshold(100);
        setting.setSlowConsumerThresholdMeasurementUnit(ArtemisConstants.ADDRESSSETING_UNIT_MPS);

        setting.setMatch(match);
        return setting;
    }

    // This test verifies known settings and logs everything which is unexpected in the test log
    // This should be updated when old releases stop being supported
    // Ideally, this should include all the settings which earliest stupported version has
    // At the moment of writing, it is 7.11
    @Test
    @Tag(Constants.TAG_OPERATOR)
    void basicAddressSettingsTest() throws IOException {
        AddressSetting settings = getDefaultAddressSetting("#");
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                .endDeploymentPlan()
                .editOrNewConsole()
                    .withExpose(true)
//                    .withIngressHost("console.test-eks2.clusters.amq-broker.xyz")
                .endConsole()
                .editOrNewAddressSettings()
                    .addToAddressSetting(settings)
                .endAddressSettings()
            .endSpec()
            .build();

        ResourceManager.createArtemis(testNamespace, broker);
        String brokerName = broker.getMetadata().getName();
        String response = JolokiaHelper.getAddressSettings(getClient().getExternalAccessServiceUrl(testNamespace, brokerName + "-" + ArtemisConstants.WEBCONSOLE_URI_PREFIX + "-0-svc-rte"), "someQueue");
        LOGGER.trace("Content: " + response);
        JSONObject jsonResponse = new JSONObject(response);
        verifyAllSettings(settings, jsonResponse);
    }

    private void verifyAllSettings(AddressSetting structure, JSONObject actualValues) {
        LOGGER.debug("Verifying correctness of address settings in JSON Response");

        Map<String, Function<AddressSetting, Object>> fieldMap = new LinkedHashMap<>();

        fieldMap.put("autoDeleteAddresses", AddressSetting::getAutoDeleteAddresses);
        fieldMap.put("autoCreateAddresses", AddressSetting::getAutoCreateAddresses);
        fieldMap.put("addressFullMessagePolicy", AddressSetting::getAddressFullPolicy);
        fieldMap.put("autoCreateDeadLetterResources", AddressSetting::getAutoCreateDeadLetterResources);
        fieldMap.put("autoCreateExpiryResources", AddressSetting::getAutoCreateExpiryResources);
        fieldMap.put("autoCreateJmsQueues", AddressSetting::getAutoCreateJmsQueues);
        fieldMap.put("autoCreateJmsTopics", AddressSetting::getAutoCreateJmsTopics);
        fieldMap.put("autoCreateQueues", AddressSetting::getAutoCreateQueues);
        fieldMap.put("autoDeleteAddressesDelay", AddressSetting::getAutoDeleteAddressesDelay);
        fieldMap.put("autoDeleteCreatedQueues", AddressSetting::getAutoDeleteCreatedQueues);
        fieldMap.put("autoDeleteJmsQueues", AddressSetting::getAutoDeleteJmsQueues);
        fieldMap.put("autoDeleteJmsTopics", AddressSetting::getAutoDeleteJmsTopics);
        fieldMap.put("autoDeleteQueues", AddressSetting::getAutoDeleteQueues);
        fieldMap.put("autoDeleteQueuesDelay", AddressSetting::getAutoDeleteQueuesDelay);
        fieldMap.put("autoDeleteQueuesMessageCount", AddressSetting::getAutoDeleteQueuesMessageCount);
        fieldMap.put("configDeleteAddresses", AddressSetting::getConfigDeleteAddresses);
        fieldMap.put("configDeleteDiverts", AddressSetting::getConfigDeleteDiverts);
        fieldMap.put("configDeleteQueues", AddressSetting::getConfigDeleteQueues);
        fieldMap.put("deadLetterAddress", AddressSetting::getDeadLetterAddress);
        fieldMap.put("deadLetterQueuePrefix", AddressSetting::getDeadLetterQueuePrefix);
        fieldMap.put("deadLetterQueueSuffix", AddressSetting::getDeadLetterQueueSuffix);
        fieldMap.put("defaultAddressRoutingType", AddressSetting::getDefaultAddressRoutingType);
        fieldMap.put("defaultConsumersBeforeDispatch", AddressSetting::getDefaultConsumersBeforeDispatch);
        fieldMap.put("defaultConsumerWindowSize", AddressSetting::getDefaultConsumerWindowSize);
        fieldMap.put("defaultGroupBuckets", AddressSetting::getDefaultGroupBuckets);
        fieldMap.put("defaultDelayBeforeDispatch", AddressSetting::getDefaultDelayBeforeDispatch);
        fieldMap.put("defaultExclusiveQueue", AddressSetting::getDefaultExclusiveQueue);
        fieldMap.put("defaultGroupRebalance", AddressSetting::getDefaultGroupRebalance);
        fieldMap.put("defaultGroupRebalancePauseDispatch", AddressSetting::getDefaultGroupRebalancePauseDispatch);
        fieldMap.put("defaultGroupFirstKey", AddressSetting::getDefaultGroupFirstKey);
        fieldMap.put("defaultLastValueKey", AddressSetting::getDefaultLastValueKey);
        fieldMap.put("defaultLastValueQueue", AddressSetting::getDefaultLastValueQueue);
        fieldMap.put("defaultMaxConsumers", AddressSetting::getDefaultMaxConsumers);
        fieldMap.put("defaultNonDestructive", AddressSetting::getDefaultNonDestructive);
        fieldMap.put("defaultPurgeOnNoConsumers", AddressSetting::getDefaultPurgeOnNoConsumers);
        fieldMap.put("defaultQueueRoutingType", AddressSetting::getDefaultQueueRoutingType);
        fieldMap.put("defaultRingSize", AddressSetting::getDefaultRingSize);
        fieldMap.put("enableMetrics", AddressSetting::getEnableMetrics);
        fieldMap.put("expiryDelay", AddressSetting::getExpiryDelay);
        fieldMap.put("expiryQueuePrefix", AddressSetting::getExpiryQueuePrefix);
        fieldMap.put("expiryQueueSuffix", AddressSetting::getExpiryQueueSuffix);
        fieldMap.put("expiryAddress", AddressSetting::getExpiryAddress);
        fieldMap.put("lastValueQueue", AddressSetting::getLastValueQueue);
        fieldMap.put("managementBrowsePageSize", AddressSetting::getManagementBrowsePageSize);
        fieldMap.put("maxExpiryDelay", AddressSetting::getMaxExpiryDelay);
        fieldMap.put("managementMessageAttributeSizeLimit", AddressSetting::getManagementMessageAttributeSizeLimit);
        fieldMap.put("maxDeliveryAttempts", AddressSetting::getMaxDeliveryAttempts);
        fieldMap.put("maxSizeBytes", AddressSetting::getMaxSizeBytes);
        fieldMap.put("maxRedeliveryDelay", AddressSetting::getMaxRedeliveryDelay);
        fieldMap.put("maxSizeBytesRejectThreshold", AddressSetting::getMaxSizeBytesRejectThreshold);
        fieldMap.put("maxSizeMessages", AddressSetting::getMaxSizeMessages);
        fieldMap.put("messageCounterHistoryDayLimit", AddressSetting::getMessageCounterHistoryDayLimit);
        fieldMap.put("minExpiryDelay", AddressSetting::getMinExpiryDelay);
        fieldMap.put("pageCacheMaxSize", AddressSetting::getPageMaxCacheSize);
        fieldMap.put("pageSizeBytes", AddressSetting::getPageSizeBytes);
        fieldMap.put("redeliveryDelay", AddressSetting::getRedeliveryDelay);
        fieldMap.put("redistributionDelay", AddressSetting::getRedistributionDelay);
        fieldMap.put("retroactiveMessageCount", AddressSetting::getRetroactiveMessageCount);
        fieldMap.put("sendToDLAOnNoRoute", AddressSetting::getSendToDlaOnNoRoute);
        fieldMap.put("slowConsumerCheckPeriod", AddressSetting::getSlowConsumerCheckPeriod);
        fieldMap.put("slowConsumerPolicy", AddressSetting::getSlowConsumerPolicy);
        fieldMap.put("slowConsumerThreshold", AddressSetting::getSlowConsumerThreshold);
        fieldMap.put("slowConsumerThresholdMeasurementUnit", AddressSetting::getSlowConsumerThresholdMeasurementUnit);

        for (String key: actualValues.keySet()) {
            if (fieldMap.containsKey(key)) {
                Function<AddressSetting, Object> funcItem = fieldMap.get(key);
                Object expected = actualValues.get(key);
                Object actual = funcItem.apply(structure);
                assertThat("Mismatch for field: " + key, actual, equalTo(expected));
            } else {
                LOGGER.info("Additional property found: {}", key);
            }
        }
    }
}
