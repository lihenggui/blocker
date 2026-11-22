// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

internal enum class Encoding { NUMBER, NUMBERS, STRING, UID_CHAIN }
internal data class Field(val number: Int, val name: String, val encoding: Encoding)
internal data class AtomSchema(val id: Int, val name: String, val fields: List<Field>)

// Selected fields of AOSP stats/atoms.proto and framework_extension_atoms.proto.
internal val wakeAtoms = listOf(
    AtomSchema(
        476,
        "ServiceRequestEventReported",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "callerUid", Encoding.NUMBER),
            Field(3, "action", Encoding.STRING),
            Field(4, "request_type", Encoding.NUMBER),
            Field(6, "startType", Encoding.NUMBER),
            Field(7, "request_process_name", Encoding.STRING),
            Field(8, "component", Encoding.STRING),
            Field(10, "package", Encoding.STRING),
            Field(11, "callerPackage", Encoding.STRING),
            Field(12, "requester_process_state", Encoding.NUMBER),
            Field(13, "service_process_state", Encoding.NUMBER),
            Field(5, "fg_required", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        475,
        "BroadcastDeliveryEventReported",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "callerUid", Encoding.NUMBER),
            Field(3, "action", Encoding.STRING),
            Field(4, "receiver_type", Encoding.NUMBER),
            Field(5, "startType", Encoding.NUMBER),
            Field(10, "package", Encoding.STRING),
            Field(11, "callerPackage", Encoding.STRING),
            Field(16, "sender_proc_state", Encoding.NUMBER),
            Field(17, "receiver_process_state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        477,
        "ProviderAcquisitionEventReported",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "callerUid", Encoding.NUMBER),
            Field(3, "startType", Encoding.NUMBER),
            Field(5, "package", Encoding.STRING),
            Field(6, "callerPackage", Encoding.STRING),
            Field(7, "client_process_state", Encoding.NUMBER),
            Field(8, "provider_process_state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        48,
        "AppStartOccurred",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "package", Encoding.STRING),
            Field(4, "component", Encoding.STRING),
            Field(5, "callerPackage", Encoding.STRING),
            Field(3, "startType", Encoding.NUMBER),
            Field(16, "source_type", Encoding.NUMBER),
            Field(8, "reason", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        632,
        "BalAllowed",
        listOf(
            Field(1, "component", Encoding.STRING),
            Field(3, "callerUid", Encoding.NUMBER),
            Field(4, "real_calling_uid", Encoding.NUMBER),
            Field(2, "allowed_reason", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        169,
        "ProcessStartTime",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "pid", Encoding.NUMBER),
            Field(3, "process", Encoding.STRING),
            Field(4, "startType", Encoding.NUMBER),
            Field(5, "process_start_time_millis", Encoding.NUMBER),
            Field(9, "component", Encoding.STRING),
            Field(10, "action", Encoding.STRING),
            Field(11, "hosting_type_id", Encoding.NUMBER),
            Field(12, "trigger_type", Encoding.NUMBER),
            Field(13, "callerUid", Encoding.NUMBER),
            Field(14, "caller_process_name", Encoding.STRING),
            Field(15, "content_provider_authority", Encoding.STRING),
            Field(16, "is_stable", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        373,
        "AppProcessDied",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "process", Encoding.STRING),
            Field(3, "reason", Encoding.NUMBER),
            Field(4, "sub_reason", Encoding.NUMBER),
            Field(5, "importance", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        254,
        "AppFreezeChanged",
        listOf(
            Field(2, "pid", Encoding.NUMBER),
            Field(3, "process", Encoding.STRING),
            Field(7, "targetUid", Encoding.NUMBER),
            Field(1, "state", Encoding.NUMBER),
            Field(6, "unfreeze_reason_v2", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        3,
        "ProcessStateChanged",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "process", Encoding.STRING),
            Field(3, "package", Encoding.STRING),
            Field(4, "state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        43,
        "IsolatedUidChanged",
        listOf(
            Field(1, "parent_uid", Encoding.NUMBER),
            Field(2, "isolated_uid", Encoding.NUMBER),
            Field(3, "event", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        8,
        "ScheduledJobStateChanged",
        listOf(
            Field(1, "attribution_node", Encoding.UID_CHAIN),
            Field(2, "job_name", Encoding.STRING),
            Field(6, "job_id", Encoding.NUMBER),
            Field(40, "namespace_hash", Encoding.STRING),
            Field(3, "state", Encoding.NUMBER),
            Field(4, "internal_stop_reason", Encoding.NUMBER),
            Field(17, "public_stop_reason", Encoding.NUMBER),
            Field(34, "is_periodic", Encoding.NUMBER),
            Field(32, "is_requested_as_user_initiated_job", Encoding.NUMBER),
            Field(21, "num_previous_attempts", Encoding.NUMBER),
            Field(14, "has_content_trigger_constraint", Encoding.NUMBER),
            Field(13, "has_connectivity_constraint", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        7,
        "SyncStateChanged",
        listOf(
            Field(1, "attribution_node", Encoding.UID_CHAIN),
            Field(3, "state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        368,
        "AlarmScheduled",
        listOf(
            Field(1, "callerUid", Encoding.NUMBER),
            Field(2, "is_exact", Encoding.NUMBER),
            Field(3, "is_wakeup", Encoding.NUMBER),
            Field(4, "is_allow_while_idle", Encoding.NUMBER),
            Field(5, "is_alarm_clock", Encoding.NUMBER),
            Field(6, "is_repeating", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        35,
        "WakeupAlarmOccurred",
        listOf(
            Field(1, "attribution_node", Encoding.UID_CHAIN),
            Field(3, "package", Encoding.STRING),
            Field(2, "tag", Encoding.STRING),
        ),
    ),
    AtomSchema(
        367,
        "AlarmBatchDelivered",
        listOf(
            Field(1, "num_alarms", Encoding.NUMBER),
            Field(2, "wakeups", Encoding.NUMBER),
            Field(3, "uids", Encoding.NUMBERS),
            Field(4, "num_alarms_per_uid", Encoding.NUMBERS),
            Field(5, "num_wakeups_per_uid", Encoding.NUMBERS),
        ),
    ),
    AtomSchema(
        60,
        "ForegroundServiceStateChanged",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "component", Encoding.STRING),
            Field(3, "state", Encoding.NUMBER),
            Field(7, "callerUid", Encoding.NUMBER),
            Field(16, "fgs_types", Encoding.NUMBER),
            Field(12, "fgs_duration_ms", Encoding.NUMBER),
            Field(5, "fgs_start_reason_code", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        99,
        "ServiceStateChanged",
        listOf(
            Field(1, "targetUid", Encoding.NUMBER),
            Field(2, "package", Encoding.STRING),
            Field(3, "component", Encoding.STRING),
            Field(4, "state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        244,
        "NotificationReported",
        listOf(
            Field(2, "targetUid", Encoding.NUMBER),
            Field(3, "package", Encoding.STRING),
            Field(1, "event_id", Encoding.NUMBER),
            Field(4, "instance_id", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        11,
        "LongPartialWakelockStateChanged",
        listOf(
            Field(1, "attribution_node", Encoding.UID_CHAIN),
            Field(4, "state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        258,
        "AppStandbyBucketChanged",
        listOf(
            Field(1, "package", Encoding.STRING),
            Field(2, "user_id", Encoding.NUMBER),
            Field(3, "bucket", Encoding.NUMBER),
            Field(4, "main_reason", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        33,
        "InteractiveStateChanged",
        listOf(
            Field(1, "state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        21,
        "DeviceIdleModeStateChanged",
        listOf(
            Field(1, "state", Encoding.NUMBER),
        ),
    ),
    AtomSchema(
        20,
        "BatterySaverModeStateChanged",
        listOf(
            Field(1, "state", Encoding.NUMBER),
        ),
    ),
).associateBy(AtomSchema::id)
