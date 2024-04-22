/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.smoke;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.junit.TestValidSince;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class VersionTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionTests.class);


    private ArtemisContainer artemisInstance;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemisInstance = getArtemisInstance(artemisName);
    }


    @Test
    @Tag(Constants.TAG_SMOKE)
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    void testConsoleProvidedVersion() {
        Assumptions.assumeThat(getEnvironment().isUpstreamArtemis()).isFalse();

        String artemisHost = artemisInstance.getHostAndPort(ArtemisConstants.CONSOLE_PORT);
        URI versionUrl = URI.create("http://" + artemisHost + "/redhat-branding/plugin/amq-broker-version");
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(versionUrl)
                    .GET()
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String testVersion = String.valueOf(((EnvironmentStandalone) getEnvironment()).getArtemisBuildVersion());
            String currentVersion = response.body();
            Assertions.assertThat(currentVersion).contains(testVersion);
        } catch (Exception e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }
}
