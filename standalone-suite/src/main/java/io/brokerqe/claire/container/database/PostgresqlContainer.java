/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container.database;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.database.JdbcData;

import java.util.Map;

public class PostgresqlContainer extends DatabaseContainer {

    public PostgresqlContainer(String name) {
        super(name);
        LOGGER.debug("[Postgresql] Creating container with name {}", name);
        // used for username, password and databaseName itself (show databases)
        String databaseName = POSTGRESQL + "-" + TestUtils.generateRandomName();
        int port = 5432;
        String connectionUrl = String.format("jdbc:postgresql://%s:%s/%s?user=%s&amp;password=%s", name, port, databaseName, databaseName, databaseName);
        jdbcData = new JdbcData(name, connectionUrl, "org.postgresql.Driver", databaseName, databaseName, databaseName, Constants.POSTGRESQL_DRIVER_URL);
        setJdbcData(jdbcData);
        // admin username is 'postgres'
        container.withEnv(Map.of(
                "POSTGRESQL_POSTGRES_PASSWORD", "postgres",
                "POSTGRESQL_DATABASE", databaseName,
                "POSTGRESQL_USERNAME", databaseName,
                "POSTGRESQL_PASSWORD", databaseName
        ));
        container.addExposedPort(port);
        withLogWait(".*database system is ready to accept connections.*");
    }
}
