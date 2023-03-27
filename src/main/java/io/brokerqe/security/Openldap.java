/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Openldap {

    private static final Logger LOGGER = LoggerFactory.getLogger(Openldap.class);
    protected final KubeClient kubeClient;
    protected final String namespace;
    protected final String podNameLabel = "openldap";
    private final String secretName = "openldap-secret";
    private final File ldifData = new File(Constants.PROJECT_TEST_DIR + "/resources/openldap/users.ldif");

    Map<String, String> secretData = Map.of(
            "adminpassword", "admin",
            "users.ldif", TestUtils.readFileContent(ldifData)
    );

    private Service service = new ServiceBuilder()
        .editOrNewMetadata()
            .withName(podNameLabel)
            .withLabels(Map.of("app.kubernetes.io/name", "openldap"))
        .endMetadata()
        .editOrNewSpec()
            .withType("ClusterIP")
            .withPorts(new ServicePortBuilder()
                .withName("tcp-ldap")
                .withPort(1389)
                .withTargetPort(new IntOrString("tcp-ldap"))
                .build())
            .withSelector(Map.of("app.kubernetes.io/name", podNameLabel))
        .endSpec()
        .build();

    Deployment openldapDeployment = new DeploymentBuilder()
        .withNewMetadata()
            .withName(podNameLabel)
            .withLabels(Map.of("app.kubernetes.io/name", podNameLabel))
        .endMetadata()
        .withNewSpec()
            .withReplicas(1)
            .withNewSelector()
                .addToMatchLabels("app.kubernetes.io/name", podNameLabel)
            .endSelector()
            .withNewTemplate()
                .withNewMetadata()
                    .addToLabels("app.kubernetes.io/name", podNameLabel)
                .endMetadata()
                .withNewSpec()
                    .editOrNewSecurityContext()
                        .withRunAsNonRoot(true)
//                                .withNewSeccompProfile()
//                                    .withType("RuntimeDefault") // localhost
//                                .endSeccompProfile()
                    .endSecurityContext()
                    .withVolumes(
                        new VolumeBuilder()
                        .withName(secretName + "-volume")
                        .withNewSecret()
                            .withSecretName(secretName)
                            .withDefaultMode(420)
                        .endSecret()
                        .build()
                    )
                    .addNewContainer()
                        .withName(podNameLabel)
                        .withImage(Constants.IMAGE_OPENLDAP)
                        .withImagePullPolicy("Always")
                        .editOrNewSecurityContext()
                            .withPrivileged(false)
                            .withAllowPrivilegeEscalation(false)
                            .withRunAsNonRoot(true)
                            .withNewCapabilities()
                                .withDrop("ALL")
                            .endCapabilities()
                        .endSecurityContext()
                        .withPorts(new ContainerPortBuilder()
                            .withName("tcp-ldap")
                            .withContainerPort(1389)
                            .build())
                        .withEnv(List.of(
                            new EnvVarBuilder()
                                .withName("LDAP_ADMIN_USERNAME")
                                .withValue("admin")
                            .build(),
                            new EnvVarBuilder()
                                .withName("LDAP_ADMIN_PASSWORD")
                                .withValueFrom(new EnvVarSourceBuilder()
                                    .withNewSecretKeyRef("adminpassword", secretName, false)
                                    .build())
                            .build(),
                            new EnvVarBuilder()
                                .withName("LDAP_CUSTOM_LDIF_DIR")
                                .withValue("/etc/" + secretName)
                            .build()
                        ))
                        .withVolumeMounts(
                            new VolumeMountBuilder()
                                .withName(secretName + "-volume")
                                .withMountPath("/etc/" + secretName)
                                .withReadOnly()
                            .build()
                        )
                    .endContainer()
                .endSpec()
            .endTemplate()
        .endSpec()
        .build();

    public Openldap(KubeClient kubeClient, String namespace) {
        this.kubeClient = kubeClient;
        this.namespace = namespace;
    }

    public void deployLdap() {
        LOGGER.info("[{}] [LDAP] Deploying {}", namespace, podNameLabel);
        kubeClient.createSecretStringData(namespace, secretName, secretData, true);
        openldapDeployment = kubeClient.getKubernetesClient().resource(openldapDeployment).inNamespace(namespace).createOrReplace();
        service = kubeClient.getKubernetesClient().services().inNamespace(namespace).resource(service).createOrReplace();
    }

    public void undeployLdap() {
        LOGGER.info("[{}] [LDAP] Undeploying {}", namespace, podNameLabel);
        kubeClient.getKubernetesClient().resource(openldapDeployment).delete();
        kubeClient.getKubernetesClient().resource(service).inNamespace(namespace).delete();
        kubeClient.deleteSecret(namespace, secretName);
    }
}
