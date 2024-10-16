/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.NfsServerContainer;
import io.brokerqe.claire.container.ToxiProxyContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


public class ToolDeployer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolDeployer.class);

    public static ArtemisConfigData setupNfsShares() {
        String exportDirName = "artemisData";
        NfsServerContainer nfsServer = getNfsServerInstance(exportDirName);

        ToxiProxyContainer tp = ResourceManager.getToxiProxyContainerInstance("toxiProxy");
        tp.start();
        tp.createProxy("nfsProxy", "0.0.0.0:8666", nfsServer.getName() + ":" + "2049");
        tp.createProxy("artemisBackupNfsProxy", "0.0.0.0:8667", nfsServer.getName() + ":" + "2049");

        LOGGER.info("Setting artemis NFS mount config");
        String artemisNfsMountDir = ArtemisContainer.ARTEMIS_INSTANCE_DIR + Constants.FILE_SEPARATOR + "shared_data_dir";
        String nfsMountPrimary = nfsServer.getNfsMountString(tp.getName(), "8666", exportDirName, artemisNfsMountDir, NfsServerContainer.DEFAULT_CLIENT_OPTIONS);
        Map<String, String> artemisPrimaryEnvVars = Map.of("NFS_MOUNTS", nfsMountPrimary);
        String nfsMountBackup = nfsServer.getNfsMountString(tp.getName(), "8667", exportDirName, artemisNfsMountDir, NfsServerContainer.DEFAULT_CLIENT_OPTIONS);
        Map<String, String>  artemisBackupEnvVars = Map.of("NFS_MOUNTS", nfsMountBackup);
        ArtemisConfigData artemisConfigDataNfs = new ArtemisConfigData()
                .withPrimaryEnvVars(artemisPrimaryEnvVars)
                .withBackupEnvVars(artemisBackupEnvVars)
                .withNfsMountDir(artemisNfsMountDir);

        return artemisConfigDataNfs;
    }

    public static NfsServerContainer getNfsServerInstance(String exportDirName) {
        LOGGER.info("[NFS] Creating server with export {}", exportDirName);
        NfsServerContainer nfsServer = ResourceManager.getNfsServerContainerInstance("nfsServer");
        String nfsServerName = nfsServer.getName();
        String exportBaseDir = "exports" + Constants.FILE_SEPARATOR + exportDirName;
        String hostExportDir = EnvironmentStandalone.getInstance().getTestConfigDir() + Constants.FILE_SEPARATOR + nfsServerName + Constants.FILE_SEPARATOR + exportBaseDir;
        String containerExportDir = Constants.FILE_SEPARATOR + exportBaseDir;
        nfsServer.withExportDir(hostExportDir, containerExportDir);
        nfsServer.start();
        return nfsServer;
    }
}
