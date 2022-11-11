DOCKERFILE         ?= Dockerfile
DOCKER_REGISTRY    ?= quay.io
DOCKER_ORG         ?= $(USER)
DOCKER_TAG         ?= latest
PROJECT_NAME       ?= thor

all: java_build docker_build docker_push

build: java_build
clean: java_clean

java_build:
	mvn clean install -DskipTests

java_clean:
	mvn clean

.PHONY: build clean
