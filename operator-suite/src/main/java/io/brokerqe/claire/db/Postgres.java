/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.db;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.helpers.DataStorer;
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

public class Postgres implements Database {

    private static final Logger LOGGER = LoggerFactory.getLogger(Postgres.class);
    private final KubeClient kubeClient;
    private final String namespace;

    protected final String name = "postgresdb";
    private final String secretName = "postgres-db-secret";
    private final String adminUsername = "newuser";
    private final String adminPassword = "testpassword";
    private StatefulSet postgresStatefulset;
    private int port = 5432;

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
                .withPort(port)
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
                                .withContainerPort(port)
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
                                    .withName("POSTGRESQL_USER")
                                    .withValueFrom(new EnvVarSourceBuilder()
                                            .withNewSecretKeyRef("username", secretName, false)
                                            .build())
                                    .build(),
                                new EnvVarBuilder()
                                    .withName("POSTGRESQL_PASSWORD")
                                    .withValueFrom(new EnvVarSourceBuilder()
                                        .withNewSecretKeyRef("password", secretName, false)
                                        .build())
                                .build(),
                                new EnvVarBuilder()
                                    .withName("POSTGRESQL_DATABASE")
                                    .withValue(applicationName)
                                .build()
                            ))
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
        .build();

        postgresStatefulset = kubeClient.getKubernetesClient().resource(postgresStatefulset).inNamespace(namespace).createOrReplace();
        DataStorer.dumpResourceToFile(postgresStatefulset);
        kubeClient.getKubernetesClient().resource(postgresStatefulset).waitUntilReady(60, TimeUnit.SECONDS);
        service = kubeClient.getKubernetesClient().services().inNamespace(namespace).resource(service).createOrReplace();
        DataStorer.dumpResourceToFile(service);
    }

    public void undeployPostgres() {
        LOGGER.info("[{}] [Postgres] Undeploying {}", namespace, name);
        kubeClient.getKubernetesClient().resource(postgresStatefulset).delete();
        kubeClient.getKubernetesClient().resource(service).inNamespace(namespace).delete();
        kubeClient.deleteSecret(namespace, secretName);
    }

    @Override
    public String getJdbcUrl() {
        return "jdbc:postgresql://%s:%s/postgres?user=%s&password=%s".formatted(getName(), port, getUsername(), getPassword());
    }

    @Override
    public String getConnectionUrl() {
        return null;
    }

    @Override
    public String getDriverName() {
        return null;
    }

    @Override
    public String getDriverUrl() {
        return null;
    }

    @Override
    public String getDriverFile() {
        return null;
    }

    @Override
    public String getDriverFilename() {
        return null;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getDatabaseName() {
        return null;
    }

    @Override
    public String getTuneFile() {
        return null;
    }

    @Override
    public String getUsername() {
        return adminUsername;
    }

    @Override
    public String getPassword() {
        return adminPassword;
    }

    public String getSecretName() {
        return secretName;
    }
}
