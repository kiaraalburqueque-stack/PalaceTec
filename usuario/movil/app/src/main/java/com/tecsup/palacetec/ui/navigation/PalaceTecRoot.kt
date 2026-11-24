package com.tecsup.palacetec.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tecsup.palacetec.R
import com.tecsup.palacetec.data.local.Session
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.ui.appViewModel
import com.tecsup.palacetec.ui.auth.LoginScreen
import com.tecsup.palacetec.ui.auth.RegisterScreen
import com.tecsup.palacetec.ui.bets.BetsScreen
import com.tecsup.palacetec.ui.home.HomeScreen
import com.tecsup.palacetec.ui.roulette.RouletteScreen
import com.tecsup.palacetec.ui.wallet.WalletScreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
}

enum class Tab(val route: String, val label: String, val title: String, @DrawableRes val icon: Int) {
    HOME("home", "Inicio", "PalaceTec", R.drawable.ic_home),
    ROULETTE("roulette", "Ruleta", "Ruleta", R.drawable.ic_roulette),
    BETS("bets", "Apuestas", "Mis apuestas", R.drawable.ic_bets),
    WALLET("wallet", "Billetera", "Billetera", R.drawable.ic_wallet),
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
    val start by rememberSaveable { mutableStateOf(if (loggedIn) Tab.HOME.route else Routes.LOGIN) }
    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.entries.find { it.route == currentRoute }

    Scaffold(
        topBar = { if (currentTab != null) PlayerTopBar(currentTab.title, viewModel::logout) },
        bottomBar = { if (currentTab != null) PlayerNavigationBar(currentTab) { nav.openTab(it) } },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.LOGIN) { LoginScreen(onRegister = { nav.navigate(Routes.REGISTER) }) }
            composable(Routes.REGISTER) { RegisterScreen(onBackToLogin = { nav.popBackStack() }) }
            composable(Tab.HOME.route) { HomeScreen(onPlayRoulette = { nav.openTab(Tab.ROULETTE) }) }
            composable(Tab.ROULETTE.route) { RouletteScreen() }
            composable(Tab.BETS.route) { BetsScreen() }
            composable(Tab.WALLET.route) { WalletScreen() }
        }
    }

    // La sesión manda: al guardarse o perderse el token se cambia de flujo y se limpia la pila.
    LaunchedEffect(loggedIn) {
        val route = nav.currentDestination?.route ?: return@LaunchedEffect
        val inAuth = route == Routes.LOGIN || route == Routes.REGISTER
        if (loggedIn == inAuth) {
            nav.navigate(if (loggedIn) Tab.HOME.route else Routes.LOGIN) {
                popUpTo(nav.graph.id) { inclusive = true }
            }
            // Otro jugador no debe heredar el estado guardado de las pestañas.
            if (!loggedIn) Tab.entries.forEach { nav.clearBackStack(it.route) }
        }
    }
}

/** Inicio queda siempre debajo y cada pestaña conserva su estado (por ejemplo, la sala elegida). */
private fun NavHostController.openTab(tab: Tab) {
    if (currentDestination?.route == tab.route) return
    if (tab == Tab.HOME) {
        popBackStack(Tab.HOME.route, inclusive = false, saveState = true)
        return
    }
    navigate(tab.route) {
        popUpTo(Tab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(title: String, onLogout: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.secondary,
        ),
        actions = {
            IconButton(onClick = onLogout) {
                Icon(painterResource(R.drawable.ic_logout), contentDescription = "Cerrar sesión")
            }
        },
    )
}

@Composable
private fun PlayerNavigationBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
