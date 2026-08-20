package com.sfdex.reactor.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sfdex.reactor.data.model.DeviceProfile
import com.sfdex.reactor.ui.viewmodel.ModelLibraryViewModel

/**
 * Modern Material 3 Model Library Screen with full-screen scrolling and sticky brand chips.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelLibraryScreen(
    viewModel: ModelLibraryViewModel,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val state by viewModel.uiState.collectAsState()
    var profileToDelete by remember { mutableStateOf<DeviceProfile?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // 1. Search Bar (scrolls naturally with content, non-sticky)
            item(key = "model_search_bar") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索品牌、机型名称、型号或代号...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            }

            // 2. Brand Filter Chips & Summary Row (stickyHeader - sticks to top upon scrolling)
            stickyHeader(key = "brand_chips_bar") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
                    ) {
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
                                    label = { Text(brand) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Summary row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "显示 ${state.displayedProfiles.size} 款机型预设",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (state.customProfiles.isNotEmpty()) {
                                Text(
                                    text = "自定义: ${state.customProfiles.size} 款",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 3. Content Area (Loading, Empty, or Profile Cards)
            if (state.isLoading) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            } else if (state.displayedProfiles.isEmpty()) {
                item(key = "empty_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "未找到匹配的机型预设",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.searchQuery.isNotEmpty() || state.selectedBrand != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.setSearchQuery("")
                                        viewModel.selectBrand(ModelLibraryViewModel.BRAND_ALL)
                                    }
                                ) {
                                    Text("重置检索条件")
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    state.displayedProfiles,
                    key = { "${it.manufacturer}_${it.brand}_${it.model}_${it.name}_${it.device}" }
                ) { profile ->
                    val isCustom = state.customProfiles.any { it.name.equals(profile.name, ignoreCase = true) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        DeviceProfileCard(
                            profile = profile,
                            isCustom = isCustom,
                            onEdit = { viewModel.startEditProfile(profile) },
                            onDelete = { profileToDelete = profile }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        ExtendedFloatingActionButton(
            onClick = { viewModel.startCreateNewProfile() },
            icon = { Icon(Icons.Default.Add, contentDescription = "新增机型") },
            text = { Text("新增自定义机型") },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    // Create / Edit Profile Dialog
    if (state.isCreatingNew || state.editingProfile != null) {
        val editing = state.editingProfile ?: DeviceProfile()
        DeviceProfileEditDialog(
            initialProfile = editing,
            isNew = state.isCreatingNew,
            onDismiss = { viewModel.dismissEditDialog() },
            onSave = { profile ->
                viewModel.saveCustomProfile(profile)
            }
        )
    }

    // Delete Confirmation Dialog
    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("确认删除自定义机型") },
            text = { Text("确定要删除自定义机型「${profile.name.ifBlank { profile.model }}」吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomProfile(profile.name)
                        profileToDelete = null
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { profileToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Detailed card component for displaying a device profile and its 5 core properties.
 */
@Composable
fun DeviceProfileCard(
    profile: DeviceProfile,
    isCustom: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCustom)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Name & Brand Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profile.name.ifBlank { profile.model },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isCustom) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "自定义",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = profile.brand.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp
                        )
                    }

                    OsFeatureBadge(profile)

                    if (isCustom && onEdit != null && onDelete != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.6.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 5 Core Properties Grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    PropertyItem(label = "MANUFACTURER", value = profile.manufacturer, modifier = Modifier.weight(1f))
                    PropertyItem(label = "BRAND", value = profile.brand, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    PropertyItem(label = "MODEL", value = profile.model, modifier = Modifier.weight(1f))
                    PropertyItem(label = "DEVICE", value = profile.device, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    PropertyItem(label = "PRODUCT", value = profile.product, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PropertyItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dialog for creating or editing custom device profile with 5 core fields + name.
 */
@Composable
fun DeviceProfileEditDialog(
    initialProfile: DeviceProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (DeviceProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile.name) }
    var manufacturer by remember { mutableStateOf(initialProfile.manufacturer) }
    var brand by remember { mutableStateOf(initialProfile.brand) }
    var model by remember { mutableStateOf(initialProfile.model) }
    var device by remember { mutableStateOf(initialProfile.device) }
    var product by remember { mutableStateOf(initialProfile.product) }

    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNew) "新增自定义机型" else "编辑机型参数")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "请填写 5 项系统核心属性 (System Properties)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Templates Chip Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = {
                            name = "Samsung Galaxy S26 Ultra (SM-S9480)"
                            manufacturer = "samsung"
                            brand = "samsung"
                            model = "SM-S9480"
                            device = "e3q"
                            product = "e3qzhx"
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("填入 S26U 模板", fontSize = 11.sp)
                    }

                    TextButton(
                        onClick = {
                            name = "Xiaomi 15 Pro (24101PNB7C)"
                            manufacturer = "Xiaomi"
                            brand = "Xiaomi"
                            model = "24101PNB7C"
                            device = "haotian"
                            product = "haotian"
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("填入小米15模板", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称 (如 Galaxy S26 Ultra)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("厂商 (ro.product.manufacturer)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError && manufacturer.isBlank(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("品牌 (ro.product.brand)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError && brand.isBlank(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("型号 (ro.product.model)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError && model.isBlank(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = device,
                    onValueChange = { device = it },
                    label = { Text("设备代号 (ro.product.device)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError && device.isBlank(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = product,
                    onValueChange = { product = it },
                    label = { Text("产品名称 (ro.product.name)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError && product.isBlank(),
                    singleLine = true
                )

                if (showError) {
                    Text(
                        text = "5 项核心参数均不能为空，请补齐后保存",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val profile = DeviceProfile(
                        name = name.ifBlank { model },
                        manufacturer = manufacturer.trim(),
                        brand = brand.trim(),
                        model = model.trim(),
                        device = device.trim(),
                        product = product.trim()
                    )
                    if (profile.isValid()) {
                        onSave(profile)
                    } else {
                        showError = true
                    }
                }
            ) {
                Text("保存机型")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Visual badge for specialized OS simulation (HyperOS / HarmonyOS).
 */
@Composable
fun OsFeatureBadge(profile: DeviceProfile, modifier: Modifier = Modifier) {
    val b = profile.brand.lowercase()
    val m = profile.manufacturer.lowercase()
    val isXiaomi = b.contains("xiaomi") || b.contains("redmi") || m.contains("xiaomi")
    val isHuawei = b.contains("huawei") || b.contains("honor") || m.contains("huawei") || m.contains("honor")

    if (isXiaomi) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = modifier
        ) {
            Text(
                text = "HyperOS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }
    } else if (isHuawei) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier
        ) {
            Text(
                text = "HarmonyOS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }
    }
}
