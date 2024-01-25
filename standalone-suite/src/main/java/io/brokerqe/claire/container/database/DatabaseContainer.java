/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container.database;

import com.sun.security.auth.module.UnixSystem;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.container.AbstractGenericContainer;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.database.JdbcData;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;


public abstract class DatabaseContainer extends AbstractGenericContainer implements Database {

    protected static final Logger LOGGER = LoggerFactory.getLogger(DatabaseContainer.class);

    public JdbcData jdbcData;

    public DatabaseContainer(String name) {
        super(name, getContainerImage(name));
        this.type = ContainerType.DATABASE;
        
        String localDbData = "/tmp/" + name + "/data";
        String dbData = "/db_data";
        LOGGER.debug("[{}] using volume mapping {} = {}", name, localDbData, dbData);
        this.withFileSystemBind(localDbData, dbData, BindMode.READ_WRITE);
    }

    private static String getContainerImage(String databaseName) {
        if (databaseName.contains(Database.MYSQL)) {
            return Constants.IMAGE_MYSQL;
        } else if (databaseName.contains(Database.MARIADB)) {
            return Constants.IMAGE_MARIADB;
        } else if (databaseName.contains(Database.MSSQL)) {
            return Constants.IMAGE_MSSQL;
        } else if (databaseName.contains(Database.POSTGRESQL)) {
            return Constants.IMAGE_POSTGRES;
        } else if (databaseName.contains(Database.ORACLE)) {
            return Constants.IMAGE_ORACLE;
        } else {
            throw new ClaireRuntimeException("Provided unknown Database name. " + databaseName);
        }
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
    public String getDatabaseName() {
        return jdbcData.getDatabaseName();
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
    public String getTuneFile() {
        return  jdbcData.getTuneFile();
    }

    @Override
    public String getUsername() {
        return jdbcData.getUsername();
    }

    @Override
    public String getPassword() {
        return jdbcData.getPassword();
    }

    public void start() {
        LOGGER.info("[Container {}] Starting.\nUsing jdbc properties: {}", name, jdbcData.toString());
        if (!this.getDatabaseName().contains(Database.ORACLE) && !this.getDatabaseName().contains(Database.MSSQL)) {
            withUserId(String.valueOf(new UnixSystem().getUid()));
        }
        super.start();
    }

    protected void setJdbcData(JdbcData jdbcData) {
        this.jdbcData = jdbcData;
    }

}
