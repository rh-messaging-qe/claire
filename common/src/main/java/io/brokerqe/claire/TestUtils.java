/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.exception.WaitException;
import io.brokerqe.claire.security.CertificateManager;
import net.datafaker.Faker;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class TestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    public static String getProjectRelativeFile(String projectRelativeFile) {
        return getProjectRelativeFilePath(projectRelativeFile).toString();
    }

    public static Path getProjectRelativeFilePath(String projectRelativeFile) {
        return  Paths.get(Constants.PROJECT_USER_DIR, projectRelativeFile).toAbsolutePath();
    }

    // ========== Random Operations ==========
    public static String getRandomString(int length) {
        if (length > 96) {
            LOGGER.warn("Trimming to max size of 96 chars");
            length = 96;
        }
        StringBuilder randomStr = new StringBuilder(UUID.randomUUID().toString());
        while (randomStr.length() < length) {
            randomStr.append(UUID.randomUUID());
        }
        randomStr = new StringBuilder(randomStr.toString().replace("-", ""));
        return randomStr.substring(0, length);
    }

    public static String generateRandomText(int sizeOfMsgsKb) {
        Faker faker = new Faker();
        String randomText;
        if (sizeOfMsgsKb <= 0) {
            randomText = faker.lorem().paragraph();
        } else {
            randomText = faker.lorem().characters(getCharSizeInKb(sizeOfMsgsKb), true, true);
        }
        return randomText;
    }

    public static String generateRandomName() {
        Faker faker = new Faker();
        return faker.name().firstName().toLowerCase(Locale.ROOT);
    }

    private static int getCharSizeInKb(int kbSize) {
        // Java chars are 2 bytes
        if (kbSize != 1) {
            kbSize = kbSize / 2;
        }
        return kbSize * 1024;
    }

    // ========== Time/retry Operations ==========
    /**
     * Poll the given {@code ready} function every {@code pollIntervalMs} milliseconds until it returns true,
     * or throw a WaitException if it does not return true within {@code timeoutMs} milliseconds.
     * @return The remaining time left until timeout occurs
     * (helpful if you have several calls which need to share a common timeout),
     * */
    public static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready) {
        return waitFor(description, pollIntervalMs, timeoutMs, ready, () -> { });
    }

    public static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready, Runnable onTimeout) {
        LOGGER.debug("Waiting for {}", description);
        long deadline = System.currentTimeMillis() + timeoutMs + Environment.get().getCustomExtraDelay();

        String exceptionMessage = null;
        String previousExceptionMessage = null;

        // in case we are polling every 1s, we want to print exception after x tries, not on the first try
        // for minutes poll interval will 2 be enough
        int exceptionAppearanceCount = Duration.ofMillis(pollIntervalMs).toMinutes() > 0 ? 2 : Math.max((int) (timeoutMs / pollIntervalMs) / 4, 2);
        int exceptionCount = 0;
        int newExceptionAppearance = 0;

        StringWriter stackTraceError = new StringWriter();

        while (true) {
            boolean result;
            try {
                result = ready.getAsBoolean();
            } catch (Exception e) {
                exceptionMessage = e.getMessage();

                if (++exceptionCount == exceptionAppearanceCount && exceptionMessage != null && exceptionMessage.equals(previousExceptionMessage)) {
                    LOGGER.error("While waiting for {} exception occurred: {}", description, exceptionMessage);
                    // log the stacktrace
                    e.printStackTrace(new PrintWriter(stackTraceError));
                } else if (exceptionMessage != null && !exceptionMessage.equals(previousExceptionMessage) && ++newExceptionAppearance == 2) {
                    previousExceptionMessage = exceptionMessage;
                }

                result = false;
            }
            long timeLeft = deadline - System.currentTimeMillis();
            if (result) {
                return timeLeft;
            }
            if (timeLeft <= 0) {
                if (exceptionCount > 1) {
                    LOGGER.error("Exception waiting for {}, {}", description, exceptionMessage);

                    if (!stackTraceError.toString().isEmpty()) {
                        // printing handled stacktrace
                        LOGGER.error(stackTraceError.toString());
                    }
                }
                onTimeout.run();
                WaitException waitException = new WaitException("Timeout after " + timeoutMs + " ms waiting for " + description);
                waitException.printStackTrace();
                throw waitException;
            }
            long sleepTime = Math.min(pollIntervalMs, timeLeft);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("{} not ready, will try again in {} ms ({}ms till timeout)", description, sleepTime, timeLeft);
            }
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                return deadline - System.currentTimeMillis();
            }
        }
    }

    public static void threadSleep(long sleepTime) {
        LOGGER.trace("Sleeping for {}ms", sleepTime);
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== YAML Operations ==========
    public static <T> T configFromYaml(String yamlPath, Class<T> c) {
        return configFromYaml(new File(yamlPath), c);
    }

    public static <T> T configFromYaml(File yamlFile, Class<T> c) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(yamlFile, c);
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void configToYaml(File yamlOutputFile, Object yamlData) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            mapper.writeValue(yamlOutputFile, yamlData);
//            Files.write(copyPath, updatedCO.toString().getBytes());
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== XML Operations ==========
    public static Document readXml(File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(xmlFile);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new ClaireRuntimeException("[XML] Error while reading XML document " + xmlFile.getAbsolutePath(), e);
        }
    }

    public static String getElementByXpathFromXml(String elementXPathExpression, String xmlFile) {
        return getElementByXpathFromXml(elementXPathExpression, new File(xmlFile));
    }

    public static String getElementByXpathFromXml(String elementXPathExpression, File xmlFile) {
        Document xmlDoc = readXml(xmlFile);
        try {
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            XPathExpression expr = xpath.compile(elementXPathExpression);
            return expr.evaluate(xmlDoc);
        } catch (XPathExpressionException e) {
            throw new ClaireRuntimeException("[XML] Can't find specified xpath element", e);
        }
    }

    // ========== Network Operations ==========
    public static void getFileFromUrl(String stringUrl, String outputFile) {
        if (!Files.exists(Paths.get(outputFile))) {
            LOGGER.debug("Downloading {} to {}", stringUrl, outputFile);
            try {
                URL url = new URL(stringUrl);
                ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
                fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.debug("[SKIP] Downloading of {} to {} skipped. File exists", stringUrl, outputFile);
        }
    }
    // Make insecure HTTPS Requests
    // https://stackoverflow.com/questions/2793150/how-to-use-java-net-urlconnection-to-fire-and-handle-http-requests
    public static URLConnection makeInsecureHttpsRequest(String uri) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, CertificateManager.trustAllCertificates, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(CertificateManager.trustAllHostnames);
            return new URL(uri).openConnection();
        } catch (IOException | NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public static URLConnection makeHttpRequest(String uri, String method) {
        try {
            URL url = new URL(uri);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            return con;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== File Operations ==========
    public static void deleteFile(Path fileName) {
        try {
            Files.delete(fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteDirectoryRecursively(Path fileName) {
        try {
            FileUtils.deleteDirectory(fileName.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createFile(String fileName, String content) {
        try {
            LOGGER.trace("Creating file: {}", fileName);
            Files.write(Paths.get(fileName), content.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean directoryExists(String directoryName) {
        Path path = Paths.get(directoryName);
        return Files.exists(path) && Files.isDirectory(path);
    }

    public static void createDirectory(String directoryName) {
        try {
            LOGGER.trace("Creating directory {}", directoryName);
            Files.createDirectories(Paths.get(directoryName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path createTestTemporaryDir(String name, String baseDirName) {
        String dirName = baseDirName + Constants.FILE_SEPARATOR + name;
        TestUtils.createDirectory(dirName);
        return Paths.get(dirName);
    }

    public static void copyDirectoryFlat(String source, String target) {
        try {
            Path sourceDir = Paths.get(source);
            Path targetDir = Paths.get(target);
            Files.copy(sourceDir, targetDir, StandardCopyOption.REPLACE_EXISTING);
            for (File file : sourceDir.toFile().listFiles()) {
                Path sourcePath = file.toPath();
                Files.copy(sourcePath, targetDir.resolve(sourcePath.getFileName()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyFile(String source, String target) {
        try {
            Files.copy(Path.of(source), Path.of(target), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path findFile(String directory, String filename) {
        try (Stream<Path> files = Files.walk(Paths.get(directory))) {
            return files.filter(file ->
                    file.toAbsolutePath().endsWith(filename)
            ).findFirst().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String replaceFileContent(String filePath, String toReplace, String replaceWith) {
        String newFilePath = filePath + "_tmpFile" + TestUtils.getRandomString(3);
        String data = readFileContent(new File(filePath));
        data = data.replace(toReplace, replaceWith);
        TestUtils.createFile(newFilePath, data);
        return newFilePath;
    }

    public static String readFileContent(File file) {
        try {
            return FileUtils.readFileToString(file, Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileContentAsBase64(String file) {
        return getEncodedBase64String(readFileContent(Paths.get(file).toFile()));
    }

    public static String getEncodedBase64String(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes());
    }

    public static String getDecodedBase64String(String data) {
        return new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
    }

    /** Untar an input file into an output file.
     * The output file is created in the output folder, having the same name
     * as the input file, minus the '.tar' extension.
     * Copied from <a href="https://stackoverflow.com/a/7556307">stackoverflow question</a>
     *
     * @param inputFile     the input .tar file
     * @param outputDir     the output directory file.
     *
     */
    public static void unTar(final String inputFile, final String outputDir) {
        Path input = Paths.get(inputFile);
        Path output = Paths.get(outputDir);
        LOGGER.debug("Untaring {} to dir {}.", input, output);
        try {
            final InputStream is = Files.newInputStream(input);
            final TarArchiveInputStream tarInputStream = (TarArchiveInputStream) new ArchiveStreamFactory()
                    .createArchiveInputStream("tar", is);
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarInputStream.getNextEntry()) != null) {
                final File outputFile = new File(output.toFile(), entry.getName());
                if (entry.isDirectory()) {
                    if (!outputFile.exists()) {
                        if (!outputFile.mkdirs()) {
                            String errMsg = String.format("Couldn't create directory %s.", outputFile.getAbsolutePath());
                            LOGGER.error(errMsg);
                            throw new ClaireRuntimeException(errMsg);
                        }
                    }
                } else {
                    final OutputStream outputFileStream = Files.newOutputStream(outputFile.toPath());
                    IOUtils.copy(tarInputStream, outputFileStream);
                    outputFileStream.close();
                }
            }
            tarInputStream.close();
        } catch (IOException | ArchiveException e) {
            String errMsg = String.format("Error on extracting file with tar: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public static void unzip(String archivePath, String unarchivePath) {
        try {
            new ZipFile(Paths.get(archivePath).toFile()).extractAll(unarchivePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String convertMapToJson(Map<String, String> perfOutput) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(perfOutput);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}