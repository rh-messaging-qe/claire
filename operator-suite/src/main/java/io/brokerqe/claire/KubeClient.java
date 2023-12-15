/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.executor.ExecutorOperator;
import io.brokerqe.claire.helpers.DataStorer;
import io.brokerqe.claire.security.CertificateManager;
import io.brokerqe.claire.security.KeyStoreData;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RoutePortBuilder;
import io.fabric8.openshift.api.model.RouteTargetReferenceBuilder;
import io.fabric8.openshift.api.model.TLSConfigBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class KubeClient {
    protected final KubernetesClient client;
    private final KubernetesPlatform platform;
    protected String namespace;

    private static final Logger LOGGER = LoggerFactory.getLogger(KubeClient.class);

    // ============================
    // ---------> CLIENT <---------
    // ============================

    public KubeClient(String namespace) {
        LOGGER.debug("Creating client in namespace: {}", namespace);
        Config config = Config.autoConfigure(System.getenv().getOrDefault("KUBE_CONTEXT", null));
        config.setConnectionTimeout(60000); // default 10000ms
        config.setRequestTimeout(30000); // default 10000ms
        KubernetesClient tmpClient = new KubernetesClientBuilder().withConfig(config).build().adapt(OpenShiftClient.class);
        KubernetesPlatform tmpPlatform;
        try {
            // if following command works, we are on openshift cluster instance
            ((OpenShiftClient) tmpClient).routes().inNamespace("default").list();
            tmpPlatform = KubernetesPlatform.OPENSHIFT;
        } catch (ClassCastException | KubernetesClientException e) {
            tmpPlatform = KubernetesPlatform.KUBERNETES;
            tmpClient.close();
            tmpClient = new KubernetesClientBuilder().withConfig(config).build().adapt(KubernetesClient.class);
        }
        client = tmpClient;
        platform = tmpPlatform;
        this.namespace = namespace;
        LOGGER.info("[{}] Created KubernetesClient for {}: {}.{} - {}", namespace, platform, client.getKubernetesVersion().getMajor(), client.getKubernetesVersion().getMinor(), client.getMasterUrl());
    }

    public KubernetesClient getKubernetesClient() {
        return client;
    }

    public KubernetesPlatform getKubernetesPlatform() {
        return this.platform;
    }

    public boolean isKubernetesPlatform() {
        return this.platform == KubernetesPlatform.KUBERNETES;
    }

    public boolean isOpenshiftPlatform() {
        return this.platform == KubernetesPlatform.OPENSHIFT;
    }

    public KubernetesPlatform getKubernetesPlatform(KubeClient client) {
        return client.getKubernetesPlatform();
    }

    // ===============================
    // ---------> NAMESPACE <---------
    // ===============================

    public KubeClient inNamespace(String namespaceName) {
        LOGGER.debug("Using namespace: {}", namespaceName);
        this.namespace = namespaceName;
        return this;
    }

    public Namespace createNamespace(String namespaceName) {
        return this.createNamespace(namespaceName, false);
    }
    public Namespace createNamespace(String namespaceName, boolean setNamespace) {
        LOGGER.info("Creating new namespace {}", namespaceName);
        Namespace ns = this.getKubernetesClient().resource(new NamespaceBuilder().withNewMetadata().withName(namespaceName).endMetadata().build()).createOrReplace();
        TestUtils.waitFor("Creating namespace", Constants.DURATION_2_SECONDS, Constants.DURATION_3_MINUTES, () -> this.namespaceExists(namespaceName));
        if (setNamespace) {
            this.namespace = namespaceName;
        }
        ResourceManager.addNamespace(namespaceName);
        return ns;
    }

    public void deleteNamespace(String namespaceName) {
        LOGGER.info("Deleting namespace {}", namespaceName);
        this.getKubernetesClient().namespaces().withName(namespaceName).delete();
        TestUtils.waitFor("Deletion of namespace", Constants.DURATION_2_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return !this.namespaceExists(namespaceName);
        });
        ResourceManager.removeNamespace(namespaceName);
    }

    public String getNamespace() {
        return namespace;
    }

    public Namespace getNamespace(String namespaceName) {
        return client.namespaces().withName(namespaceName).get();
    }

    public boolean namespaceExists(String namespaceName) {
        return client.namespaces().list().getItems().stream().map(n -> n.getMetadata().getName())
            .collect(Collectors.toList()).contains(namespaceName);
    }

    /**
     * Gets namespace status
     */
    public boolean getNamespaceStatus(String namespaceName) {
        return client.namespaces().withName(namespaceName).isReady();
    }

    // ================================
    // ---------> CONFIG MAP <---------
    // ================================
    public ConfigMap getConfigMap(String namespaceName, String configMapName) {
        return client.configMaps().inNamespace(namespaceName).withName(configMapName).get();
    }

    public ConfigMap getConfigMap(String configMapName) {
        return getConfigMap(namespace, configMapName);
    }


    public boolean getConfigMapStatus(String configMapName) {
        return client.configMaps().inNamespace(getNamespace()).withName(configMapName).isReady();
    }

    // ================================
    // ---------> REPLICASET <---------
    // ================================
    public List<ReplicaSet> getReplicaSetsWithPrefix(String namespaceName, String prefixName) {
        return client.apps().replicaSets().inNamespace(namespaceName).list().getItems().stream().filter(
                replicaSet -> replicaSet.getMetadata().getName().startsWith(prefixName)).collect(Collectors.toList());
    }

    // =========================
    // ---------> POD <---------
    // =========================
    public List<Pod> listPods() {
        return client.pods().inNamespace(namespace).list().getItems();
    }

    public List<Pod> listPods(String namespaceName) {
        return client.pods().inNamespace(namespaceName).list().getItems();
    }

    public Pod getArtemisPodByLabel(String namespace) {
        List<Pod> pods = listPods(namespace);
        Pod foundPod = null;
        for (Pod pod : pods) {
            Map<String, String> labels = pod.getMetadata().getLabels();
            for (String key : labels.keySet()) {
                if (key.equals(ArtemisConstants.LABEL_ACTIVEMQARTEMIS)) {
                    StatefulSet ss = getDefaultArtemisStatefulSet(labels.get(key));
                    if (ss != null) {
                        foundPod = pod;
                        break;
                    }
                }
            }
        }
        return foundPod;
    }

    /**
     * Returns list of pods by prefix in pod name
     * @param namespaceName Namespace name
     * @param podNamePrefix prefix with which the name should begin
     * @return List of pods
     */
    public List<Pod> listPodsByPrefixName(String namespaceName, String podNamePrefix) {
        return listPods(namespaceName)
                .stream().filter(p -> p.getMetadata().getName().startsWith(podNamePrefix))
                .collect(Collectors.toList());
    }

    /**
     * Gets pod
     */
    public Pod getPod(String namespaceName, String name) {
        return client.pods().inNamespace(namespaceName).withName(name).get();
    }

    public Pod getPod(String name) {
        return getPod(namespace, name);
    }

    public void deletePod(String namespace, Pod pod) {
        deletePod(namespace, pod, true);
    }

    public void deletePod(String namespace, Pod pod, boolean waitForDeletion) {
        getKubernetesClient().pods().inNamespace(namespace).resource(pod).delete();
        if (waitForDeletion) {
            waitUntilPodIsDeleted(namespace, pod);
        }
    }

    public Pod getFirstPodByPrefixName(String namespaceName, String podNamePrefix) {
        List<Pod> pods = listPodsByPrefixName(namespaceName, podNamePrefix);
        if (pods.size() > 1) {
            LOGGER.warn("[{}] Returning first found pod with name '{}' of many ({})!", namespaceName, podNamePrefix, pods.size());
            return pods.get(0);
        } else if (pods.size() > 0) {
            return pods.get(0);
        } else {
            return null;
        }
    }

    public void reloadPodWithWait(String namespaceName, Pod pod, String podName) {
        this.getKubernetesClient().resource(pod).inNamespace(namespaceName).delete();
        waitForPodReload(namespaceName, pod, podName);
    }

    public void waitUntilPodIsReady(String namespaceName, Pod pod) {
        client.pods().inNamespace(namespaceName).resource(pod).waitUntilReady(3, TimeUnit.MINUTES);
    }

    public void waitUntilPodIsDeleted(String namespaceName, Pod pod) {
        TestUtils.waitFor("deletion of pod " + pod.getMetadata().getName(), Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return getPod(namespaceName, pod.getMetadata().getName()) == null;
        });
    }

    public Pod waitForPodReload(String namespace, Pod pod, String podName) {
        return waitForPodReload(namespace, pod, podName, Constants.DURATION_1_MINUTE);
    }

    public Pod waitForPodReload(String namespace, Pod pod, String podName, long maxTimeout) {
        String originalUid = pod.getMetadata().getUid();

        LOGGER.info("[{}] Waiting {}s for pod {} reload", namespace, Duration.ofMillis(maxTimeout).toSeconds(), podName);
        TestUtils.waitFor("Pod to be reloaded and ready", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            Pod newPod = getFirstPodByPrefixName(namespace, podName);
            LOGGER.debug("[{}] OriginalPodUid {} vs currentPodUid {}", namespace, originalUid, newPod.getMetadata().getUid());
            return newPod != null && !newPod.getMetadata().getUid().equals(originalUid);
        });

        for (Pod podTmp : listPodsByPrefixName(namespace, podName)) {
            if (!podTmp.getMetadata().getUid().equals(originalUid)) {
                this.waitUntilPodIsReady(namespace, podTmp);
                LOGGER.trace("[{}] Returning reloaded pod {}", namespace, podName);
                return getPod(namespace, podTmp.getMetadata().getName());
            }
        }
        LOGGER.error("[{}] Reloaded pod {} has not been found!", namespace, podName);
        return null;
    }

    public String executeCommandInPod(Pod pod, String cmd, long timeout) {
        ExecutorOperator executor = new ExecutorOperator(pod);
        executor.executeCommand(Constants.DURATION_30_SECONDS, "/bin/bash", "-c", String.join(" ", cmd));
        return executor.getCommandData(timeout);
    }

    public void uploadFilesToPod(String namespace, Pod pod, List<String> localSourcePaths, String podDestinationDirPath) {
        for (String fileToUpload : localSourcePaths) {
            String filename = Paths.get(fileToUpload).getFileName().toString();
            uploadFileToPod(namespace, pod, fileToUpload, podDestinationDirPath + "/" + Paths.get(filename));
        }
    }

    public void uploadFileToPod(String namespace, Pod pod, String localSourcePath, String podDestinationPath) {
        boolean success = getKubernetesClient().pods().inNamespace(namespace).withName(pod.getMetadata().getName()).file(podDestinationPath).upload(Paths.get(localSourcePath));
        if (!success) {
            LOGGER.debug("[{}][{}] Failed to upload file to pod. Trying bash method using cat & tee", namespace, pod.getMetadata().getName());
            String fileContent = TestUtils.getFileContentAsBase64(localSourcePath);
            executeCommandInPod(pod, "echo " + fileContent + " | base64 -d > " + podDestinationPath, Constants.DURATION_30_SECONDS);
        }
    }

    // ==================================
    // ---------> STATEFUL SET <---------
    // ==================================

    /**
     * Gets stateful set
     */
    public StatefulSet getStatefulSet(String namespaceName, String statefulSetName) {
        return client.apps().statefulSets().inNamespace(namespaceName).withName(statefulSetName).get();
    }

    public StatefulSet getStatefulSet(String statefulSetName) {
        return getStatefulSet(namespace, statefulSetName);
    }

    /**
     * Gets stateful set
     */
    public RollableScalableResource<StatefulSet> statefulSet(String namespaceName, String statefulSetName) {
        return client.apps().statefulSets().inNamespace(namespaceName).withName(statefulSetName);
    }

    public RollableScalableResource<StatefulSet> statefulSet(String statefulSetName) {
        return statefulSet(namespace, statefulSetName);
    }
    // ================================
    // ---------> DEPLOYMENT <---------
    // ================================

    /**
     * Gets deployment
     */

    public Deployment getDeployment(String namespace, String deploymentName) {
        return client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
    }

    public Deployment getDeployment(String deploymentName) {
        return client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
    }

    public List<Deployment> getDeployments(String namespace) {
        return client.apps().deployments().inNamespace(namespace).list().getItems();
    }

    public List<Deployment> getDeploymentByPrefixName(String namespace, String deploymentPrefixName) {
        return client.apps().deployments().inNamespace(namespace).list().getItems().stream().filter(
                deployment -> deployment.getMetadata().getName().startsWith(deploymentPrefixName)).collect(Collectors.toList());
    }

    public Deployment getDeploymentFromAnyNamespaces(String deploymentName) {
        return client.apps().deployments().inAnyNamespace().list().getItems().stream().filter(
            deployment -> deployment.getMetadata().getName().equals(deploymentName))
                .findFirst()
                .orElseThrow();
    }

    public void setDeployment(String namespaceName, Deployment deployment) {
        setDeployment(namespaceName, deployment, true);
    }

    public void setDeployment(String namespaceName, Deployment deployment, boolean waitForDeployment) {
        deployment = getKubernetesClient().apps().deployments().inNamespace(namespaceName).resource(deployment).createOrReplace();
        DataStorer.dumpResourceToFile(deployment);
        if (waitForDeployment) {
            LOGGER.info("[{}] Waiting for deployment {} to be ready", namespaceName, deployment.getMetadata().getName());
            TestUtils.threadSleep(5000);
            getKubernetesClient().resource(deployment).waitUntilReady(1, TimeUnit.MINUTES);
        }
    }

    // =============================
    // ---------> SERVICE <---------
    // =============================

    public List<Service> getServicesInNamespace(String namespaceName) {
        return client.services().inNamespace(namespaceName).list().getItems();
    }

    public Service getServiceByName(String namespaceName, String serviceName) {
        return client.services().inNamespace(namespaceName).withName(serviceName).get();
    }


    public Service getFirstServiceBrokerAcceptor(String namespaceName, String brokerName, String acceptorName) {
        return getServiceBrokerAcceptors(namespaceName, brokerName, acceptorName).get(0);
    }

    public List<Service> getServiceBrokerAcceptors(String namespaceName, String brokerName, String acceptorName) {
        return client.services().inNamespace(namespaceName).list().getItems().stream()
                .filter(svc -> svc.getMetadata().getName().startsWith(brokerName + "-" + acceptorName)
                ).collect(Collectors.toList());
    }

    // =====================================
    // ----------> INGRESS/ROUTE <----------
    // =====================================
    // Route:
    // artemis-broker-my-amqp-0-svc-rte    artemis-broker-my-amqp-0-svc-rte-namespacename.apps.lala.amq-broker-qe.my-host.com
    // Ingress
    // artemis-broker-my-amqp-0-svc-ing    artemis-broker-my-amqp-0-svc-ing.apps.artemiscloud.io

    public List<HasMetadata> getExternalAccessServicePrefixName(String namespaceName, String externalAccessPrefixName) {
        List<HasMetadata> externalUrls = new ArrayList<>();
        if (isKubernetesPlatform()) {
            externalUrls.addAll(getIngressByPrefixName(namespaceName, externalAccessPrefixName));
        } else {
            externalUrls.addAll(getRouteByPrefixName(namespaceName, externalAccessPrefixName));
        }
        return externalUrls;
    }

    public String getExternalAccessServiceUrl(String namespaceName, String externalAccess) {
        if (isKubernetesPlatform()) {
            return getIngressByName(namespaceName, externalAccess).getSpec().getRules().get(0).getHost();
        } else {
            return getRouteByName(namespaceName, externalAccess).getSpec().getHost();
        }
    }

    public Route createRoute(String namespaceName, String name, String port, Service service) {
        Route route = new RouteBuilder()
                .editOrNewMetadata()
                    .withName(name)
                    .withNamespace(namespaceName)
                .endMetadata()
                .editOrNewSpec()
                    .withHost(name + "-" + getPlatformIngressDomainUrl(namespace))
                    .withPort(new RoutePortBuilder()
                            .withTargetPort(new IntOrString(port))
                        .build())
                    .withTls(new TLSConfigBuilder()
                            .withTermination("passthrough")
                            .withInsecureEdgeTerminationPolicy("Redirect")
                        .build())
                    .withTo(new RouteTargetReferenceBuilder()
                            .withName(service.getMetadata().getName())
                            .withKind("Service")
                        .build())
                .endSpec()
                .build();
        route = ((OpenShiftClient) client).routes().inNamespace(namespaceName).resource(route).create();
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        LOGGER.debug("[{}] Created route {}", namespaceName, route.getMetadata().getName());
        DataStorer.dumpResourceToFile(route);
        return route;
    }

    public List<OperatorGroup> getOperatorGroups(String namespaceName) {
        return ((OpenShiftClient) client).operatorHub().operatorGroups().inNamespace(namespaceName).list().getItems();
    }

    public HasMetadata createOperatorGroup(String namespaceName, String name) {
        return createOperatorGroup(namespaceName, name, null);
    }

    public HasMetadata createOperatorGroup(String namespaceName, String name, List<String> targetNamespaces) {
        if (getOperatorGroups(namespaceName).size() > 0) {
            LOGGER.warn("[{}] There is already existing OperatorGroup in this namespace!", namespaceName);
        }

        String watchedNamespacesString;
        if (targetNamespaces != null) {
            watchedNamespacesString = String.join("\n    - ", targetNamespaces);
        } else {
            watchedNamespacesString = namespaceName;
        }
        String operatorGroupString = String.format("""
            apiVersion: operators.coreos.com/v1
            kind: OperatorGroup
            metadata:
              name: %s
              namespace: %s
            spec:
              targetNamespaces:
                - %s
            """, name, namespaceName, watchedNamespacesString);

        LOGGER.info("[OLM][{}] Creating OperatorGroup", namespaceName);
        LOGGER.debug("[OLM][{}] {}", namespaceName, operatorGroupString);
        HasMetadata operatorGroup = getKubernetesClient().resource(operatorGroupString).inNamespace(namespace).createOrReplace();
        DataStorer.dumpResourceToFile(operatorGroup);
        return operatorGroup;
    }

    public List<ClusterServiceVersion> getClusterServiceVersions(String namespaceName) {
        return ((OpenShiftClient) client).operatorHub().clusterServiceVersions().inNamespace(namespaceName).list().getItems();
    }
    public ClusterServiceVersion getClusterServiceVersion(String namespaceName, String operatorNamePrefix) {
        List<ClusterServiceVersion> csvList = new ArrayList<>();
        getClusterServiceVersions(namespaceName).stream().filter(csv -> csv.getMetadata().getName().startsWith(operatorNamePrefix)).collect(Collectors.toCollection(() -> csvList));
        if (csvList.isEmpty()) {
            return null;
        } else if (csvList.size() == 1) {
            return csvList.get(0);
        } else {
            LOGGER.warn("[{}] Found multiple ClusterServiceVersions! Returning first found! {}", namespaceName, csvList.get(0).getMetadata().getName());
            List<String> csvNames = new ArrayList<>();
            for (ClusterServiceVersion csv : csvList) {
                csvNames.add(csv.getMetadata().getName());
            }
            LOGGER.debug("[{}] CSVs found: {}", namespaceName, String.join(" ", csvNames));
            return csvList.get(0);
        }
    }

    public String getPlatformIngressDomainUrl(String namespace) {
        String svc;
        String domain;
        if (isKubernetesPlatform()) {
            svc = "svc-ing";
            // https://github.com/artemiscloud/activemq-artemis-operator/blob/d04ed9609b1f8fe399fe9ea12b4f5488c6c9d9d9/pkg/resources/ingresses/ingress.go#L70
            // hardcoded
            domain = "apps.artemiscloud.io";
        } else {
            svc = "svc-rte";
            domain = namespace + "." + getKubernetesClient().getMasterUrl().getHost().replace("api", "apps");
        }
        return svc + "-" + domain;
    }

    public List<String> getExternalAccessServiceUrlPrefixName(String namespaceName, String externalAccessPrefixName) {
        List<String> externalUrls = new ArrayList<>();
        if (this.isKubernetesPlatform()) {
            List<Ingress> ingresses = getIngressByPrefixName(namespaceName, externalAccessPrefixName);
            for (Ingress ingress : ingresses) {
                externalUrls.add(ingress.getSpec().getRules().get(0).getHost());
            }
        } else {
            List<Route> routes = getRouteByPrefixName(namespaceName, externalAccessPrefixName);
            for (Route route : routes) {
                externalUrls.add(route.getSpec().getHost());
            }
        }
        LOGGER.debug("[{}] Found externalServiceUrl (ingress/route) {}", namespaceName, externalUrls);
        return externalUrls;
    }

    public Ingress getIngressByName(String namespaceName, String ingressName) {
        Ingress ingress = null;
        LOGGER.debug("[{}] Searching for ingress with {}", namespaceName, ingressName);
        try {
            ingress = client.network().v1().ingresses().inNamespace(namespaceName).withName(ingressName).get();
        } catch (KubernetesClientException e) {
            LOGGER.debug("[{}] Calling for Ingress resource on different platform", namespaceName);
        }
        return ingress;
    }

    public List<Ingress> getIngressByPrefixName(String namespaceName, String ingressPrefixName) {
        List<Ingress> ingresses = null;
        LOGGER.debug("[{}] Searching for ingresses with {}*", namespaceName, ingressPrefixName);
        try {
            ingresses = client.network().v1().ingresses().inNamespace(namespaceName).list().getItems().stream().filter(
                    ingress -> ingress.getMetadata().getName().startsWith(ingressPrefixName)).collect(Collectors.toList());
        } catch (KubernetesClientException e) {
            LOGGER.debug("[{}] Calling for Ingress resource on different platform {}", namespace, e.getMessage());
        }
        return ingresses;
    }

    public Route getRouteByName(String namespaceName, String routeName) {
        Route route = null;
        LOGGER.debug("[{}] Searching for route with {}", namespaceName, routeName);
        try {
            route = ((OpenShiftClient) client).routes().inNamespace(namespaceName).withName(routeName).get();
        } catch (KubernetesClientException e) {
            LOGGER.debug("[{}] Calling for Ingress resource on different platform", namespaceName);
        }
        return route;
    }

    public List<Route> getRouteByPrefixName(String namespaceName, String routePrefixName) {
        List<Route> routes = null;
        try {
            routes = ((OpenShiftClient) client).routes().inNamespace(namespaceName).list().getItems().stream().filter(
                    route -> route.getMetadata().getName().startsWith(routePrefixName)).collect(Collectors.toList());
        } catch (KubernetesClientException e) {
            LOGGER.debug("[{}] Calling for Route resource on different platform", namespaceName);
        }
        return routes;
    }

    // ==========================
    // ---------> NODE <---------
    // ==========================

    public String getNodeAddress() {
        return listNodes().get(0).getStatus().getAddresses().get(0).getAddress();
    }

    public List<Node> listNodes() {
        return client.nodes().list().getItems();
    }

    public List<Node> listWorkerNodes() {
        return listNodes().stream().filter(node -> node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/worker")).collect(Collectors.toList());
    }

    public List<Node> listMasterNodes() {
        return listNodes().stream().filter(node -> node.getMetadata().getLabels().containsKey("node-role.kubernetes.io/master")).collect(Collectors.toList());
    }

    // =========================
    // ---------> JOB <---------
    // =========================

    public boolean jobExists(String jobName) {
        return client.batch().v1().jobs().inNamespace(namespace).list().getItems().stream().anyMatch(j -> j.getMetadata().getName().startsWith(jobName));
    }

    public Job getJob(String jobName) {
        return client.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
    }

    public boolean checkSucceededJobStatus(String jobName) {
        return checkSucceededJobStatus(getNamespace(), jobName, 1);
    }

    public boolean checkSucceededJobStatus(String namespaceName, String jobName, int expectedSucceededPods) {
        return getJobStatus(namespaceName, jobName).getSucceeded().equals(expectedSucceededPods);
    }

    public boolean checkFailedJobStatus(String namespaceName, String jobName, int expectedFailedPods) {
        return getJobStatus(namespaceName, jobName).getFailed().equals(expectedFailedPods);
    }

    // Pods Statuses:  0 Running / 0 Succeeded / 1 Failed
    public JobStatus getJobStatus(String namespaceName, String jobName) {
        return client.batch().v1().jobs().inNamespace(namespaceName).withName(jobName).get().getStatus();
    }

    public JobStatus getJobStatus(String jobName) {
        return getJobStatus(namespace, jobName);
    }

    public JobList getJobList() {
        return client.batch().v1().jobs().inNamespace(namespace).list();
    }

    public List<Job> listJobs(String namePrefix) {
        return client.batch().v1().jobs().inNamespace(getNamespace()).list().getItems().stream()
            .filter(job -> job.getMetadata().getName().startsWith(namePrefix)).collect(Collectors.toList());
    }

    // ============================
    // ----------> LOGS <----------
    // ============================

    public String getLogsFromPod(Pod pod) {
        return getLogsFromPod(pod, null, -1);
    }

    public String getLogsFromPod(Pod pod, int sinceSeconds) {
        return getLogsFromPod(pod, null, sinceSeconds);
    }

    public String getLogsFromPod(Pod pod, Instant sinceInstant) {
        return getLogsFromPod(pod, sinceInstant, -1);
    }

    public String getLogsFromPod(Pod pod, Instant sinceInstant, int sinceSeconds) {
        if (sinceInstant == null && sinceSeconds == -1) {
            return getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace()).resource(pod).getLog();
        } else if (sinceInstant != null) {
            // TODO problem with time zones (UTC is not guaranteed)
            return getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace()).resource(pod)
                    .sinceTime(sinceInstant.atOffset(ZoneOffset.UTC).toString()).getLog();
        } else {
            return getKubernetesClient().pods().inNamespace(pod.getMetadata().getNamespace()).resource(pod).sinceSeconds(sinceSeconds).getLog();
        }
    }

    public void createConfigMap(String namespaceName, ConfigMap configMap) {
        configMap = client.configMaps().inNamespace(namespaceName).resource(configMap).createOrReplace();
        DataStorer.dumpResourceToFile(configMap);
    }
    
    public StatefulSet getDefaultArtemisStatefulSet(String brokerName) {
        return getStatefulSet(brokerName + "-ss");
    }

    public Service getService(String namespaceName, String serviceName) {
        return client.services().inNamespace(namespaceName).withName(serviceName).get();
    }

    // ============================
    // ---------> SECRET <---------
    // ============================
    public Secret createSecretEncodedData(String namespaceName, String secretName, Map<String, String> data) {
        return createSecretEncodedData(namespaceName, secretName, data, true);
    }

    public Secret createSecretEncodedData(String namespaceName, String secretName, Map<String, String> data, boolean waitForCreation) {
        Secret secret = new SecretBuilder()
            .withNewMetadata()
                .withName(secretName)
                .withNamespace(namespaceName)
            .endMetadata()
            .withData(data)
            .build();
        secret = client.secrets().inNamespace(namespaceName).resource(secret).createOrReplace();
        if (waitForCreation) {
            waitForSecretCreation(namespaceName, secretName);
        }
        DataStorer.dumpResourceToFile(secret);
        return secret;
    }

    public Secret createSecretStringData(String namespaceName, String secretName, Map<String, String> data, boolean waitForCreation) {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(namespaceName)
                .endMetadata()
                .withType("generic")
                .withStringData(data)
                .build();
        secret = client.secrets().inNamespace(namespaceName).resource(secret).createOrReplace();
        if (waitForCreation) {
            waitForSecretCreation(namespaceName, secretName);
        }
        DataStorer.dumpResourceToFile(secret);
        return secret;
    }

    public Secret getSecret(String namespaceName, String secretName) {
        return client.secrets().inNamespace(namespaceName).withName(secretName).get();
    }

    public List<Secret> getSecretByPrefixName(String namespaceName, String secretNamePrefix) {
        return client.secrets().inNamespace(namespaceName).list().getItems().stream().filter(
                secret -> secret.getMetadata().getName().startsWith(secretNamePrefix)).collect(Collectors.toList());
    }

    public void waitForSecretCreation(String namespaceName, String secretName) {
        TestUtils.waitFor("creation of secret " + secretName, Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE,
                () -> client.secrets().inNamespace(namespaceName).withName(secretName).get() != null);
    }

    public void deleteSecret(Secret secret) {
        client.secrets().resource(secret).delete();
        waitForSecretDeletion(secret.getMetadata().getNamespace(), secret.getMetadata().getName());
    }

    public void deleteSecret(String namespaceName, String secretName) {
        deleteSecret(namespaceName, secretName, true);
    }

    public void deleteSecret(String namespaceName, String secretName, boolean waitForDeletion) {
        client.secrets().inNamespace(namespaceName).withName(secretName).delete();
        if (waitForDeletion) {
            waitForSecretDeletion(namespaceName, secretName);
        }
        LOGGER.info("[{}] Deleted secret {}", namespaceName, secretName);
    }

    private void waitForSecretDeletion(String namespaceName, String secretName) {
        LOGGER.info("[{}] Waiting for secret deletion {}", namespaceName, secretName);
        TestUtils.waitFor(" deletion of secret " + secretName, Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE,
                () -> client.secrets().inNamespace(namespaceName).withName(secretName).get() == null);
    }

    public Secret getRouterDefaultSecret() {
        return getSecret("openshift-ingress", "router-certs-default");
    }

    public static Secret createBrokerTruststoreSecretWithOpenshiftRouter(KubeClient kubeClient, String namespace, String secretName, String brokerTsFileName) {
        Secret routerSecret = kubeClient.getRouterDefaultSecret();
        X509Certificate routerCert = getCertificateFromSecret(routerSecret, "tls.crt");
        KeyStoreData brokerTrustStore = CertificateManager.createEmptyKeyStore(Constants.BROKER_TRUSTSTORE_ID, CertificateManager.DEFAULT_BROKER_PASSWORD, brokerTsFileName);
        CertificateManager.addToTruststore(brokerTrustStore, routerCert, "openshift-router");
        return kubeClient.createSecretEncodedData(namespace, secretName, Map.of(Constants.BROKER_TRUSTSTORE_ID, brokerTrustStore.getEncodedKeystoreFileData()));
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

    // ===============================
    // ---------> CONFIGMAP <---------
    // ===============================
    public ConfigMap createConfigMap(String namespaceName, String configmapName, Map<String, String> data) {
        return createConfigMap(namespaceName, configmapName, data, true);
    }

    public ConfigMap createConfigMap(String namespaceName, String configmapName, Map<String, String> data, boolean waitForCreation) {
        ConfigMap configMap = new ConfigMapBuilder()
            .editOrNewMetadata()
                .withName(configmapName)
            .endMetadata()
            .withData(data)
            .build();

        configMap = getKubernetesClient().configMaps().inNamespace(namespaceName).resource(configMap).createOrReplace();
        if (waitForCreation) {
            waitForConfigmapCreation(namespaceName, configmapName);
        }
        DataStorer.dumpResourceToFile(configMap);
        return configMap;
    }

    public ConfigMap createConfigMapBinaryData(String namespaceName, String configmapName, Map<String, String> binaryData) {
        return createConfigMapBinaryData(namespaceName, configmapName, binaryData, true);
    }

    public ConfigMap createConfigMapBinaryData(String namespaceName, String configmapName, Map<String, String> binaryData, boolean waitForCreation) {
        ConfigMap configMap = new ConfigMapBuilder()
            .editOrNewMetadata()
                .withName(configmapName)
            .endMetadata()
            .withBinaryData(binaryData)
            .build();

        if (waitForCreation) {
            waitForConfigmapCreation(namespaceName, configmapName);
        }
        configMap = getKubernetesClient().configMaps().inNamespace(namespaceName).resource(configMap).createOrReplace();
        DataStorer.dumpResourceToFile(configMap);
        return configMap;
    }

    public void waitForConfigmapCreation(String namespaceName, String configmapName) {
        TestUtils.waitFor("creation of configmap " + configmapName, Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE,
                () -> client.configMaps().inNamespace(namespaceName).withName(configmapName).get() != null);
    }

    public void deleteConfigMap(String namespaceName, String configMapName) {
        deleteConfigMap(namespaceName, configMapName, true);
    }

    public void deleteConfigMap(String namespaceName, String configMapName, boolean waitForDeletion) {
        client.configMaps().inNamespace(namespaceName).withName(configMapName).delete();
        if (waitForDeletion) {
            LOGGER.info("[{}] Waiting for config map deletion {}", namespaceName, configMapName);
            TestUtils.waitFor(" deletion of config map " + configMapName, Constants.DURATION_5_SECONDS,
                    Constants.DURATION_1_MINUTE,
                    () -> client.configMaps().inNamespace(namespaceName).withName(configMapName).get() == null);
        }
        LOGGER.info("[{}] Deleted config map {}", namespaceName, configMapName);
    }

    // =============================
    // ---------> HELPERS <---------
    // =============================

    public Path copyPodDir(Pod artemisPod, String srcDir, Path dstDir) {
        boolean copyResult = client.pods().resource(artemisPod).dir(srcDir).copy(dstDir);
        assertThat(copyResult, is(Boolean.TRUE));
        return Path.of(dstDir + Constants.FILE_SEPARATOR + srcDir);
    }
}
