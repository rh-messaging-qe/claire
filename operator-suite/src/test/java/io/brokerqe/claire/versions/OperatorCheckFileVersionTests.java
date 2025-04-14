/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.versions;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.KubernetesVersion;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.DisableOnNoPackageManifestFile;
import io.brokerqe.claire.junit.TestMinimumKubernetesVersion;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

@DisableOnNoPackageManifestFile
@TestMinimumKubernetesVersion(KubernetesVersion.VERSION_1_26)
public class OperatorCheckFileVersionTests extends AbstractSystemTests {
    // Test to check operator.yaml file from examples, which will make sure, that all supported/released + current_candidate versions of images are defined
    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorCheckFileVersionTests.class);

    @Test
    void testOperatorImageVersionsFile() {
        // load examples/operator.yaml file
        Path operatorYamlPath = ArtemisFileProvider.getOperatorInstallFile();
        Deployment operatorDeployment = TestUtils.configFromYaml(operatorYamlPath.toFile(), Deployment.class);
        List<EnvVar> containerEnvVars = operatorDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        List<String> actualImages = containerEnvVars.stream().map(EnvVar::getName).collect(Collectors.toList());

        // RELATED_IMAGE_ActiveMQ_Artemis_Broker_Init_7124
        // RELATED_IMAGE_ActiveMQ_Artemis_Broker_Kubernetes_7124_s390x
        List<String> imageNames = List.of("RELATED_IMAGE_ActiveMQ_Artemis_Broker_Init_", "RELATED_IMAGE_ActiveMQ_Artemis_Broker_Kubernetes_");
        List<String> imageSuffix = List.of("", "_ppc64le", "_s390x");
        List<String> expectedVersions = createShortVersions();
        LOGGER.info("Creating full image names from \n{} \n{} \n{}", imageNames, expectedVersions, imageSuffix);

        List<String> expectedImageFullNames = constructImageNames(imageNames, expectedVersions, imageSuffix);

        for (String expectedFullName : expectedImageFullNames) {
            LOGGER.debug("Expected {} exists: {}", expectedFullName, actualImages.contains(expectedFullName));
            assertThat(actualImages, hasItem(expectedFullName));
        }
    }

    private List<String> constructImageNames(List<String> imageNames, List<String> versions, List<String> imageSuffix) {
        List<String> concatImageNames = new ArrayList<>();

        for (String image : imageNames) {
            for (String version : versions) {
                for (String suffix : imageSuffix) {
                    concatImageNames.add(image + version + suffix);
                }
            }
        }
        return concatImageNames;
    }

    private List<String> createShortVersions() {
        String oprVersions = ResourceManager.getEnvironment().getTestUpgradePackageManifestContent();
        List<String> versions = Arrays.stream(oprVersions.split("\\n")).toList();
        List<String> shortVersions = new ArrayList<>();
        versions.forEach(version -> {
            shortVersions.add(version.substring(0, version.indexOf(".OPR")).replaceAll("\\.", ""));
        });
        LOGGER.debug("Provided versions: {}", shortVersions);
        return shortVersions;
    }
}
