#!/sbin/sh
#
# Copyright 2025 Blocker
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Blocker Recovery Script - Entry Point
# This script is executed by recovery when flashing the APK as a ZIP
#
# Note: This script uses only basic shell commands to ensure compatibility
# with minimal recovery environments without requiring busybox.
#

OUTFD=$2
ZIPFILE="$3"

ui_print() {
    echo -e "ui_print $1\nui_print" >> /proc/self/fd/$OUTFD
}

ui_print "****************************************"
ui_print " IFW Recovery Tool"
ui_print "****************************************"
ui_print ""

# Mount /data partition if not already mounted
if [ ! -d "/data/system" ]; then
    ui_print "- Mounting /data partition..."
    mount /data 2>/dev/null
    if [ ! -d "/data/system" ]; then
        ui_print "! Failed to mount /data partition"
        ui_print "! Please ensure your device is decrypted"
        exit 1
    fi
fi

IFW_DIR="/data/system/ifw"

# Check if IFW directory exists
if [ ! -d "$IFW_DIR" ]; then
    ui_print "- IFW directory does not exist"
    ui_print "- Nothing to clean"
    ui_print ""
    ui_print "- Done!"
    exit 0
fi

if [ -z "$(ls -A "$IFW_DIR" 2>/dev/null)" ]; then
    ui_print "- No IFW rule files found"
    ui_print "- Nothing to clean"
else
    ui_print "- Found IFW rule files"
    ui_print "- Removing all IFW rules..."
    rm -rf "$IFW_DIR"/*
    if [ $? -eq 0 ]; then
        ui_print "- Successfully removed all IFW rules"
    else
        ui_print "! Error removing some files"
        ui_print "! Please check manually after reboot"
    fi
fi

ui_print ""
ui_print "****************************************"
ui_print " Recovery Complete!"
ui_print " Please reboot your device."
ui_print "****************************************"

exit 0
