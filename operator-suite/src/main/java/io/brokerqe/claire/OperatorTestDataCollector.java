/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.brokerqe.claire.junit.TestSeparator;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class OperatorTestDataCollector extends TestDataCollector {
    static final Logger LOGGER = LoggerFactory.getLogger(OperatorTestDataCollector.class);
    KubeClient kubeClient;

    private ArtemisCloudClusterOperator getOperatorDifferentNamespace() {
        LOGGER.error("Not implemented yet!");
        return null;
    }

    @Override
    protected void collectTestData() {
        List<String> testNamespaces = getTestNamespaces();
        LOGGER.info("Error detected will gather! data from namespace: {}", String.join(" ", testNamespaces));
        kubeClient = (KubeClient) getTestInstanceDeclaredField(testInstance, "client");
        for (String testNamespace : testNamespaces) {
            String archiveDirTmp = archiveDir + Constants.FILE_SEPARATOR + testNamespace;
            TestUtils.createDirectory(archiveDirTmp);
            LOGGER.debug("[{}] Gathering debug data for failed {}#{} into {}", testNamespace, testClass, testMethod, archiveDirTmp);

            List<Deployment> deployments = kubeClient.getKubernetesClient().apps().deployments().inNamespace(testNamespace).list().getItems();
            List<StatefulSet> statefulSets = kubeClient.getKubernetesClient().apps().statefulSets().inNamespace(testNamespace).list().getItems();
            List<ReplicaSet> replicaSets = kubeClient.getKubernetesClient().apps().replicaSets().inNamespace(testNamespace).list().getItems();
            List<ConfigMap> configMaps = kubeClient.getKubernetesClient().configMaps().inNamespace(testNamespace).list().getItems();
            List<PersistentVolumeClaim> persistentVolumeClaims = kubeClient.getKubernetesClient().persistentVolumeClaims().inNamespace(testNamespace).list().getItems();
            List<PersistentVolume> persistentVolumes = kubeClient.getKubernetesClient().persistentVolumes().list().getItems();
            List<Service> services = kubeClient.getKubernetesClient().services().inNamespace(testNamespace).list().getItems();
            List<Secret> secrets = kubeClient.getKubernetesClient().secrets().inNamespace(testNamespace).list().getItems();
            List<Event> events = kubeClient.getKubernetesClient().v1().events().inNamespace(testNamespace).list().getItems();
            List<Pod> pods = kubeClient.getKubernetesClient().pods().inNamespace(testNamespace).list().getItems();
            List<ActiveMQArtemis> artemises = ResourceManager.getArtemisClient().inNamespace(testNamespace).list().getItems();
            List<ActiveMQArtemisAddress> artemisAddresses = ResourceManager.getArtemisAddressClient().inNamespace(testNamespace).list().getItems();
            List<ActiveMQArtemisSecurity> artemisSecurities = ResourceManager.getArtemisSecurityClient().inNamespace(testNamespace).list().getItems();

            writeHasMetadataObject(deployments, archiveDirTmp);
            writeHasMetadataObject(statefulSets, archiveDirTmp);
            writeHasMetadataObject(replicaSets, archiveDirTmp);
            writeHasMetadataObject(configMaps, archiveDirTmp);
            writeHasMetadataObject(persistentVolumeClaims, archiveDirTmp);
            writeHasMetadataObject(persistentVolumes, archiveDirTmp);
            writeHasMetadataObject(services, archiveDirTmp);
            writeHasMetadataObject(secrets, archiveDirTmp);
            writeHasMetadataObject(pods, archiveDirTmp);
            writeHasMetadataObject(artemises, archiveDirTmp);
            writeHasMetadataObject(artemisAddresses, archiveDirTmp);
            writeHasMetadataObject(artemisSecurities, archiveDirTmp);
            writeEvents(events, archiveDirTmp);
            collectPodLogs(pods, archiveDirTmp);
            collectBrokerPodFiles(pods, archiveDirTmp);
        }
    }

    private void collectBrokerPodFiles(List<Pod> pods, String archiveLocation) {
        List<String> fileList = List.of("artemis-roles.properties", "artemis.profile", "broker.xml", "jolokia-access.xml",
                "login.config", "artemis-users.properties", "bootstrap.xml", "jgroups-ping.xml", "logging.properties", "management.xml");
        final String amqBrokerEtcHome = Constants.CONTAINER_BROKER_HOME_ETC_DIR;
        for (Pod pod : pods) {
            if (pod.getMetadata().getLabels().containsKey(Constants.LABEL_ACTIVEMQARTEMIS)) {
                String dirName = archiveLocation + Constants.FILE_SEPARATOR + "broker_etc" + Constants.FILE_SEPARATOR + pod.getMetadata().getName();
                TestUtils.createDirectory(dirName);

                for (String file : fileList) {
                    String outputFileName = dirName + Constants.FILE_SEPARATOR + file;
                    String podFileName = amqBrokerEtcHome + file;
                    kubeClient.getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace())
                         .withName(pod.getMetadata().getName()).file(podFileName).copy(Paths.get(outputFileName));
                }
                // collect /amq/extra/ mounted configuration files + possibly /etc/<cr-name>-secret-name
                String output = kubeClient.executeCommandInPod(pod, "find /amq/extra/ -type f", Constants.DURATION_10_SECONDS);
                for (String file : output.split("\n")) {
                    String outputDirName = dirName + Constants.FILE_SEPARATOR + "container" + Constants.FILE_SEPARATOR + Paths.get(file).getFileName();
                    kubeClient.getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace())
                            .withName(pod.getMetadata().getName()).file(file).copy(Paths.get(outputDirName));
                }
            }
        }
    }

    private void collectPodLogs(List<Pod> pods, String archiveLocation) {
        for (Pod pod : pods) {
            String dirName = archiveLocation + Constants.FILE_SEPARATOR + "logs";
            String containerName = null;
            List<Container> containers = pod.getSpec().getContainers();
            containers.addAll(pod.getSpec().getInitContainers());
            try {
                for (Container container : containers) {
                    containerName = container.getName();
                    String fileName = dirName + Constants.FILE_SEPARATOR + "pod_" + pod.getMetadata().getName() + "_c_" + containerName + ".log";
                    String containerLog = kubeClient.getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace())
                            .withName(pod.getMetadata().getName()).inContainer(containerName).getLog();

                    TestUtils.createDirectory(dirName);
                    TestUtils.createFile(fileName, containerLog);
                }
            } catch (KubernetesClientException e) {
                LOGGER.error("[{}] Unable to get pod/container logs {} - skipping. {}", pod.getMetadata().getNamespace(), pod.getMetadata().getName() + "/" + containerName, e.getMessage());
            }
        }
    }

    // Method Applicable for StatefulSet, Deployment, Service, Pod, ...
    private void writeHasMetadataObject(List<? extends HasMetadata> deployments, String archiveLocation) {
        deployments.stream().forEach(deployment -> {
            String dirName = archiveLocation + Constants.FILE_SEPARATOR + deployment.getKind().toLowerCase(Locale.ROOT);
            String fileName = dirName + Constants.FILE_SEPARATOR + deployment.getMetadata().getName() + ".yaml";
            TestUtils.createDirectory(dirName);
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

    private List<String> getTestNamespaces() {
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
