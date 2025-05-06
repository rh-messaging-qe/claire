/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container.database;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.database.JdbcData;

import java.util.Map;

public class OracleDbContainer extends DatabaseContainer {

    private String adminPassword = "adminPass123";
    String databaseName = Database.ORACLE + TestUtils.generateRandomName();
    String userPassword = databaseName;

    String sqlStartupFilename = "/tmp/oraStartup.sql";
    String containerSqlStartupFilename = "/home/oracle/oraStartup.sql";
    String sqlAdminContent = String.format("""
        CREATE USER %s IDENTIFIED BY %s;
        GRANT CREATE SESSION TO %s;
        GRANT ALL PRIVILEGES TO %s;
        """, databaseName, userPassword, databaseName, databaseName);

    public OracleDbContainer(String name) {
        super(name);
        LOGGER.debug("[OracleDB] Creating container with name {}", name);
        // used for username, password and databaseName itself (show databases)
        int port = 1521;
        String connectionUrl = String.format("jdbc:oracle:thin:%s/%s@%s:%s/freepdb1", databaseName, userPassword, name, port);
        jdbcData = new JdbcData(name, connectionUrl, "oracle.jdbc.OracleDriver", databaseName, databaseName, databaseName, Constants.ORACLE_DRIVER_URL);
        setJdbcData(jdbcData);
        container.withEnv(Map.of(
                "ORACLE_PWD", adminPassword
            ));
        container.addExposedPort(port);
        withLogWait(".*DATABASE IS READY TO USE.*");
        TestUtils.createFile(sqlStartupFilename, sqlAdminContent);
    }

    @Override
    public void start() {
        super.start();
        setupDatabase();
    }

    public void setupDatabase() {
        String createScript = String.format("echo '%s' > %s", sqlAdminContent, containerSqlStartupFilename);
        executeCommand("sh", "-ic", createScript);
        // podman exec -it <oracle-db> sqlplus sys/<your_password>@FREE as sysdba

        String setupCommand = String.format("echo exit | sqlplus sys/%s@FREEPDB1 as sysdba @%s", adminPassword, containerSqlStartupFilename);
        executeCommand("sh", "-ic", setupCommand);
    }


}
