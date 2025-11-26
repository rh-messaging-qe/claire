/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.brokerqe.claire.TestUtils;
import io.fabric8.openshift.api.model.Route;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public class JMXHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(JMXHelper.class);

    private String user = ArtemisConstants.JOLOKIA_DEFAULT_USER;
    private String pass = ArtemisConstants.JOLOKIA_DEFAULT_PASS;

    private KubeClient client;

    public static String getJmxCallBase() {
        return getJmxCallBase(ArtemisConstants.JOLOKIA_DEFAULT_BROKERNAME);
    }

    public static String getJmxCallBase(String brokerName) {
        return ArtemisConstants.JOLOKIA_READ_ENDPOINT + getBrokerNameParam(brokerName);
    }

    public static String getBrokerNameParam() {
        return getBrokerNameParam(ArtemisConstants.JOLOKIA_DEFAULT_BROKERNAME);
    }

    public static String getBrokerNameParam(String brokerName) {
        return ":broker=" + getQuotedMBean(brokerName);
    }

    public static String getQuotedMBean(String quoteMe) {
        String quotedString;
        // Artemis 2.40+ uses new Jolokia, which quotes differently - using '!'
        if (ResourceManager.getEnvironment().getArtemisTestVersion().getVersionNumber() < ArtemisVersion.VERSION_2_40.getVersionNumber()) {
            quotedString = URLEncoder.encode("\"" + quoteMe + "\"", StandardCharsets.UTF_8);
        } else {
            quotedString = URLEncoder.encode("!\"" + quoteMe + "!\"", StandardCharsets.UTF_8);
        }
        return quotedString;
    }

    public JMXHelper withUser(String user) {
        this.user = user;
        return this;
    }

    public JMXHelper withPass(String pass) {
        this.pass = pass;
        return this;
    }

    public JMXHelper withKubeClient(KubeClient client) {
        this.client = client;
        return this;
    }

    private Route getRoute(String deployName, int pod) {
        String expectedRouteName = deployName + String.format("-wconsj-%d-svc-rte", pod);
        return client.getRouteByName(client.getNamespace(), expectedRouteName);
    }

    private String getBasicAuth() {
        String userpass = user + ":" + pass;
        return "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
    }

    private String performJmxCall(String host, String jmxPath) throws IOException {
        HttpURLConnection con = (HttpURLConnection) TestUtils.makeHttpRequest("http://" + host + jmxPath, Constants.GET);
        con.setRequestProperty("Authorization", getBasicAuth());
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        con.disconnect();
        return content.toString();
    }

    private List<String> getAllAddresses(String host) throws IOException {
        String jmxPath = getJmxCallBase() + "/AddressNames";
        String content = performJmxCall(host, jmxPath);
        JSONObject json = new JSONObject(content);

        JSONArray array = json.getJSONArray("value");
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String item = array.getString(i);
            // Internal addresses info of which we wouldn't need in the tests.
            // Requesting message count on these would spit out errors, thus either removing it
            if (!item.contains("artemis.internal.sf") && !item.contains("sys.mqtt.sessions") && !item.contains("activemq.notifications")) {
                addresses.add(item);
            }
        }
        return addresses;
    }


    private int getMessageCount(String host, String address, String routingType, String queue) throws IOException {
        String jmxPath = getJmxCallBase() +
                ",component=addresses" +
                ",address=" + getQuotedMBean(address) +
                ",subcomponent=queues" +
                ",routing-type=" + getQuotedMBean(routingType.toLowerCase(Locale.ROOT)) +
                ",queue=" + getQuotedMBean(queue) +
                "/MessageCount";
        String content = performJmxCall(host, jmxPath);
        JSONObject json = new JSONObject(content);
        int result;
        try {
            result = json.getInt("value");
        } catch (JSONException e) {
            LOGGER.info("no messages on the address");
            result = 0;

        }
        return result;
    }

    private List<String> getQueueNames(String host, String address) throws IOException {
        String jmxPath = getJmxCallBase() + ",component=addresses,address=" + JMXHelper.getQuotedMBean(address) + "/QueueNames";
        String content = performJmxCall(host, jmxPath);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(content);
        JsonNode array =  json.get("value");
        return mapper.readValue(array.toString(), new TypeReference<List<String>>() { });
    }

    public List<AddressData> getAllAddressesQueues(String deployName, String routingType, int pod) {
        List<AddressData> result = new ArrayList<>();
        try {
            Route route = getRoute(deployName, pod);
            String host = route.getSpec().getHost();
            List<String> addresses = getAllAddresses(host);
            for (String address: addresses) {
                for (String queue: getQueueNames(host, address)) {
                    AddressData addressData = new AddressData();
                    addressData.setAddress(address);
                    addressData.setQueueName(queue);
                    addressData.setTotalMsgCount(getMessageCount(host, address, routingType, queue));
                    result.add(addressData);
                }
            }
            return result;
        } catch (Exception e) {
            throw new ClaireRuntimeException("[JMX] Error with " +  e.getMessage());
        }
    }

    public AddressData getAddressQueue(String deployName, BPActiveMQArtemisAddress address, int pod) {
        return getAddressQueue(deployName, address.getAddressName(), address.getSingularQueueName(), address.getRoutingType().toLowerCase(Locale.ROOT), pod);
    }
    public AddressData getAddressQueue(String deployName, String addressName, String queueName, String routingType, int pod)  {
        try {
            // get route || ingress
            Route route = getRoute(deployName, pod);
            String host = route.getSpec().getHost();
            AddressData ar = new AddressData();
            ar.setAddress(addressName);
            ar.setQueueName(queueName);
            ar.setTotalMsgCount(getMessageCount(host, addressName, routingType, queueName));
            return ar;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
