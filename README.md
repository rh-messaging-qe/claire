# Broker-QE Java System Tests

This repository contains code for testing AMQ Broker/Artemis operator and it's operands.

It uses fabric8 for communication with Kubernetes API.

[CRDs](crds) contains CRDs for AMQ Broker 7.10.x. In todo there is a task which should deal with it's removal.

The core of this test suite is re-used from [Strimzi systemtest module](https://github.com/strimzi/strimzi-kafka-operator).

# TODO
- [x] Play a little with the code base (see how fabric8 works, how it handles individual resources, etc.)
- [ ] Create simple ResourceManager which will managed resources creation/deletion in tests. 
You can find something similar in [strimzi 0.22](https://github.com/strimzi/strimzi-kafka-operator/blob/release-0.22.x/systemtest/src/main/java/io/strimzi/systemtest/resources/ResourceManager.java).
Current implementation in Strimzi main is more focused on Parallel test execution, and it's overkill for this test suite now!
  - This manager should at first handle just Deployments, Secrets, ConfigMaps, etc. Not CRs yet.
- [ ] Add `waitFor` for dynamic wait from [Strimzi test module](https://github.com/strimzi/strimzi-kafka-operator/blob/main/test/src/main/java/io/strimzi/test/TestUtils.java)
  - Guess more method from this class could be useful
- [ ] Add Broker CRDs into code to make it available in fabric8 client
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
  - This should dbe done for all CRs and probably in different class than `KubeClient`
  - Create a simple test which will get Broker CR from OpenShift and check if it has some values
- [ ] Add way how to deploy AMQ Broker Operator from code
- [ ] Add way how to easily create different CRs for the Operator
- [ ] Find a way how to remove [crds](crds) folder and download CRDs as part of every build
- [ ] Add option to build dockerfile with the tests inside
- [ ] Add some job which will automatically build the code to avoid introducing failures

# Hints
- Use hamcrest matchers for asserts
- Maybe reuse `Constants` and `Environment` classes if needed
- keep code clean

