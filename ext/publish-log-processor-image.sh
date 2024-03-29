#!/bin/bash -e

PROJECT="log-processor"
IMAGE_NAME="core-ng-project/${PROJECT}"
VERSION="3.0.0"

../gradlew -p ${PROJECT} check docker

az account set -s "${FTI_QA_SUBSCRIPTION_ID}"
az acr build --registry FTIDevAcr --image ${IMAGE_NAME}:${VERSION} --timeout 600 --debug ../build/ext/${PROJECT}/docker
#az acr build --registry FTIDevAcr --image ${IMAGE_NAME}:${VERSION} --image ${IMAGE_NAME}:latest --timeout 600 --debug ../build/ext/${PROJECT}/docker
#az acr build --registry FTIUatAcr --image ${IMAGE_NAME}:${VERSION} --image ${IMAGE_NAME}:latest --timeout 600 --debug ../build/ext/${PROJECT}/docker

#az account set -s "${WONDER_PROD_SUBSCRIPTION_ID}"
#az acr build --registry RFProdV2ACR --image ${IMAGE_NAME}:${VERSION} --image ${IMAGE_NAME}:latest --timeout 600 --debug ../build/ext/${PROJECT}/docker