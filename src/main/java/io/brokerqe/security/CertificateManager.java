/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
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
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
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

@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity"})
public class CertificateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateManager.class);
    protected static final String KEY_PAIR_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    public static final String KEYSTORE_TYPE_JKS = "JKS";
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

    public static String generateArtemisCloudDN(String ou, String cn) {
        return "C=CZ, L=Brno, O=ArtemisCloud, OU=" + ou + ", CN=" + cn;
    }

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, final int days) {
        return generate(keyPair, hashAlgorithm, distinguishedName, days, null);
    }

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, final int days, List<Extension> extensions) {
        final Instant now = Instant.now();
        return generate(keyPair, hashAlgorithm, distinguishedName, Date.from(now.minus(Duration.ofDays(1L))), Date.from(now.plus(Duration.ofDays(days))), null, null);
    }

    public static X509Certificate generate(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, Date validNotBefore, Date validNotAfter, List<Extension> extensions, CertificateData issuerCD) {
        final Instant now = Instant.now();
        final ContentSigner contentSigner;
        final X500Name issuer;
        List<Extension> applyExtentions;

        try {
            List<Extension> defaultExtensions = new ArrayList<Extension>();
            defaultExtensions.add(new Extension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()).getEncoded()));

            if (issuerCD != null) {
                defaultExtensions.add(new Extension(Extension.keyUsage, true, new DEROctetString(new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature))));
//                defaultExtensions.add(new Extension(Extension.basicConstraints, true, (new BasicConstraints(false)).getEncoded()));

                issuer = new X500Name(RFC4519Style.INSTANCE, issuerCD.getCertificate().getSubjectX500Principal().getName());
                contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(issuerCD.getKeyPair().getPrivate());
                defaultExtensions.add(new Extension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(issuerCD.getKeyPair().getPublic()).getEncoded()));
            } else {
                // self signed cert
                issuer = new X500Name(RFC4519Style.INSTANCE, distinguishedName);
                contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());
                defaultExtensions.add(new Extension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()).getEncoded()));
            }

            if (extensions == null) {
                applyExtentions = defaultExtensions;
            } else {
                applyExtentions = new ArrayList<>(defaultExtensions);
                applyExtentions.addAll(extensions);
            }

            final X500Name subject = new X500Name(RFC4519Style.INSTANCE, distinguishedName);
            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    issuer,
                    BigInteger.valueOf(now.toEpochMilli()),
                    validNotBefore,
                    validNotAfter,
                    subject,
                    keyPair.getPublic());

            for (Extension extension : applyExtentions) {
                certificateBuilder.addExtension(extension);
            }

            return new JcaX509CertificateConverter()
                    .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
        } catch (OperatorCreationException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509Certificate generateCA(final KeyPair keyPair, final String hashAlgorithm, final String distinguishedName, Date validNotBefore, Date validNotAfter, CertificateData issuerCD) {
        final Instant now = Instant.now();
        final ContentSigner contentSigner;
        final X500Name issuer;

        try {
            List<Extension> extensions = new ArrayList<>();
            extensions.add(new Extension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()).getEncoded()));
            extensions.add(new Extension(Extension.keyUsage, true, new DEROctetString(new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))));
            extensions.add(new Extension(Extension.basicConstraints, true, (new BasicConstraints(true)).getEncoded()));
            if (issuerCD != null) {
                // second level CA
                issuer = new X500Name(RFC4519Style.INSTANCE, issuerCD.getCertificate().getSubjectX500Principal().getName("RFC2253"));
                contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(issuerCD.getKeyPair().getPrivate());
                extensions.add(new Extension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(issuerCD.getKeyPair().getPublic()).getEncoded()));
            } else {
                // self-signed ROOT CA cert
                issuer = new X500Name(RFC4519Style.INSTANCE, distinguishedName);
                contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());
