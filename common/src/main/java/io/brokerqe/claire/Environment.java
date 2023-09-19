/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Properties;

public abstract class Environment {

    public abstract String getTestLogLevel();
    public abstract String getArtemisVersion();
    public abstract boolean isUpstreamArtemis();
    public abstract ArtemisVersion getArtemisTestVersion();
    public abstract String getLogsDirLocation();
    public abstract String getTmpDirLocation();
    public String getCertificatesLocation() {
        return getTmpDirLocation() + Constants.FILE_SEPARATOR + Constants.CERTS_GENERATION_DIR;
    }
    public abstract String getKeycloakVersion();
    public abstract boolean isCollectTestData();
    public abstract int getCustomExtraDelay();
    public abstract void setupDatabase();

    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);
    protected String databaseFile;
    private Database database;
    private static Environment environment;
    private Properties appProperties;

    protected HashMap<String, HashMap<String, HashMap<String, String>>> buildProperties;


    public <T extends Environment> void set(T env) {
        environment = env;
    }

    public static <T extends Environment> T get() {
        return (T) environment;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database db) {
        database = db;
    }

    public String getJdbcDatabaseFile() {
        return databaseFile;
    }

    protected ArtemisVersion convertArtemisVersion(String version) {
        ArtemisVersion versionRet;
        if (isUpstreamArtemis()) {
            return ArtemisVersion.values()[ArtemisVersion.values().length - 1];
        } else {
            String artemisContainerVersionUpstream = String.valueOf(buildProperties.get("operator").get("artemis_container_version_upstream"));
            LOGGER.info("[ENV] Found mapping of provided {} into {}", version, artemisContainerVersionUpstream);
            version = artemisContainerVersionUpstream;
        }

        String versionSplit = version.replace(".", "");
        int dotsCount = version.length() - versionSplit.length();
        if (dotsCount <= 2) {
            try {
                versionRet = ArtemisVersion.getByOrdinal(Integer.parseInt(versionSplit));
            } catch (RuntimeException e) {
                LOGGER.error("[ENV] Provided unknown {} value {}!", Constants.EV_ARTEMIS_TEST_VERSION, version);
                throw new IllegalArgumentException("Unknown " + Constants.EV_ARTEMIS_TEST_VERSION + " version: " + version);
            }
        } else {
            LOGGER.error("[ENV] Provided incorrect {} value {}! Exiting.", Constants.EV_ARTEMIS_TEST_VERSION, version);
            throw new IllegalArgumentException("Unknown " + Constants.EV_ARTEMIS_TEST_VERSION + " version: " + version);
        }
        return versionRet;
    }

    protected String getConfigurationValue(String envKey, String propKey, String defaultValue) {
        String value;

        value = System.getenv(envKey);
        if (value == null) {
            value = appProperties.getProperty(propKey, defaultValue);
            value = value.equals("") ? defaultValue : value;
        }
        return value;
    }

    protected void loadProjectProperties(String fileName) {
        String propertiesFile = TestUtils.getProjectRelativeFile(fileName);
        appProperties = new Properties();
        try {
            appProperties.load(new FileInputStream(propertiesFile));
        } catch (IOException e) {
            String error = String.format("Error loading properties file %s: %s", propertiesFile, e.getMessage());
            throw new ClaireRuntimeException(error, e);
        }
    }

    protected void loadBuildProperties() {
        if (buildProperties == null) {
            File propFile = new File(Constants.BUILD_PROPERTIES_FILE);
            try {
                buildProperties = new Yaml().load(FileUtils.readFileToString(propFile, Charset.defaultCharset()));
            } catch (IOException e) {
                String errMsg = String.format("[ENV] Expecting to find build properties file %s", Constants.BUILD_PROPERTIES_FILE);
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, e);
            }
        }
    }
}
