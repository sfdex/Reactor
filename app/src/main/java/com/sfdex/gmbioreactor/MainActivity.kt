package com.sfdex.gmbioreactor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sfdex.gmbioreactor.data.repository.AppListRepository
import com.sfdex.gmbioreactor.data.repository.ConfigRepository
import com.sfdex.gmbioreactor.data.repository.ModelRepository
import com.sfdex.gmbioreactor.ui.navigation.AppNavigation
import com.sfdex.gmbioreactor.ui.theme.GMBioreactorTheme
import com.sfdex.gmbioreactor.ui.viewmodel.AppListViewModel
import com.sfdex.gmbioreactor.ui.viewmodel.ModelLibraryViewModel

class MainActivity : ComponentActivity() {

    private val appListViewModel: AppListViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppListViewModel(
                    appListRepository = AppListRepository(applicationContext),
                    configRepository = ConfigRepository(applicationContext)
                ) as T
            }
        }
    }

    private val modelLibraryViewModel: ModelLibraryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ModelLibraryViewModel(
                    modelRepository = ModelRepository(applicationContext)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GMBioreactorTheme {
                AppNavigation(
                    appListViewModel = appListViewModel,
                    modelLibraryViewModel = modelLibraryViewModel
                )
            }
        }
    }
}