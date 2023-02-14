/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.brokerqe.Constants;
import org.bouncycastle.asn1.x509.Extension;
import javax.security.auth.x500.X500PrivateCredential;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class CertificateData {

    private final String alias;
    private final X509Certificate certificate;
    private final X500PrivateCredential privateCredential;
    private final KeyPair keyPair;
    private final String distinguishedName;
    private final String fileName;

    public CertificateData(String alias, String distinguishedName) {
        this(alias, distinguishedName, null, 30, null);
    }

    public CertificateData(String alias, String distinguishedName, List<Extension> extensions, CertificateData issuer) {
        this(alias, distinguishedName, extensions, 30, issuer);
    }

    public CertificateData(String alias, String distinguishedName, List<Extension> extensions, int validityDays, CertificateData issuer) {
        this(alias, distinguishedName, extensions,
                Date.from(Instant.now().minus(Duration.ofDays(1L))),
                Date.from(Instant.now().plus(Duration.ofDays(validityDays))), issuer);
    }

    public CertificateData(String alias, String distinguishedName, List<Extension> extensions, Date validNotBefore, Date validNotAfter, CertificateData issuer) {
        this.alias = alias;
        this.keyPair = CertificateManager.createKeyPairGenerator();
        this.distinguishedName = distinguishedName;
        this.certificate = CertificateManager.generate(keyPair, CertificateManager.SIGNATURE_ALGORITHM, distinguishedName, validNotBefore, validNotAfter, extensions, issuer);
        this.privateCredential = CertificateManager.createPrivateCredential(certificate, keyPair, alias);
        this.fileName = Constants.CERTS_GENERATION_DIR + alias + ".crt";
        CertificateManager.writeCertificateToFile(certificate, fileName);
    }

    /** Use this to generate CA certs **/
    public CertificateData(String alias, String distinguishedName, CertificateData issuer) {
        this(alias, distinguishedName, issuer,
                Date.from(Instant.now().minus(Duration.ofDays(1L))),
                Date.from(Instant.now().plus(Duration.ofDays(30))));
    }
    public CertificateData(String alias, String distinguishedName, CertificateData issuer, Date validNotBefore, Date validNotAfter) {
        this.alias = alias;
        this.keyPair = CertificateManager.createKeyPairGenerator();
        this.distinguishedName = distinguishedName;
        this.certificate = CertificateManager.generateCA(keyPair, CertificateManager.SIGNATURE_ALGORITHM, distinguishedName, validNotBefore, validNotAfter, issuer);
        this.privateCredential = CertificateManager.createPrivateCredential(certificate, keyPair, alias);
        this.fileName = Constants.CERTS_GENERATION_DIR + alias + ".crt";
        CertificateManager.writeCertificateToFile(certificate, fileName);
    }

    public String getAlias() {
        return alias;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }

    public X500PrivateCredential getPrivateCredential() {
        return privateCredential;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getBaseFileName() {
        return Paths.get(fileName).getFileName().toString();
    }
}
