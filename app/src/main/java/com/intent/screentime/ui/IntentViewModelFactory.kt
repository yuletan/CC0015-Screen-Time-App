package com.intent.screentime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Tiny factory so ViewModels can take plain constructor dependencies from the
 * [com.intent.screentime.core.di.AppContainer] without an annotation processor.
 */
class IntentViewModelFactory<VM : ViewModel>(
    private val create: () -> VM,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
