package com.tecsup.palacetec.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tecsup.palacetec.AppContainer
import com.tecsup.palacetec.PalaceTecApp

/** Crea el ViewModel con las dependencias de PalaceTecApp. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { create((this[APPLICATION_KEY] as PalaceTecApp).container) }
        },
    )
