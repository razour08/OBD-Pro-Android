package com.ramzi.obdpro.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating ObdViewModel with application context.
 * Required because the ViewModel needs Context for service binding.
 */
class ObdViewModelFactory(private val applicationContext: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ObdViewModel::class.java)) {
            return ObdViewModel(applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
