# Broker-QE Java System Tests

## Warning: This project is still under heavy development

This repository contains code for testing AMQ Broker/Artemis operator and it's operands.

It uses fabric8 for communication with Kubernetes API.
For details of usage fabric8 client see perfect [Cheatsheet](https://github.com/fabric8io/kubernetes-client/blob/v6.2.0/doc/CHEATSHEET.md), which contains all resources with examples.

[CRDs](crds) contains Either upstream or downstream CRDs for AMQ Broker 7.10.x. Currently we only support downstream, as upstream versions are different (imports are different) and 
we need to solve this problem nicely. If you're using upstream, please update all imports manually (find all and replace) accordingly.

## How to run tests

Currently, we support running `test_smoke_downstream` and `test_smoke_upstream` (which executes `SmokeTests` and `ClusteredOperatorSmokeTests`) tests using `make`.
These targets will execute all necessary steps - clean, download files, build and generate needed code and finally execute tests.
Review `Makefile` for more details.


## List of available Environment Variables

| Name                      | Description                           | Default                     | Possible values                         |
|---------------------------|---------------------------------------|-----------------------------|-----------------------------------------|
| ARTEMIS_VERSION           | ArtemisCloud Version to be used       | 7.10.1                      | <url>                                   |
| OPERATOR_IMAGE            | ArtemisCloud Operator image url       | not set                     | <url>                                   |
| BROKER_IMAGE              | Broker image url                      | not set                     | <url>                                   |
| BROKER_INIT_IMAGE         | Broker init image url                 | not set                     | <url>                                   |
| BUNDLE_IMAGE              | Bundle image url                      | not set                     | <url>                                   |
| DISABLE_RANDOM_NAMESPACES | Whether to use random string suffices | not set (`false`)           | `true`, `false`                         |
| TEST_LOG_LEVEL            | Set logging level of test suite       | `INFO` set in `logback.xml` | `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF` |
| CLUSTER_OPERATOR_MANAGED  | Whether test suite managed CO or not  | true                        | `false`                                 |
| OPERATOR_INSTALL_ZIP      | Url to zip file with install/examples | 7.10.1 url                  | <url>                                   |
| OPERATOR_VERSION_UPSTREAM | Version/branch of repository          | main                        | <branch>                                |

## Setting log level
Currently, there is supported `TEST_LOG_LEVEL` environment variable, which can set desired logging level of test suite.
By default, we use `INFO` level. Supported values are `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`.

## Disable generating random suffices for namespaces
Set environment variable `DISABLE_RANDOM_NAMESPACES` to `false` to disable using random suffix in namespace names. This is useful for debugging purposes.
Example `test-namespace-2a6c` will be always `test-namespace`.

## TODO
- [ ] Add OLM installation
- [ ] Implement management client (amqx)
- [ ] Implement usage of [containerized clients](https://github.com/rh-messaging/cli-java)
- [ ] (?) Use Velocity project to manage usage of ArtemisCloud CRD versions
- [ ] Add option to build dockerfile with the tests inside

## Hints
- Use hamcrest matchers for asserts
- keep code clean

## Attribution
The very core of this test suite is based on [Strimzi systemtest module](https://github.com/strimzi/strimzi-kafka-operator).
