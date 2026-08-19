package com.sfdex.gmbioreactor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.ui.viewmodel.AppListViewModel
import com.sfdex.gmbioreactor.ui.viewmodel.ModelLibraryViewModel

/**
 * Model Picker Screen: choose from presets or customize 5 properties for a target application.
 */
@Composable
fun ModelPickerScreen(
    targetPackage: String,
    targetAppName: String,
    appListViewModel: AppListViewModel,
    modelLibraryViewModel: ModelLibraryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appListState by appListViewModel.uiState.collectAsState()
    val modelLibState by modelLibraryViewModel.uiState.collectAsState()

    val currentAppItem = remember(appListState.allApps, targetPackage) {
        appListState.allApps.find { it.packageName == targetPackage }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Presets, 1: Manual 5-Field Custom
    var selectedProfile by remember(currentAppItem) {
        mutableStateOf<DeviceProfile?>(currentAppItem?.spoofProfile)
    }

    // Manual input states
    var customName by remember(selectedProfile) { mutableStateOf(selectedProfile?.name ?: "") }
    var customManufacturer by remember(selectedProfile) { mutableStateOf(selectedProfile?.manufacturer ?: "") }
    var customBrand by remember(selectedProfile) { mutableStateOf(selectedProfile?.brand ?: "") }
    var customModel by remember(selectedProfile) { mutableStateOf(selectedProfile?.model ?: "") }
    var customDevice by remember(selectedProfile) { mutableStateOf(selectedProfile?.device ?: "") }
    var customProduct by remember(selectedProfile) { mutableStateOf(selectedProfile?.product ?: "") }

    var customError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Target App Status Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    drawable = currentAppItem?.icon,
                    modifier = Modifier.size(48.dp),
                    contentDescription = targetAppName
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = targetAppName.ifBlank { targetPackage },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = targetPackage,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (currentAppItem?.isSpoofConfigured == true && currentAppItem.spoofProfile != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "当前已配置: ${currentAppItem.spoofProfile.name.ifBlank { currentAppItem.spoofProfile.model }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "当前使用系统原生机型 (未开启伪装)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tabs: Presets vs Manual 5-Field
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("从预设库选择") },
                icon = { Icon(Icons.Default.Smartphone, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    // Sync current selected profile to fields
                    selectedProfile?.let {
                        customName = it.name
                        customManufacturer = it.manufacturer
                        customBrand = it.brand
                        customModel = it.model
                        customDevice = it.device
                        customProduct = it.product
                    }
                },
                text = { Text("自定义 5 核心参数") },
                icon = { Icon(Icons.Default.Edit, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content body based on tab
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (selectedTab == 0) {
                // Presets tab
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar
                    OutlinedTextField(
                        value = modelLibState.searchQuery,
                        onValueChange = { modelLibraryViewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索预设机型、品牌或型号...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (modelLibState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { modelLibraryViewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Brand Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(modelLibState.brandNames) { brand ->
                            val isSelected = (brand == ModelLibraryViewModel.BRAND_ALL && modelLibState.selectedBrand == null) ||
                                    (modelLibState.selectedBrand == brand)
                            FilterChip(
                                selected = isSelected,
                                onClick = { modelLibraryViewModel.selectBrand(brand) },
                                label = { Text(brand) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            modelLibState.displayedProfiles,
                            key = { "${it.manufacturer}_${it.brand}_${it.model}_${it.name}_${it.device}" }
                        ) { profile ->
                            val isChosen = selectedProfile?.let {
                                it.manufacturer.equals(profile.manufacturer, ignoreCase = true) &&
                                        it.model.equals(profile.model, ignoreCase = true) &&
                                        it.device.equals(profile.device, ignoreCase = true)
                            } == true

                            PickerProfileItemCard(
                                profile = profile,
                                isSelected = isChosen,
                                onClick = {
                                    selectedProfile = profile
                                    customName = profile.name
                                    customManufacturer = profile.manufacturer
                                    customBrand = profile.brand
                                    customModel = profile.model
                                    customDevice = profile.device
                                    customProduct = profile.product
                                }
                            )
                        }
                    }
                }
            } else {
                // Manual 5-Field Editor Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "手动指定该应用的 5 项核心系统属性：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // Quick presets chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                customName = "Samsung Galaxy S26 Ultra"
                                customManufacturer = "samsung"
                                customBrand = "samsung"
                                customModel = "SM-S9480"
                                customDevice = "e3q"
                                customProduct = "e3qzhx"
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("S26 Ultra 预设", fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                customName = "Xiaomi 15 Pro"
                                customManufacturer = "Xiaomi"
                                customBrand = "Xiaomi"
                                customModel = "24101PNB7C"
                                customDevice = "haotian"
                                customProduct = "haotian"
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("小米 15 Pro", fontSize = 12.sp)
                        }

                        TextButton(
                            onClick = {
                                customName = "Google Pixel 9 Pro"
                                customManufacturer = "Google"
                                customBrand = "google"
                                customModel = "Pixel 9 Pro"
                                customDevice = "caiman"
                                customProduct = "caiman"
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Pixel 9 Pro", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("机型展示别名 (如 Galaxy S26 Ultra)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customManufacturer,
                        onValueChange = { customManufacturer = it },
                        label = { Text("厂商 MANUFACTURER (如 samsung)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customError && customManufacturer.isBlank(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customBrand,
                        onValueChange = { customBrand = it },
                        label = { Text("品牌 BRAND (如 samsung)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customError && customBrand.isBlank(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = { Text("型号 MODEL (如 SM-S9480)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customError && customModel.isBlank(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customDevice,
                        onValueChange = { customDevice = it },
                        label = { Text("设备代号 DEVICE (如 e3q)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customError && customDevice.isBlank(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customProduct,
                        onValueChange = { customProduct = it },
                        label = { Text("产品名称 PRODUCT (如 e3qzhx)") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = customError && customProduct.isBlank(),
                        singleLine = true
                    )

                    if (customError) {
                        Text(
                            text = "5 项核心参数均不能为空，请补齐",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selected profile confirmation banner
        selectedProfile?.let { p ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "拟生效机型: ${p.name.ifBlank { p.model }} (${p.manufacturer} / ${p.model})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action Buttons Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Apply & Force Stop (Instant Activation)
                Button(
                    onClick = {
                        val profileToSave = if (selectedTab == 1) {
                            DeviceProfile(
                                name = customName.ifBlank { customModel },
                                manufacturer = customManufacturer.trim(),
                                brand = customBrand.trim(),
                                model = customModel.trim(),
                                device = customDevice.trim(),
                                product = customProduct.trim()
                            )
                        } else {
                            selectedProfile
                        }

                        if (profileToSave != null && profileToSave.isValid()) {
                            appListViewModel.bindModelToApp(targetPackage, profileToSave)
                            onNavigateBack()
                        } else {
                            customError = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存并立即重启生效")
                }

                // If configured, offer clear/restore button
                if (currentAppItem?.isSpoofConfigured == true) {
                    OutlinedButton(
                        onClick = {
                            appListViewModel.removeAppSpoof(targetPackage)
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("恢复原生")
                    }
                }
            }
        }
    }
}

/**
 * Single selectable profile card for picker list.
 */
@Composable
fun PickerProfileItemCard(
    profile: DeviceProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name.ifBlank { profile.model },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = profile.brand.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OsFeatureBadge(profile)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${profile.manufacturer} / ${profile.model} (device: ${profile.device})",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
