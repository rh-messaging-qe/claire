/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JolokiaHelper {

    final private static String JOLOKIA_CALL_ENDPOINT = "/console/jolokia/exec/org.apache.activemq.artemis";
    final private static String JOLOKIA_BROKER_PARAM = ":broker=\"amq-broker\"";
    final private static String JOLOKIA_GET_ADRESSSETTING_PARAM = "/getAddressSettingsAsJSON/";
    final private static String JOLOKIA_ORIGIN_HEADER = "http://localhost:8161";
    final private static String DEFAULT_USER = "admin";
    final private static String DEFAULT_PASS = "admin";

    static final Logger LOGGER = LoggerFactory.getLogger(JolokiaHelper.class);


    private static URL getJolokiaAddressCallURL(String host, String queue)  {
        String jolokiaCallUrlParams = JOLOKIA_CALL_ENDPOINT + JOLOKIA_BROKER_PARAM + JOLOKIA_GET_ADRESSSETTING_PARAM + queue + "/";
        URL fullUrl = null;
        try {
            fullUrl = new URL("http://" + host + jolokiaCallUrlParams);
        } catch (MalformedURLException e) {
            LOGGER.error("MalformedURLException; incorrect host? Params: host={}, urlParams={}\n message: {}", host, jolokiaCallUrlParams, e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return fullUrl;
    }

    public static String getAddressSettings(String host, String queue) throws IOException {
        return getAddressSettings(host, queue, DEFAULT_USER, DEFAULT_PASS);
    }

    /**
     * @param host full hostname of broker. it is
     * @param queue queue for which addresssettings are retrieved
     * @param user username for broker authentication
     * @param pass password for broker authentication
     * @return String containing JSONObject
     * @throws IOException
     */
    public static String getAddressSettings(String host, String queue, String user, String pass) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) getJolokiaAddressCallURL(host, queue).openConnection();
        } catch (IOException e) {
            LOGGER.error("Unable to open connection to {}\n message: {}", getJolokiaAddressCallURL(host, queue), e.getMessage());
            throw new RuntimeException(e);
        }
        conn.setRequestProperty("Origin", JOLOKIA_ORIGIN_HEADER);
        String userpass = user + ":" + pass;
        String basicAuth = new String(Base64.getEncoder().encode(userpass.getBytes()));
        conn.setRequestProperty("Authorization", basicAuth);
        InputStream contentStream = null;
        try {
            contentStream = conn.getInputStream();
        } catch (IOException e) {
            LOGGER.error("Unable to open inputstream on connection");
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(contentStream, StandardCharsets.UTF_8))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to create reader of {}\n message: {}", contentStream, e.getMessage());
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

}
