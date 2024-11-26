/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.brokerqe.claire.TestUtils;
import io.fabric8.openshift.api.model.Route;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public class JMXHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(JMXHelper.class);
    private static final String JMX_CALL_BASE = ArtemisConstants.JOLOKIA_READ_ENDPOINT + ArtemisConstants.JOLOKIA_BROKER_PARAM;

    private String user = ArtemisConstants.JOLOKIA_DEFAULT_USER;
    private String pass = ArtemisConstants.JOLOKIA_DEFAULT_PASS;

    private KubeClient client;

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
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
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
        String jmxPath = JMX_CALL_BASE + "/AddressNames";
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
        String jmxTemplate = JMX_CALL_BASE +
                ",component=addresses" +
                ",address=!\"%s!\"" +
                ",subcomponent=queues" +
                ",routing-type=!\"%s!\"" +
                ",queue=!\"%s!\"" +
                "/MessageCount";
        String jmxPath = String.format(jmxTemplate, address, routingType.toLowerCase(Locale.ROOT), queue);
        String content = performJmxCall(host, jmxPath);
        JSONObject json = new JSONObject(content);
        return json.getInt("value");
    }

    private List<String> getQueueNames(String host, String address) throws IOException {
        String jmxTemplate = JMX_CALL_BASE +
                ",component=addresses" +
                ",address=!\"%s!\"/QueueNames";
        String jmxPath = String.format(jmxTemplate, address);
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
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public AddressData getAddressQueue(String deployName, BPActiveMQArtemisAddress address, int pod) {
        return getAddressQueue(deployName, address.getAddressName(), address.getSingularQueueName(), address.getRoutingType().toLowerCase(Locale.ROOT), pod);
    }
    public AddressData getAddressQueue(String deployName, String addressName, String queueName, String routingType, int pod)  {
        try {
            Route route = getRoute(deployName, pod);
            String host = route.getSpec().getHost();
            AddressData ar = new AddressData();
            ar.setAddress(addressName);
            ar.setQueueName(queueName);
            ar.setTotalMsgCount(getMessageCount(host, addressName, routingType, queueName));
            return ar;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
