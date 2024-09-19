/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.TestInfo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtemisDeployment {

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

}
