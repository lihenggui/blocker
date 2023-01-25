#!/usr/bin/env bash

#
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
cd $DIR

# Build
GRADLE_PARAMS=" --stacktrace"
$DIR/gradlew :app:clean :app:assemble ${GRADLE_PARAMS}
BUILD_RESULT=$?

# FOSS debug
cp $APP_OUT/apk/foss/debug/app-foss-debug.apk $DIST_DIR

# FOSS release
cp $APP_OUT/apk/foss/release/app-foss-release.apk $DIST_DIR

# Market debug
cp $APP_OUT/apk/market/debug/app-market-debug.apk $DIST_DIR/app-market-debug.apk

# Market release
cp $APP_OUT/apk/market/release/app-market-release.apk $DIST_DIR/app-market-release.apk
#cp $APP_OUT/mapping/release/mapping.txt $DIST_DIR/mobile-release-apk-mapping.txt

# Build App Bundles
# Don't clean here, otherwise all apks are gone.
$DIR/gradlew :app:bundle ${GRADLE_PARAMS}

# FOSS debug
cp $APP_OUT/bundle/fossDebug/app-foss-debug.aab $DIST_DIR/app-foss-debug.aab

# FOSS release
cp $APP_OUT/bundle/fossRelease/app-foss-release.aab $DIST_DIR/app-foss-release.aab

# Market debug
cp $APP_OUT/bundle/marketDebug/app-market-debug.aab $DIST_DIR/app-market-debug.aab

# Market release
cp $APP_OUT/bundle/marketRelease/app-market-release.aab $DIST_DIR/app-market-release.aab
#cp $APP_OUT/mapping/marketRelease/mapping.txt $DIST_DIR/mobile-release-aab-mapping.txt
BUILD_RESULT=$?

exit $BUILD_RESULT