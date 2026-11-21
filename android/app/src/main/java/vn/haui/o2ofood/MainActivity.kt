package vn.haui.o2ofood

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.haui.o2ofood.data.O2oFoodApiService
import vn.haui.o2ofood.data.TokenManager
import vn.haui.o2ofood.ui.agent.AgentHandoverAndQrScreen
import vn.haui.o2ofood.ui.customer.CustomerMainScreen
import vn.haui.o2ofood.ui.driver.DriverTripHandoverScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenManager = TokenManager(applicationContext)
        val apiService = O2oFoodApiService.create(BuildConfig.API_BASE_URL, tokenManager)

        setContent {
            MaterialTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("Khách hàng") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Đại lý (QR)") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                label = { Text("Tài xế") },
                                icon = {}
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(12.dp)
                    ) {
                        when (selectedTab) {
                            0 -> CustomerMainScreen(apiService = apiService)
                            1 -> AgentHandoverAndQrScreen(apiService = apiService)
                            2 -> DriverTripHandoverScreen(apiService = apiService)
                        }
                    }
                }
            }
        }
    }
}
