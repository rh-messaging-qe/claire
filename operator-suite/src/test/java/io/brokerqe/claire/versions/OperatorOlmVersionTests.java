/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.versions;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.KubernetesVersion;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.junit.DisableOnNoPackageManifestFile;
import io.brokerqe.claire.junit.TestMinimumKubernetesVersion;
import io.fabric8.openshift.api.model.operatorhub.packages.v1.ChannelEntry;
import io.fabric8.openshift.api.model.operatorhub.packages.v1.PackageChannel;
import io.fabric8.openshift.api.model.operatorhub.packages.v1.PackageManifest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@DisableOnNoPackageManifestFile
@TestMinimumKubernetesVersion(KubernetesVersion.VERSION_1_26)
public class OperatorOlmVersionTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorOlmVersionTests.class);
    private final String testNamespace = getRandomNamespaceName("operator-version-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }


    @Test
    void testPackageManifestVersions() {
        /*
          oc get catalogsource cs-iib-893878 -n openshift-marketplace
          oc get packagemanifest -l catalog=cs-iib-893878 -n openshift-marketplace
          oc get packagemanifest amq-broker-rhel8|9 -n openshift-marketplace
         */
        Map<String, List<String>> oprVersions = getOrderedProvidedVersions();

        PackageManifest pm = operator.getRelatedPackageManifestOlm(true);
        LOGGER.info("===== Checking out PackageManifest: {} from CatalogSource: {} =====", pm.getMetadata().getName(), pm.getMetadata().getLabels().get("catalog"));

        for (PackageChannel channel : pm.getStatus().getChannels()) {
            List<ChannelEntry> channelEntries = channel.getEntries();
            List<String> oprVersionsExpected = oprVersions.get(channel.getName());

            if (oprVersionsExpected == null) {
                LOGGER.info("[Skipping channel {}]", channel.getName());
                continue;
            }
            // Get versions from channel
            List<String> channelVersions = new ArrayList<>();
            channelEntries.forEach(channelEntry -> {
                channelVersions.add(channelEntry.getVersion());
            });

            LOGGER.info("[Expected {}]: {}", channel.getName(), oprVersionsExpected);
            LOGGER.info("[Channel/CS {}]: {}", channel.getName(), channelVersions);

            LOGGER.info("=== [{}] Compare expected versions vs channel versions ===", channel.getName());
            for (String versionExpected : oprVersionsExpected) {
                boolean contains = channelVersions.stream().anyMatch(channelVersion -> channelVersion.startsWith(versionExpected));
                LOGGER.info("[{}]: {}", versionExpected, contains);
                assertThat("expected version not found!", contains, is(true));
            }

            LOGGER.info("=== [{}] Compare channel vs expected versions ===", channel.getName());
            List<String> verifiedVersions = new ArrayList<>();
            for (ChannelEntry channelEntry : channelEntries) {
                boolean versionContain = false;
                String channelVersion = channelEntry.getVersion();
                String shortChannelVersion, versionMMM;
                // skip automatically built versions 7.10.2-opr-2+0.1680622941.p,  7.11.7-opr-1-1726157430 7.12.3-opr-1+0.1733315503.p
                Pattern pattern = Pattern.compile("((\\d+\\.\\d+\\.\\d+)-opr-\\d+)(.*)");
                Matcher matcher = pattern.matcher(channelVersion);
                if (matcher.matches()) {
                    // convert version to short one 7.10.2-opr-2+0.1680622941.p -> 7.10.2-opr-2
                    shortChannelVersion = matcher.group(1);
                    versionMMM = matcher.group(2);
                } else {
                    throw new ClaireRuntimeException("Unknown version detected! " + channelVersion);
                }

                if (oprVersionsExpected.contains(shortChannelVersion) || verifiedVersions.contains(versionMMM)) {
                    verifiedVersions.add(versionMMM);
                    versionContain = true;
                } else {
                    LOGGER.error("{} -> FALSE; {}", shortChannelVersion, oprVersionsExpected);
                }
                LOGGER.info("[{}] -> {}", shortChannelVersion, oprVersionsExpected.contains(shortChannelVersion));
                assertThat("expected version not found!", versionContain, is(true));
            }
        }
    }

    // Read PACKAGE_MANIFEST file
    private Map<String, List<String>> getOrderedProvidedVersions() {
        String oprVersions = ResourceManager.getEnvironment().getTestUpgradePackageManifestContent();
        String[] versions = oprVersions.split("\\n");
        LOGGER.debug("Provided oprVersions: {}", oprVersions.replaceAll("\\n", ", "));
        Map<String, List<String>> allChannelVersions = new HashMap<>();

        for (String version : versions) {
            // change version format 7.11.0.OPR.3.CR1 --> v7.11.0-opr-3
            String versionOLM = version.substring(0, version.lastIndexOf(".")).replace(".OPR.", "-opr-");
            int dotTwo = version.indexOf(".", version.indexOf(".") + 1);
            String channelStr = version.substring(0, dotTwo) + ".x";
            if (!allChannelVersions.containsKey(channelStr)) {
                allChannelVersions.put(channelStr, new LinkedList<>());
            }
            allChannelVersions.get(channelStr).add(versionOLM);
        }
        LOGGER.info("Ordered expected channel versions: {}", allChannelVersions);
        return allChannelVersions;
    }

}
