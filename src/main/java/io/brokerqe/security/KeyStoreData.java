/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Base64;

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

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public String getPassword() {
        return password;
    }
    public String getEncodedPassword() {
        return Base64.getEncoder().encodeToString(password.getBytes());
    }

    public String getEncodedKeystoreFileData() {
        try {
            byte[] content = Files.readAllBytes(Paths.get(keyStorePath));
            return Base64.getEncoder().encodeToString(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
