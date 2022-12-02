# Broker-QE Java System Tests

## Warning: This project is still under heavy development

This repository contains code for testing AMQ Broker/Artemis operator and it's operands.

It uses fabric8 for communication with Kubernetes API.
For details of usage fabric8 client see perfect [Cheatsheet](https://github.com/fabric8io/kubernetes-client/blob/v6.2.0/doc/CHEATSHEET.md), which contains all resources with examples.

[CRDs](crds) contains Either upstream or downstream CRDs for AMQ Broker 7.10.x. Currently we only support downstream, as upstream versions are different (imports are different) and 
we need to solve this problem nicely. If you're using upstream, please update all imports manually (find all and replace) accordingly.

## How to run tests
Currently ActiveMQArtemis CRD is not working properly in Typed CRD way. Please use typeless or perform following commands
```shell
# Build custom kubernetes-client version from https://github.com/fabric8io/kubernetes-client/pull/4612
git clone https://github.com/fabric8io/kubernetes-client
git fetch origin pull/4612/head:artemis
git checkout artemis
mvn clean install -DskipTests=true -f generator-annotations/pom.xml
#mvn clean install -f java-generator/pom.xml -DskipTests=true
mvn clean install -DskipTests=true

# import this locally build version into claire project
Set <fabric8.version>6.3-SNAPSHOT</fabric8.version in claire/pom.xml

make build_downstream # or build_upstream
```

The very core of this test suite is re-used from [Strimzi systemtest module](https://github.com/strimzi/strimzi-kafka-operator).

# TODO
- [x] Play a little with the code base (see how fabric8 works, how it handles individual resources, etc.)
- [x] Create simple ResourceManager which will managed resources creation/deletion in tests. 
You can find something similar in [strimzi 0.22](https://github.com/strimzi/strimzi-kafka-operator/blob/release-0.22.x/systemtest/src/main/java/io/strimzi/systemtest/resources/ResourceManager.java).
Current implementation in Strimzi main is more focused on Parallel test execution, and it's overkill for this test suite now!
  - This manager should at first handle just Deployments, Secrets, ConfigMaps, etc. Not CRs yet.
- [x] Add `waitFor` for dynamic wait from [Strimzi test module](https://github.com/strimzi/strimzi-kafka-operator/blob/main/test/src/main/java/io/strimzi/test/TestUtils.java)
  - Guess more method from this class could be useful
- [x] Add Broker CRDs into code to make it available in fabric8 client
  ```java
  /**
    * Check why this is not generated because it is needed by client
      */
      public class ArtemisList extends DefaultKubernetesResourceList<ActiveMQArtemis> {
      private static final long serialVersionUID = 1L;
      }

  /**
    * Usage: artemisV1BetaV1().inNamespace(namespace).withName(broker).dostuff()
      */
      public MixedOperation<ActiveMQArtemis, ArtemisList, Resource<ActiveMQArtemis>> artemisV1BetaV1() {
      return client.resources(ActiveMQArtemis.class, ArtemisList.class);
      }
    ```
  - This should be done for all CRs and probably in different class than `KubeClient`
  - Create a simple test which will get Broker CR from OpenShift and check if it has some values
- [x] Add way how to deploy AMQ Broker Operator from code
- [x] Add way how to easily create different CRs for the Operator
- [x] Find a way how to remove [crds](crds) folder and download CRDs as part of every build
- [ ] Add option to build dockerfile with the tests inside
- [x] Add some job which will automatically build the code to avoid introducing failures

# Hints
- Use hamcrest matchers for asserts
- Maybe reuse `Constants` and `Environment` classes if needed
- keep code clean
