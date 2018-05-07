package com.merxury.blocker.ui.baseview

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.View

class ContextMenuRecyclerView : RecyclerView {

    private var mContextMenuInfo: RecyclerContextMenuInfo? = null

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun getContextMenuInfo(): ContextMenu.ContextMenuInfo? {
        return mContextMenuInfo
    }

    override fun showContextMenuForChild(originalView: View): Boolean {
        val longPressPosition = getChildAdapterPosition(originalView)
        if (longPressPosition >= 0) {
            val longPressId = adapter.getItemId(longPressPosition)
            mContextMenuInfo = RecyclerContextMenuInfo(longPressPosition, longPressId)
            return super.showContextMenuForChild(originalView)
        }
        return false
    }

    class RecyclerContextMenuInfo(val position: Int, val id: Long) : ContextMenu.ContextMenuInfo

}