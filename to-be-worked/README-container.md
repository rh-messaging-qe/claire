# Container Image Usage

This container image is designed to run the Claire Test Suite.

## Image Components

This container image uses a multi-stage build to optimize caching and includes the following components:

* Base image: Red Hat Universal Base Image (UBI) 9 with OpenJDK 17
* Maven dependencies are cached as an intermediate image
* The final image includes the application files and the required packages and tools for running the tests
* The user can choose between using the kubectl or oc client
* Environment variables are set for Kubernetes and OpenShift configuration
* An entrypoint script is provided for running the tests

## Prerequisites

* Podman

## Pulling the Image

Pull the container image from the registry using the following command:

```bash
podman pull quay.io/rhmessagingqe/claire:latest
```

## Running the Image

Run the container image using the following command:

```bash
podman run --rm -ti quay.io/rhmessagingqe/claire:latest /app/
```

## Customizing the Environment

To customize the environment, you can set environment variables when running the container image. For example:

```bash
podman run --rm -ti -e CLIENT_TYPE=oc -e OCP_API_URL=https://my-ocp-cluster:6443 quay.io/rhmessagingqe/claire:latest
```

This will run the container image with the oc client and the specified OpenShift API URL.
After running the command, you will be logged in to the OpenShift cluster and can interact
with the cluster using the oc command line tool.

## Running the Image with Maven Options

### Openshift
To run the container image and test the Artemis operator on an OpenShift cluster with Maven options, use the following command:

```bash
podman run --rm -e CLIENT_TYPE=oc -e OCP_API_URL=https://my-ocp-cluster:6443 -e OCP_USERNAME=CHANGE_ME -e OCP_PASSWORD=CHANGE_ME  quay.io/rhmessagingqe/claire:latest mvn test -Dmaven.main.skip=true -Dtest=SmokeTests
```

### Kubernetes

To run the container image and test the Artemis operator on a Kubernetes cluster with Maven options, use the following command:

```bash
podman run --rm -ti -v ~/.kube/config:/kube/config quay.io/rhmessagingqe/claire:latest mvn test -Dmaven.main.skip=true -Dtest=SmokeTests
```

This command mounts the Kubernetes configuration file to the container and uses Maven options to skip building the main project and only run the SmokeTests.
Make sure that your Kubernetes configuration file is located at ~/.kube/config. If it is located in a different directory, you should adjust the -v option accordingly.

## Deploying the container and running testsuite in K8s or Open Shift

To deploy the container to Kubernetes/Openshift by using the manifest and secrets, follow these steps:

Create the necessary secrets for OpenShift configuration:

```bash
kubectl create secret generic ocp-config \
  --from-literal=ocp-url='localhost' \
  --from-literal=username=ocp-username='ABC' \
  --from-literal=password=ocp-password='123'
```

Replace localhost, ABC, and 123 with the appropriate values for your OpenShift cluster.
Create a Kubernetes/Openshift manifest file (manifest-smoke-tests.yaml) with the following contents:

Create the necessary secrets for Kubernetes configuration:
```bash
kubectl create secret generic kube-config --from-file=kube-config=~/.kube/config
```

```yaml
apiVersion: v1
kind: Pod
metadata:
name: artemis-operator-test
spec:
  containers:
    - name: artemis-operator-test
      image: quay.io/rhmessagingqe/claire:latest
      command:
        - "mvn"
        - "test"
        - "-Dmaven.main.skip=true"
        - "-Dtest=SmokeTests"
      volumeMounts:
        - name: kube-config
          mountPath: /app/.kube/config
          readOnly: true
  volumes:
    - name: kube-config
      secret:
        secretName: kube-config
```

This manifest file specifies a Pod with a container that runs the container image. 
It also mounts the Kubernetes configuration file as a volume in the container.

or use example manifest from `manifests/*`

### Deploy the Kubernetes/Openshift manifest:

```yaml
kubectl create -f manifest-smoke-tests.yaml
```

This will create the Pod and run the container with the specified image and options.
  