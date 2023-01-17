/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class TestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

    public static String getRandomString(int length) {
        if (length > 96) {
            LOGGER.warn("Trimming to max size of 96 chars");
            length = 96;
        }
        StringBuilder randomStr = new StringBuilder(UUID.randomUUID().toString());
        while (randomStr.length() < length) {
            randomStr.append(UUID.randomUUID().toString());
        }
        randomStr = new StringBuilder(randomStr.toString().replace("-", ""));
        return randomStr.substring(0, length);
    }

    /**
     * Poll the given {@code ready} function every {@code pollIntervalMs} milliseconds until it returns true,
     * or throw a WaitException if it doesn't returns true within {@code timeoutMs} milliseconds.
     * @return The remaining time left until timeout occurs
     * (helpful if you have several calls which need to share a common timeout),
     * */
    public static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready) {
        return waitFor(description, pollIntervalMs, timeoutMs, ready, () -> { });
    }

    public static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready, Runnable onTimeout) {
        LOGGER.debug("Waiting for {}", description);
        long deadline = System.currentTimeMillis() + timeoutMs;

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

    public static String getOperatorControllerManagerName(Path yamlFile) {
        Deployment operatorCODeployment = configFromYaml(yamlFile.toFile(), Deployment.class);
        return operatorCODeployment.getMetadata().getName();
    }

    public static String updateClusterRoleBindingFileNamespace(Path yamlFile, String namespace) {
        String newCRBFileName = "cluster_role_binding_" + TestUtils.getRandomString(3) + ".yaml";
        Path copyPath = Paths.get(yamlFile.toAbsolutePath().toString().replace("cluster_role_binding.yaml", newCRBFileName));
        ClusterRoleBinding updatedCRB = configFromYaml(yamlFile.toFile(), ClusterRoleBinding.class);
        updatedCRB.getSubjects().get(0).setNamespace(namespace);
        configToYaml(copyPath.toFile(), updatedCRB);
        return copyPath.toString();
    }

    public static String updateOperatorFileWatchNamespaces(Path yamlFile, List<String> watchedNamespaces) {
        String newCOFileName = "operator_cw_" + TestUtils.getRandomString(3) + ".yaml";
        Path copyPath = Paths.get(yamlFile.toAbsolutePath().toString().replace("operator.yaml", newCOFileName));

        // Load Deployment from yaml file
        Deployment updatedCO = configFromYaml(yamlFile.toFile(), Deployment.class);

        // Get and update WATCH_NAMESPACE value
        updatedCO.getSpec().getAdditionalProperties().get("containers");
        List<EnvVar> envVars = updatedCO.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar watchNamespaceEV = envVars.stream().filter(envVar -> envVar.getName().equals("WATCH_NAMESPACE")).findFirst().get();
        watchNamespaceEV.setValue(String.join(",", watchedNamespaces));
        watchNamespaceEV.setValueFrom(null);
        updatedCO.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

        // Write updated Deployment into file
        // mapper.writeValue(copyPath.toFile(), updatedCO);
        configToYaml(copyPath.toFile(), updatedCO);
        return copyPath.toString();
    }

    public static void deleteFile(String fileName) {
        try {
            Files.delete(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createFile(String fileName, String content) {
        try {
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
            Files.createDirectories(Paths.get(directoryName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public static void updateImagesInOperatorFile(Path operatorFile, String imageType, String imageUrl, String version) {
        List<EnvVar> envVars;
        String imageTypeVersion = null;
        if (version != null) {
            imageTypeVersion = imageType + version.replace(".", "");
        }
        Deployment operator = configFromYaml(operatorFile.toFile(), Deployment.class);

        if (imageType.equals(Constants.OPERATOR_IMAGE_OPERATOR_PREFIX)) {
            operator.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageUrl);
        }

        if (imageType.equals(Constants.BROKER_IMAGE_OPERATOR_PREFIX) || imageType.equals(Constants.BROKER_INIT_IMAGE_OPERATOR_PREFIX)) {
            envVars = operator.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
            String finalImageTypeVersion = imageTypeVersion;
            EnvVar brokerImageEV = envVars.stream().filter(envVar -> envVar.getName().equals(finalImageTypeVersion)).findFirst().get();
            brokerImageEV.setValue(imageUrl);
        }

        configToYaml(operatorFile.toFile(), operator);

    }
}
