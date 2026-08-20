package com.sfdex.gmbioreactor.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sfdex.gmbioreactor.ui.screens.AppListScreen
import com.sfdex.gmbioreactor.ui.screens.ModelLibraryScreen
import com.sfdex.gmbioreactor.ui.screens.ModelPickerScreen
import com.sfdex.gmbioreactor.ui.screens.SettingsScreen
import com.sfdex.gmbioreactor.ui.viewmodel.AppListViewModel
import com.sfdex.gmbioreactor.ui.viewmodel.ModelLibraryViewModel

/**
 * State holder for Navigation backstack.
 */
class AppNavigationState(initialRoute: AppRoute = AppRoute.AppList) {
    val backStack = mutableStateListOf<AppRoute>(initialRoute)

    val currentRoute: AppRoute
        get() = backStack.lastOrNull() ?: AppRoute.AppList

    val canPop: Boolean
        get() = backStack.size > 1

    fun navigate(route: AppRoute) {
        backStack.add(route)
    }

    fun popBackStack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }

    fun navigateToTab(route: AppRoute) {
        if (currentRoute == route && backStack.size == 1) return
        backStack.clear()
        backStack.add(route)
    }
}

@Composable
fun rememberAppNavigationState(initialRoute: AppRoute = AppRoute.AppList): AppNavigationState {
    return remember { AppNavigationState(initialRoute) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    appListViewModel: AppListViewModel = viewModel(),
    modelLibraryViewModel: ModelLibraryViewModel = viewModel(),
    navState: AppNavigationState = rememberAppNavigationState()
) {
    val currentRoute = navState.currentRoute
    val snackbarHostState = remember { SnackbarHostState() }

    val appListState by appListViewModel.uiState.collectAsState()
    val modelLibraryState by modelLibraryViewModel.uiState.collectAsState()

    // Persistent scroll & UI states for each tab across navigation
    val appListScrollState = rememberLazyListState()
    val modelLibraryScrollState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    // Handle system back navigation
    BackHandler(enabled = navState.canPop) {
        navState.popBackStack()
    }

    // Trigger snackbars on status messages
    LaunchedEffect(appListState.statusMessage) {
        appListState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            appListViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(modelLibraryState.statusMessage) {
        modelLibraryState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            modelLibraryViewModel.clearStatusMessage()
        }
    }

    val isSubScreen = currentRoute is AppRoute.ModelPicker

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            AppRoute.AppList -> "GMBioreactor 机型伪装"
                            AppRoute.ModelLibrary -> "机型预设库"
                            is AppRoute.ModelPicker -> "机型配置 · ${currentRoute.appName.ifBlank { currentRoute.packageName }}"
                            AppRoute.Settings -> "运行状态与设置"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (isSubScreen) {
                        IconButton(onClick = { navState.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            if (!isSubScreen) {
                NavigationBar {
                    NavigationTab.entries.forEach { tab ->
                        val selected = when (tab) {
                            NavigationTab.APP_LIST -> currentRoute is AppRoute.AppList
                            NavigationTab.MODEL_LIBRARY -> currentRoute is AppRoute.ModelLibrary
                            NavigationTab.SETTINGS -> currentRoute is AppRoute.Settings
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navState.navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        NavigationTab.APP_LIST -> Icons.Default.Android
                                        NavigationTab.MODEL_LIBRARY -> Icons.Default.PhoneAndroid
                                        NavigationTab.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val route = currentRoute) {
                AppRoute.AppList -> {
                    AppListScreen(
                        viewModel = appListViewModel,
                        listState = appListScrollState,
                        onNavigateToPicker = { pkg, name ->
                            navState.navigate(AppRoute.ModelPicker(packageName = pkg, appName = name))
                        }
                    )
                }

                AppRoute.ModelLibrary -> {
                    ModelLibraryScreen(
                        viewModel = modelLibraryViewModel,
                        listState = modelLibraryScrollState
                    )
                }

                is AppRoute.ModelPicker -> {
                    ModelPickerScreen(
                        targetPackage = route.packageName,
                        targetAppName = route.appName,
                        appListViewModel = appListViewModel,
                        modelLibraryViewModel = modelLibraryViewModel,
                        onNavigateBack = { navState.popBackStack() }
                    )
                }

                AppRoute.Settings -> {
                    SettingsScreen(
                        appListViewModel = appListViewModel,
                        scrollState = settingsScrollState
                    )
                }
            }
        }
    }
}
