/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.container.database.OracleDbContainer;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.database.JdbcData;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.container.database.MariadbContainer;
import io.brokerqe.claire.container.database.MssqlContainer;
import io.brokerqe.claire.container.database.MysqlContainer;
import io.brokerqe.claire.container.database.PostgresqlContainer;
import io.brokerqe.claire.container.database.ProvidedDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class EnvironmentStandalone extends Environment {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentStandalone.class);

    private static EnvironmentStandalone instance;
    private final String osInfo;
    private final String logLevel;
    private final String logsDirLocation;
    private final String tmpDirLocation;
    private final boolean logContainers;
    private final boolean collectTestData;
    private final String artemisContainerImage;
    private final String artemisContainerJavaHome;
    private final String nfsServerContainerImage;
    private final String toxiProxyContainerImage;
    private final String providedArtemisConfig;
    private final String zookeeperContainerImage;
    private final String yacfgArtemisContainerImage;
    private final String yacfgArtemisProfile;
    private final String yacfgArtemisProfilesOverrideDir;
    private final String yacfgArtemisTemplatesOverrideDir;
    private final String artemisVersionStr;
    private final ArtemisVersion artemisVersion;

    private EnvironmentStandalone() {
        loadProjectProperties(Constants.STANDALONE_MODULE_PROPERTIES_FILE);
        this.set(this);
        databaseFile = getConfigurationValue(Constants.EV_JDBC_DATA, Constants.PROP_JDBC_DATA, null);
        osInfo = getOSInfo();
        // Default value is in logback settings file
        logLevel = getConfigurationValue(Constants.EV_TEST_LOG_LEVEL, Constants.PROP_LOG_LEVEL, ArtemisConstants.DEFAULT_LOG_LEVEL);

        logsDirLocation = getConfigurationValue(Constants.EV_LOGS_LOCATION, Constants.PROP_LOG_DIR, Constants.LOGS_DEFAULT_DIR)
                + Constants.FILE_SEPARATOR + TestUtils.generateTimestamp();
        tmpDirLocation = System.getProperty(Constants.EV_TMP_LOCATION, Constants.TMP_DEFAULT_DIR)
                + Constants.FILE_SEPARATOR + TestUtils.generateTimestamp();
        logContainers = Boolean.parseBoolean(getConfigurationValue(Constants.EV_LOG_CONTAINERS, Constants.PROP_LOG_CONTAINERS,
                String.valueOf(Constants.DEFAULT_LOG_CONTAINERS)));
        collectTestData = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_COLLECT_TEST_DATA, "true"));
        artemisContainerImage = getConfigurationValue(Constants.EV_ARTEMIS_CONTAINER_IMAGE,
                Constants.PROP_ARTEMIS_CONTAINER_IMAGE, Constants.DEFAULT_ARTEMIS_CONTAINER_IMAGE);

        artemisContainerJavaHome = getConfigurationValue(Constants.EV_ARTEMIS_CONTAINER_JAVA_HOME,
                Constants.PROP_ARTEMIS_CONTAINER_JAVA_HOME,
                Constants.DEFAULT_ARTEMIS_CONTAINER_INSTANCE_JAVA_HOME);

        nfsServerContainerImage = getConfigurationValue(Constants.EV_NFS_SERVER_CONTAINER_IMAGE,
                Constants.PROP_NFS_SERVER_CONTAINER_IMAGE, Constants.DEFAULT_NFS_SERVER_CONTAINER_IMAGE);

        toxiProxyContainerImage = getConfigurationValue(Constants.EV_TOXI_PROXY_CONTAINER_IMAGE,
                Constants.PROP_TOXI_PROXY_CONTAINER_IMAGE, Constants.DEFAULT_TOXI_PROXY_CONTAINER_IMAGE);

        zookeeperContainerImage = getConfigurationValue(Constants.EV_ZOOKEEPER_CONTAINER_IMAGE,
                Constants.PROP_ZOOKEEPER_CONTAINER_IMAGE, Constants.DEFAULT_ZOOKEEPER_CONTAINER_IMAGE);

        artemisVersionStr = getArtemisVersionFromInstallDir();
        artemisVersion = convertArtemisVersion(artemisVersionStr);

        providedArtemisConfig = getConfigurationValue(Constants.EV_USE_EXISTING_CONFIG,
                Constants.PROP_USE_EXISTING_CONFIG, "");

        yacfgArtemisContainerImage = getConfigurationValue(Constants.EV_YACFG_ARTEMIS_CONTAINER_IMAGE,
                Constants.PROP_YACFG_ARTEMIS_CONTAINER_IMAGE, getDefaultYacfgArtemisImage(artemisVersionStr));

        yacfgArtemisProfile = getConfigurationValue(Constants.EV_YACFG_ARTEMIS_PROFILE,
                Constants.PROP_YACFG_ARTEMIS_PROFILE,
                Constants.DEFAULT_YACFG_ARTEMIS_PROFILE.replaceAll("%ARTEMIS_VERSION%", getArtemisMajorMinorMicroVersion(artemisVersionStr)));

        yacfgArtemisProfilesOverrideDir =  getConfigurationValue(Constants.EV_YACFG_ARTEMIS_PROFILES_OVERRIDE_DIR,
                Constants.PROP_YACFG_ARTEMIS_PROFILES_OVERRIDE_DIR, null);

        yacfgArtemisTemplatesOverrideDir =  getConfigurationValue(Constants.EV_YACFG_ARTEMIS_TEMPLATES_OVERRIDE_DIR,
                Constants.PROP_YACFG_ARTEMIS_TEMPLATES_OVERRIDE_DIR, null);

        printAllUsedTestVariables();
    }

    private String getDefaultYacfgArtemisImage(String artemisVersionStr) {
        return Constants.DEFAULT_YACFG_ARTEMIS_CONTAINER_IMAGE_BASE + ":" + getArtemisMajorMinorMicroVersion(artemisVersionStr);
    }

    public String getYacfgArtemisProfilesOverrideDir() {
        return yacfgArtemisProfilesOverrideDir;
    }

    public String getYacfgArtemisTemplatesOverrideDir() {
        return yacfgArtemisTemplatesOverrideDir;
    }

    private void printAllUsedTestVariables() {
        String envVars = "Test environment info:" + Constants.LINE_SEPARATOR +
                "OS: " + osInfo + Constants.LINE_SEPARATOR +
                "Artemis version from install dir: " + artemisVersionStr + Constants.LINE_SEPARATOR +
                Constants.LINE_SEPARATOR +
                "Configuration values:" + Constants.LINE_SEPARATOR +
                Constants.LINE_SEPARATOR +
                Constants.PROP_LOG_LEVEL + ": " + logLevel + Constants.LINE_SEPARATOR +
                Constants.PROP_LOG_DIR + ": " + logsDirLocation + Constants.LINE_SEPARATOR +
                Constants.PROP_LOG_CONTAINERS + ": " + logContainers + Constants.LINE_SEPARATOR +
                Constants.PROP_ARTEMIS_CONTAINER_IMAGE + ": " + artemisContainerImage + Constants.LINE_SEPARATOR +
                Constants.PROP_ARTEMIS_CONTAINER_JAVA_HOME + ": " + artemisContainerJavaHome + Constants.LINE_SEPARATOR +
                Constants.PROP_NFS_SERVER_CONTAINER_IMAGE + ": " + nfsServerContainerImage + Constants.LINE_SEPARATOR +
                Constants.PROP_TOXI_PROXY_CONTAINER_IMAGE + ": " + toxiProxyContainerImage + Constants.LINE_SEPARATOR +
                Constants.PROP_ZOOKEEPER_CONTAINER_IMAGE + ": " + zookeeperContainerImage + Constants.LINE_SEPARATOR +
                Constants.PROP_YACFG_ARTEMIS_CONTAINER_IMAGE + ": " + yacfgArtemisContainerImage + Constants.LINE_SEPARATOR +
                Constants.PROP_YACFG_ARTEMIS_PROFILE + ": " + yacfgArtemisProfile + Constants.LINE_SEPARATOR +
                Constants.PROP_YACFG_ARTEMIS_PROFILES_OVERRIDE_DIR + ": " + yacfgArtemisProfilesOverrideDir + Constants.LINE_SEPARATOR +
                Constants.PROP_YACFG_ARTEMIS_TEMPLATES_OVERRIDE_DIR + ": " + yacfgArtemisTemplatesOverrideDir + Constants.LINE_SEPARATOR +
                Constants.PROP_JDBC_DATA + ": " + databaseFile + Constants.LINE_SEPARATOR +
                Constants.PROP_USE_EXISTING_CONFIG + ": " + providedArtemisConfig;
        LOGGER.info(envVars);
    }

    private String getArtemisVersionFromInstallDir() {
        String version;
        Path installBinDir = TestUtils.getProjectRelativeFilePath(ArtemisConstants.INSTALL_DIR
                + Constants.FILE_SEPARATOR + ArtemisConstants.BIN_DIR);
        try {
            String[] versionCmd = {"/bin/sh", "-c", installBinDir.toAbsolutePath() + Constants.FILE_SEPARATOR
                    + "artemis version | grep \"Apache ActiveMQ Artemis \" | cut -d \" \" -f4"};
            Process versionProcess = Runtime.getRuntime().exec(versionCmd);
            versionProcess.waitFor(Constants.DURATION_5_SECONDS, TimeUnit.MILLISECONDS);
            if (versionProcess.exitValue() != 0) {
                try (BufferedReader buffReader = versionProcess.errorReader()) {
                    String errorReader = buffReader.readLine();
                    String error = String.format("error or retrieving artemis version from install dir: %s",
                            errorReader);
                    throw new ClaireRuntimeException(error);
                }
            }
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(versionProcess.getInputStream()));
            version = in.readLine();
            in.close();
            versionProcess.destroyForcibly();
        }  catch (IOException | InterruptedException e) {
            String error = String.format("Error on getting artemis version from install dir: %s", e.getMessage());
            LOGGER.error(error);
            throw new ClaireRuntimeException(error, e);
        }
        return version;
    }

    @Override
    public ArtemisVersion convertArtemisVersion(String strVersion) {
        String majorMinorVersion = strVersion.replaceAll("(^[0-9]+\\.[0-9]+)\\..*", "$1");
        String enumFormat = "VERSION_" + majorMinorVersion.replaceAll("\\.", "_");
        return ArtemisVersion.valueOf(enumFormat);
    }

    public String getArtemisMajorMinorMicroVersion(String strVersion) {
        return strVersion.replaceAll("(^[0-9]+\\.[0-9]+\\.[0-9]+).*", "$1");
    }

    private String getOSInfo() {
        String distro;
        String version;

        try {
            String[] distroCmd = {"/bin/sh", "-c", "cat /etc/os-release |grep \"^ID=\" |cut -d \"=\" -f2 | sed -e 's/\"//g'"};
            Process distroProcess = Runtime.getRuntime().exec(distroCmd);
            distroProcess.waitFor(Constants.DURATION_5_SECONDS, TimeUnit.MILLISECONDS);
            if (distroProcess.exitValue() != 0) {
                try (BufferedReader buffReader = distroProcess.errorReader()) {
                    String errorReader = buffReader.readLine();
                    String error = String.format("error or retrieving OS name: %s", errorReader);
                    throw new ClaireRuntimeException(error);
                }
            }
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(distroProcess.getInputStream()));
            distro = in.readLine();
            in.close();
            distroProcess.destroyForcibly();

            String[] versionCmd = {"/bin/sh", "-c", "cat /etc/os-release |grep \"^VERSION_ID=\" |cut -d \"=\" -f2 | sed -e 's/\"//g'"};
            Process versionProcess = Runtime.getRuntime().exec(versionCmd);
            versionProcess.waitFor(Constants.DURATION_5_SECONDS, TimeUnit.MILLISECONDS);
            if (versionProcess.exitValue() != 0) {
                try (BufferedReader buffReader = distroProcess.errorReader()) {
                    String errorReader = buffReader.readLine();
                    String error = String.format("error or retrieving OS version: %s", errorReader);
                    throw new ClaireRuntimeException(error);
                }
            }
            in = new BufferedReader(
                    new InputStreamReader(versionProcess.getInputStream()));
            version = in.readLine();
            in.close();
            versionProcess.destroyForcibly();
        } catch (IOException | InterruptedException e) {
            String error = String.format("Error on getting operational system info: %s", e.getMessage());
            LOGGER.error(error);
            throw new ClaireRuntimeException(error, e);
        }

        return distro + " - " + version;
    }

    public static synchronized EnvironmentStandalone getInstance() {
        if (instance == null) {
            instance = new EnvironmentStandalone();
        }
        return instance;
    }

    public boolean isLogContainers() {
        return logContainers;
    }

    public String getArtemisContainerImage() {
        return artemisContainerImage;
    }

    public String getArtemisContainerJavaHome() {
        return artemisContainerJavaHome;
    }

    public String getSystemTestClientsImage() {
        return Constants.IMAGE_SYSTEMTEST_CLIENTS;
    }

    public String getSystemtestCliProtonDotnet() {
        return Constants.IMAGE_SYSTEMTEST_CLI_PROTON_DOTNET;
    }
    public String getSystemtestCliCpp() {
        return Constants.IMAGE_SYSTEMTEST_CLI_CPP;
    }
    public String getSystemtestCliProtonPython() {
        return Constants.IMAGE_SYSTEMTEST_CLI_PROTON_PYTHON;
    }
    public String getSystemtestCliRhea() {
        return Constants.IMAGE_SYSTEMTEST_CLI_RHEA;
    }

    public String getNfsServerContainerImage() {
        return nfsServerContainerImage;
    }

    public String getToxiProxyContainerImage() {
        return toxiProxyContainerImage;
    }

    public String getZookeeperContainerImage() {
        return zookeeperContainerImage;
    }

    public String getYacfgArtemisContainerImage() {
        return yacfgArtemisContainerImage;
    }

    @Override
    public String getTestLogLevel() {
        return logLevel;
    }

    public String getArtemisVersion() {
        return artemisVersionStr;
    }

    @Override
    public boolean isUpstreamArtemis() {
        return !artemisVersionStr.contains("redhat-");
    }

    @Override
    public ArtemisVersion getArtemisTestVersion() {
        return artemisVersion;
    }

    @Override
    public String getLogsDirLocation() {
        return logsDirLocation;
    }

    @Override
    public String getTmpDirLocation() {
        if (!TestUtils.directoryExists(tmpDirLocation)) {
            TestUtils.createDirectory(tmpDirLocation);
        }
        return tmpDirLocation;
    }

    @Override
    public String getKeycloakVersion() {
        return null;
    }

    @Override
    public boolean isCollectTestData() {
        return collectTestData;
    }

    @Override
    public int getCustomExtraDelay() {
        return 0;
    }

    public String getYacfgArtemisProfile() {
        return yacfgArtemisProfile;
    }

    public void setupDatabase() {
        // Deploy actual DB based on key-name
        Database database;
        String name = TestUtils.generateRandomName();
        database = switch (databaseFile.toLowerCase(Locale.ROOT)) {
            case Database.MARIADB -> new MariadbContainer(Database.MARIADB + "-db1-" + name);
            case Database.MYSQL -> new MysqlContainer(Database.MYSQL + "-db1-" + name);
            case Database.MSSQL -> new MssqlContainer(Database.MSSQL + "-db1-" + name);
            case Database.ORACLE -> new OracleDbContainer(Database.ORACLE + "-db1-" + name);
            case Database.POSTGRESQL -> new PostgresqlContainer(Database.POSTGRESQL + "-db1-" + name);
            default ->
                // Load data from provided DB
                new ProvidedDatabase(new JdbcData(databaseFile));
        };
        setDatabase(database);
    }

    public String getProvidedArtemisConfig() {
        if (providedArtemisConfig.equals("")) {
            return null;
        } else {
            return providedArtemisConfig;
        }
    }
}
