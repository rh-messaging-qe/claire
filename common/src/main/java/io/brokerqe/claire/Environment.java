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
    public abstract boolean isTeardownEnv();
    public abstract boolean isPlaywrightDebug();
    public abstract int getCustomExtraDelay();
    public abstract void setupDatabase();

    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);
    protected String databaseFile;
    protected String testUpgradePlan;
    protected String testUpgradePackageManifest;
    private Database database;
    private static Environment environment;
    private Properties appProperties;
    private String path = System.getenv("PATH") + ":/usr/local/bin:/usr/local/sbin:/usr/bin:/usr/sbin";

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

    public String getTestUpgradePlanContent() {
        if (testUpgradePlan != null) {
            return TestUtils.readFileContent(new File(testUpgradePlan));
        } else {
            throw new IllegalArgumentException(Constants.EV_UPGRADE_PLAN + " variable has not been set!");
        }
    }

    public String getTestUpgradePackageManifestContent() {
        if (testUpgradePackageManifest != null) {
            return TestUtils.readFileContent(new File(testUpgradePackageManifest));
        } else {
            throw new IllegalArgumentException(Constants.EV_PACKAGE_MANIFEST + " variable has not been set!");
        }
    }

    public Boolean isLocalExecution() {
        return Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_LOCAL_EXEC, "false"));
    }

    public String getArtemisMajorMinorMicroVersion(String strVersion) {
        return strVersion.replaceAll("(^[0-9]+\\.[0-9]+\\.[0-9]+).*", "$1");
    }

    public ArtemisVersion convertArtemisVersion(String version) {
        ArtemisVersion versionRet;
        String versionUpstream;
        if (isUpstreamArtemis()) {
            return ArtemisVersion.values()[ArtemisVersion.values().length - 1];
        } else {
            try {
                File versionsFile = new File(Constants.VERSION_MAPPER_PATH);
                HashMap<String, HashMap<String, String>> versions = new Yaml().load(FileUtils.readFileToString(versionsFile, Charset.defaultCharset()));
                LOGGER.info("[ENV] Found mapping of provided {} into {}", versions.get(version), version);
                versionUpstream = versions.get(version).get("upstream");
                if (versionUpstream == null) {
                    LOGGER.error("[ENV] null versionUpstream from {}", versions);
                    LOGGER.error("[ENV] convertArtemisVersion({})", version);
                    throw new ClaireRuntimeException("null versionUpstream");
                }
            } catch (IOException e) {
                String errMsg = String.format("[ENV] Expecting to find version file %s", Constants.VERSION_MAPPER_PATH);
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, e);
            }
        }

        String versionSplit = versionUpstream.replace(".", "");
        int dotsCount = versionUpstream.length() - versionSplit.length();
        if (dotsCount <= 2) {
            try {
                versionRet = ArtemisVersion.getByOrdinal(Integer.parseInt(versionSplit));
            } catch (RuntimeException e) {
                LOGGER.error("[ENV] Provided unknown {} value {}!", Constants.EV_ARTEMIS_TEST_VERSION, versionUpstream);
                throw new IllegalArgumentException("Unknown " + Constants.EV_ARTEMIS_TEST_VERSION + " version: " + versionUpstream);
            }
        } else {
            LOGGER.error("[ENV] Provided incorrect {} value {}! Exiting.", Constants.EV_ARTEMIS_TEST_VERSION, versionUpstream);
            throw new IllegalArgumentException("Unknown " + Constants.EV_ARTEMIS_TEST_VERSION + " version: " + versionUpstream);
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

    public String getEnvironmentDefaultPath() {
        return path;
    }
}
