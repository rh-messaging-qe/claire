# Broker-QE Java System Tests

## Warning: This project is still under heavy development

This repository contains code for testing AMQ Broker/Artemis operator and it's operands.

It uses fabric8 for communication with Kubernetes API.
For details of usage fabric8 client see perfect [Cheatsheet](https://github.com/fabric8io/kubernetes-client/blob/v6.3.0/doc/CHEATSHEET.md), which contains all resources with examples.

[CRDs](crds) contains Either upstream or downstream CRDs for AMQ Broker 7.10.x. Currently we only support downstream, as upstream versions are different (imports are different) and 
we need to solve this problem nicely. If you're using upstream, please update all imports manually (find all and replace) accordingly.

## How to run tests

Test suite expects you to be logged into some kind of Kubernetes instance (minikube, k3s, Kubernetes, ..).
Currently, we support running `test_smoke_downstream` and `test_smoke_upstream` (which executes `SmokeTests` and `ClusteredOperatorSmokeTests`) tests using `make`.
These targets will execute all necessary steps - clean, download files, build and generate needed code and finally execute tests.
Review `Makefile` for more details.

To execute specific tests with maven, you need to build project (make) and execute maven tests, with skipping clean & compilation part
```bash
make build_upstream
mvn test -Dmaven.main.skip=true -Dtest=<my-specific-test>
```


## List of available Environment Variables

| Name                      | Description                                      | Default                     | Possible values                                  |
|---------------------------|--------------------------------------------------|-----------------------------|--------------------------------------------------|
| ARTEMIS_VERSION           | ArtemisCloud Version to be used (Makefile)       | 7.10.2                      | <url>                                            |
| ARTEMIS_TEST_VERSION      | ArtemisCloud Version to be used by tests         | not set                     | <major>.<minor>                                  |
| OPERATOR_IMAGE            | ArtemisCloud Operator image url                  | not set                     | <url>                                            |
| BROKER_IMAGE              | Broker image url                                 | not set                     | <url>                                            |
| BROKER_INIT_IMAGE         | Broker init image url                            | not set                     | <url>                                            |
| BUNDLE_IMAGE              | Bundle image url                                 | not set                     | <url>                                            |
| DISABLE_RANDOM_NAMESPACES | Whether to use random string suffices            | not set (`false`)           | `true`, `false`                                  |
| LOGS_LOCATION             | Location where to generate collected logs        | `test-logs`                 | <directory>                                      |
| TEST_LOG_LEVEL            | Set logging level of test suite                  | `INFO` set in `logback.xml` | `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF` |
| CLUSTER_OPERATOR_MANAGED  | Whether test suite manages CO or not (Makefile)  | `true`                      | `false`                                          |
| COLLECT_TEST_DATA         | Whether to gather test data on error or not      | `true`                      | `true`, `false`                                  |
| OPERATOR_INSTALL_ZIP      | Url to zip file with install/examples (Makefile) | 7.10.1 url                  | <url>                                            |
| OPERATOR_VERSION_UPSTREAM | Version/branch of repository (Makefile)          | main                        | <branch>                                         |

## Setting log level
Currently, there is supported `TEST_LOG_LEVEL` environment variable, which can set desired logging level of test suite.
By default, we use `INFO` level. Supported values are `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`.

## Disable generating random suffices for namespaces
Set environment variable `DISABLE_RANDOM_NAMESPACES` to `false` to disable using random suffix in namespace names. This is useful for debugging purposes.
Example `test-namespace-2a6c` will be always `test-namespace`.

## TODO
- [ ] Add OLM installation
- [ ] Implement management client (amqx)
- [ ] (?) Use Velocity project to manage usage of ArtemisCloud CRD versions
- [ ] Add option to build dockerfile with the tests inside

## Hints
- Use hamcrest matchers for asserts
- keep code clean

## Attribution
The very core of this test suite is based on [Strimzi systemtest module](https://github.com/strimzi/strimzi-kafka-operator).
