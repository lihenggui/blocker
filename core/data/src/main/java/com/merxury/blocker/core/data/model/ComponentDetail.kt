package com.merxury.blocker.core.data.model

import com.merxury.blocker.core.database.cmpdetail.ComponentDetailEntity
import com.merxury.blocker.core.network.model.NetworkComponentDetail
fun NetworkComponentDetail.asEntity() = ComponentDetailEntity(
    name = name,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    contributor = contributor,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock,
)
