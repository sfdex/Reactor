package com.sfdex.gmbioreactor.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.ui.viewmodel.AppFilterType
import com.sfdex.gmbioreactor.ui.viewmodel.AppListViewModel
import com.sfdex.gmbioreactor.ui.viewmodel.ModelLibraryViewModel

/**
 * State holder for Navigation 3 backstack.
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

    // Handle system back navigation
    BackHandler(enabled = navState.canPop) {
        navState.popBackStack()
    }

    // Trigger snackbars on status message
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
                            is AppRoute.ModelPicker -> "选择机型: ${currentRoute.appName.ifBlank { currentRoute.packageName }}"
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
                    AppListScreenSkeleton(
                        viewModel = appListViewModel,
                        onNavigateToPicker = { pkg, name ->
                            navState.navigate(AppRoute.ModelPicker(packageName = pkg, appName = name))
                        }
                    )
                }

                AppRoute.ModelLibrary -> {
                    ModelLibraryScreenSkeleton(
                        viewModel = modelLibraryViewModel
                    )
                }

                is AppRoute.ModelPicker -> {
                    ModelPickerScreenSkeleton(
                        targetPackage = route.packageName,
                        targetAppName = route.appName,
                        modelLibraryViewModel = modelLibraryViewModel,
                        onProfileSelected = { profile ->
                            appListViewModel.bindModelToApp(route.packageName, profile)
                            navState.popBackStack()
                        }
                    )
                }

                AppRoute.Settings -> {
                    SettingsScreenSkeleton(
                        appListViewModel = appListViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppListScreenSkeleton(
    viewModel: AppListViewModel,
    onNavigateToPicker: (packageName: String, appName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索应用名称或包名...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AppFilterType.entries) { filter ->
                FilterChip(
                    selected = state.filterType == filter,
                    onClick = { viewModel.setFilterType(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "共找到 ${state.displayedApps.size} 个应用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.displayedApps, key = { it.packageName }) { appItem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (appItem.isSpoofConfigured)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appItem.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = appItem.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (appItem.isSpoofConfigured && appItem.spoofProfile != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "伪装型号: ${appItem.spoofProfile.name.ifBlank { appItem.spoofProfile.model }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (appItem.isSpoofConfigured) {
                            Switch(
                                checked = appItem.isSpoofEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.toggleAppSpoof(appItem.packageName, enabled)
                                }
                            )
                            IconButton(onClick = { viewModel.removeAppSpoof(appItem.packageName) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "取消伪装",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            TextButton(onClick = { onNavigateToPicker(appItem.packageName, appItem.appName) }) {
                                Text("设置机型")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelLibraryScreenSkeleton(
    viewModel: ModelLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startCreateNewProfile() }) {
                Icon(Icons.Default.Add, contentDescription = "新增自定义机型")
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("全局检索品牌/型号/代码...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Brand selector chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.brandNames) { brand ->
                    val isSelected = (brand == ModelLibraryViewModel.BRAND_ALL && state.selectedBrand == null) ||
                            (state.selectedBrand == brand)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectBrand(brand) },
                        label = { Text(brand) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "显示 ${state.displayedProfiles.size} 款机型预设",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.displayedProfiles, key = { "${it.manufacturer}_${it.model}_${it.name}" }) { profile ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name.ifBlank { profile.model },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = profile.brand.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Model: ${profile.model} | Device: ${profile.device} | Product: ${profile.product}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelPickerScreenSkeleton(
    targetPackage: String,
    targetAppName: String,
    modelLibraryViewModel: ModelLibraryViewModel,
    onProfileSelected: (DeviceProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by modelLibraryViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Target app banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "正在为以下应用配置机型：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = targetAppName.ifBlank { targetPackage },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = targetPackage,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { modelLibraryViewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索预设机型...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(state.brandNames) { brand ->
                val isSelected = (brand == ModelLibraryViewModel.BRAND_ALL && state.selectedBrand == null) ||
                        (state.selectedBrand == brand)
                FilterChip(
                    selected = isSelected,
                    onClick = { modelLibraryViewModel.selectBrand(brand) },
                    label = { Text(brand) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.displayedProfiles, key = { "${it.manufacturer}_${it.model}_${it.name}" }) { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProfileSelected(profile) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = profile.name.ifBlank { profile.model },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${profile.manufacturer} / ${profile.model} (${profile.device})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreenSkeleton(
    appListViewModel: AppListViewModel,
    modifier: Modifier = Modifier
) {
    val state by appListViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "环境诊断与权限状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Root status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.isRootAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (state.isRootAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Root (su) 权限状态", fontWeight = FontWeight.Medium)
                        Text(
                            text = if (state.isRootAvailable) "已授权 (uid=0)" else "未检测到 su 权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Zygisk module status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.isZygiskModuleInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (state.isZygiskModuleInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Zygisk 模块部署状态", fontWeight = FontWeight.Medium)
                        Text(
                            text = if (state.isZygiskModuleInstalled) "模块已安装在 /data/adb/modules/" else "未检测到模块目录 (请确认在 Magisk/APatch/KernelSU 刷入)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Config file path
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("配置文件路径", fontWeight = FontWeight.Medium)
                        Text(
                            text = "/data/adb/gmbioreactor/config.json",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action Buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "快捷操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = { appListViewModel.forceStopAllEnabled() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重启所有已开启伪装的应用进程")
                }

                TextButton(
                    onClick = { appListViewModel.refreshRootStatus() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新检测 Root 与模块状态")
                }
            }
        }
    }
}
