// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.model.wake.WakeContinuation
import com.merxury.blocker.core.model.wake.WakeExemption
import com.merxury.blocker.core.model.wake.WakeFindingDay
import com.merxury.blocker.core.model.wake.WakeHoldAssociation
import com.merxury.blocker.core.model.wake.WakeHoldPeriod
import com.merxury.blocker.core.model.wake.WakeHoldProcess
import com.merxury.blocker.core.model.wake.WakeIdentity
import com.merxury.blocker.core.model.wake.WakeProtection
import com.merxury.blocker.core.model.wake.WakeRecord
import com.merxury.blocker.core.model.wake.WakeRelation
import com.merxury.blocker.core.model.wake.WakeSourceStatus
import com.merxury.blocker.core.model.wake.WakeVerdictDay
import com.merxury.blocker.core.database.wake.WakeContinuation as WakeContinuationEntity
import com.merxury.blocker.core.database.wake.WakeExemption as WakeExemptionEntity
import com.merxury.blocker.core.database.wake.WakeFindingDay as WakeFindingDayEntity
import com.merxury.blocker.core.database.wake.WakeHoldAssociation as WakeHoldAssociationEntity
import com.merxury.blocker.core.database.wake.WakeHoldPeriod as WakeHoldPeriodEntity
import com.merxury.blocker.core.database.wake.WakeHoldProcess as WakeHoldProcessEntity
import com.merxury.blocker.core.database.wake.WakeIdentity as WakeIdentityEntity
import com.merxury.blocker.core.database.wake.WakeProtection as WakeProtectionEntity
import com.merxury.blocker.core.database.wake.WakeRecord as WakeRecordEntity
import com.merxury.blocker.core.database.wake.WakeRelation as WakeRelationEntity
import com.merxury.blocker.core.database.wake.WakeSourceStatus as WakeSourceStatusEntity
import com.merxury.blocker.core.database.wake.WakeVerdictDay as WakeVerdictDayEntity

internal fun WakeContinuationEntity.asModel() = WakeContinuation(
    outgoing = outgoing,
    incoming = incoming,
)

internal fun WakeContinuation.asEntity() = WakeContinuationEntity(
    outgoing = outgoing,
    incoming = incoming,
)

internal fun WakeExemptionEntity.asModel() = WakeExemption(
    target = target,
    idleWhitelist = idleWhitelist,
    standbyBucket = standbyBucket,
    backgroundRestricted = backgroundRestricted,
    checkedAt = checkedAt,
)

internal fun WakeExemption.asEntity() = WakeExemptionEntity(
    target = target,
    idleWhitelist = idleWhitelist,
    standbyBucket = standbyBucket,
    backgroundRestricted = backgroundRestricted,
    checkedAt = checkedAt,
)

internal fun WakeFindingDayEntity.asModel() = WakeFindingDay(
    id = id,
    day = day,
    target = target,
    pathKey = pathKey,
    trigger = trigger,
    hold = hold,
    actor = actor,
    detail = detail,
    actorInBackground = actorInBackground,
    afterKill = afterKill,
    justification = justification,
    confidence = confidence,
    chainRoot = chainRoot,
    chainDepth = chainDepth,
    count = count,
    durationNanos = durationNanos,
    screenOffNanos = screenOffNanos,
    lastSeenNanos = lastSeenNanos,
    downstreamStarts = downstreamStarts,
)

internal fun WakeFindingDay.asEntity() = WakeFindingDayEntity(
    id = id,
    day = day,
    target = target,
    pathKey = pathKey,
    trigger = trigger,
    hold = hold,
    actor = actor,
    detail = detail,
    actorInBackground = actorInBackground,
    afterKill = afterKill,
    justification = justification,
    confidence = confidence,
    chainRoot = chainRoot,
    chainDepth = chainDepth,
    count = count,
    durationNanos = durationNanos,
    screenOffNanos = screenOffNanos,
    lastSeenNanos = lastSeenNanos,
    downstreamStarts = downstreamStarts,
)

internal fun WakeHoldAssociationEntity.asModel() = WakeHoldAssociation(
    periodId = periodId,
    target = target,
    uid = uid,
    component = component,
    sourceProcess = sourceProcess,
    sourceUid = sourceUid,
    boundMillis = boundMillis,
    activeMillis = activeMillis,
)

internal fun WakeHoldAssociation.asEntity() = WakeHoldAssociationEntity(
    periodId = periodId,
    target = target,
    uid = uid,
    component = component,
    sourceProcess = sourceProcess,
    sourceUid = sourceUid,
    boundMillis = boundMillis,
    activeMillis = activeMillis,
)

internal fun WakeHoldPeriodEntity.asModel() = WakeHoldPeriod(
    periodId = periodId,
    startMillis = startMillis,
    endMillis = endMillis,
    checkedAt = checkedAt,
)

