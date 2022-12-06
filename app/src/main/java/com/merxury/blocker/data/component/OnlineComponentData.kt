package com.merxury.blocker.data.component

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class OnlineComponentData(
    var name: String? = null,
    var icon: String? = null,
    @SerializedName("sdk_name")
    var sdkName: String? = null,
    var description: String? = null,
    @SerializedName("disableEffect")
    var disableEffect: String? = null,
    var author: String? = null,
    @SerializedName("added_version")
    var addedVersion: String? = null,
    @SerializedName("recommend_to_block")
    var recommendToBlock: Boolean = false,
)
