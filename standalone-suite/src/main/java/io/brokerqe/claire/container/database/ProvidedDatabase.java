/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container.database;

import io.brokerqe.claire.Environment;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.database.JdbcData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProvidedDatabase implements Database {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProvidedDatabase.class);

    public JdbcData jdbcData;

    public ProvidedDatabase(JdbcData jdbcData) {
        this.jdbcData = jdbcData;
        LOGGER.info("[DB] Using provided {} with {}", getName(), getConnectionUrl());
    }

    @Override
    public String getJdbcUrl() {
        return jdbcData.getJdbcUrl();
    }

    @Override
    public String getConnectionUrl() {
        return jdbcData.getFullConnectionUrl();
    }

    @Override
    public String getDriverName() {
        return jdbcData.getDriverClassName();
    }

    @Override
    public String getDriverUrl() {
        return jdbcData.getDriverUrl();
    }

    @Override
    public String getDriverFile() {
        return jdbcData.getDriverLocalPath();
    }

    @Override
    public String getDriverFilename() {
        return jdbcData.getDriverFilename();
    }

    @Override
    public String getName() {
        return jdbcData.getName();
    }

    @Override
    public String getDatabaseName() {
        return jdbcData.getDatabaseName();
    }

    @Override
    public String getTuneFile() {
        return Environment.get().getJdbcDatabaseFile();
    }

    @Override
    public String getUsername() {
        return jdbcData.getUsername();
    }

    @Override
    public String getPassword() {
        return jdbcData.getPassword();
    }


}
