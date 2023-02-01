/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import java.security.KeyStore;

public class KeyStoreData {

    private KeyStore keyStore;
    private String keyStorePath;
    private String identifier;
    private String password;

    public KeyStoreData(KeyStore keyStore, String keyStorePath, String identifier, String password) {
        this.keyStore = keyStore;
        this.keyStorePath = keyStorePath;
        this.identifier = identifier;
        this.password = password;
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

    public String getPassword() {
        return password;
    }
    public String getEncodedPassword() {
        return CertificateManager.getEncodedString(password);
    }

    public String getEncodedKeystoreFileData() {
        return CertificateManager.readCertificateFromFile(keyStorePath);
    }
}
