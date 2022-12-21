package com.merxury.blocker.core.model.data

import androidx.compose.ui.graphics.vector.ImageVector

data class DropDownMenuItem(
    val text: String,
    val trailingIcon: ImageVector? = null,
    val onClick: () -> Unit
)