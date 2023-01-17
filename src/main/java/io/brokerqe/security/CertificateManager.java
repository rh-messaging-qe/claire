/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.KubernetesPlatform;
import io.brokerqe.TestUtils;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500PrivateCredential;

// Original Certificate generation code in https://github.com/misterpki/selfsignedcert/
// https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html#keypairgenerator-algorithms
// https://www.bouncycastle.org/specifications.html
@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling"})
public class CertificateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateManager.class);
    protected static final String KEY_PAIR_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA512withRSA";
    public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";
    final static String DEFAULT_BROKER_ALIAS = "brokerUser";
    final static String DEFAULT_BROKER_PASSWORD = "brokerPass";
    final static String DEFAULT_CLIENT_ALIAS = "clientUser";
    final static String DEFAULT_CLIENT_PASSWORD = "clientPass";

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, final int days) {
        return generate(keyPair, hashAlgorithm, distinguishedName, days, null);
    }

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, final int days, List<Extension> extensions) {
        final Instant now = Instant.now();
        final Date notBefore = Date.from(now);
        final Date notAfter = Date.from(now.plus(Duration.ofDays(days)));
        final ContentSigner contentSigner;

        try {
            List<Extension> defaultExtensions = new ArrayList<Extension>();
            defaultExtensions.add(new Extension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()).getEncoded()));
            defaultExtensions.add(new Extension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()).getEncoded()));
            defaultExtensions.add(new Extension(Extension.basicConstraints, true, (new BasicConstraints(true)).getEncoded()));

            if (extensions != null) {
                defaultExtensions.addAll(extensions);
            } else {
                extensions = defaultExtensions;
            }

            contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());

            final X500Name issuer = new X500Name(distinguishedName);
            final X500Name subject = new X500Name(distinguishedName);
            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    issuer,
                    BigInteger.valueOf(now.toEpochMilli()),
                    notBefore,
                    notAfter,
                    subject,
                    keyPair.getPublic());

            for (Extension extension : extensions) {
                certificateBuilder.addExtension(extension);
            }

            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
        } catch (OperatorCreationException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair createKeyPairGenerator() {
        KeyPairGenerator keyPairGenerator;
        try {
            Security.addProvider(new BouncyCastleProvider());
            keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM);
            keyPairGenerator.initialize(4096);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates the hash value of the public key.
     * @param publicKey of the certificate
     * @return SubjectKeyIdentifier hash
     */
    private static SubjectKeyIdentifier createSubjectKeyId(final PublicKey publicKey) {
        final SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        final DigestCalculator digCalc;
        try {
            digCalc = new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }
        return new X509ExtensionUtils(digCalc).createSubjectKeyIdentifier(publicKeyInfo);
    }

    /**
     * Creates the hash value of the authority public key.
     * @param publicKey of the authority certificate
     * @return AuthorityKeyIdentifier hash
     */
    private static AuthorityKeyIdentifier createAuthorityKeyId(final PublicKey publicKey) {
        final SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        final DigestCalculator digCalc;
        try {
            digCalc = new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));
        } catch (OperatorCreationException e) {
            throw new RuntimeException(e);
        }
        return new X509ExtensionUtils(digCalc).createAuthorityKeyIdentifier(publicKeyInfo);
    }

    public static X500PrivateCredential createPrivateCredential(X509Certificate certificate, KeyPair keypair, String certificateAlias) {
        return new X500PrivateCredential(certificate, keypair.getPrivate(), certificateAlias);
    }

    public static Map<String, KeyStoreData> createKeystores(X500PrivateCredential brokerCredential, X500PrivateCredential clientCredential, String brokerAlias, String brokerPassword, String clientAlias, String clientPassword) {
        Map<String, KeyStoreData> keystores = new HashMap<>();
        String brokerKeyStoreFileName = Constants.CERTS_GENERATION_DIR + brokerAlias + "_keystore.p12";
        String brokerTrustStoreFileName = Constants.CERTS_GENERATION_DIR + brokerAlias + "_truststore.p12";
        String clientKeyStoreFileName = Constants.CERTS_GENERATION_DIR + clientAlias + "_keystore.p12";
        String clientTrustStoreFileName = Constants.CERTS_GENERATION_DIR + clientAlias + "_truststore.p12";
        TestUtils.createDirectory(Constants.CERTS_GENERATION_DIR);

        try {
            LOGGER.info("[TLS] Creating Broker keystore");
            KeyStore brokerKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
            brokerKeyStore.load(null, brokerPassword.toCharArray());
            brokerKeyStore.setCertificateEntry(brokerAlias, brokerCredential.getCertificate());
            brokerKeyStore.setKeyEntry(brokerAlias, brokerCredential.getPrivateKey(), brokerPassword.toCharArray(),
                    new java.security.cert.Certificate[]{brokerCredential.getCertificate()});
            brokerKeyStore.store(new FileOutputStream(brokerKeyStoreFileName), brokerPassword.toCharArray());

            keystores.put(Constants.BROKER_KEYSTORE_ID, new KeyStoreData(brokerKeyStore, brokerKeyStoreFileName, Constants.BROKER_KEYSTORE_ID, brokerPassword));

            LOGGER.info("[TLS] Creating Broker truststore");
            KeyStore brokerTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
            brokerTrustStore.load(null, brokerPassword.toCharArray());
//            brokerTrustStore.setCertificateEntry(clientAlias, clientCredential.getCertificate());
            brokerTrustStore.setKeyEntry(clientAlias, clientCredential.getPrivateKey(), clientPassword.toCharArray(), new java.security.cert.Certificate[]{clientCredential.getCertificate()});
            brokerTrustStore.store(new FileOutputStream(brokerTrustStoreFileName), brokerPassword.toCharArray());
            keystores.put(Constants.BROKER_TRUSTSTORE_ID, new KeyStoreData(brokerTrustStore, brokerTrustStoreFileName, Constants.BROKER_TRUSTSTORE_ID, brokerPassword));

            LOGGER.info("Created {}, {} with password {}", brokerKeyStoreFileName, brokerTrustStoreFileName, brokerPassword);

            LOGGER.info("[TLS] Creating Client keystore");
            KeyStore clientKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
            clientKeyStore.load(null, clientPassword.toCharArray());
            clientKeyStore.setCertificateEntry(clientAlias, clientCredential.getCertificate());
            clientKeyStore.setKeyEntry(clientAlias, clientCredential.getPrivateKey(), clientPassword.toCharArray(),
                    new java.security.cert.Certificate[]{clientCredential.getCertificate()});
            clientKeyStore.store(new FileOutputStream(clientKeyStoreFileName), clientPassword.toCharArray());
            keystores.put(Constants.CLIENT_KEYSTORE_ID, new KeyStoreData(clientKeyStore, clientKeyStoreFileName, Constants.CLIENT_KEYSTORE_ID, clientPassword));

            LOGGER.info("[TLS] Creating Client truststore");
            KeyStore clientTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
            clientTrustStore.load(null, clientPassword.toCharArray());
//            clientTrustStore.setCertificateEntry(brokerAlias, brokerCredential.getCertificate());
            clientTrustStore.setKeyEntry(brokerAlias, brokerCredential.getPrivateKey(), brokerPassword.toCharArray(), new java.security.cert.Certificate[]{brokerCredential.getCertificate()});
            clientTrustStore.store(new FileOutputStream(clientTrustStoreFileName), clientPassword.toCharArray());
            keystores.put(Constants.CLIENT_TRUSTSTORE_ID, new KeyStoreData(clientTrustStore, clientTrustStoreFileName, Constants.CLIENT_TRUSTSTORE_ID, clientPassword));

            LOGGER.info("Created {}, {} with password {}", clientKeyStoreFileName, clientTrustStoreFileName, clientPassword);
            return keystores;
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException |
                 NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    public static Extension generateSanDnsNames(KubeClient kubeClient, ActiveMQArtemis broker, List<Acceptors> acceptors) {
        // DNS:$APPLICATION_NAME-$ACCEPTOR-$ORDINAL-svc-rte-$NAMESPACE.$DOMAIN_NAME"
        // Route
        // artemis-broker-my-amqp-0-svc-rte    artemis-broker-my-amqp-0-svc-rte-namespacename.apps.lala.amq-broker-qe.my-host.com
        // Ingress
        // artemis-broker-my-amqp-0-svc-ing    artemis-broker-my-amqp-0-svc-ing.apps.artemiscloud.io
        Extension sanExtension;
        List<String> acceptorNames = acceptors.stream().map(Acceptors::getName).collect(Collectors.toList());
        String appName = broker.getMetadata().getName();
        String namespace = broker.getMetadata().getNamespace();

        // platform specific
        String svc;
        String domain;
        if (kubeClient.getKubernetesType().equals(KubernetesPlatform.KUBERNETES)) {
            svc = "svc-ing";
            // https://github.com/artemiscloud/activemq-artemis-operator/blob/d04ed9609b1f8fe399fe9ea12b4f5488c6c9d9d9/pkg/resources/ingresses/ingress.go#L70
            // hardcoded
            domain = "apps.artemiscloud.io";
        } else {
            svc = "svc-rte";
            domain = namespace + "." + kubeClient.getKubernetesClient().getMasterUrl().getHost().replace("api", "apps");
        }

        ASN1EncodableVector sanNames = new ASN1EncodableVector();
        int size = broker.getSpec().getDeploymentPlan().getSize();
        for (int i = 0; i < size; i++) {
            for (String acceptorName : acceptorNames) {
                sanNames.add(new GeneralName(GeneralName.dNSName, appName + "-" + acceptorName + "-" + i + "-" + svc + "-" + domain));
            }
        }

        for (int i = 0; i < sanNames.size(); i++) {
            LOGGER.debug("[TLS] Created SAN=DNS:{}", sanNames.get(i).toString());
        }

        try {
            GeneralNames sans = GeneralNames.getInstance(new DERSequence(sanNames));
            sanExtension = new Extension(Extension.subjectAlternativeName, false, sans.getEncoded());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sanExtension;
    }

    private static void writeCertificateToFile(X509Certificate certificate, String fileName) {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter jpw = new JcaPEMWriter(sw)) {
            jpw.writeObject(certificate);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        TestUtils.createDirectory(Constants.CERTS_GENERATION_DIR);
        TestUtils.createFile(fileName, sw.toString());
    }

    public static Map<String, KeyStoreData> generateCertificateKeystores(String namespace, ActiveMQArtemis broker, String brokerDN, String clientDN, List<Extension> extensions) {
        LOGGER.info("[TLS] Generating Broker KeyPair, Certificates");
        KeyPair keyPair = CertificateManager.createKeyPairGenerator();
        final X509Certificate brokerCert = CertificateManager.generate(keyPair, CertificateManager.SIGNATURE_ALGORITHM, brokerDN, 30, extensions);
        X500PrivateCredential brokerCredential = CertificateManager.createPrivateCredential(brokerCert, keyPair, DEFAULT_BROKER_ALIAS);
        writeCertificateToFile(brokerCert, Constants.CERTS_GENERATION_DIR + DEFAULT_BROKER_ALIAS + ".crt");

        // Client cert + keypair
        LOGGER.info("[TLS] Generating Client KeyPair, Certificates");
        KeyPair clientKeyPair = CertificateManager.createKeyPairGenerator();
        final X509Certificate clientCert = CertificateManager.generate(clientKeyPair, CertificateManager.SIGNATURE_ALGORITHM, clientDN, 30);
        X500PrivateCredential clientCredential = CertificateManager.createPrivateCredential(clientCert, clientKeyPair, DEFAULT_CLIENT_ALIAS);
        writeCertificateToFile(clientCert, Constants.CERTS_GENERATION_DIR + DEFAULT_CLIENT_ALIAS + ".crt");

        return CertificateManager.createKeystores(brokerCredential, clientCredential, DEFAULT_BROKER_ALIAS, DEFAULT_BROKER_PASSWORD, DEFAULT_CLIENT_ALIAS, DEFAULT_CLIENT_PASSWORD);
    }

    /**
     * Method is useful for debugging purposes, as generation of certificates in debug mode takes very long.
     * @return previously generated *default* keystores and truststores.
     */
    public static Map<String, KeyStoreData> reuseDefaultGeneratedKeystoresFromFiles() {
        Map<String, KeyStoreData> keystores = new HashMap<>();
        if (TestUtils.directoryExists(Constants.CERTS_GENERATION_DIR)) {
            String brokerKeyStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_BROKER_ALIAS + "_keystore.p12";
            String brokerTrustStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_BROKER_ALIAS + "_truststore.p12";
            String clientKeyStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_CLIENT_ALIAS + "_keystore.p12";
            String clientTrustStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_CLIENT_ALIAS + "_truststore.p12";

            try {
                Security.addProvider(new BouncyCastleProvider());
                KeyStore brokerKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
                brokerKeyStore.load(new FileInputStream(brokerKeyStoreFileName), DEFAULT_BROKER_PASSWORD.toCharArray());
                keystores.put(Constants.BROKER_KEYSTORE_ID, new KeyStoreData(brokerKeyStore, brokerKeyStoreFileName, Constants.BROKER_KEYSTORE_ID, DEFAULT_BROKER_PASSWORD));

                KeyStore brokerTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
                brokerTrustStore.load(new FileInputStream(brokerTrustStoreFileName), DEFAULT_BROKER_PASSWORD.toCharArray());
                keystores.put(Constants.BROKER_TRUSTSTORE_ID, new KeyStoreData(brokerTrustStore, brokerTrustStoreFileName, Constants.BROKER_TRUSTSTORE_ID, DEFAULT_BROKER_PASSWORD));

                KeyStore clientKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
                clientKeyStore.load(new FileInputStream(clientKeyStoreFileName), DEFAULT_CLIENT_PASSWORD.toCharArray());
                keystores.put(Constants.CLIENT_KEYSTORE_ID, new KeyStoreData(clientKeyStore, clientKeyStoreFileName, Constants.CLIENT_KEYSTORE_ID, DEFAULT_CLIENT_PASSWORD));

                KeyStore clientTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
                clientTrustStore.load(new FileInputStream(clientTrustStoreFileName), DEFAULT_CLIENT_PASSWORD.toCharArray());
                keystores.put(Constants.CLIENT_TRUSTSTORE_ID, new KeyStoreData(clientTrustStore, clientTrustStoreFileName, Constants.CLIENT_TRUSTSTORE_ID, DEFAULT_CLIENT_PASSWORD));

                return keystores;
            } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException |
                     NoSuchProviderException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.error("[TLS] {} does not exist! Can not reuse it!", Constants.CERTS_GENERATION_DIR);
            throw new RuntimeException("Can not find expected directory and load certificates!");
        }
    }

}
