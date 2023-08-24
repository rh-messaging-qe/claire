# Use consistent and specific version tags for base images
ARG UBI_VERSION=9
ARG OPENJDK_VERSION=17
ARG UBI_TAG=1.14
ARG IMAGE=registry.access.redhat.com/ubi${UBI_VERSION}/openjdk-${OPENJDK_VERSION}:${UBI_TAG}

FROM registry.access.redhat.com/ubi9/openjdk-17:1.14 AS base
# FROM $IMAGE AS base
USER root

# Temporary fix for handling internal certificates. We need a more permanent solution for handling certificates
# with upstream and downstream services. I attempted to use the include argument with the environment variable,
# but it did not work.

ENV MAVEN_OPTS="-Xmx2g -Dmaven.repo.local=/build/.m2 -Dmaven.artifact.threads=42\
    -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true\
    -Dmaven.wagon.http.ssl.ignore.validity.dates=true -Dmaven.resolver.transport=wagon"
ENV MAVEN_ARGS="--settings ./.mvn/settings.xml -T 1.5C "

RUN microdnf -y install git git gzip make unzip wget && \
    wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/bin/yq && \
    chmod +x /usr/bin/yq && \
    microdnf clean all

RUN curl -sSL https://archive.apache.org/dist/maven/maven-3/3.9.1/binaries/apache-maven-3.9.1-bin.tar.gz | \
    tar xz -C /opt && ln -s /opt/apache-maven-3.9.1/bin/mvn /usr/local/bin/mvn

# Cache maven dependencies as an intermediate container image
FROM base as dependencies

COPY pom.xml /build/
COPY .mvn /build/.mvn
COPY common/pom.xml /build/common/
COPY operator-suite/pom.xml /build/operator-suite/
COPY standalone-suite/pom.xml /build/standalone-suite/

WORKDIR /build/

# MDEP-204 Workaround for old Maven
# Build dependency common
RUN mvn install -pl common -DskipTests -Dcheckstyle.skip

# Cache all dependencies
RUN mvn --batch-mode dependency:go-offline dependency:resolve-plugins dependency:copy-dependencies

# Use multi-stage build
FROM base AS build

# Copy cached dependencies
COPY --from=dependencies /build/.m2 /build/.m2

# Copy application files and set the working directory
COPY . /build

# Use a root user
USER root

# Set the working directory
WORKDIR /build

# Install required packages
RUN microdnf -y install git git gzip make unzip wget && \
    wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/bin/yq && \
    chmod +x /usr/bin/yq && \
    microdnf clean all

# Run build process
RUN make build

# Final stage for the tests image
FROM base AS final
# Use a root user
USER root

# Copy the application files from the build stage
COPY --from=build --chown=1001:0 /build /app

# Set the entrypoint script and ensure it is executable
RUN chmod a+x /app/container_data/entrypoint.sh

# Allow the user to choose which client to use
ENV CLIENT_TYPE=${CLIENT_TYPE:-kubectl}

# Set environment variables for OpenShift
ENV OCP_API_URL=https://api.openshift:6443
ENV OCP_USERNAME=CHANGE_ME
ENV OCP_PASSWORD=CHANGE_ME

# Add metadata labels
LABEL name="Red Hat Messaging QE - Artemis Cloud Claire Test Suite" \
      run="podman run --rm -ti <image_name:tag> /app/"

# Install required packages and oc tools
# It's not compatible with aarch64
RUN bash -c "$(curl -L https://raw.githubusercontent.com/cptmorgan-rh/install-oc-tools/master/install-oc-tools.sh)"\
    -s "--latest" && microdnf clean all

# Set the working directory
WORKDIR /app

# Use a non-root user (created in the base image)
USER 1001

# Set the entrypoint and default command
ENTRYPOINT ["/app/container_data/entrypoint.sh"]
CMD ["/bin/bash"]
