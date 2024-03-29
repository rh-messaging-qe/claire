/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.brokerqe.claire.TestUtils;

import java.security.KeyStore;

public class KeyStoreData {

    private final CertificateData certificateData;
    private KeyStore keyStore;
    private String keyStorePath;
    private String identifier;
    private String password;

    public KeyStoreData(KeyStore keyStore, String keyStorePath, String identifier, String password) {
        this(keyStore, keyStorePath, identifier, password, null);
    }

    public KeyStoreData(KeyStore keyStore, String keyStorePath, String identifier, String password, CertificateData certificateData) {
        this.keyStore = keyStore;
        this.keyStorePath = keyStorePath;
        this.identifier = identifier;
        this.password = password;
        this.certificateData = certificateData;
    }

    public String getIdentifier() {
        return identifier;
    }

    public KeyStore getKeyStore() {

        return keyStore;
    }

    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public String getKeyStorePathFileName() {
        return keyStorePath.substring(keyStorePath.lastIndexOf("/") + 1);
    }

    public String getPassword() {
        return password;
    }

    public String getEncodedPassword() {
        return TestUtils.getEncodedBase64String(password);
    }

    public String getEncodedKeystoreFileData() {
        return CertificateManager.readCertificateFromFile(keyStorePath);
    }

    public CertificateData getCertificateData() {
        return certificateData;
    }
}
