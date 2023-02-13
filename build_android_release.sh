#!/usr/bin/env bash

#
# Copyright 2023 Blocker
# Copyright 2022 The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       https://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

# IGNORE this file, it's only used in the internal Google release process

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_OUT=$DIR/app/build/outputs

export JAVA_HOME="$(cd $DIR/../../../prebuilts/studio/jdk/jdk11/linux && pwd )"
echo "JAVA_HOME=$JAVA_HOME"

export ANDROID_HOME="$(cd $DIR/../../../prebuilts/fullsdk/linux && pwd )"
echo "ANDROID_HOME=$ANDROID_HOME"

echo "Copying google-services.json"
cp $DIR/../blocker-prebuilts/google-services.json $DIR/app-compose

echo "Copying local.properties"
cp $DIR/../blocker-prebuilts/local.properties $DIR

cd $DIR

# Build the marketRelease variant
GRADLE_PARAMS=" --stacktrace -Puse-google-services"
$DIR/gradlew :app-compose:clean :app-compose:assembleMarketRelease :app-compose:bundleMarketRelease ${GRADLE_PARAMS}
BUILD_RESULT=$?

# Market release APK
cp $APP_OUT/apk/market/release/app-market-release.apk $DIST_DIR/app-market-release.apk
# Market release bundle
cp $APP_OUT/bundle/marketRelease/app-market-release.aab $DIST_DIR/app-market-release.aab
# Market release bundle mapping
cp $APP_OUT/mapping/marketRelease/mapping.txt $DIST_DIR/mobile-release-aab-mapping.txt

exit $BUILD_RESULT