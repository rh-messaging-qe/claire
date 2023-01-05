/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.brokerqe.separator.TestSeparator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.joda.time.DateTime;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class TestDataCollector implements TestWatcher, TestExecutionExceptionHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(TestDataCollector.class);
    KubeClient kubeClient;

    static String archiveDir = null;

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (throwable instanceof TestAbortedException) {
            return;
        }
        String testClass = context.getRequiredTestClass().getName();
        String testMethod = context.getRequiredTestMethod().getName();

        Object testInstance = context.getRequiredTestInstance();
        List<String> testNamespaces = getTestNamespaces(testInstance);
        Environment testEnv = (Environment) getTestInstanceDeclaredField(testInstance, "testEnvironment");
        archiveDir = testEnv.getLogsDirLocation() + Constants.FILE_SEPARATOR + createArchiveName();
        kubeClient = (KubeClient) getTestInstanceDeclaredField(testInstance, "client");

        LOGGER.info("Will gather data from namespace: {}", String.join(" ", testNamespaces));
        for (String testNamespace : testNamespaces) {
            String archiveDirName = archiveDir + Constants.FILE_SEPARATOR + testClass + "." + testMethod + Constants.FILE_SEPARATOR + testNamespace;

            LOGGER.info("[{}] Gathering debug data for failed {}#{} into {}", testNamespace, testClass, testMethod, archiveDirName);
            TestUtils.createDirectory(archiveDirName);
            collectTestData(testNamespace, archiveDirName);
        }

    }

    private Object getTestInstanceDeclaredField(Object testInstance, String fieldName) {
        Field field = null;
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(testInstance);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                LOGGER.debug("DeclaredField {} not found in class {}, trying superclass()", fieldName, clazz);
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private ArtemisCloudClusterOperator getOperatorDifferentNamespace() {
        LOGGER.error("Not implemented yet!");
        return null;
    }

    private static String createArchiveName() {
        LocalDateTime date = LocalDateTime.now();
        String dateFormat = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));
        String archiveName = dateFormat;
        LOGGER.debug(archiveName);
        return archiveName;
    }

    private void collectTestData(String namespace, String archiveLocation) {
        LOGGER.debug("Current ns {}", kubeClient.getNamespace());

        List<Deployment> deployments = kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).list().getItems();
        List<StatefulSet> statefulSets = kubeClient.getKubernetesClient().apps().statefulSets().inNamespace(namespace).list().getItems();
        List<ReplicaSet> replicaSets = kubeClient.getKubernetesClient().apps().replicaSets().inNamespace(namespace).list().getItems();
        List<ConfigMap> configMaps = kubeClient.getKubernetesClient().configMaps().inNamespace(namespace).list().getItems();
        List<PersistentVolumeClaim> persistentVolumeClaims = kubeClient.getKubernetesClient().persistentVolumeClaims().inNamespace(namespace).list().getItems();
        List<PersistentVolume> persistentVolumes = kubeClient.getKubernetesClient().persistentVolumes().list().getItems();
        List<Service> services = kubeClient.getKubernetesClient().services().inNamespace(namespace).list().getItems();
        List<Event> events = kubeClient.getKubernetesClient().v1().events().inNamespace(namespace).list().getItems();
        List<Pod> pods = kubeClient.getKubernetesClient().pods().inNamespace(namespace).list().getItems();

        writeHasMetadataObject(deployments, archiveLocation);
        writeHasMetadataObject(statefulSets, archiveLocation);
        writeHasMetadataObject(replicaSets, archiveLocation);
        writeHasMetadataObject(configMaps, archiveLocation);
        writeHasMetadataObject(persistentVolumeClaims, archiveLocation);
        writeHasMetadataObject(persistentVolumes, archiveLocation);
        writeHasMetadataObject(services, archiveLocation);
        writeHasMetadataObject(pods, archiveLocation);
        writeEvents(events, archiveLocation);
        collectPodLogs(pods, archiveLocation);
    }

    private void collectPodLogs(List<Pod> pods, String archiveLocation) {
        for (Pod pod : pods) {
            String fileName = archiveLocation + Constants.FILE_SEPARATOR + "pod_" + pod.getMetadata().getName() + ".log";
            try {
                String podLog = kubeClient.getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()).getLog();
                TestUtils.createFile(fileName, podLog);
            } catch (KubernetesClientException e) {
                LOGGER.error("[{}] Unable to get pod logs {}! Skipping {}", pod.getMetadata().getNamespace(), pod.getMetadata().getName(), e.getMessage());
                throw e;
            }
        }
    }

    // Method Applicable for StatefulSet, Deployment, Service, Pod, ...
    private void writeHasMetadataObject(List<? extends HasMetadata> deployments, String archiveLocation) {
        deployments.stream().forEach(deployment -> {
            String fileName = archiveLocation + Constants.FILE_SEPARATOR + deployment.getKind().toLowerCase(Locale.ROOT)
                    + "_" + deployment.getMetadata().getName() + ".yaml";
            TestUtils.createFile(fileName, Serialization.asYaml(deployment).toString());
            LOGGER.trace("Stored data into {}", fileName);
        });
    }

    private void writeEvents(List<Event> events, String archiveLocation) {
        StringBuilder eventsData = new StringBuilder();

        // There can be null EventTime, FirstTimestamp or LastTimestamp.
        // In order to get time-sorted events, we need to figure out which time is actually filled.
        // If EventTime is filled, others are null, and vice-versa.
        // In rare case (which should never happen), we provide our custom DateTime (untested).
        events.sort((o1, o2) -> {
            String o1CompareTime;
            String o2CompareTime;
            if (o1.getEventTime() != null) {
                o1CompareTime = o1.getEventTime().getTime();
            } else if (o1.getLastTimestamp() != null) {
                o1CompareTime = o1.getLastTimestamp();
            } else if (o1.getFirstTimestamp() != null) {
                o1CompareTime = o1.getFirstTimestamp();
            } else {
                o1CompareTime = new MicroTime(DateTime.now().toString()).toString();
            }
            if (o2.getEventTime() != null) {
                o2CompareTime = o2.getEventTime().getTime();
            } else if (o2.getLastTimestamp() != null) {
                o2CompareTime = o2.getLastTimestamp();
            } else if (o2.getFirstTimestamp() != null) {
                o2CompareTime = o2.getFirstTimestamp();
            } else {
                o2CompareTime = new MicroTime(DateTime.now().toString()).toString();
            }
            return o1CompareTime.compareTo(o2CompareTime);
        });

        events.forEach(item -> {
            String eventMsg = String.format("%s - %s - %s/%s -%s", item.getLastTimestamp(), item.getReason(),
                    item.getInvolvedObject().getKind(), item.getInvolvedObject().getName(), item.getMessage());
            eventsData.append(eventMsg).append("\n");
        });
        String eventsFile = archiveLocation + Constants.FILE_SEPARATOR + "events.log";
        TestUtils.createFile(eventsFile, eventsData.toString());
        LOGGER.trace("Stored events into {}", eventsFile);
    }

    private List<String> getTestNamespaces(Object testInstance) {
        List<String> namespaces = new ArrayList<>();
        Class<?> clazz = testInstance.getClass();

        while (clazz != null && !clazz.equals(TestSeparator.class)) {
            Arrays.stream(clazz.getDeclaredFields()).filter(
                    field -> field.getName().toLowerCase(Locale.ROOT).contains("namespace")
            ).forEachOrdered(field -> {
                try {
                    field.setAccessible(true);
                    namespaces.add((String) field.get(testInstance));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
            clazz = clazz.getSuperclass();
        }
        return namespaces;
    }
}
