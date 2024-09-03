/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container.database;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.database.JdbcData;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

/**
 * https://hub.docker.com/_/microsoft-mssql-server
 * docker exec -it <container_id> /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P '4dm!nSup3rS3cr3t'
 */
public class MssqlContainer extends DatabaseContainer {

    // used for username, password and databaseName itself (show databases)
    String databaseName = Database.MSSQL + TestUtils.generateRandomName();
    String adminPassword = "4dm!nSup3rS3cr3t";
    String userPassword = "Tralala-myPass12";
    int port = 1433;
    String sqlStartupFilename = "/tmp/mssqlStartup.sql";
    String sqlAdminContent = String.format("""
            CREATE DATABASE %s
            GO
            USE %s
            GO
            CREATE LOGIN %s WITH PASSWORD = '%s'
            GO
            CREATE USER %s FOR LOGIN %s
            GO
            EXEC sp_addrolemember 'db_datareader', '%s'
            EXEC sp_addrolemember 'db_datawriter', '%s'
            EXEC sp_addrolemember 'db_owner', '%s'
            GO
            """, databaseName, databaseName,
            databaseName, userPassword, databaseName, databaseName,
            databaseName, databaseName, databaseName);

    public MssqlContainer(String name) {
        super(name);
        LOGGER.debug("[Mssql] Creating container with name {}", name);
        String connectionUrl = String.format("jdbc:sqlserver://%s:%s;DatabaseName=%s;user=%s;password=%s;encrypt=true;trustServerCertificate=true", name, port, databaseName, databaseName, userPassword);
        jdbcData = new JdbcData(name, connectionUrl, "com.microsoft.sqlserver.jdbc.SQLServerDriver", databaseName, databaseName, databaseName, Constants.MSSQL_DRIVER_URL, "mssql-jdbc-12.2.0.jre11.jar");
        setJdbcData(jdbcData);
        container.withEnv(Map.of(
                "ACCEPT_EULA", "Y",
                "MSSQL_SA_PASSWORD", adminPassword,
                "MSSQL_PID", "Enterprise"
            ));
        container.addExposedPort(port);
        withLogWait(".*Recovery is complete.*");
        TestUtils.createFile(sqlStartupFilename, sqlAdminContent);
        container.withCopyFileToContainer(MountableFile.forHostPath(sqlStartupFilename), sqlStartupFilename);
    }

    @Override
    public void start() {
        super.start();
        setupDatabase();
    }

    public void setupDatabase() {
        String setupCommand = String.format("/opt/mssql-tools18/bin/sqlcmd -S localhost -C -U sa -P %s -i %s", adminPassword, sqlStartupFilename);
        executeCommand(setupCommand.split(" "));
    }
}
