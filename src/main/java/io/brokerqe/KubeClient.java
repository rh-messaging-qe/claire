/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.brokerqe.executor.Executor;
import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
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
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
        TestUtils.waitFor("Creating namespace", Constants.DURATION_2_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return this.namespaceExists(namespaceName);
        });
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

    /**
     * Returns list of pods by prefix in pod name
     * @param namespaceName Namespace name
     * @param podNamePrefix prefix with which the name should begin
     * @return List of pods
     */
    public List<Pod> listPodsByPrefixInName(String namespaceName, String podNamePrefix) {
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

    public Pod getFirstPodByPrefixName(String namespaceName, String podNamePrefix) {
        List<Pod> pods = listPodsByPrefixInName(namespaceName, podNamePrefix);
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

    public void waitUntilPodIsReady(String namespaceName, String podName) {
        client.pods().inNamespace(namespaceName).withName(podName).waitUntilReady(3, TimeUnit.MINUTES);
    }

    public void waitForPodReload(String namespace, Pod pod, String podName) {
        waitForPodReload(namespace, pod, podName, Constants.DURATION_1_MINUTE);
    }

    public void waitForPodReload(String namespace, Pod pod, String podName, long maxTimeout) {
        String originalUid = pod.getMetadata().getUid();

        LOGGER.info("[{}] Waiting for pod {} reload", namespace, podName);

        TestUtils.waitFor("Pod to be reloaded and ready", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            Pod newPod = this.getFirstPodByPrefixName(namespace, podName);
            return newPod != null && !newPod.getMetadata().getUid().equals(originalUid);
        });
        this.waitUntilPodIsReady(namespace, this.getFirstPodByPrefixName(namespace, podName).getMetadata().getName());
    }

    public String executeCommandInPod(String namespace, Pod pod, String cmd, long timeout) {
        try (Executor executor = new Executor()) {
            executor.execCommandOnPod(namespace, pod.getMetadata().getName(), 30, "/bin/bash", "-c", String.join(" ", cmd));
            try {
                return executor.getListenerData().get(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void uploadFilesToPod(String namespace, Pod pod, List<String> localSourcePaths, String podDestinationDirPath) {
        for (String fileToUpload : localSourcePaths) {
            String filename = Paths.get(fileToUpload).getFileName().toString();
            uploadFileToPod(namespace, pod, fileToUpload, podDestinationDirPath + "/" + Paths.get(filename));
        }
    }

    public void uploadFileToPod(String namespace, Pod pod, String localSourcePath, String podDestinationPath) {
        getKubernetesClient().pods().inNamespace(namespace).withName(pod.getMetadata().getName()).file(podDestinationPath).upload(Paths.get(localSourcePath));
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

    public Deployment getDeployment(String namespaceName, String deploymentName) {
        return client.apps().deployments().inNamespace(namespaceName).withName(deploymentName).get();
    }

    public Deployment getDeployment(String deploymentName) {
        return client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
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
        getKubernetesClient().apps().deployments().inNamespace(namespaceName).resource(deployment).createOrReplace();
        if (waitForDeployment) {
            LOGGER.info("[{}] Waiting for deployment {} to be ready", namespaceName, deployment.getMetadata().getName());
            TestUtils.threadSleep(5000);
            getKubernetesClient().resource(deployment).waitUntilReady(1, TimeUnit.MINUTES);
        }
    }

    /**
     * Gets deployment status
     */
    public LabelSelector getDeploymentSelectors(String namespaceName, String deploymentName) {
        return client.apps().deployments().inNamespace(namespaceName).withName(deploymentName).get().getSpec().getSelector();
    }

    // =============================
    // ---------> SERVICE <---------
    // =============================

    public List<Service> getServiceByNames(String namespaceName) {
        return client.services().inNamespace(namespaceName).list().getItems();
    }

    public Service getServiceByName(String namespaceName, String serviceName) {
        return client.services().inNamespace(namespaceName).withName(serviceName).get();
    }


    public Service geServiceBrokerAcceptorFirst(String namespaceName, String brokerName, String acceptorName) {
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
        LOGGER.debug("[{}] Searching for route with {}*", namespaceName, routeName);
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

    public String getLogsFromPod(String namespaceName, Pod pod) {
        return getLogsFromPod(namespaceName, pod, null);
    }

    public String getLogsFromPod(String namespaceName, Pod pod, Instant since) {
        if (since == null) {
            return getKubernetesClient().pods().inNamespace(namespaceName).resource(pod).getLog();
        } else {
            return getKubernetesClient().pods().inNamespace(namespaceName).resource(pod)
                    .sinceTime(since.atOffset(ZoneOffset.UTC).toString()).getLog();
        }
    }

    public void setOperatorLogLevel(ArtemisCloudClusterOperator operator, String logLevel) {
        if (ArtemisCloudClusterOperator.ZAP_LOG_LEVELS.contains(logLevel)) {
            Deployment deployment = getDeployment(namespace, operator.getOperatorName());
            Pod podOld = getFirstPodByPrefixName(operator.getDeploymentNamespace(), operator.getOperatorName());
            List<String> args = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs();
            List<String> argsUpdated = new ArrayList<>();

            for (String arg : args) {
                if (arg.contains("zap-log-level")) {
                    argsUpdated.add("--zap-log-level=" + logLevel.toLowerCase(Locale.ROOT));
                } else {
                    argsUpdated.add(arg);
                }
            }

            if (!args.equals(argsUpdated)) {
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setArgs(argsUpdated);
                setDeployment(operator.getDeploymentNamespace(), deployment);
                waitForPodReload(operator.getDeploymentNamespace(), podOld, operator.getOperatorName());
                LOGGER.info("[{}] Changed operator {} log level to {}", operator.getDeploymentNamespace(), operator.getOperatorName(), logLevel);
            } else {
                LOGGER.debug("[{}] Reload is not needed, zap-log-level is {} as expected", namespace, logLevel);
            }
        } else {
            LOGGER.error("[{}] Unable to set provided log level to operator {}", operator.getDeploymentNamespace(), operator.getOperatorName());
        }
    }

    // ============================
    // ---------> SECRET <---------
    // ============================
    public Secret createSecretEncodedData(String namespaceName, String secretName, Map<String, String> data, boolean waitForCreation) {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(namespaceName)
                .endMetadata()
                .withData(data)
                .build();
        client.secrets().inNamespace(namespaceName).resource(secret).createOrReplace();
        if (waitForCreation) {
            waitForSecretCreation(namespaceName, secretName);
        }
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
        client.secrets().inNamespace(namespaceName).resource(secret).createOrReplace();
        if (waitForCreation) {
            waitForSecretCreation(namespaceName, secretName);
        }
        return secret;
    }

    public Secret getSecret(String namespaceName, String secretName) {
        return client.secrets().inNamespace(namespaceName).withName(secretName).get();
    }

    public void waitForSecretCreation(String namespaceName, String secretName) {
        TestUtils.waitFor("creation of secret " + secretName, Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE,
                () -> client.secrets().inNamespace(namespaceName).withName(secretName).get() != null);
    }

    public void deleteSecret(String namespaceName, String secretName) {
        deleteSecret(namespaceName, secretName, true);
    }

    public void deleteSecret(String namespaceName, String secretName, boolean waitForDeletion) {
        client.secrets().inNamespace(namespaceName).withName(secretName).delete();
        if (waitForDeletion) {
            LOGGER.info("[{}] Waiting for secret deletion {}", namespaceName, secretName);
            TestUtils.waitFor(" deletion of secret " + secretName, Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE,
                    () -> client.secrets().inNamespace(namespaceName).withName(secretName).get() == null);
        }
        LOGGER.info("[{}] Deleted secret {}", namespaceName, secretName);

    }

    public X509Certificate getCertificateFromSecret(Secret secret, String dataKey) {
        String caCert = secret.getData().get(dataKey);
        byte[] decoded = Base64.getDecoder().decode(caCert);
        X509Certificate cacert = null;
        try {
            cacert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(decoded));
        } catch (CertificateException e) {
            e.printStackTrace();
        }
        return cacert;
    }

}
