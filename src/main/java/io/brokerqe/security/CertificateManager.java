/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.KubernetesPlatform;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500PrivateCredential;

// Original Certificate generation code in https://github.com/misterpki/selfsignedcert/
// https://docs.oracle.com/javase/9/docs/specs/security/standard-names.html#keypairgenerator-algorithms
// https://www.bouncycastle.org/specifications.html
@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity"})
public class CertificateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateManager.class);
    protected static final String KEY_PAIR_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA512withRSA";
    public static final String KEYSTORE_TYPE_PKCS12 = "PKCS12";
    final static String DEFAULT_BROKER_ALIAS = "brokerUser";
    final static String DEFAULT_BROKER_PASSWORD = "brokerPass";
    final static String DEFAULT_CLIENT_ALIAS = "clientUser";
    final static String DEFAULT_CLIENT_PASSWORD = "clientPass";

    public static TrustManager[] trustAllCertificates = new TrustManager[]{
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(
                    X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(
                    X509Certificate[] certs, String authType) {
            }
        }
    };
    public static HostnameVerifier trustAllHostnames = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    public static String generateDefaultBrokerDN(KubernetesClient kubernetesClient) {
        // Make sure you use correct kubernetesClient when using multi-cluster deployment
        return generateDefaultBrokerDN(kubernetesClient, "Broker");
    }
    public static String generateDefaultBrokerDN(KubernetesClient kubernetesClient, String ou) {
        return "C=CZ, L=Brno, O=ArtemisCloud, OU=" + ou + ", CN=" + kubernetesClient.getMasterUrl().getHost().replace("api", "*");
    }

    public static String generateDefaultClientDN(KubernetesClient kubernetesClient) {
        return generateDefaultClientDN(kubernetesClient, "Client");
    }
    public static String generateDefaultClientDN(KubernetesClient kubernetesClient, String ou) {
        return "C=CZ, L=Brno, O=ArtemisCloud, OU=" + ou + ", CN=" + kubernetesClient.getMasterUrl().getHost().replace("api", "*");
    }

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, final int days) {
        return generate(keyPair, hashAlgorithm, distinguishedName, days, null);
    }

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, final int days, List<Extension> extensions) {
        final Instant now = Instant.now();
        final Date notBefore = Date.from(now.minus(Duration.ofDays(1L)));
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

    public static Extension generateSanDnsNames(KubeClient kubeClient, ActiveMQArtemis broker, List<String> serviceNames) {
        // DNS:$APPLICATION_NAME-$ACCEPTOR-$ORDINAL-svc-rte-$NAMESPACE.$DOMAIN_NAME"
        // Route
        // artemis-broker-my-amqp-0-svc-rte    artemis-broker-my-amqp-0-svc-rte-namespacename.apps.lala.amq-broker-qe.my-host.com
        // Ingress
        // artemis-broker-my-amqp-0-svc-ing    artemis-broker-my-amqp-0-svc-ing.apps.artemiscloud.io
        Extension sanExtension;
        String appName = broker.getMetadata().getName();
        String namespace = broker.getMetadata().getNamespace();

        // platform specific
        String svc;
        String domain;
        if (kubeClient.getKubernetesPlatform().equals(KubernetesPlatform.KUBERNETES)) {
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
            for (String acceptorName : serviceNames) {
                sanNames.add(new GeneralName(GeneralName.dNSName, appName + "-" + acceptorName + "-" + i + "-" + svc + "-" + domain));
//                sanNames.add(new GeneralName(GeneralName.dNSName, "*.app-services-dev.net"));
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

    public static void writeCertificateToFile(X509Certificate certificate, String fileName) {
        StringWriter sw = new StringWriter();
        try (JcaPEMWriter jpw = new JcaPEMWriter(sw)) {
            jpw.writeObject(certificate);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        TestUtils.createDirectory(Constants.CERTS_GENERATION_DIR);
        TestUtils.createFile(fileName, sw.toString());
    }

    public static String readCertificateFromFile(String certificatePath) {
        try {
            byte[] content = Files.readAllBytes(Paths.get(certificatePath));
            return Base64.getEncoder().encodeToString(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public static Secret createClientKeystoreSecret(KubeClient kubeClient, String secretName, Map<String, KeyStoreData> keystores) {
        Map<String, String> clientTlsSecret = new HashMap<>();
        clientTlsSecret.put(Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getEncodedKeystoreFileData());
        clientTlsSecret.put(Constants.KEY_KEYSTORE_PASSWORD, keystores.get(Constants.CLIENT_KEYSTORE_ID).getEncodedPassword());
        clientTlsSecret.put(Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getEncodedKeystoreFileData());
        clientTlsSecret.put(Constants.KEY_TRUSTSTORE_PASSWORD, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getEncodedPassword());
        return kubeClient.createSecretEncodedData(kubeClient.getNamespace(), secretName, clientTlsSecret, true);
    }

    public static Secret createBrokerKeystoreSecret(KubeClient kubeClient, String secretName, Map<String, KeyStoreData> keystores) {
        Map<String, String> brokerTlsSecret = new HashMap<>();
        brokerTlsSecret.put(Constants.BROKER_KEYSTORE_ID, keystores.get(Constants.BROKER_KEYSTORE_ID).getEncodedKeystoreFileData());
        brokerTlsSecret.put(Constants.KEY_KEYSTORE_PASSWORD, keystores.get(Constants.BROKER_KEYSTORE_ID).getEncodedPassword());
        // broker expects `client.ts` key
        brokerTlsSecret.put(Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.BROKER_TRUSTSTORE_ID).getEncodedKeystoreFileData());
        brokerTlsSecret.put(Constants.KEY_TRUSTSTORE_PASSWORD, keystores.get(Constants.BROKER_TRUSTSTORE_ID).getEncodedPassword());
        return kubeClient.createSecretEncodedData(kubeClient.getNamespace(), secretName, brokerTlsSecret, true);
    }

    public static Secret createConsoleKeystoreSecret(KubeClient kubeClient, String secretName, Map<String, KeyStoreData> keystores) {
        Map<String, String> consoleTlsSecret = new HashMap<>();
        consoleTlsSecret.put(Constants.BROKER_KEYSTORE_ID, keystores.get(Constants.BROKER_KEYSTORE_ID).getEncodedKeystoreFileData());
        // broker expects `client.ts` key
        consoleTlsSecret.put(Constants.KEY_KEYSTORE_PASSWORD, keystores.get(Constants.BROKER_KEYSTORE_ID).getEncodedPassword());
        consoleTlsSecret.put(Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getEncodedKeystoreFileData());
        consoleTlsSecret.put(Constants.KEY_TRUSTSTORE_PASSWORD, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getEncodedPassword());
        return kubeClient.createSecretEncodedData(kubeClient.getNamespace(), secretName, consoleTlsSecret, true);
    }

    public static KeyStoreData addToTruststore(KeyStoreData keyStoreData, String tlsCert, String alias) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate trustedCertificate = (X509Certificate)
                    certificateFactory.generateCertificate(new ByteArrayInputStream(getDecodedString(tlsCert).getBytes()));

            LOGGER.info("[TLS] Add trusted certificate to Broker truststore");
            KeyStore brokerTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_PKCS12, BouncyCastleProvider.PROVIDER_NAME);
            brokerTrustStore.load(new FileInputStream(keyStoreData.getKeyStorePath()), keyStoreData.getPassword().toCharArray());
            brokerTrustStore.setCertificateEntry(alias, trustedCertificate);
            brokerTrustStore.store(new FileOutputStream(keyStoreData.getKeyStorePath()), keyStoreData.getPassword().toCharArray());
            keyStoreData.setKeyStore(brokerTrustStore);
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException |
                 NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
        return keyStoreData;
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

    public static String getEncodedString(String password) {
        return Base64.getEncoder().encodeToString(password.getBytes());
    }

    public static String getDecodedString(String data) {
        return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
    }
}
