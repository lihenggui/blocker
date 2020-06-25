package com.merxury.blocker.base

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

abstract class BaseLazyFragment : Fragment() {

    private var isViewInitiated = false
    private var isVisibleToUser = false
    private var isDataInitiated = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewInitiated = true
    }


    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        this.isVisibleToUser = isVisibleToUser
        canLoad()
    }

    override fun onResume() {
        super.onResume()
        canLoad()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewInitiated = false
        isDataInitiated = false
    }

    private fun canLoad(){
        if(isViewInitiated && !isDataInitiated && isVisibleToUser){
            loadData()
            isDataInitiated = true
        }
    }

    abstract fun loadData()
}