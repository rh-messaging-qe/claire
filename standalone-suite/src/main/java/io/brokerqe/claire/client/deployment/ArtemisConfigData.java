/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.YacfgArtemisContainer;
import io.brokerqe.claire.database.Database;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtemisConfigData {

    private String installDir;
    private String instanceDir;
    private Map<String, String> primaryEnvVars = new HashMap<>();
    private Map<String, String> backupEnvVars = new HashMap<>();
    private Map<String, String> envVars = new HashMap<>();
    private String tuneFile;
    private String primaryTuneFile;
    private String backupTuneFile;
    private ArtemisVersion artemisVersion;
    private String artemisVersionString;
    private String yacfgProfileTemplate;
    private List<String> yacfgOptions = new ArrayList<>();
    private boolean start = true;
    private Duration startTimeout = Duration.ofMinutes(1);
    private boolean isBackup = false;
    private boolean isPrimary = false;
    private boolean isSharedStore = false;
    private Database database;
    private String artemisNfsMountDir;

    public ArtemisConfigData() {}

    public ArtemisConfigData withStart(boolean start) {
        this.start = start;
        return this;
    }

    public boolean isStart() {
        return start;
    }

    public ArtemisConfigData withStartTimeout(Duration duration) {
        this.startTimeout = duration;
        return this;
    }

    public Duration getStartTimeout() {
        return startTimeout;
    }

    public ArtemisConfigData withIsBackup(boolean isBackup) {
        this.isBackup = isBackup;
        this.isPrimary = !isBackup;
        return this;
    }

    public ArtemisConfigData withIsSharedStore(boolean isSharedStore) {
        this.isSharedStore = isSharedStore;
        return this;
    }

    public ArtemisConfigData withPrimaryEnvVars(Map<String, String> primaryEnvVars) {
        this.primaryEnvVars = primaryEnvVars;
        return this;
    }

    public ArtemisConfigData withBackupEnvVars(Map<String, String> backupEnvVars) {
        this.backupEnvVars = backupEnvVars;
        return this;
    }

    public ArtemisConfigData withEnvVars(Map<String, String> envVars) {
        this.envVars = envVars;
        return this;
    }

    public ArtemisConfigData withYacfgProfileTemplate(String yacfgProfileTemplate) {
        this.yacfgProfileTemplate = yacfgProfileTemplate;
        return this;
    }

    public ArtemisConfigData withYacfgOptions(List<String> yacfgOptions) {
        this.yacfgOptions = yacfgOptions;
        return this;
    }

    public ArtemisConfigData withInstallDir(String installDir) {
        this.installDir = installDir;
        return this;
    }

    public ArtemisConfigData withInstanceDir(String instanceDir) {
        this.instanceDir = instanceDir;
        return this;
    }

    public ArtemisConfigData withArtemisVersionString(String artemisVersion) {
        this.artemisVersionString = artemisVersion;
        return this;
    }

    public ArtemisConfigData withArtemisVersion(ArtemisVersion artemisVersion) {
        this.artemisVersion = artemisVersion;
        return this;
    }

    public ArtemisConfigData withDebugLogs(boolean auditOff) {
        if (auditOff) {
            withCustomTuneFile(ArtemisContainer.ARTEMIS_TUNE_LOGS_DEBUG_FILE);
        } else {
            withCustomTuneFile(ArtemisContainer.ARTEMIS_TUNE_LOGS_DEBUG_ALL_FILE);
        }
        return this;
    }

    private String getTuneFileFullPath(String tuneFile) {
        if (tuneFile == null) {
            return null;
        }
        return YacfgArtemisContainer.YACFG_CONTAINER_CLAIRE_STANDALONE_DIR + Constants.FILE_SEPARATOR + "tests"
                + Constants.FILE_SEPARATOR + EnvironmentStandalone.getInstance().getPackageClassDir() + Constants.FILE_SEPARATOR + tuneFile;
    }

    public ArtemisConfigData withTuneFile(String tuneFile) {
        this.tuneFile = tuneFile;
        return this;
    }

    public ArtemisConfigData withCustomTuneFile(String tuneFile) {
        List<String> customYacfgOptions = getYacfgOptions();
        customYacfgOptions.add("tune_file=" + tuneFile);
        withYacfgOptions(customYacfgOptions);
        return this;
    }

    public ArtemisConfigData withPrimaryTuneFile(String primaryTuneFile) {
        this.primaryTuneFile = primaryTuneFile;
        return this;
    }

    public ArtemisConfigData withBackupTuneFile(String backupTuneFile) {
        this.backupTuneFile = backupTuneFile;
        return this;
    }

    public Map<String, String> getPrimaryEnvVars() {
        return primaryEnvVars;
    }

    public Map<String, String> getBackupEnvVars() {
        return backupEnvVars;
    }

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public String getInstallDir() {
        if (installDir == null) {
            return TestUtils.getProjectRelativeFilePath(ArtemisConstants.INSTALL_DIR).toString();
        }
        return installDir;
    }

    public String getInstanceDir() {
        return instanceDir;
    }

    public ArtemisVersion getArtemisTestVersion() {
        if (artemisVersion == null) {
            artemisVersion = Environment.get().getArtemisTestVersion();
        }
        return artemisVersion;
    }

    public String getArtemisVersion() {
        if (artemisVersionString == null) {
            artemisVersionString = Environment.get().getArtemisMajorMinorMicroVersion(EnvironmentStandalone.get().getArtemisVersion());
        }
        return artemisVersionString;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public boolean isSharedStore() {
        return isSharedStore;
    }

    // === YACFG OPTIONS ===
    public String getTuneFile() {
        return getTuneFileFullPath(tuneFile);
    }

    public String getPrimaryTuneFile() {
        if (getArtemisTestVersion().getVersionNumber() <= ArtemisVersion.VERSION_2_30.getVersionNumber()) {
            return "old-" + primaryTuneFile;
        }
        return primaryTuneFile;
    }

    public String getBackupTuneFile() {
        if (getArtemisTestVersion().getVersionNumber() <= ArtemisVersion.VERSION_2_30.getVersionNumber()) {
            return "old-" + backupTuneFile;
        }
        return backupTuneFile;
    }

    public List<String> getYacfgOptions() {
        return yacfgOptions;
    }

    public String getYacfgProfileTemplate() {
        if (yacfgProfileTemplate == null) {
            return "claire-default-profile-" + getArtemisVersion() + ".yaml.jinja2";
        }
        return yacfgProfileTemplate;
    }

    public ArtemisConfigData withNfsMountDir(String artemisNfsMountDir) {
        this.artemisNfsMountDir = artemisNfsMountDir;
        return this;
    }

    public String getNfsMountDir() {
        return artemisNfsMountDir;
    }

    public ArtemisConfigData withDatabase(Database database) {
        this.database = database;
        return this;
    }

    public Database getDatabase() {
        return database;
    }
}
