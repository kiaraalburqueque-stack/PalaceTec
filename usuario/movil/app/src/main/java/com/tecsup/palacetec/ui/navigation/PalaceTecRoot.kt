package com.tecsup.palacetec.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.tecsup.palacetec.ui.auth.SplashScreen
import com.tecsup.palacetec.ui.bets.BetsScreen
import com.tecsup.palacetec.ui.home.HomeScreen
import com.tecsup.palacetec.ui.notifications.NotificationsScreen
import com.tecsup.palacetec.ui.notifications.UnreadViewModel
import com.tecsup.palacetec.ui.profile.ProfileLink
import com.tecsup.palacetec.ui.profile.ProfileScreen
import com.tecsup.palacetec.ui.responsible.ResponsibleScreen
import com.tecsup.palacetec.ui.roulette.RouletteScreen
import com.tecsup.palacetec.ui.settings.PushPermissionRequest
import com.tecsup.palacetec.ui.settings.SettingsScreen
import com.tecsup.palacetec.ui.stats.StatsScreen
import com.tecsup.palacetec.ui.wallet.WalletScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val SPLASH_MILLIS = 1_200L

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
}

enum class Tab(val route: String, val label: String, val title: String, @param:DrawableRes val icon: Int) {
    HOME("home", "Inicio", "PalaceTec", R.drawable.ic_home),
    ROULETTE("roulette", "Ruleta", "Ruleta", R.drawable.ic_roulette),
    BETS("bets", "Apuestas", "Mis apuestas", R.drawable.ic_bets),
    WALLET("wallet", "Billetera", "Billetera", R.drawable.ic_wallet),
}

/** Pantallas que se abren encima de una pestaña, con flecha para volver. */
enum class Section(val route: String, val title: String) {
    NOTIFICATIONS("notifications", "Notificaciones"),
    PROFILE("profile", "Mi perfil"),
    STATS("stats", "Estadísticas"),
    RESPONSIBLE("responsible", "Juego responsable"),
    SETTINGS("settings", "Ajustes"),
}

class SessionViewModel(private val auth: AuthRepository) : ViewModel() {
    val session: StateFlow<Session> = auth.session

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

/** La sesión decide el flujo: al guardarse o perderse el token se cambia de navegación completa. */
@Composable
fun PalaceTecRoot(viewModel: SessionViewModel = appViewModel { SessionViewModel(it.authRepository) }) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    // La marca se ve un momento al abrir la app aunque la sesión se lea al instante.
    var splashShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        splashShown = true
    }
    if (!splashShown) {
        SplashScreen()
        return
    }
    when (val current = session) {
        Session.Loading -> SplashScreen()
        Session.LoggedOut -> AuthFlow()
        // Otra sesión parte de cero: no hereda pestañas ni datos del jugador anterior.
        is Session.LoggedIn -> key(current.token) { PlayerFlow(viewModel::logout) }
    }
}

@Composable
private fun AuthFlow() {
    val nav = rememberNavController()
    // Cada pantalla de acceso pinta su fondo detrás de las barras del sistema y se aparta de ellas.
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        NavHost(nav, startDestination = Routes.LOGIN, modifier = Modifier.padding(padding)) {
            composable(Routes.LOGIN) { LoginScreen(onRegister = { nav.navigate(Routes.REGISTER) }) }
            composable(Routes.REGISTER) { RegisterScreen(onBackToLogin = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun PlayerFlow(
    onLogout: () -> Unit,
    unreadViewModel: UnreadViewModel = appViewModel { UnreadViewModel(it.notificationRepository) },
) {
    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val currentTab = Tab.entries.find { it.route == currentRoute } ?: Tab.HOME
    val section = Section.entries.find { it.route == currentRoute }
    val unread by unreadViewModel.unread.collectAsStateWithLifecycle()
    PushPermissionRequest()
    // La campana se actualiza al entrar, al volver a la app y al cambiar de pantalla.
    LifecycleStartEffect(currentRoute) {
        if (section != Section.NOTIFICATIONS) unreadViewModel.refresh()
        onStopOrDispose { }
    }
    Scaffold(
        topBar = {
            if (section != null) SectionTopBar(section.title, onBack = { nav.popBackStack() })
            else PlayerTopBar(currentTab.title, unread, onOpen = { nav.openSection(it) }, onLogout)
        },
        bottomBar = { if (section == null) PlayerNavigationBar(currentTab) { nav.openTab(it) } },
    ) { padding ->
        NavHost(
            nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    onPlayRoulette = { nav.openTab(Tab.ROULETTE) },
                    onResponsible = { nav.openSection(Section.RESPONSIBLE) },
                )
            }
            composable(Tab.ROULETTE.route) { RouletteScreen() }
            composable(Tab.BETS.route) { BetsScreen() }
            composable(Tab.WALLET.route) { WalletScreen() }
            composable(Section.NOTIFICATIONS.route) { NotificationsScreen() }
            composable(Section.PROFILE.route) {
                ProfileScreen(
                    links = listOf(
                        ProfileLink("Estadísticas", R.drawable.ic_stats) { nav.openSection(Section.STATS) },
                        ProfileLink("Juego responsable", R.drawable.ic_shield) {
                            nav.openSection(Section.RESPONSIBLE)
                        },
                        ProfileLink("Ajustes", R.drawable.ic_settings) { nav.openSection(Section.SETTINGS) },
                    ),
                )
            }
            composable(Section.STATS.route) { StatsScreen() }
            composable(Section.RESPONSIBLE.route) { ResponsibleScreen() }
            composable(Section.SETTINGS.route) { SettingsScreen() }
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

private fun NavHostController.openSection(section: Section) {
    navigate(section.route) { launchSingleTop = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun topBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    titleContentColor = MaterialTheme.colorScheme.primary,
    navigationIconContentColor = MaterialTheme.colorScheme.secondary,
    actionIconContentColor = MaterialTheme.colorScheme.secondary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        colors = topBarColors(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Volver")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(title: String, unread: Long, onOpen: (Section) -> Unit, onLogout: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        colors = topBarColors(),
        actions = {
            IconButton(onClick = { onOpen(Section.NOTIFICATIONS) }) {
                BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                    Icon(
                        painterResource(R.drawable.ic_notifications),
                        contentDescription = if (unread > 0) "Notificaciones: $unread sin leer" else "Notificaciones",
                    )
                }
            }
            IconButton(onClick = { onOpen(Section.PROFILE) }) {
                Icon(painterResource(R.drawable.ic_person), contentDescription = "Mi perfil")
            }
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