internal fun WakeHoldPeriod.asEntity() = WakeHoldPeriodEntity(
    periodId = periodId,
    startMillis = startMillis,
    endMillis = endMillis,
    checkedAt = checkedAt,
)

internal fun WakeHoldProcessEntity.asModel() = WakeHoldProcess(
    periodId = periodId,
    target = target,
    uid = uid,
    process = process,
    screenOffMillis = screenOffMillis,
    screenOnMillis = screenOnMillis,
    fgsScreenOffMillis = fgsScreenOffMillis,
    fgsScreenOnMillis = fgsScreenOnMillis,
)

internal fun WakeHoldProcess.asEntity() = WakeHoldProcessEntity(
    periodId = periodId,
    target = target,
    uid = uid,
    process = process,
    screenOffMillis = screenOffMillis,
    screenOnMillis = screenOnMillis,
    fgsScreenOffMillis = fgsScreenOffMillis,
    fgsScreenOnMillis = fgsScreenOnMillis,
)

internal fun WakeIdentityEntity.asModel() = WakeIdentity(
    uid = uid,
    packageName = packageName,
    revision = revision,
    versionCode = versionCode,
    signatures = signatures,
    components = components,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
)

internal fun WakeIdentity.asEntity() = WakeIdentityEntity(
    uid = uid,
    packageName = packageName,
    revision = revision,
    versionCode = versionCode,
    signatures = signatures,
    components = components,
    firstSeen = firstSeen,
    lastSeen = lastSeen,
)

internal fun WakeProtectionEntity.asModel() = WakeProtection(
    pathId = pathId,
    caller = caller,
    target = target,
    component = component,
    action = action,
    proof = proof,
    disabledByUser = disabledByUser,
    configured = configured,
    method = method,
    callerRevision = callerRevision,
    targetRevision = targetRevision,
    targetSigners = targetSigners,
    previousState = previousState,
    pendingChange = pendingChange,
)

internal fun WakeProtection.asEntity() = WakeProtectionEntity(
    pathId = pathId,
    caller = caller,
    target = target,
    component = component,
    action = action,
    proof = proof,
    disabledByUser = disabledByUser,
    configured = configured,
    method = method,
    callerRevision = callerRevision,
    targetRevision = targetRevision,
    targetSigners = targetSigners,
    previousState = previousState,
    pendingChange = pendingChange,
)

internal fun WakeRecordEntity.asModel() = WakeRecord(
    id = id,
    boot = boot,
    elapsedNanos = elapsedNanos,
    wallMillis = wallMillis,
    source = source,
    kind = kind,
    callerUid = callerUid,
    targetUid = targetUid,
    callerPackage = callerPackage,
    targetPackage = targetPackage,
    component = component,
    action = action,
    startType = startType,
    pid = pid,
    processName = processName,
    attributes = attributes,
)

internal fun WakeRecord.asEntity() = WakeRecordEntity(
    id = id,
    boot = boot,
    elapsedNanos = elapsedNanos,
    wallMillis = wallMillis,
    source = source,
    kind = kind,
    callerUid = callerUid,
    targetUid = targetUid,
    callerPackage = callerPackage,
    targetPackage = targetPackage,
    component = component,
    action = action,
    startType = startType,
    pid = pid,
    processName = processName,
    attributes = attributes,
)

internal fun WakeRelationEntity.asModel() = WakeRelation(
    requestId = requestId,
    processId = processId,
    evidence = evidence,
)

internal fun WakeRelation.asEntity() = WakeRelationEntity(
    requestId = requestId,
    processId = processId,
    evidence = evidence,
)

internal fun WakeSourceStatusEntity.asModel() = WakeSourceStatus(
    source = source,
    checkedAt = checkedAt,
    status = status,
)

internal fun WakeSourceStatus.asEntity() = WakeSourceStatusEntity(
    source = source,
    checkedAt = checkedAt,
    status = status,
)

internal fun WakeVerdictDayEntity.asModel() = WakeVerdictDay(
    day = day,
    target = target,
    verdict = verdict,
    revives = revives,
    noneRevives = noneRevives,
    afterKillRevives = afterKillRevives,
    afterKillNoneRevives = afterKillNoneRevives,
    peerBackgroundNoneRevives = peerBackgroundNoneRevives,
    screenOffHoldNoneNanos = screenOffHoldNoneNanos,
    wakesOthers = wakesOthers,
    downstreamStarts = downstreamStarts,
)

internal fun WakeVerdictDay.asEntity() = WakeVerdictDayEntity(
    day = day,
    target = target,
    verdict = verdict,
    revives = revives,
    noneRevives = noneRevives,
    afterKillRevives = afterKillRevives,
    afterKillNoneRevives = afterKillNoneRevives,
    peerBackgroundNoneRevives = peerBackgroundNoneRevives,
    screenOffHoldNoneNanos = screenOffHoldNoneNanos,
    wakesOthers = wakesOthers,
    downstreamStarts = downstreamStarts,
)
