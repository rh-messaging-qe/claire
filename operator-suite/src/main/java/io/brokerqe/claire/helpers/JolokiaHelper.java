/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helpers;

import io.brokerqe.claire.ArtemisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class JolokiaHelper {

    static final Logger LOGGER = LoggerFactory.getLogger(JolokiaHelper.class);


    private static URI getJolokiaAddress(String host, String requestPath) {
        String jolokiaParams = ArtemisConstants.JOLOKIA_EXEC_ENDPOINT + URLEncoder.encode(ArtemisConstants.JOLOKIA_BROKER_PARAM) + requestPath;
        URI fullUrl = null;
        try {
            fullUrl = new URI("http://" + host + jolokiaParams);
        } catch (URISyntaxException e) {
            LOGGER.error("URISyntaxException; incorrect host? Params: host={}, urlParams={}\n message: {}", host, jolokiaParams, e.getMessage());
            throw new RuntimeException(e);
        }
        return fullUrl;
    }
    private static URI getJolokiaAddressCallURL(String host, String queue) {
        return getJolokiaAddress(host, ArtemisConstants.JOLOKIA_ADDRESSETTINGS_ENDPOINT + queue + "/");
    }

    public static String getAddressSettings(String host, String queue) {
        return getAddressSettings(host, queue, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS);
    }

    public static Boolean checkJolokiaConnection(String host) {
        HttpRequest request = null;
        String userpass = ArtemisConstants.ADMIN_NAME + ":" + ArtemisConstants.ADMIN_PASS;
        String basicAuth = new String(Base64.getEncoder().encode(userpass.getBytes()));
        try {
            request = HttpRequest.newBuilder(getJolokiaAddress(host, ArtemisConstants.JOLOKIA_STATUS_ENDPOINT))
                    .header("Origin", ArtemisConstants.JOLOKIA_ORIGIN_HEADER)
                    .header("Authorization", "Basic " + basicAuth)
                    .GET()
                    .build();
        } catch (Exception e) {
            return false;
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param host  ull hostname of broker. it is
     * @param queue queue for which addresssettings are retrieved
     * @param user username for broker authentication
     * @param pass password for broker authentication
     * @return String containing JSONObject
     * @throws IOException
     */
    public static String getAddressSettings(String host, String queue, String user, String pass) {
        HttpRequest request = null;
        String userpass =  ArtemisConstants.ADMIN_NAME + ":" + ArtemisConstants.ADMIN_PASS;
        String basicAuth = new String(Base64.getEncoder().encode(userpass.getBytes()));
        try {
            request = HttpRequest.newBuilder(getJolokiaAddressCallURL(host, queue))
                    //   .header("Origin", JOLOKIA_ORIGIN_HEADER) - disabled!
                    .header("Authorization", "Basic " + basicAuth)
                    .GET()
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Unexpected response from Jolokia: " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
