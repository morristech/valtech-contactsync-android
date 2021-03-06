#!/bin/bash

set -e
echo $VALTECH_KEYSTORE | base64 --decode > valtech.keystore
set +e
./gradlew assembleRelease
rm valtech.keystore
set -e
cp app/build/outputs/apk/app-release.apk $CIRCLE_ARTIFACTS
