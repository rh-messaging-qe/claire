/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.YacfgArtemisContainer;
import io.brokerqe.claire.container.database.DatabaseContainer;
import io.brokerqe.claire.database.Database;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public static Map<String, String> createArtemisInstanceFromInstallDir(String installDir, String instanceName) {
        String instanceDir = EnvironmentStandalone.getInstance().getTestConfigDir() + Constants.FILE_SEPARATOR + instanceName;
        return createArtemisInstanceFromInstallDir(installDir, instanceName, instanceDir);
    }

    public static Map<String, String> createArtemisInstanceFromInstallDir(String installDir, String instanceName, String instanceDir) {
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

        Map<String, String> artemisData = new HashMap<>();
        artemisData.put(ArtemisContainer.INSTALL_DIR_KEY, installDir);
        artemisData.put(ArtemisContainer.INSTANCE_DIR_KEY, instanceDir);

        return artemisData;
    }

    public static Map<String, String> createArtemisInstanceFromUrl(TestInfo testInfo, String artemisUrl, String version,
                                                                   String testConfigDir, String instanceName, String instanceDir) {
        String installDir = downloadPrepareArtemisInstallDir(testInfo, artemisUrl, version, testConfigDir);
        return createArtemisInstanceFromInstallDir(installDir, instanceName, instanceDir);
    }

    public static ArtemisContainer createArtemis(String name, String artemisVersion, Map<String, String> artemisData) {
        return createArtemis(name, artemisVersion, artemisData, null);
    }

    public static ArtemisContainer createArtemis(String name, String artemisVersion, Map<String, String> artemisData, String tuneFile) {
        String yacfgArtemisProfile = "claire-default-profile-" + artemisVersion + ".yaml.jinja2";
        return createArtemis(name, artemisVersion, artemisData, yacfgArtemisProfile, tuneFile, false);
    }

    public static ArtemisContainer createArtemis(String name, String artemisVersion, Map<String, String> artemisData, String yacfgProfileTemplate, String tuneFile, boolean isBackupInstance) {
        String artemisTuneFile = null;
        String yacfgArtemisProfile = Objects.requireNonNullElseGet(yacfgProfileTemplate, () -> "claire-default-profile-" + artemisVersion + ".yaml.jinja2");

        if (tuneFile != null) {
            artemisTuneFile = generateYacfgProfilesContainerTestDir(tuneFile, EnvironmentStandalone.getInstance().getPackageClassDir());
        }

        LOGGER.info("[UPGRADE] Creating artemis instance: {} with profile {}", name, yacfgArtemisProfile);
//        artemisPrimary0 = getArtemisInstance(artemisPrimaryName + 0, artemisTuneFile);
        return getArtemisInstance(name, artemisTuneFile, new ArrayList<>(List.of("profile=" + yacfgArtemisProfile)), artemisData, isBackupInstance);
    }

    public static List<ArtemisContainer> createArtemisHAPair(String primaryName, String backupName, String version, String artemisVersion, String installDir) {
        return createArtemisHAPair(primaryName, backupName, version, artemisVersion, installDir, null, null);
    }

    public static List<ArtemisContainer> createArtemisHAPair(String primaryName, String backupName, String version, String artemisVersion, String installDir, String yacfgProfileTemplate, String tuneFile) {
        String primaryTune = "primary-tune.yaml.jinja2";
        String backupTune = "backup-tune.yaml.jinja2";

        ArtemisVersion currentVersion = EnvironmentStandalone.getInstance().convertArtemisVersion(artemisVersion);
        if (currentVersion.getVersionNumber() <= ArtemisVersion.VERSION_2_30.getVersionNumber()) {
            // use OLD STYLE master/slave on 7.11.x and smaller
            primaryTune = "old-" + primaryTune;
            backupTune = "old-" + backupTune;
        }

        String yacfgArtemisProfile = Objects.requireNonNullElseGet(yacfgProfileTemplate, () -> "claire-default-profile-" + artemisVersion + ".yaml.jinja2");
        Map<String, String> artemisData = ArtemisDeployment.createArtemisInstanceFromInstallDir(installDir, primaryName);

        LOGGER.info("[UPGRADE][primary] Creating artemis instance: " + primaryName);
        ArtemisContainer artemisPrimary = createArtemis(primaryName, artemisVersion, artemisData, yacfgArtemisProfile, primaryTune, false);

        LOGGER.info("[UPGRADE][backup] Creating artemis instance: " + backupName);
        ArtemisContainer artemisBackup = createArtemis(backupName, artemisVersion, artemisData, yacfgArtemisProfile, backupTune, true);

        return List.of(artemisPrimary, artemisBackup);
    }

    // ===== original methods
    public static boolean useArtemisWithDB() {
        Database database = EnvironmentStandalone.getInstance().getDatabase();
        return database != null;
    }

    public static ArtemisContainer setupArtemisWithDB() {
        Database database = EnvironmentStandalone.getInstance().getDatabase();
        if (database.getClass().getSuperclass().equals(DatabaseContainer.class)) {
            ((DatabaseContainer) database).start();
        }
        ArtemisContainer artemis = ResourceManager.getArtemisContainerInstance(ArtemisConstants.ARTEMIS_STRING);
        LOGGER.info("[{}] Setting up database {}", artemis.getName(), database.getName());
        generateArtemisCfg(artemis, new ArrayList<>(List.of("tune_file=" + database.getTuneFile())));
        artemis.withLibFile(database.getDriverFile(), database.getDriverFilename());
        // DB should be empty, so no need to wait login time to establish connection and prepare db for usage
        artemis.start(Duration.ofMinutes(10));
        artemis.ensureBrokerStarted();
        artemis.ensureBrokerIsLive();
        artemis.ensureBrokerUsesJdbc(database);
        return artemis;
    }

    public static ArtemisContainer getArtemisInstance(String instanceName) {
        return getArtemisInstance(instanceName, null, new ArrayList<>(), new HashMap<>(), null, false, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile) {
        return getArtemisInstance(instanceName, tuneFile, new ArrayList<>(), new HashMap<>(), null, false, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile, List<String> yacfgOpts) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, new HashMap<>(), null, false, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile, List<String> yacfgOpts, Map<String, String> artemisData) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, new HashMap<>(), artemisData, false, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile, List<String> yacfgOpts, Map<String, String> artemisData, boolean isBackupInstance) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, new HashMap<>(), artemisData, isBackupInstance, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile, boolean isBackupInstance) {
        return getArtemisInstance(instanceName, tuneFile, new ArrayList<>(), new HashMap<>(), null, isBackupInstance, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile, List<String> yacfgOpts, Map<String, String> envVars, Map<String, String> artemisData) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, envVars, null, false, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile,
                                                  List<String> yacfgOpts, Map<String, String> envVars,
                                                  Map<String, String> artemisData, boolean isBackupInstance) {
        return getArtemisInstance(instanceName, tuneFile, yacfgOpts, envVars, artemisData, isBackupInstance, true);
    }

    public static ArtemisContainer getArtemisInstance(String instanceName, String tuneFile,
                                                  List<String> yacfgOpts, Map<String, String> envVars,
                                                  Map<String, String> artemisData, boolean isBackupInstance, boolean startInstance) {
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
            generateArtemisCfg(artemis, artemisData, mutableYacfgOpts);

            artemis.setBackup(isBackupInstance);
            artemis.setPrimary(!isBackupInstance);

            if (isBackupInstance) {
                artemis.withLogWait(ArtemisContainer.BACKUP_ANNOUNCED_LOG_REGEX);
            }
            if (startInstance) {
                artemis.start();
                artemis.ensureBrokerStarted();
            }
            return artemis;
        }
    }

    public static String generateYacfgProfilesContainerTestDir(String file, String outputDir) {
        return YacfgArtemisContainer.YACFG_CONTAINER_CLAIRE_STANDALONE_DIR + Constants.FILE_SEPARATOR + "tests"
                + Constants.FILE_SEPARATOR + outputDir + Constants.FILE_SEPARATOR + file;
    }

    protected static void generateArtemisCfg(ArtemisContainer artemisInstance) {
        generateArtemisCfg(artemisInstance, new ArrayList<>());
    }

    public static void generateArtemisCfg(ArtemisContainer artemisInstance, List<String> yacfgParams) {
        generateArtemisCfg(artemisInstance, null, yacfgParams);
    }
    public static void generateArtemisCfg(ArtemisContainer artemisInstance, Map<String, String> artemisData, List<String> yacfgParams) {
        String instanceDir = EnvironmentStandalone.getInstance().getTestConfigDir() + Constants.FILE_SEPARATOR + artemisInstance.getName();
        String providedInstanceDir = null;
        String providedInstallDir = null;
        if (artemisData != null) {
            providedInstanceDir = artemisData.get(ArtemisContainer.INSTANCE_DIR_KEY);
            providedInstallDir = artemisData.get(ArtemisContainer.INSTALL_DIR_KEY);
            artemisInstance.setInstallDir(providedInstallDir);
        }

        if (providedInstanceDir == null) {
            LOGGER.debug("[Config] Using default installDir {}", instanceDir);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.BIN_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.DATA_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.ETC_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.LIB_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.LOG_DIR);
            TestUtils.createDirectory(instanceDir + ArtemisConstants.TMP_DIR);
        } else {
            LOGGER.debug("[Config] Using provided instanceDir {}", providedInstanceDir);
            TestUtils.copyDirectories(providedInstanceDir, instanceDir);
            artemisInstance.setConfigBinDir(instanceDir + ArtemisConstants.BIN_DIR);
        }
        artemisInstance.withInstanceDir(instanceDir);

        String artemisConfig = EnvironmentStandalone.getInstance().getProvidedArtemisConfig();
        if (artemisConfig != null) {
            LOGGER.debug("[config] Reusing existing etc profile: {}", artemisConfig);
            artemisInstance.withConfigDir(artemisConfig);
        } else {
            LOGGER.debug("[config] YACFG is going to generate new etc profile");
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
            if (yacfg.getExitCode() != 0) {
                LOGGER.debug("[{}] {}", yacfg.getName(), yacfg.getLogTail(20));
            } else {
                LOGGER.debug("[{}] Exited properly", yacfg.getName());
            }
        }
    }

}
