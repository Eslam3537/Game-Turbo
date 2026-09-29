package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.BoosterDatabase
import com.example.data.BoosterRepository
import com.example.ui.BoosterViewModel
import com.example.ui.BoosterViewModelFactory
import com.example.ui.DashboardLayout
import com.example.ui.theme.MyApplicationTheme
import com.example.util.PermissionManager
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: BoosterViewModel

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissions()
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == PermissionManager.SHIZUKU_REQUEST_CODE) {
            if (::viewModel.isInitialized) {
                viewModel.refreshPermissions()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = BoosterDatabase.getDatabase(this)
        val repository = BoosterRepository(database.boosterDao())
        val viewModelFactory = BoosterViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, viewModelFactory)[BoosterViewModel::class.java]

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}

        requestAllStartupPermissions()
        com.example.engine.update.InAppUpdateManager.checkForUpdates(this, isUserInitiated = false)

        setContent {
            MyApplicationTheme {
                DashboardLayout(
                    viewModel = viewModel,
                    onRequestPermissions = { requestAllPermissionsSequentially() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissions()
        }
        com.example.engine.update.InAppUpdateManager.checkForUpdates(this, isUserInitiated = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
    }

    private fun requestAllStartupPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionManager.hasNotificationPermission(this)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        if (PermissionManager.isShizukuRunning() && !PermissionManager.hasShizukuPermission()) {
            PermissionManager.requestShizukuPermission()
        }
        viewModel.refreshPermissions()
    }

    private fun requestAllPermissionsSequentially() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionManager.hasNotificationPermission(this)) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        if (PermissionManager.isShizukuRunning() && !PermissionManager.hasShizukuPermission()) {
            PermissionManager.requestShizukuPermission()
        }

        if (!PermissionManager.hasOverlayPermission(this)) {
            Toast.makeText(this, "Please grant Overlay permission to enable Floating FPS HUD", Toast.LENGTH_SHORT).show()
            PermissionManager.openOverlaySettings(this)
            return
        }

        if (!PermissionManager.hasUsageStatsPermission(this)) {
            Toast.makeText(this, "Please grant Usage Access to enable automatic game detection", Toast.LENGTH_SHORT).show()
            PermissionManager.openUsageAccessSettings(this)
            return
        }

        if (!PermissionManager.hasDndPermission(this)) {
            Toast.makeText(this, "Please grant DND Access to silence notifications during matches", Toast.LENGTH_SHORT).show()
            PermissionManager.openDndSettings(this)
            return
        }
        viewModel.refreshPermissions()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
