/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.database;

public interface Database {
    String MYSQL = "mysql";
    String MARIADB = "mariadb";
    String MSSQL = "mssql2022";
    String ORACLE = "oracle23";
    String POSTGRESQL = "postgresql";
    String getJdbcUrl();
    String getConnectionUrl();
    String getDriverName();
    String getDriverUrl();
    String getDriverFile();
    String getDriverFilename();
    String getName();
    String getDatabaseName();
    String getTuneFile();
    String getUsername();
    String getPassword();

}
