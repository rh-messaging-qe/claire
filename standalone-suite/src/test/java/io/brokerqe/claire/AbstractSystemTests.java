/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helper.ArtemisJmxHelper;
import io.brokerqe.claire.helper.TimeHelper;
import io.brokerqe.claire.junit.TestSeparator;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.database.DatabaseContainer;
import io.brokerqe.claire.container.NfsServerContainer;
import io.brokerqe.claire.container.YacfgArtemisContainer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(StandaloneTestDataCollector.class)
public class AbstractSystemTests implements TestSeparator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSystemTests.class);
    protected static final String DEFAULT_ALL_PORT = String.valueOf(ArtemisContainer.DEFAULT_ALL_PROTOCOLS_PORT);
    protected static final String DEFAULT_AMQP_PORT = String.valueOf(ArtemisContainer.DEFAULT_AMQP_PORT);
    protected Database database;

    @BeforeAll
    public void setupTestEnvironment() {
        if (getEnvironment().getJdbcDatabaseFile() != null) {
            getEnvironment().setupDatabase();
        }
    }

    @AfterAll
    public void tearDownTestEnvironment() {
        ResourceManager.disconnectAllClients();
        ResourceManager.stopAllContainers();
        Path testCfgDir = Paths.get(getTestConfigDir());
        try {
            FileUtils.deleteDirectory(testCfgDir.toFile());
        } catch (IOException e) {
            String errMsg = String.format("Error on deleting directory %s: %s", testCfgDir, e.getMessage());
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    protected TestInfo testInfo;
    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public boolean useArtemisWithDB() {
        database = getEnvironment().getDatabase();
        return database != null;
    }

    public ArtemisContainer setupArtemisWithDB() {
        if (database.getClass().getSuperclass().equals(DatabaseContainer.class)) {
            ((DatabaseContainer) database).start();
        }
        ArtemisContainer artemis = ResourceManager.getArtemisContainerInstance(Constants.ARTEMIS_STRING);
        LOGGER.info("[{}] Setting up database {}", artemis.getName(), database.getName());
        generateArtemisCfg(artemis, new ArrayList<>(List.of("tune_file=" + database.getTuneFile())));
        artemis.withLibFile(database.getDriverFile(), database.getDriverFilename());
        // DB should be empty, so no need to wait login time to establish connection and prepare db for usage
        artemis.start(Duration.ofMinutes(10));
        ensureBrokerStarted(artemis);
        ensureBrokerIsLive(artemis);
        ensureBrokerUsesJdbc(artemis, database);
        return artemis;
    }

    protected ArtemisContainer getArtemisInstance(String instanceName) {
        return getArtemisInstance(instanceName, null, new ArrayList<>(), new HashMap<>(), false, true);
    }

    protected ArtemisContainer getArtemisInstance(String instanceName, String tuneFile) {
        return getArtemisInstance(instanceName, tuneFile, new ArrayList<>(), new HashMap<>(), false, true);
    }

    protected ArtemisContainer getArtemisInstance(String instanceName, String tuneFile, boolean isBackupInstance) {
        return getArtemisInstance(instanceName, tuneFile, new ArrayList<>(), new HashMap<>(), isBackupInstance, true);
    }

    protected ArtemisContainer getArtemisInstance(String instanceName, String tuneFile,
                                                  List<String> yacfgOpts,
                                                  Map<String, String> envVars) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, envVars, false, true);
    }

    protected ArtemisContainer getArtemisInstance(String instanceName, String tuneFile,
                                                  List<String> yacfgOpts, Map<String, String> envVars,
                                                  boolean isBackupInstance) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, envVars, isBackupInstance, true);
    }

    protected ArtemisContainer getArtemisInstance(String instanceName, String tuneFile,
                                                  List<String> yacfgOpts, Map<String, String> envVars,
                                                  boolean isBackupInstance, boolean startInstance) {
        List<String> mutableYacfgOpts = new ArrayList<>(yacfgOpts);
        if (useArtemisWithDB()) {
            return setupArtemisWithDB();
        } else {
            ArtemisContainer artemis = ResourceManager.getArtemisContainerInstance(instanceName);
            artemis.withEnvVar(envVars);
            if (tuneFile != null) {
                mutableYacfgOpts.add("--tune");
                mutableYacfgOpts.add(tuneFile);
            }
            generateArtemisCfg(artemis, mutableYacfgOpts);
            if (isBackupInstance) {
                artemis.withLogWait(ArtemisContainer.BACKUP_ANNOUNCED_LOG_REGEX);
            }
            if (startInstance) {
                artemis.start();
                ensureBrokerStarted(artemis);
                if (isBackupInstance) {
                    ensureBrokerIsBackup(artemis);
                } else {
                    ensureBrokerIsLive(artemis);
                }
            }
            return artemis;
        }
    }

    public String getTestRandomName() {
        // Call this method directly from testMethod to work https://stackoverflow.com/a/34948763/2604720
        return Thread.currentThread().getStackTrace()[2].getMethodName() + "-" + TestUtils.generateRandomName();
    }

    protected Environment getEnvironment() {
        return EnvironmentStandalone.getInstance();
    }

    private String getPkgClassAsDir() {
        String pkgAndClass = this.getClass().getName().replaceAll(Constants.CLAIRE_TEST_PKG_REGEX, "");
        return pkgAndClass.replaceAll("\\.", Constants.FILE_SEPARATOR);
    }

    protected String generateYacfgProfilesContainerTestDir(String file) {
        return YacfgArtemisContainer.YACFG_CONTAINER_CLAIRE_STANDALONE_DIR + Constants.FILE_SEPARATOR + "tests"
                + Constants.FILE_SEPARATOR + getPkgClassAsDir() + Constants.FILE_SEPARATOR + file;
    }

    private String getTestConfigDir() {
        String cfgDir = getPkgClassAsDir();
        return TestUtils.getProjectRelativeFile(Constants.ARTEMIS_TEST_CFG_DIR + Constants.FILE_SEPARATOR + cfgDir);
    }

    protected void generateArtemisCfgInParallel(Map<ArtemisContainer, List<String>> configMap) {
        configMap.entrySet().stream().parallel().forEach(e -> {
            generateArtemisCfg(e.getKey(), e.getValue());
        });
    }

    protected void generateArtemisCfg(ArtemisContainer artemisInstance) {
        generateArtemisCfg(artemisInstance, new ArrayList<>(), null);
    }

    protected void generateArtemisCfg(ArtemisContainer artemisInstance, List<String> yacfgParams) {
        generateArtemisCfg(artemisInstance, yacfgParams, null);
    }

    protected void generateArtemisCfg(ArtemisContainer artemisInstance, List<String> yacfgParams, String profileFileName) {
        String instanceDir = getTestConfigDir() + Constants.FILE_SEPARATOR + artemisInstance.getName();
        TestUtils.createDirectory(instanceDir + Constants.BIN_DIR);
        TestUtils.createDirectory(instanceDir + Constants.DATA_DIR);
        TestUtils.createDirectory(instanceDir + Constants.ETC_DIR);
        TestUtils.createDirectory(instanceDir + Constants.LIB_DIR);
        TestUtils.createDirectory(instanceDir + Constants.LOG_DIR);
        TestUtils.createDirectory(instanceDir + Constants.TMP_DIR);
        artemisInstance.withInstanceDir(instanceDir);

        String artemisConfig = EnvironmentStandalone.getInstance().getProvidedArtemisConfig();
        if (artemisConfig != null) {
            LOGGER.debug("[config] Reusing existing etc profile: {}", artemisConfig);
            artemisInstance.withConfigDir(artemisConfig);
        } else {
            LOGGER.debug("[config] YACFG is going to generate new etc profile");
            final YacfgArtemisContainer yacfg;

            yacfg = ResourceManager.getYacfgArtemisContainerInstance(String.format("yacfg-%s", artemisInstance.getName()));
            String instanceYacfgOutputDir = instanceDir + Constants.FILE_SEPARATOR + Constants.ETC_DIR;
            yacfg.withHostOutputDir(instanceYacfgOutputDir);

            if (profileFileName != null && !profileFileName.isBlank() && !profileFileName.isEmpty()) {
                yacfg.withProfile(profileFileName);
            }

            if (yacfgParams.stream().noneMatch(e -> e.contains("broker_home"))) {
                yacfg.withParam(YacfgArtemisContainer.OPT_PARAM_KEY, String.format("broker_home=%s", ArtemisContainer.ARTEMIS_INSTALL_DIR));
            }

            if (yacfgParams.stream().noneMatch(e -> e.contains("broker_name="))) {
                yacfg.withParam(YacfgArtemisContainer.OPT_PARAM_KEY, String.format("broker_name=%s", artemisInstance.getName()));
            }

            if (yacfgParams.stream().noneMatch(e -> e.contains("broker_instance="))) {
                yacfg.withParam(YacfgArtemisContainer.OPT_PARAM_KEY, String.format("broker_instance=%s", ArtemisContainer.ARTEMIS_INSTANCE_DIR));
            }

            Predicate<String> tunePredicate = e -> e.contains("tune_file=");
            yacfgParams.stream().filter(tunePredicate).forEach(e -> {
                Path file = Paths.get(StringUtils.substringAfter(e, "="));
                String containerFileLocation = YacfgArtemisContainer.YACFG_CONTAINER_TUNES_DIR + "/" + file.getFileName().toString();
                yacfg.withParam(YacfgArtemisContainer.TUNE_PARAM_KEY, containerFileLocation);
                yacfg.withFileSystemBind(file.toAbsolutePath().toString(), containerFileLocation, BindMode.READ_ONLY);
            });
            yacfgParams.removeIf(tunePredicate);
            yacfg.withParams(yacfgParams);

            LOGGER.debug("[config] YACFG - Starting container with params: {}", yacfgParams);
            yacfg.start();
            TimeHelper.waitFor(e -> yacfg.getStatus().equalsIgnoreCase("exited"), Constants.DURATION_500_MILLISECONDS,
                    Constants.DURATION_5_SECONDS);
            artemisInstance.withConfigDir(instanceYacfgOutputDir);
        }
    }

    public static void ensureSameMessages(int totalProducedMessages, Map<String, Message> producedMsgs, Map<String, Message> consumedMsgs) {
        // ensure produced messages number is correct
        assertThat(producedMsgs).isNotEmpty().hasSize(totalProducedMessages);

        // ensure consumed messages number is correct
        assertThat(consumedMsgs).isNotEmpty().hasSize(totalProducedMessages);

        // ensure all produced messages are consumed and contains the same content
        for (Map.Entry<String, Message> entry : producedMsgs.entrySet()) {
            String msgId = entry.getKey();
            if (TextMessage.class.isAssignableFrom(entry.getValue().getClass())
                    && TextMessage.class.isAssignableFrom(consumedMsgs.get(msgId).getClass())) {
                TextMessage v = (TextMessage) entry.getValue();
                try {
                    TextMessage consumedMsg = (TextMessage) consumedMsgs.get(msgId);
                    assertThat(consumedMsg.getText()).contains(v.getText());
                } catch (JMSException e) {
                    String errMsg = String.format("error on getting message information: %s", e.getMessage());
                    LOGGER.error(errMsg);
                    throw new ClaireRuntimeException(errMsg, e);
                }
            } else {
                String errMsg = "Tried to process unsupported type of message";
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg);
            }
        }
    }

    public static void ensureBrokerStarted(ArtemisContainer artemisInstance) {
        boolean isStarted = ArtemisJmxHelper.isStarted(artemisInstance, true, 10,
                Constants.DURATION_500_MILLISECONDS);
        assertThat(isStarted).isTrue();
    }

    public static void ensureBrokerIsLive(ArtemisContainer artemisInstance) {
        LOGGER.info("Ensure broker instance {} became the broker live", artemisInstance.getName());
        boolean isLive = ArtemisJmxHelper.isLive(artemisInstance, true, 40,
                Constants.DURATION_500_MILLISECONDS);
        assertThat(isLive).isTrue();
    }

    public static void ensureBrokerIsBackup(ArtemisContainer artemisInstance) {
        boolean isBackup = ArtemisJmxHelper.isBackup(artemisInstance, true,
                40, Constants.DURATION_500_MILLISECONDS);
        assertThat(isBackup).isTrue();
    }

    public static void ensureBrokerReplicaIsInSync(ArtemisContainer artemisInstance) {
        boolean isReplicaInSync = ArtemisJmxHelper.isReplicaInSync(artemisInstance, true,
                10, Constants.DURATION_500_MILLISECONDS);
        assertThat(isReplicaInSync).isTrue();
    }

    public static void ensureBrokerPagingCount(ArtemisContainer artemisInstance, String addressName, int expectedResult) {
        long pagingCount = ArtemisJmxHelper.getAddressPageCount(artemisInstance, addressName, expectedResult, 10, Constants.DURATION_500_MILLISECONDS);
        assertThat(pagingCount).isEqualTo(expectedResult);
    }

    public static void ensureBrokerIsPaging(ArtemisContainer artemisInstance, String addressName, boolean expectedResult) {
        boolean isPaging = ArtemisJmxHelper.isPaging(artemisInstance, addressName, expectedResult, 10, Constants.DURATION_500_MILLISECONDS);
        assertThat(isPaging).isEqualTo(expectedResult);
    }

    public static void ensureQueueCount(ArtemisContainer artemisInstance, String addressName, String queueName, RoutingType routeType, int expectedResult) {
        LOGGER.info("Ensure queue has {} messages", expectedResult);
        Long countResult = ArtemisJmxHelper.getQueueCount(artemisInstance, addressName, queueName, routeType,
                expectedResult, 10, Constants.DURATION_500_MILLISECONDS);
        assertThat(countResult).isEqualTo(expectedResult);
    }

    public static void ensureBrokerUsesJdbc(ArtemisContainer artemisInstance, Database database) {
        assertThat(artemisInstance.getLogs()).containsAnyOf(database.getJdbcUrl(), database.getConnectionUrl());
    }

    protected NfsServerContainer getNfsServerInstance(String exportDirName) {
        NfsServerContainer nfsServer = ResourceManager.getNfsServerContainerInstance("nfsServer");
        String nfsServerName = nfsServer.getName();
        String exportBaseDir = "exports" + Constants.FILE_SEPARATOR + exportDirName;
        String hostExportDir = getTestConfigDir() + Constants.FILE_SEPARATOR + nfsServerName + Constants.FILE_SEPARATOR + exportBaseDir;
        String containerExportDir = Constants.FILE_SEPARATOR + exportBaseDir;
        nfsServer.withExportDir(hostExportDir, containerExportDir);
        nfsServer.start();
        return nfsServer;
    }
}
