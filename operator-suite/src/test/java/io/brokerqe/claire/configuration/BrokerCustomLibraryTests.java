/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.EnvBuilder;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.db.Postgres;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.KubernetesArchitecture;
import io.brokerqe.claire.junit.DisabledTestArchitecture;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@DisabledTestArchitecture(archs = {KubernetesArchitecture.S390X, KubernetesArchitecture.PPC64LE})
public class BrokerCustomLibraryTests extends AbstractSystemTests {
    private final String testNamespace = getRandomNamespaceName("brkplugin-tests", 2);
    private Postgres postgres;

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        deployPostgres();
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
        postgres.undeployPostgres();
    }

    protected void deployPostgres() {
        postgres = ResourceManager.getPostgresInstance(testNamespace);
        postgres.deployPostgres("db-jar-plugin");
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_32)
    void testPostgresqlDB() {
        String brokerName = "brk-dbplugin";
        String downloadDriverCommand = "mkdir -p /amq/init/config/extra-libs && wget -O /amq/init/config/extra-libs/postgresql.jar %s".formatted(Constants.POSTGRESQL_DRIVER_URL);
        StatefulSet jdbcPatchSs = new StatefulSetBuilder()
            .editOrNewSpec()
                .editOrNewTemplate()
                    .editOrNewSpec()
                        .withInitContainers(
                            new ContainerBuilder()
                                .withName("postgresql-jdbc-driver-init")
                                .withImage("quay.io/rhmessagingqe/alpine-curl:latest")
                                .withVolumeMounts(
                                    new VolumeMountBuilder()
                                        .withName("amq-cfg-dir")
                                        .withMountPath("/amq/init/config")
                                        .build()
                                )
                                .withCommand(List.of("sh", "-c"))
                                .withArgs(downloadDriverCommand)
                                .build()
                        )
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
        BPActiveMQArtemisAddress myAddress = ResourceManager.createBPArtemisAddress("dbplugin", ArtemisConstants.ROUTING_TYPE_ANYCAST);

        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withPersistenceEnabled()
                .endDeploymentPlan()
                .addToEnv(new EnvBuilder()
                    .withName("ARTEMIS_EXTRA_LIBS")
                    .withValue("/amq/init/config/extra-libs")
                    .build()
                )
                .withBrokerProperties(List.of(
                        "storeConfiguration=DATABASE",
                        "storeConfiguration.jdbcDriverClassName=org.postgresql.Driver",
                        "storeConfiguration.jdbcConnectionUrl=" + postgres.getJdbcUrl()
                ))
                .addNewResourceTemplate()
                    .withNewSelector()
                        .withKind("StatefulSet")
                    .endSelector()
                    .editOrNewPatch()
                        .withAdditionalProperties(Map.of(
                                "kind", "StatefulSet",
                                "spec", jdbcPatchSs.getSpec())
                        )
                    .endPatch()
                .endResourceTemplate()
                .withBrokerProperties(myAddress.getPropertiesList())
                .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);

        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        testMessaging(testNamespace, brokerPod, myAddress, 10);

        ResourceManager.deleteArtemis(testNamespace, broker);
    }

}
