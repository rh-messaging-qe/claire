/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.db;

import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Postgres {

    private static final Logger LOGGER = LoggerFactory.getLogger(Postgres.class);
    private final KubeClient kubeClient;
    private final String namespace;

    protected final String name = "postgresdb";
    private final String secretName = "postgres-db-secret";
    private final String adminUsername = "postgres";
    private final String adminPassword = "testpassword";
    private StatefulSet postgresStatefulset;

    Map<String, String> secretData = Map.of(
            "username", adminUsername,
            "password", adminPassword
    );

    private Service service = new ServiceBuilder()
        .editOrNewMetadata()
            .withName(name)
        .endMetadata()
        .editOrNewSpec()
            .withType("ClusterIP")
            .withPorts(new ServicePortBuilder()
                .withName(name)
                .withPort(5432)
                .withTargetPort(new IntOrString(name))
                .build())
            .withSelector(Map.of("app", name))
        .endSpec()
        .build();
    
    public Postgres(KubeClient kubeClient, String namespace) {
        this.kubeClient = kubeClient;
        this.namespace = namespace;
    }

    public void deployPostgres(String applicationName) {
        LOGGER.info("[{}] [Postgres] Deploying {}", namespace, name);
        kubeClient.createSecretStringData(namespace, secretName, secretData, true);
        
        postgresStatefulset = new StatefulSetBuilder()
            .withNewMetadata()
                .withName(name)
            .endMetadata()
            .withNewSpec()
                .withSelector(
                    new LabelSelectorBuilder()
                        .withMatchLabels(Map.of("app", name))
                    .build()
                )
                .withReplicas(1)
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", name)
                    .endMetadata()
                    .withNewSpec()
                        .editOrNewSecurityContext()
                            .withRunAsNonRoot(true)
                        .endSecurityContext()
                        .addNewContainer()
                            .withName(name)
                            .withImage(Constants.IMAGE_POSTGRES)
                            .withImagePullPolicy("Always")
                            .withPorts(new ContainerPortBuilder()
                                .withName(name)
                                .withContainerPort(5432)
                                .build())
                            .editOrNewSecurityContext()
                                .withPrivileged(false)
                                .withAllowPrivilegeEscalation(false)
                                .withRunAsNonRoot(true)
                                .withNewCapabilities()
                                    .withDrop("ALL")
                                .endCapabilities()
                            .endSecurityContext()
                            .withEnv(List.of(
                                new EnvVarBuilder()
                                    .withName("POSTGRES_USER")
                                    .withValueFrom(new EnvVarSourceBuilder()
                                            .withNewSecretKeyRef("username", secretName, false)
                                            .build())
                                    .build(),
                                new EnvVarBuilder()
                                    .withName("POSTGRES_PASSWORD")
                                    .withValueFrom(new EnvVarSourceBuilder()
                                        .withNewSecretKeyRef("password", secretName, false)
                                        .build())
                                .build(),
                                new EnvVarBuilder()
                                    .withName("POSTGRES_DB")
                                    .withValue(applicationName)
                                .build()
                            ))
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
        .build();

        postgresStatefulset = kubeClient.getKubernetesClient().resource(postgresStatefulset).inNamespace(namespace).createOrReplace();
        kubeClient.getKubernetesClient().resource(postgresStatefulset).waitUntilReady(60, TimeUnit.SECONDS);
        service = kubeClient.getKubernetesClient().services().inNamespace(namespace).resource(service).createOrReplace();
    }

    public void undeployPostgres() {
        LOGGER.info("[{}] [Postgres] Undeploying {}", namespace, name);
        kubeClient.getKubernetesClient().resource(postgresStatefulset).delete();
        kubeClient.getKubernetesClient().resource(service).inNamespace(namespace).delete();
        kubeClient.deleteSecret(namespace, secretName);
    }

    public String getName() {
        return name;
    }

    public String getSecretName() {
        return secretName;
    }
}
