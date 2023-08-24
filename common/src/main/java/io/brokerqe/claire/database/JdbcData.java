/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.database;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class JdbcData {

    protected static final Logger LOGGER = LoggerFactory.getLogger(JdbcData.class);

    Map<String, String> databaseData = new HashMap<>();
    
    public JdbcData(String databaseFile) {
        try {
            databaseData = new Yaml().load(
                    FileUtils.readFileToString(new File(databaseFile), Charset.defaultCharset()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JdbcData(String name, String connectionUrl, String driverClassName, String username, String password, String databaseName, String driverUrl) {
        this(name, connectionUrl, driverClassName, username, password, databaseName, driverUrl, null);
    }
    public JdbcData(String name, String connectionUrl, String driverClassName, String username, String password, String databaseName, String driverUrl, String driverArchivePath) {
//        databaseLoaded.put("uuid", null);
        databaseData.put("db.primary_label", name);
        databaseData.put("db.schema", databaseName);
        databaseData.put("jdbc_connection_url", connectionUrl);
        if (connectionUrl.contains("?")) {
            databaseData.put("db.jdbc_url", connectionUrl.substring(0, connectionUrl.indexOf("?")));
        } else {
            // mssql?
            databaseData.put("db.jdbc_url", connectionUrl);
        }
        databaseData.put("jdbc_driver_class_name", driverClassName);
        databaseData.put("driver_url", driverUrl);
        databaseData.put("db.username", username);
        databaseData.put("db.password", password);
        if (driverArchivePath != null) {
            databaseData.put("driver_local_file", getJdbcDriver(driverUrl, driverArchivePath));
        } else {
            databaseData.put("driver_local_file", getJdbcDriver(driverUrl));
        }

        // last item
        databaseData.put("output_file", createTuneFile());
    }

    private String createTuneFile() {
        String tuneJdbcFilename = "/tmp/tune_jdbc_" + this.getName();
        TestUtils.createFile(tuneJdbcFilename, toString());
        return tuneJdbcFilename;
    }

    private String getJdbcDriver(String driverUrl, String driverFileName) {
        String destinationFilename = TestUtils.getProjectRelativeFile(Constants.ARTEMIS_DEFAULT_CFG_LIB_DIR) + "/" + driverFileName;
        String filename = driverUrl.substring(driverUrl.lastIndexOf("/") + 1);
        String driverArchivePath = "/tmp/" + filename;
        String driverUnarchivedPath = "/tmp/" + filename + "-" + TestUtils.getRandomString(2);
        TestUtils.getFileFromUrl(driverUrl, driverArchivePath);
        if (driverUrl.endsWith(".zip")) {
            TestUtils.unzip(driverArchivePath, driverUnarchivedPath);
        }
        Path file = TestUtils.findFile(driverUnarchivedPath, driverFileName);
        TestUtils.copyFile(file.toString(), destinationFilename);
        return destinationFilename;
    }

    private String getJdbcDriver(String driverUrl) {
        String filename = driverUrl.substring(driverUrl.lastIndexOf("/") + 1);
        String driverPath = "/tmp/" + filename;
        TestUtils.getFileFromUrl(driverUrl, driverPath);
        String destinationFilename = TestUtils.getProjectRelativeFile(Constants.ARTEMIS_DEFAULT_CFG_LIB_DIR) + "/" + filename;
        TestUtils.copyFile(driverPath, destinationFilename);
        return destinationFilename;
    }

    public String get(String name) {
        return databaseData.get(name);
    }

    public String getUUID() {
        return databaseData.get("uuid");
    }

    public String getFullConnectionUrl() {
        return databaseData.get("jdbc_connection_url");
    }

    public String getDriverClassName() {
        return databaseData.get("jdbc_driver_class_name");
    }

    public String getDriverUrl() {
        return databaseData.get("driver_url");
    }

    public String getDriverLocalPath() {
        return databaseData.get("driver_local_file");
    }

    public String getDriverFilename() {
        String driverPath = databaseData.get("driver_local_file");
        return driverPath.substring(driverPath.lastIndexOf("/") + 1);
    }

    public String getName() {
        return databaseData.get("db.primary_label");
    }

    public String getUsername() {
        return databaseData.get("db.username");
    }

    public String getPassword() {
        return databaseData.get("db.password");
    }

    public String getJdbcUrl() {
        return databaseData.get("db.jdbc_url");
    }

    public String getDatabaseName() {
        return databaseData.get("db.schema");
    }

    public String getTuneFile() {
        return databaseData.get("output_file");
    }

    @Override
    public String toString() {
        return
            "db.primary_label: " + getName() + "\n" +
            "db.schema: " + getDatabaseName() + "\n" +
            "jdbc_connection_url: " + getFullConnectionUrl() + "\n" +
            "jdbc_driver_class_name: " + getDriverClassName() + "\n" +
            "driver_url: " + getDriverUrl() + "\n" +
            "driver_local_file: " + getDriverLocalPath() + "\n" +
            "db.username: " + getUsername() + "\n" +
            "db.password: " + getPassword();
    }
}
