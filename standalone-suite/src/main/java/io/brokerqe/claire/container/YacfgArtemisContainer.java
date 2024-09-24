/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import com.sun.security.auth.module.UnixSystem;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;

import java.util.ArrayList;
import java.util.List;

public class YacfgArtemisContainer extends AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(YacfgArtemisContainer.class);

    private static final String YACFG_COMMAND = "yacfg";
    private static final String PROFILE_ARG_KEY = "--profile";
    private static final String OUTPUT_ARG_KEY = "--output";
    public static final String OPT_PARAM_KEY = "--opt";
    public static final String TUNE_PARAM_KEY = "--tune";

    private static final String CLAIRE_STANDALONE_YACFG_PROFILES = "yacfg-profiles";
    private static final String YACFG_CONTAINER_DATA_DIR = "/data";
    private static final String YACFG_CONTAINER_OUTPUT_DIR = YACFG_CONTAINER_DATA_DIR + "/output";
    private static final String YACFG_CONTAINER_PROFILES_DIR = YACFG_CONTAINER_DATA_DIR + "/profiles";
    private static final String YACFG_CONTAINER_TEMPLATES_DIR = YACFG_CONTAINER_DATA_DIR + "/templates";
    public static final String YACFG_CONTAINER_CLAIRE_STANDALONE_DIR =  YACFG_CONTAINER_DATA_DIR + Constants.FILE_SEPARATOR
            + "claire";
    public static final String YACFG_CONTAINER_TUNES_DIR =  YACFG_CONTAINER_DATA_DIR + Constants.FILE_SEPARATOR
            + "tunes";

    private final List<String> params = new ArrayList<>();

    public YacfgArtemisContainer(String name) {
        super(name, ENVIRONMENT_STANDALONE.getYacfgArtemisContainerImage());
        this.name = name;
        this.type = ContainerType.YACFG_ARTEMIS;

        String profilesOverrideDir = ENVIRONMENT_STANDALONE.getYacfgArtemisProfilesOverrideDir();
        if (profilesOverrideDir != null) {
            withFileSystemBind(profilesOverrideDir, YACFG_CONTAINER_PROFILES_DIR, BindMode.READ_WRITE);
        }

        String templatesOverrideDir = ENVIRONMENT_STANDALONE.getYacfgArtemisTemplatesOverrideDir();
        if (templatesOverrideDir != null) {
            withFileSystemBind(templatesOverrideDir, YACFG_CONTAINER_TEMPLATES_DIR, BindMode.READ_ONLY);
        }

        String claireYacfgProfiles = TestUtils.getProjectRelativeFile(CLAIRE_STANDALONE_YACFG_PROFILES);
        withFileSystemBind(claireYacfgProfiles, YACFG_CONTAINER_CLAIRE_STANDALONE_DIR, BindMode.READ_ONLY);
    }

    public void withParams(List<String> params) {
        this.params.addAll(params);
    }

    public void withParam(String paramName, String paramValue) {
        params.add(paramName);
        params.add(paramValue);
    }

    public void withHostOutputDir(String hostOutputDir) {
        TestUtils.createDirectory(hostOutputDir);
        withFileSystemBind(hostOutputDir, YACFG_CONTAINER_OUTPUT_DIR, BindMode.READ_WRITE);
    }

    public void withProfile(String profileFile) {
        params.add(PROFILE_ARG_KEY);
        params.add(YACFG_CONTAINER_CLAIRE_STANDALONE_DIR + Constants.FILE_SEPARATOR + profileFile);
    }

    public void start() {
        LOGGER.info("[{}] - About to start", name);

        List<String> yacfgCmdArgs = new ArrayList<>();
        yacfgCmdArgs.add(YACFG_COMMAND);

        if (params.stream().noneMatch(e -> e.contains(PROFILE_ARG_KEY))) {
            yacfgCmdArgs.add(PROFILE_ARG_KEY);
            yacfgCmdArgs.add(YACFG_CONTAINER_CLAIRE_STANDALONE_DIR + Constants.FILE_SEPARATOR + getDefaultProfile());
        }

        if (params.stream().noneMatch(e -> e.contains(OUTPUT_ARG_KEY))) {
            yacfgCmdArgs.add(OUTPUT_ARG_KEY);
            yacfgCmdArgs.add(YACFG_CONTAINER_OUTPUT_DIR);
        }

        addCustomBuildParams();

        yacfgCmdArgs.addAll(params);

        container.withCreateContainerCmdModifier(cmd -> cmd.withCmd(yacfgCmdArgs.toArray(new String[0])));
        withUserId(String.valueOf(new UnixSystem().getUid()));
        super.start();
    }

    private String getDefaultProfile() {
        return ENVIRONMENT_STANDALONE.getYacfgArtemisProfile();
    }

    private void addCustomBuildParams() {
        if (!ENVIRONMENT_STANDALONE.isUpstreamArtemis()) {
            String bootstrapOpts = "bootstrap_apps=[";
            bootstrapOpts += "{url: redhat-branding, war: redhat-branding.war},";
            bootstrapOpts += "{url: artemis-plugin, war: artemis-plugin.war},";
            bootstrapOpts += "{url: console, war: hawtio.war},";
            bootstrapOpts += "{url: metrics, war: metrics.war}";
            bootstrapOpts += "]";
            params.addAll(List.of(OPT_PARAM_KEY, bootstrapOpts));
        }
    }
}
