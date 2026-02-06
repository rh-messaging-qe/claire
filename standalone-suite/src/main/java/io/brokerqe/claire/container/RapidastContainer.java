/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import io.brokerqe.claire.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import io.brokerqe.claire.Constants;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import java.time.Duration;


public class RapidastContainer extends AbstractGenericContainer {

    protected static final Logger LOGGER = LoggerFactory.getLogger(RapidastContainer.class);

    public static final String RAPIDAST_DIR = "/tmp/rapidast";
    public static final String RESULTS_DIR = "/tmp/rapidast/results";
    public static final String RAPIDAST_CONFIG = "/tmp/rapidast/config.yaml";

    public RapidastContainer(String name, String consoleURL, String scanName, int timeout) {
        super(name, Constants.IMAGE_RAPIDAST);
        container.withFileSystemBind(RAPIDAST_DIR, RAPIDAST_DIR, BindMode.READ_WRITE);
        container.withFileSystemBind(RESULTS_DIR, "/opt/rapidast/results", BindMode.READ_WRITE);
        container.withFileSystemBind(RAPIDAST_CONFIG, "/opt/rapidast/config/config.yaml", BindMode.READ_WRITE);
        container.withCreateContainerCmdModifier(cmd -> cmd.withUser("root"));
        container.withStartupCheckStrategy(
                new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(timeout))
        );

        LOGGER.info("Preparing Rapidast scan environment");
        TestUtils.createDirectory(RAPIDAST_DIR);
        TestUtils.createDirectory(RESULTS_DIR);
        String config = generateConfigString(consoleURL, scanName);
        LOGGER.info("Generated config:\n" + config);
        TestUtils.createFile(RAPIDAST_CONFIG, config);
    }

    private static String generateConfigString(String consoleURL, String shortName) {
        return String.format(
                """
                    config:
                      configVersion: 6
                    application:
                      shortName: %s
                      url: %s
                    general:
                      authentication:
                        type: http_basic
                        parameters:
                          username: "admin"
                          password: "admin"
                    scanners:
                      zap:
                        spiderAjax:
                          maxDuration: 10 # in minutes, default: 0 unlimited
                          browserId: firefox-headless
                        passiveScan:
                          disabledRules: "2,10015,10024,10027,10054,10096,10109,10112"
                        activeScan:
                          policy: "API-scan-minimal"
                        report:
                          format: ["json","html"]
                        miscOptions:
                          updateAddons: False""", shortName, consoleURL);
    }

}
