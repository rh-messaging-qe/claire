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

public class MariadbContainer extends DatabaseContainer {

    String tmpConfig = "/tmp/my_custom.cnf";

    public MariadbContainer(String name) {
        super(name);
        LOGGER.debug("[MariaDb] Creating container with name {}", name);
        // used for username, password and databaseName itself (show databases)
        String databaseName = Database.MARIADB + "-" + TestUtils.generateRandomName();
        int port = 3306;
        String connectionUrl = String.format("jdbc:mariadb://%s:%s/%s?user=%s&amp;password=%s", name, port, databaseName, databaseName, databaseName);
        jdbcData = new JdbcData(name, connectionUrl, "org.mariadb.jdbc.Driver", databaseName, databaseName, databaseName, Constants.MARIADB_DRIVER_URL);
        setJdbcData(jdbcData);
        container.withEnv(Map.of(
                "MARIADB_ROOT_USER", "admin",
                "MARIADB_ROOT_PASSWORD", "admin",
                "MARIADB_DATABASE", databaseName,
                "MARIADB_USER", databaseName,
                "MARIADB_PASSWORD", databaseName
            ));
        container.addExposedPort(port);
        withLogWait(".*mysqld: ready for connections.*");
        createConfig();
    }

    private void createConfig() {
        String customConfig = """
                [mysqld]
                max_allowed_packet=32M
                skip-log-bin
                disable_log_bin
                """;
        TestUtils.createFile(tmpConfig, customConfig);
        container.withFileSystemBind(tmpConfig, "/opt/bitnami/mariadb/conf/my_custom.cnf");
    }
}
