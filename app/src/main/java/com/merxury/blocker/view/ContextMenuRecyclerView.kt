/*
 * Copyright 2023 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.view

import android.content.Context
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ContextMenuRecyclerView : RecyclerView {

    private var contextMenuInfo: RecyclerContextMenuInfo? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle,
    )

    override fun getContextMenuInfo(): ContextMenu.ContextMenuInfo? {
        return contextMenuInfo
    }

    override fun showContextMenuForChild(originalView: View): Boolean {
        val longPressPosition = getChildAdapterPosition(originalView)
        adapter?.let {
            if (longPressPosition >= 0) {
                val longPressId = it.getItemId(longPressPosition)
                contextMenuInfo = RecyclerContextMenuInfo(longPressPosition, longPressId)
                return super.showContextMenuForChild(originalView)
            }
        }
        return false
    }

    class RecyclerContextMenuInfo(val position: Int, val id: Long) : ContextMenu.ContextMenuInfo
}
