/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.testcontainers.containers.BindMode;


public class RapidastSecuredTests extends RapidastDefaultTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(RapidastSecuredTests.class);


    protected String getScanName() {
        return "secured-spider";
    }
    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Generating certificates: " + artemisName);
        CertificateData rootCACertData = new CertificateData("rootca", "C=CZ, L=Brno, O=ArtemisCloud, OU=CertificateAuthority, CN=rootca", null);
        CertificateData myCACertData = new CertificateData("myca", "C=CZ, L=Brno, O=ArtemisCloud, OU=tls-tests, CN=myca", rootCACertData);

        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                "C=CZ, L=Brno, O=ArtemisCloud, OU=Broker CN=localhost",
                "C=CZ, L=Brno, O=ArtemisCloud, OU=Client CN=*",
                null,
                myCACertData
        );
        CertificateData producerCertData = new CertificateData("producer", CertificateManager.generateArtemisCloudDN("tls-tests", "producer"), null, 30, myCACertData);
        KeyStoreData truststoreBrokerData = keystores.get(Constants.BROKER_TRUSTSTORE_ID);
        CertificateManager.addToTruststore(truststoreBrokerData, producerCertData.getCertificate(), producerCertData.getAlias());

        KeyStoreData keystoreBrokerData = keystores.get(Constants.BROKER_KEYSTORE_ID);
        String keyStoreContainerPath = ArtemisContainer.ARTEMIS_INSTANCE_DIR + "/" + keystoreBrokerData.getKeyStorePathFileName();
        String trustStoreContainerPath = ArtemisContainer.ARTEMIS_INSTANCE_DIR + "/" + truststoreBrokerData.getKeyStorePathFileName();
        LOGGER.info("Creating artemis instance: " + artemisName);
        String tuneFileName = TestUtils.getProjectRelativeFile("https_console_tune.yaml");
        String tuneFileContent = String.format("""
                boostrap_xml_bindings:
                  - name: 'artemis'
                    uri: https://0.0.0.0:8161
                    sniHostCheck: "false"
                    sniRequired: "false"
                    clientAuth: "false"
                    keyStorePath: %s
                    keyStorePassword: brokerPass
                    trustStorePath: %s
                    trustStorePassword: brokerPass
                """, keyStoreContainerPath,  trustStoreContainerPath);

        TestUtils.createFile(tuneFileName, tuneFileContent);
        ArtemisContainer artemis = ResourceManager.getArtemisContainerInstance(ArtemisConstants.ARTEMIS_STRING);
        artemis.withFileSystemBind(keystoreBrokerData.getKeyStorePath(), keyStoreContainerPath, BindMode.READ_WRITE);
        artemis.withFileSystemBind(truststoreBrokerData.getKeyStorePath(), trustStoreContainerPath, BindMode.READ_WRITE);
        generateArtemisCfg(artemis, new ArrayList<>(List.of("tune_file=" + tuneFileName)));
        artemis.start();
        ensureBrokerStarted(artemis);
        ensureBrokerIsLive(artemis);
        artemis.setSecured(true);

        consoleURL = artemis.getConsoleUrl();
    }

}
