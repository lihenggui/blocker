package com.merxury.blocker.core.data.model

import com.merxury.blocker.core.database.cmpdetail.ComponentDetailEntity
import com.merxury.blocker.core.network.model.NetworkComponentDetail

fun NetworkComponentDetail.asEntity() = ComponentDetailEntity(
    name = name,
    icon = icon,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    author = author,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock
)
