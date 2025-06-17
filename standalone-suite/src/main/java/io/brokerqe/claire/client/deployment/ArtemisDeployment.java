/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.YacfgArtemisContainer;
import io.brokerqe.claire.container.database.DatabaseContainer;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helper.TimeHelper;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ArtemisDeployment {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisDeployment.class);

    public static String downloadPrepareArtemisInstallDir(TestInfo testInfo, String artemisZipUrl, String version, String testConfigDir) {
        String testName = TestUtils.getClassName(testInfo) + "_" + TestUtils.getRandomString(2);
        String filename = artemisZipUrl.substring(artemisZipUrl.lastIndexOf("/") + 1);
        String tmpTestFile = EnvironmentStandalone.getInstance().getTmpDirLocation() + Constants.FILE_SEPARATOR + testName;


        String tmpZipPath = Constants.TMP_DIR_SYSTEM + Constants.FILE_SEPARATOR + filename;
        String artemisInstallDirTmp = tmpTestFile + Constants.FILE_SEPARATOR + testName;
        String installDir = testConfigDir + Constants.FILE_SEPARATOR + version + "_install";

        TestUtils.createDirectory(tmpTestFile);
        TestUtils.deleteDirectoryRecursively(Paths.get(installDir));
        TestUtils.createDirectory(installDir);
        TestUtils.getFileFromUrl(artemisZipUrl, tmpZipPath);
        if (tmpZipPath.endsWith(".zip")) {
            TestUtils.unzip(tmpZipPath, artemisInstallDirTmp);
            List<Path> paths = TestUtils.searchForGlobFile(artemisInstallDirTmp, "glob:**/apache-artemis-*", 1);
            TestUtils.copyDirectories(paths.get(0).toString(), installDir);
        }
        return installDir;
    }

    public static ArtemisConfigData createArtemisInstanceFromInstallDir(String installDir, String instanceName) {
        String instanceDir = EnvironmentStandalone.getInstance().getTestConfigDir() + Constants.FILE_SEPARATOR + instanceName;
        return createArtemisInstanceFromInstallDir(installDir, instanceName, instanceDir);
    }

    public static ArtemisConfigData createArtemisInstanceFromInstallDir(String installDir, String instanceName, String instanceDir) {
        // create default instance for given version (for instance/bin directory)
        String createCmd = installDir + "/bin/artemis create --force --name " + instanceName
                + " --user " + ArtemisConstants.ADMIN_NAME + " --password " + ArtemisConstants.ADMIN_PASS
                + " --require-login " + instanceDir;
        TestUtils.executeLocalCommand(createCmd);
        // update artemis.profile to point to exact 'etc' folder (in future container)
        TestUtils.replaceFileContent(instanceDir + "/bin/artemis",
                "ARTEMIS_INSTANCE_ETC='" + instanceDir + "/etc'",
                "ARTEMIS_INSTANCE_ETC=" + ArtemisContainer.ARTEMIS_INSTANCE_ETC_DIR,
                true);

        ArtemisConfigData defaultACD = new ArtemisConfigData()
                .withInstallDir(installDir)
                .withInstanceDir(instanceDir)
                .withInstanceBinDir(Paths.get(instanceDir, ArtemisConstants.BIN_DIR).toString());
        return defaultACD;
    }

    public static ArtemisConfigData createArtemisInstanceFromUrl(TestInfo testInfo, String artemisUrl, String version,
                                                                 String testConfigDir, String instanceName, String instanceDir) {
        String installDir = downloadPrepareArtemisInstallDir(testInfo, artemisUrl, version, testConfigDir);
        return createArtemisInstanceFromInstallDir(installDir, instanceName, instanceDir);
    }

    public static List<ArtemisContainer> createArtemisHAPair(String primaryName, String backupName) {
        ArtemisConfigData defaultArtemisConfigData = new ArtemisConfigData()
                .withPrimaryTuneFile("primary-tune.yaml.jinja2")
                .withBackupTuneFile("backup-tune.yaml.jinja2");
        return createArtemisHAPair(primaryName, backupName, defaultArtemisConfigData);
    }

    public static List<ArtemisContainer> createArtemisHAPair(String primaryName, String backupName, ArtemisConfigData artemisConfigData) {
        LOGGER.info("[HA-Primary] Creating artemis instance: " + primaryName);
        artemisConfigData.withEnvVars(artemisConfigData.getPrimaryEnvVars())
                .withTuneFile(artemisConfigData.getPrimaryTuneFile())
                .withIsBackup(false);
        ArtemisContainer artemisPrimary = createArtemis(primaryName, artemisConfigData);
        if (artemisConfigData.isSharedStore()) {
            TestUtils.threadSleep(Constants.DURATION_30_SECONDS);
        }

        LOGGER.info("[HA-Backup] Creating artemis instance: " + backupName);
        artemisConfigData.withEnvVars(artemisConfigData.getBackupEnvVars())
                .withTuneFile(artemisConfigData.getBackupTuneFile())
                .withIsBackup(true);
        ArtemisContainer artemisBackup = createArtemis(backupName, artemisConfigData);
        return List.of(artemisPrimary, artemisBackup);
    }

    // ===== original methods
    public static ArtemisContainer createArtemis(String instanceName) {
        return createArtemis(instanceName, new ArtemisConfigData());
    }

    public static ArtemisContainer createArtemis(String instanceName, ArtemisConfigData artemisConfigData) {
        Database database = EnvironmentStandalone.getInstance().getDatabase();
        List<String> yacfgOptions = new ArrayList<>(artemisConfigData.getYacfgOptions());
        String yacfgArtemisProfile = artemisConfigData.getYacfgProfileTemplate();
        yacfgOptions.add("profile=" + yacfgArtemisProfile);

        if (database != null) {
            if (database.getClass().getSuperclass().equals(DatabaseContainer.class)) {
                ((DatabaseContainer) database).start();
            }
            yacfgOptions.add("tune_file=" + database.getTuneFile());
            // DB should be empty, so no need to wait login time to establish connection and prepare db for usage
            artemisConfigData.withDatabase(database).withStartTimeout(Duration.ofMinutes(10));
            instanceName = instanceName + "-" + database.getName();
        }

        // === Creation of artemis container ===
        LOGGER.info("[CREATE] Creating artemis instance: {} with profile {}", instanceName, yacfgArtemisProfile);
        ArtemisContainer artemis = ResourceManager.getArtemisContainerInstance(instanceName);
        artemis.withEnvVar(artemisConfigData.getEnvVars());
        artemis.setArtemisData(artemisConfigData);

        // === Configuration of artemis container yacfg ===
        String tuneFile = artemisConfigData.getTuneFile();
        if (tuneFile != null) {
            yacfgOptions.add("--tune");
            yacfgOptions.add(tuneFile);
        }
        generateArtemisConfig(artemis, artemisConfigData, yacfgOptions);

        // === Pre-start checks/additions ===
        if (database != null) {
            LOGGER.info("[{}] Setting up artemis with database {}", artemis.getName(), database.getName());
            artemis.withLibFile(database.getDriverFile(), database.getDriverFilename());
        }

        artemis.setBackup(artemisConfigData.isBackup());
        artemis.setPrimary(artemisConfigData.isPrimary());

        if (artemisConfigData.isBackup()) {
            artemis.withLogWait(ArtemisContainer.BACKUP_ANNOUNCED_LOG_REGEX);
        } else {
            artemis.withLogWait(ArtemisContainer.PRIMARY_LIVE_LOG_REGEX);
            // shared-store primary
//            artemis.withLogWait(ArtemisContainer.PRIMARY_OBTAINED_LOCK_REGEX);
        }

        if (artemisConfigData.isStart()) {
            artemis.start(artemisConfigData.getStartTimeout());
            artemis.ensureBrokerStarted();
        }
        // === Post-start checks ===
        if (database != null) {
            artemis.ensureBrokerUsesJdbc(database);
        }
        return artemis;
    }

    public static void generateArtemisConfig(ArtemisContainer artemisInstance, ArtemisConfigData artemisConfigData, List<String> yacfgParams) {
        String instanceDir = EnvironmentStandalone.getInstance().getTestConfigDir() + Constants.FILE_SEPARATOR + artemisInstance.getName();
        String providedInstanceDir = null;
        String providedInstallDir;
        if (artemisConfigData != null) {
            providedInstanceDir = artemisConfigData.getInstanceDir();
            providedInstallDir = artemisConfigData.getInstallDir();
            artemisInstance.setInstallDir(providedInstallDir);
        }

        if (providedInstanceDir == null) {
            LOGGER.debug("[Config] Using default instanceDir {}", instanceDir);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.DATA_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.ETC_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.LIB_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.LOG_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.TMP_DIR);
            String binDir = instanceDir + ArtemisConstants.BIN_DIR;
            TestUtils.createDirectory(binDir);
            if (artemisConfigData.getInstanceBinDir() != null) {
                // use hack - default artemis instance for given version test-cfg/upgrade/X/install-7.12.4
                TestUtils.copyDirectories(artemisConfigData.getInstanceBinDir(), binDir);
            } else {
                //if does not exist use Claire default artemis instance artemis/artemis_default_cfg
                TestUtils.copyDirectories(Constants.ARTEMIS_DEFAULT_CFG_BIN_DIR, binDir);
            }
        } else {
            LOGGER.debug("[YACFG] Using provided instanceDir {}", providedInstanceDir);
            TestUtils.copyDirectories(providedInstanceDir, instanceDir);
        }
        artemisInstance.withInstanceDir(instanceDir);

        // TODO: Removed due to issues in FailoverReplicationTests; verify if 100% safe to delete
        //artemisConfigData.withInstanceDir(instanceDir);

        artemisInstance.setConfigBinDir(instanceDir + ArtemisConstants.BIN_DIR);

        String artemisConfig = EnvironmentStandalone.getInstance().getProvidedArtemisConfig();
        if (artemisConfig != null) {
            LOGGER.debug("[YACFG] Reusing existing etc profile: {}", artemisConfig);
            artemisInstance.withConfigDir(artemisConfig);
        } else {
            LOGGER.debug("[YACFG] Generating new etc profile");
            final YacfgArtemisContainer yacfg;

            yacfg = ResourceManager.getYacfgArtemisContainerInstance(String.format("yacfg-%s", artemisInstance.getName()));
            String instanceYacfgOutputDir = instanceDir + ArtemisConstants.ETC_DIR;
            yacfg.withHostOutputDir(instanceYacfgOutputDir);

            Predicate<String> homePredicate = e -> e.contains("broker_home=");
            yacfgParams.stream().filter(homePredicate).forEach(e -> {
                yacfg.withParam(YacfgArtemisContainer.OPT_PARAM_KEY, String.format("broker_home=%s", ArtemisContainer.ARTEMIS_INSTALL_DIR));
            });
            yacfgParams.removeIf(homePredicate);

            if (yacfgParams.stream().noneMatch(e -> e.contains("broker_name="))) {
                yacfg.withParam(YacfgArtemisContainer.OPT_PARAM_KEY, String.format("broker_name=%s", artemisInstance.getName()));
            }

            if (yacfgParams.stream().noneMatch(e -> e.contains("broker_instance="))) {
                yacfg.withParam(YacfgArtemisContainer.OPT_PARAM_KEY, String.format("broker_instance=%s", ArtemisContainer.ARTEMIS_INSTANCE_DIR));
            }

            Predicate<String> profilePredicate = e -> e.contains("profile=");
            yacfgParams.stream().filter(profilePredicate).forEach(e -> {
                String profile = StringUtils.substringAfter(e, "=");
                yacfg.withProfile(profile);
            });
            yacfgParams.removeIf(profilePredicate);

            // Used for custom or non-test-based tune files
            Predicate<String> tunePredicate = e -> e.contains("tune_file=");
            yacfgParams.stream().filter(tunePredicate).forEach(e -> {
                Path file = Paths.get(StringUtils.substringAfter(e, "="));
                String containerFileLocation = YacfgArtemisContainer.YACFG_CONTAINER_TUNES_DIR + "/" + file.getFileName().toString();
                LOGGER.debug("[YACFG] Using custom tune file from {}", file);
                yacfg.withParam(YacfgArtemisContainer.TUNE_PARAM_KEY, containerFileLocation);
                yacfg.withFileSystemBind(file.toAbsolutePath().toString(), containerFileLocation, BindMode.READ_ONLY);
            });
            yacfgParams.removeIf(tunePredicate);
            yacfg.withParams(yacfgParams);
            yacfg.withArtemisConfigData(artemisConfigData);
            yacfg.start();
            TimeHelper.waitFor(e -> yacfg.getStatus().equalsIgnoreCase("exited"), Constants.DURATION_500_MILLISECONDS,
                    Constants.DURATION_5_SECONDS);
            artemisInstance.withConfigDir(instanceYacfgOutputDir);
            if (yacfg.getExitCode() != 0) {
                LOGGER.warn("[{}] {}", yacfg.getName(), yacfg.getLogTail(20));
                throw new ClaireRuntimeException("[YACFG] Generation of artemis etc folder failed!");
            } else {
                LOGGER.debug("[{}] Generated config exited OK", yacfg.getName());
            }
        }
    }

}