//                extensions.add(new Extension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()).getEncoded()));
            }

            final X500Name subject = new X500Name(RFC4519Style.INSTANCE, distinguishedName);
            X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                    issuer,
                    BigInteger.valueOf(now.toEpochMilli()),
                    validNotBefore,
                    validNotAfter,
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

    /**
     * Creates keystore and truststore with provided password and certificateData.
     * Creates KeyStoreData using keys in following format "<alias>.ks" and "<alias>.ts".
     * @param certificateData
     * @param keystorePassword
     * @return Map of ts/ks keys with related KeyStoreData objects.
     */
    public static Map<String, KeyStoreData> createEntityKeystores(CertificateData certificateData, String keystorePassword) {
        Map<String, KeyStoreData> keystores = new HashMap<>();
        String keyStoreFileName = Constants.CERTS_GENERATION_DIR + certificateData.getAlias() + "_keystore.jks";
        String trustStoreFileName = Constants.CERTS_GENERATION_DIR + certificateData.getAlias() + "_truststore.jks";
        String keyStoreDataName = certificateData.getAlias() + ".ks";
        String trustStoreDataName = certificateData.getAlias() + ".ts";
        TestUtils.createDirectory(Constants.CERTS_GENERATION_DIR);

        try {
            LOGGER.info("[TLS] Creating {} keystore", certificateData.getAlias());
            KeyStore clientKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
            clientKeyStore.load(null, keystorePassword.toCharArray());
            clientKeyStore.setCertificateEntry(certificateData.getAlias(), certificateData.getCertificate());
            clientKeyStore.setKeyEntry(certificateData.getAlias(), certificateData.getKeyPair().getPrivate(), keystorePassword.toCharArray(),
                    new java.security.cert.Certificate[]{certificateData.getCertificate()});
            clientKeyStore.store(new FileOutputStream(keyStoreFileName), keystorePassword.toCharArray());
            keystores.put(keyStoreDataName, new KeyStoreData(clientKeyStore, keyStoreFileName, keyStoreDataName, keystorePassword, certificateData));

            LOGGER.info("[TLS] Creating {} truststore", certificateData.getAlias());
            KeyStore clientTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
            clientTrustStore.load(null, keystorePassword.toCharArray());
            // we have none to trust. Add trust later or reuse existing truststore
//            clientTrustStore.setKeyEntry(brokerAlias, brokerCredential.getPrivateKey(), brokerPassword.toCharArray(), new java.security.cert.Certificate[]{brokerCredential.getCertificate()});
            clientTrustStore.store(new FileOutputStream(trustStoreFileName), keystorePassword.toCharArray());
            keystores.put(trustStoreDataName, new KeyStoreData(clientTrustStore, trustStoreFileName, trustStoreDataName, keystorePassword, certificateData));

            LOGGER.info("[TLS] Created {}, {} with password {}", keyStoreFileName, trustStoreFileName, keystorePassword);
            return keystores;
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, KeyStoreData> createKeystores(CertificateData brokerCertificateData, CertificateData clientCertificateData,
                                                            String brokerAlias, String brokerPassword, String clientAlias, String clientPassword) {
        Map<String, KeyStoreData> keystores = new HashMap<>();
        String brokerKeyStoreFileName = Constants.CERTS_GENERATION_DIR + brokerAlias + "_keystore.jks";
        String brokerTrustStoreFileName = Constants.CERTS_GENERATION_DIR + brokerAlias + "_truststore.jks";
        String clientKeyStoreFileName = Constants.CERTS_GENERATION_DIR + clientAlias + "_keystore.jks";
        String clientTrustStoreFileName = Constants.CERTS_GENERATION_DIR + clientAlias + "_truststore.jks";
        TestUtils.createDirectory(Constants.CERTS_GENERATION_DIR);

        try {
            LOGGER.info("[TLS] Creating Broker keystore");
            KeyStore brokerKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
            brokerKeyStore.load(null, brokerPassword.toCharArray());
            brokerKeyStore.setCertificateEntry(brokerAlias, brokerCertificateData.getCertificate());
            brokerKeyStore.setKeyEntry(brokerAlias, brokerCertificateData.getKeyPair().getPrivate(), brokerPassword.toCharArray(),
                    new java.security.cert.Certificate[]{brokerCertificateData.getCertificate()});
            brokerKeyStore.store(new FileOutputStream(brokerKeyStoreFileName), brokerPassword.toCharArray());

            keystores.put(Constants.BROKER_KEYSTORE_ID, new KeyStoreData(brokerKeyStore, brokerKeyStoreFileName, Constants.BROKER_KEYSTORE_ID, brokerPassword, brokerCertificateData));

            LOGGER.info("[TLS] Creating Broker truststore");
            KeyStore brokerTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
            brokerTrustStore.load(null, brokerPassword.toCharArray());
            brokerTrustStore.setCertificateEntry(clientAlias, clientCertificateData.getCertificate());
            brokerTrustStore.setKeyEntry(clientAlias, clientCertificateData.getKeyPair().getPrivate(), clientPassword.toCharArray(), new java.security.cert.Certificate[]{clientCertificateData.getCertificate()});
            brokerTrustStore.store(new FileOutputStream(brokerTrustStoreFileName), brokerPassword.toCharArray());
            keystores.put(Constants.BROKER_TRUSTSTORE_ID, new KeyStoreData(brokerTrustStore, brokerTrustStoreFileName, Constants.BROKER_TRUSTSTORE_ID, brokerPassword, brokerCertificateData));

            LOGGER.info("Created {}, {} with password {}", brokerKeyStoreFileName, brokerTrustStoreFileName, brokerPassword);

            if (clientCertificateData != null && clientAlias != null && clientPassword != null) {
                LOGGER.info("[TLS] Creating Client keystore");
                KeyStore clientKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
                clientKeyStore.load(null, clientPassword.toCharArray());
                clientKeyStore.setCertificateEntry(clientAlias, clientCertificateData.getCertificate());
                clientKeyStore.setKeyEntry(clientAlias, clientCertificateData.getKeyPair().getPrivate(), clientPassword.toCharArray(),
                        new java.security.cert.Certificate[]{clientCertificateData.getCertificate()});
                clientKeyStore.store(new FileOutputStream(clientKeyStoreFileName), clientPassword.toCharArray());
                keystores.put(Constants.CLIENT_KEYSTORE_ID, new KeyStoreData(clientKeyStore, clientKeyStoreFileName, Constants.CLIENT_KEYSTORE_ID, clientPassword, clientCertificateData));

                LOGGER.info("[TLS] Creating Client truststore");
                KeyStore clientTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
                clientTrustStore.load(null, clientPassword.toCharArray());
                clientTrustStore.setCertificateEntry(brokerAlias, brokerCertificateData.getCertificate());
                clientTrustStore.setKeyEntry(brokerAlias, brokerCertificateData.getKeyPair().getPrivate(), brokerPassword.toCharArray(), new java.security.cert.Certificate[]{brokerCertificateData.getCertificate()});
                clientTrustStore.store(new FileOutputStream(clientTrustStoreFileName), clientPassword.toCharArray());
                keystores.put(Constants.CLIENT_TRUSTSTORE_ID, new KeyStoreData(clientTrustStore, clientTrustStoreFileName, Constants.CLIENT_TRUSTSTORE_ID, clientPassword, clientCertificateData));

                LOGGER.info("Created {}, {} with password {}", clientKeyStoreFileName, clientTrustStoreFileName, clientPassword);
            }
            return keystores;
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyStoreData createEmptyKeyStore(String keyStoreType, String keystorePassword, String keystoreFilename) {
        KeyStoreData keystore;
        try {
            LOGGER.info("[TLS] Creating empty keystore/truststore");
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
            keyStore.load(null, keystorePassword.toCharArray());
            keyStore.store(new FileOutputStream(keystoreFilename), keystorePassword.toCharArray());
//            keystores.put(keyStoreDataName, new KeyStoreData(clientKeyStore, keyStoreFileName, keyStoreDataName, keystorePassword, certificateData));
            keystore = new KeyStoreData(keyStore, keystoreFilename, keyStoreType, keystorePassword);

            LOGGER.info("Created keystore {} with password {}", keystoreFilename, keystorePassword);
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
        return keystore;
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
        String domain = kubeClient.getPlatformIngressDomainUrl(namespace);

        ASN1EncodableVector sanNames = new ASN1EncodableVector();
        int size = broker.getSpec().getDeploymentPlan().getSize();
        for (int i = 0; i < size; i++) {
            for (String acceptorName : serviceNames) {
                sanNames.add(new GeneralName(GeneralName.dNSName, appName + "-" + acceptorName + "-" + i + "-" + domain));
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

    public static Map<String, KeyStoreData> generateDefaultCertificateKeystores(String namespace, ActiveMQArtemis broker, String brokerDN, String clientDN, List<Extension> extensions, CertificateData issuer) {
        LOGGER.info("[TLS] Generating Broker KeyPair, Certificates");
        CertificateData brokerCertData = new CertificateData(DEFAULT_BROKER_ALIAS, brokerDN, extensions, 30, issuer);
        writeCertificateToFile(brokerCertData.getCertificate(), Constants.CERTS_GENERATION_DIR + DEFAULT_BROKER_ALIAS + ".crt");

        // Client cert + keypair
        LOGGER.info("[TLS] Generating Client KeyPair, Certificates");
        CertificateData clientCertData = new CertificateData(DEFAULT_CLIENT_ALIAS, clientDN);
        writeCertificateToFile(clientCertData.getCertificate(), Constants.CERTS_GENERATION_DIR + DEFAULT_CLIENT_ALIAS + ".crt");

        return CertificateManager.createKeystores(brokerCertData, clientCertData,
                DEFAULT_BROKER_ALIAS, DEFAULT_BROKER_PASSWORD, DEFAULT_CLIENT_ALIAS, DEFAULT_CLIENT_PASSWORD);
    }

    public static String readCertificateFromFile(String certificatePath) {
        try {
            byte[] content = Files.readAllBytes(Paths.get(certificatePath));
            return Base64.getEncoder().encodeToString(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509Certificate getCertificateFromSecret(Secret secret, String dataKey) {
        String caCert = secret.getData().get(dataKey);
        byte[] decoded = Base64.getDecoder().decode(caCert);
        X509Certificate cacert = null;
        try {
            cacert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(decoded));
        } catch (CertificateException e) {
            LOGGER.error("[{}] Unable to create certificate entry from provided secret {} and key {}",
                    secret.getMetadata().getNamespace(), secret.getMetadata().getName(), dataKey);
            e.printStackTrace();
        }
        return cacert;
    }

    public static Secret createKeystoreSecret(KubeClient kubeClient, String secretName, Map<String, KeyStoreData> keystores, String alias) {
        Map<String, String> tlsSecret = new HashMap<>();
        KeyStoreData ksdKeystore = keystores.get(alias + ".ks");
        KeyStoreData ksdTruststore = keystores.get(alias + ".ts");

        tlsSecret.put(ksdKeystore.getIdentifier(), ksdKeystore.getEncodedKeystoreFileData());
        tlsSecret.put(Constants.KEY_KEYSTORE_PASSWORD, ksdKeystore.getEncodedPassword());

        tlsSecret.put(ksdTruststore.getIdentifier(), ksdTruststore.getEncodedKeystoreFileData());
        tlsSecret.put(Constants.KEY_TRUSTSTORE_PASSWORD, ksdTruststore.getEncodedPassword());
        return kubeClient.createSecretEncodedData(kubeClient.getNamespace(), secretName, tlsSecret, true);
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

    public static void addToTruststore(KeyStoreData keyStoreData, String tlsCert, String alias) {
        addToTruststore(keyStoreData, tlsCert, null, alias);
    }

    public static void addToTruststore(KeyStoreData keyStoreData, X509Certificate certificate, String alias) {
        addToTruststore(keyStoreData, null, certificate, alias);
    }

    public static void addToTruststore(KeyStoreData keyStoreData, String stringTlsCert, X509Certificate certificate, String alias) {
        X509Certificate trustedCertificate;
        try {
            if (stringTlsCert != null) {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                trustedCertificate = (X509Certificate)
                        certificateFactory.generateCertificate(new ByteArrayInputStream(
                                TestUtils.getDecodedBase64String(stringTlsCert).getBytes()));
            } else {
                trustedCertificate = certificate;
            }
            KeyStore brokerTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
            brokerTrustStore.load(new FileInputStream(keyStoreData.getKeyStorePath()), keyStoreData.getPassword().toCharArray());
            brokerTrustStore.setCertificateEntry(alias, trustedCertificate);
            brokerTrustStore.store(new FileOutputStream(keyStoreData.getKeyStorePath()), keyStoreData.getPassword().toCharArray());
            keyStoreData.setKeyStore(brokerTrustStore);
            LOGGER.info("[TLS] Add trusted certificate {} to {} truststore", alias, keyStoreData.getIdentifier());
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static Secret createBrokerTruststoreSecretWithOpenshiftRouter(KubeClient kubeClient, String namespace,
                                                                         String brokerTruststoreSecretName, String brokerTruststoreFileName) {
        Secret routerSecret = kubeClient.getRouterDefaultSecret();
        X509Certificate routerCert = CertificateManager.getCertificateFromSecret(routerSecret, "tls.crt");
        KeyStoreData brokerTrustStore = CertificateManager.createEmptyKeyStore(Constants.BROKER_TRUSTSTORE_ID, CertificateManager.DEFAULT_BROKER_PASSWORD, brokerTruststoreFileName);
        CertificateManager.addToTruststore(brokerTrustStore, routerCert, "openshift-router");
        return kubeClient.createSecretEncodedData(namespace, brokerTruststoreSecretName, Map.of(Constants.BROKER_TRUSTSTORE_ID, brokerTrustStore.getEncodedKeystoreFileData()));
    }

    /**
     * Method is useful for debugging purposes, as generation of certificates in debug mode takes very long.
     * @return previously generated *default* keystores and truststores.
     */
    public static Map<String, KeyStoreData> reuseDefaultGeneratedKeystoresFromFiles() {
        Map<String, KeyStoreData> keystores = new HashMap<>();
        if (TestUtils.directoryExists(Constants.CERTS_GENERATION_DIR)) {
            String brokerKeyStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_BROKER_ALIAS + "_keystore.jks";
            String brokerTrustStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_BROKER_ALIAS + "_truststore.jks";
            String clientKeyStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_CLIENT_ALIAS + "_keystore.jks";
            String clientTrustStoreFileName = Constants.CERTS_GENERATION_DIR + DEFAULT_CLIENT_ALIAS + "_truststore.jks";

            try {
                Security.addProvider(new BouncyCastleProvider());
                KeyStore brokerKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
                brokerKeyStore.load(new FileInputStream(brokerKeyStoreFileName), DEFAULT_BROKER_PASSWORD.toCharArray());
                keystores.put(Constants.BROKER_KEYSTORE_ID, new KeyStoreData(brokerKeyStore, brokerKeyStoreFileName, Constants.BROKER_KEYSTORE_ID, DEFAULT_BROKER_PASSWORD));

                KeyStore brokerTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
                brokerTrustStore.load(new FileInputStream(brokerTrustStoreFileName), DEFAULT_BROKER_PASSWORD.toCharArray());
                keystores.put(Constants.BROKER_TRUSTSTORE_ID, new KeyStoreData(brokerTrustStore, brokerTrustStoreFileName, Constants.BROKER_TRUSTSTORE_ID, DEFAULT_BROKER_PASSWORD));

                KeyStore clientKeyStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
                clientKeyStore.load(new FileInputStream(clientKeyStoreFileName), DEFAULT_CLIENT_PASSWORD.toCharArray());
                keystores.put(Constants.CLIENT_KEYSTORE_ID, new KeyStoreData(clientKeyStore, clientKeyStoreFileName, Constants.CLIENT_KEYSTORE_ID, DEFAULT_CLIENT_PASSWORD));

                KeyStore clientTrustStore = KeyStore.getInstance(KEYSTORE_TYPE_JKS);
                clientTrustStore.load(new FileInputStream(clientTrustStoreFileName), DEFAULT_CLIENT_PASSWORD.toCharArray());
                keystores.put(Constants.CLIENT_TRUSTSTORE_ID, new KeyStoreData(clientTrustStore, clientTrustStoreFileName, Constants.CLIENT_TRUSTSTORE_ID, DEFAULT_CLIENT_PASSWORD));

                return keystores;
            } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.error("[TLS] {} does not exist! Can not reuse it!", Constants.CERTS_GENERATION_DIR);
            throw new RuntimeException("Can not find expected directory and load certificates!");
        }
    }
}
