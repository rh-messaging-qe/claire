/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.webconsole;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.KubernetesPlatform;
import io.brokerqe.junit.TestSupportedPlatform;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.security.CertificateManager;
import io.brokerqe.security.KeyStoreData;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

public class WebConsoleTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebConsoleTests.class);
    private final String testNamespace = getRandomNamespaceName("webconsole-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }


    public void checkHttpResponse(URLConnection connection, int expectedCode, String expectedString) {
        InputStream response = null;
        try {
            response = connection.getInputStream();
            assertThat(((HttpURLConnection) connection).getResponseCode(), equalTo(expectedCode));
            Scanner scanner = new Scanner(response);
            String responseBody = scanner.useDelimiter("\\A").next();
            assertThat(responseBody, containsString(expectedString));
            response.close();
        } catch (IOException e) {
            // carry on with execution, we've got expected exception
            if (e.getMessage().contains(String.valueOf(expectedCode))) {
                assertThat(e.getMessage(), containsString(String.valueOf(expectedCode)));
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    @TestSupportedPlatform(KubernetesPlatform.OPENSHIFT)
    public void unsecuredConsoleTLSExternalAccessTest() {
        String brokerName = "artemis";

        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                    .endDeploymentPlan()
                    .editOrNewConsole()
                        .withExpose(true)
                        .withSslEnabled(false)
                    .endConsole()
                .endSpec()
                .build();

        ResourceManager.createArtemis(testNamespace, artemis, true);

        List<HasMetadata> webserviceUrl = getClient().getExternalAccessServicePrefixName(testNamespace,
                brokerName + "-" + Constants.WEBCONSOLE_URI_PREFIX);
        for (HasMetadata service : webserviceUrl) {
            String serviceUrl = getClient().getExternalAccessServiceUrl(testNamespace, service.getMetadata().getName());
            LOGGER.debug("[{}] Using webservice url {}", testNamespace, serviceUrl);
            String url = "https://" + serviceUrl;
            LOGGER.info("[{}] Probing https request on console should fail.", testNamespace);
            checkHttpResponse(TestUtils.makeInsecureHttpsRequest(url), HttpURLConnection.HTTP_UNAVAILABLE, "Application is not available");

            if (getClient().getKubernetesPlatform().equals(KubernetesPlatform.OPENSHIFT)) {
                Route route = (Route) service;
                route.getSpec().setTls(new TLSConfigBuilder().withInsecureEdgeTerminationPolicy("Redirect").withTermination("edge").build());
                ((OpenShiftClient) getKubernetesClient()).routes().withName(route.getMetadata().getName()).patch(PatchContext.of(PatchType.SERVER_SIDE_APPLY), route);
                TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
            } else {
                // Once supported, remove Openshift limitation
                throw new NotImplementedException("Ingress is not supported yet!");
            }
            LOGGER.info("[{}] Probing https request on console should pass", testNamespace);
            checkHttpResponse(TestUtils.makeInsecureHttpsRequest(url), HttpURLConnection.HTTP_OK, "hawtio-login");
        }
        ResourceManager.deleteArtemis(testNamespace, artemis);
    }

    @Test
    public void connectSecurelyConsoleTest() {
        String brokerName = "artemis";
        String amqpAcceptorName = "my-amqp";
        String brokerSecretName = "broker-tls-secret";
        String consoleSecretName = brokerName + "-console-secret";

        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
//                        .withJolokiaAgentEnabled(true)
//                        .withManagementRBACEnabled(true)
                    .endDeploymentPlan()
                    .editOrNewConsole()
                        .withExpose(true)
                        .withSslEnabled(true)
                        .withSslSecret(consoleSecretName)
                    .endConsole()
                .endSpec()
                .build();

        Map<String, KeyStoreData> keystores = CertificateManager.generateCertificateKeystores(
                testNamespace,
                artemis,
                CertificateManager.generateDefaultBrokerDN(getKubernetesClient()),
                CertificateManager.generateDefaultClientDN(getKubernetesClient()),
                List.of(CertificateManager.generateSanDnsNames(getClient(), artemis, List.of(amqpAcceptorName, Constants.WEBCONSOLE_URI_PREFIX)))
        );
        Secret consoleSecret = CertificateManager.createConsoleKeystoreSecret(getClient(), consoleSecretName, keystores);

        ResourceManager.createArtemis(testNamespace, artemis, true);
        List<HasMetadata> webserviceUrl = getClient().getExternalAccessServicePrefixName(testNamespace, brokerName + "-" + Constants.WEBCONSOLE_URI_PREFIX);
        LOGGER.info("[{}] webservice url {}", testNamespace, webserviceUrl);
        // TODO make assertions of accessibility etc.
    }
}
