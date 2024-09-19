# Broker-QE Java System Tests

## Warning: This project is still under heavy development

Test suite for standalone and operator-based deployments of ActiveMQ Artemis.
* Standalone test suite (codename `Phoebe`) is based on [test-containers](https://www.testcontainers.org/) for testing of [Apache Artemis](https://github.com/apache/activemq-artemis) broker.
* Operator test suite (codename `Claire`), uses fabric8 java client, which exercises [ArtemisCloud](https://github.com/artemiscloud/) deployment on Kubernetes-based platforms.

## Prerequisites

To be able to successfully build whole project, you need to have pre-built CRDs for ArtemisCloud project.
This can be easily done using [crd-jar-maker](https://gitlab.cee.redhat.com/amq-broker/crd-jar-maker) project.
Once this is installed into your local m2/maven repository, following issue should disappear.
`Could not resolve dependencies for project io.brokerqe:operator-suite:jar:0.1-SNAPSHOT: io.brokerqe:artemiscloud-crd:jar`

## How to build project and run tests
Use `makefile` targets to build project using following command (when prerequisite is satisfied) from `project-root/Makefile` file.
```bash
make build
```
This will build whole project for you.

## How to run tests

For detailed instructions please see README.md file for `standalone-suite` and `operator-suite`.
They contain more details about their specifics.

To quickly execute SmokeTests for each test suite, after building of project execute
```bash
make standalone_test_smoke
make operator_test_smoke
```

If you would like to execute all tests execute following command.
```bash
make standalone_test
make operator_test
```

If you would like to start only some tests, set `TESTS` environment variable, which is passed as
`-Dtest=<TESTS>` argument into maven. Execute same command as above.

## How to build container image

To build container image executed the command:
```bash
make -f Makefile.downstream container_build
```

once the image is built you can push it to quay.io:
```bash
podman login --username you_user_name https://quay.io
make -f Makefile.downstream container_push
```

To execute the tests using the container you can use the command:
```bash
operator-suite/container/scripts/run-test.sh --make-envvar OLM=true --image-tag amq-broker-lpt
```

You may check the run-test.sh help with:
```bash
operator-suite/container/scripts/run-test.sh --help
```

## List of shared Environment Variables

| Name                      | Description                                      | Default                     | Possible values                                  |
|---------------------------|--------------------------------------------------|-----------------------------|--------------------------------------------------|
| ARTEMIS_VERSION           | ArtemisCloud Version to be used (Makefile)       | 7.10.2                      | \<major\>.\<minor\>.\<micro\>                    |
| ARTEMIS_TEST_VERSION      | ArtemisCloud Version to be used by tests         | not set                     | \<major\>.\<minor\>                              |
| LOGS_LOCATION             | Location where to generate collected logs        | `test-logs`                 | \<directory\>                                    |
| TEST_LOG_LEVEL            | Set logging level of test suite                  | `INFO` set in `logback.xml` | `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF` |
| COLLECT_TEST_DATA         | Whether to gather test data on error or not      | `true`                      | `true`, `false`                                  |
| TESTS | Which tests to execute (maven syntax) | not set | <mvn-regexp> |

## More information
For more information about subprojects - refer to README.md files in `operator-suite` or `standalone-suite` folders, which contain more details about usage, more environment variables, etc.

## Hints
- keep code clean
