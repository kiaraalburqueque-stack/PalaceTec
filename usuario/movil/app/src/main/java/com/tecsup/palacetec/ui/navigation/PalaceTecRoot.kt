package com.tecsup.palacetec.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tecsup.palacetec.data.local.Session
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.auth.LoginScreen
import com.tecsup.palacetec.ui.auth.RegisterScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

class SessionViewModel(private val auth: AuthRepository) : ViewModel() {
    val session: StateFlow<Session> = auth.session

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

@Composable
fun PalaceTecRoot(viewModel: SessionViewModel = appViewModel { SessionViewModel(it.authRepository) }) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    if (session == Session.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val loggedIn = session is Session.LoggedIn
    val start by rememberSaveable { mutableStateOf(if (loggedIn) Routes.HOME else Routes.LOGIN) }
    val nav = rememberNavController()

    Scaffold { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.LOGIN) { LoginScreen(onRegister = { nav.navigate(Routes.REGISTER) }) }
            composable(Routes.REGISTER) { RegisterScreen(onBackToLogin = { nav.popBackStack() }) }
            composable(Routes.HOME) {
                TextButton(onClick = viewModel::logout) { Text("Cerrar sesión") }
            }
        }
    }

    // La sesión manda: al guardarse o perderse el token se cambia de flujo y se limpia la pila.
    LaunchedEffect(loggedIn) {
        val route = nav.currentDestination?.route ?: return@LaunchedEffect
        val inAuth = route == Routes.LOGIN || route == Routes.REGISTER
        if (loggedIn == inAuth) {
            nav.navigate(if (loggedIn) Routes.HOME else Routes.LOGIN) {
                popUpTo(nav.graph.id) { inclusive = true }
            }
        }
    }
}
