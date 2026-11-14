package kr.nostalife.companion

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val model: RecommendationViewModel by viewModels()
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.refresh() else model.permissionDenied()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NostalifeApp(model, refresh = { fetchLatest() }) }
    }
    private fun fetchLatest() {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            localNetworkPermission.launch(permission)
        else model.refresh()
    }
}
