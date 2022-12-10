package com.merxury.blocker.core.data.model

import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.network.model.NetworkComponentDetail

fun NetworkComponentDetail.asEntity() = ComponentDetail(
    name = name,
    icon = icon,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    author = author,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock
)
