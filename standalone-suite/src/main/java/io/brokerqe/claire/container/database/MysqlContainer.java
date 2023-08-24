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

public class MysqlContainer extends DatabaseContainer {

    String tmpConfig = "/tmp/my_custom.cnf";

    public MysqlContainer(String name) {
        super(name);
        LOGGER.debug("[Mysql] Creating container with name {}", name);
        // used for username, password and databaseName itself (show databases)
        String databaseName = Database.MYSQL + "-" + TestUtils.generateRandomName();
        int port = 3306;
        String connectionUrl = String.format("jdbc:mysql://%s:%s/%s?user=%s&amp;password=%s", name, port, databaseName, databaseName, databaseName);
        jdbcData = new JdbcData(name, connectionUrl, "com.mysql.cj.jdbc.Driver", databaseName, databaseName, databaseName, Constants.MYSQL_DRIVER_URL);
        setJdbcData(jdbcData);
        container.withEnv(Map.of(
                "MYSQL_ROOT_USER", "admin",
                "MYSQL_ROOT_PASSWORD", "admin",
                "MYSQL_DATABASE", databaseName,
                "MYSQL_USER", databaseName,
                "MYSQL_PASSWORD", databaseName,
                "MYSQL_AUTHENTICATION_PLUGIN", "mysql_native_password"
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
        container.withFileSystemBind(tmpConfig, "/opt/bitnami/mysql/conf/my_custom.cnf");
    }
}
