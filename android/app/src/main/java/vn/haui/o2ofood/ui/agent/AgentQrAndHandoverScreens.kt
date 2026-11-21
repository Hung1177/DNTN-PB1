package vn.haui.o2ofood.ui.agent

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch
import vn.haui.o2ofood.data.*

/**
 * Màn hình Phân hệ Đại lý nhận hàng trên Android (ML Kit Barcode Scanning):
 * 1. Quét mã QR kiện hàng khi kiểm nhận bàn giao với Tài xế (DRIVER_TO_AGENT — AT-08).
 * 2. Quét mã QR nhận hàng dùng 1 lần của Khách hàng (CustomerHandoverScreen — UC-C05).
 */
@Composable
fun AgentHandoverAndQrScreen(apiService: O2oFoodApiService) {
    val scope = rememberCoroutineScope()

    var orderIdInput by remember { mutableStateOf("1") }
    var scannedCustomerQrToken by remember { mutableStateOf("") }
    var tripStopIdInput by remember { mutableStateOf("1") }
    var packageIdInput by remember { mutableStateOf("1") }
    var conditionStatus by remember { mutableStateOf("ACCEPTED") }
    var resultMessage by remember { mutableStateOf("") }

    // Cấu hình bộ quét mã QR Google ML Kit
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val mlKitScanner = remember { BarcodeScanning.getClient(scannerOptions) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Phân Hệ Đại Lý Nhận Hàng (ML Kit QR Scanner)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "1. Quét QR Nhận Hàng Dùng 1 Lần Của Khách (UC-C05)",
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = orderIdInput,
                    onValueChange = { orderIdInput = it },
                    label = { Text("Order ID") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scannedCustomerQrToken,
                    onValueChange = { scannedCustomerQrToken = it },
                    label = { Text("Mã QR khách hàng (ML Kit Scanned Payload)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val orderId = orderIdInput.toLongOrNull() ?: 1L
                                val res = apiService.scanCustomerQrByAgent(
                                    orderId,
                                    ScanQrRequestDto(scannedCustomerQrToken)
                                )
                                resultMessage = res.message
                            }.onFailure {
                                resultMessage = "Lỗi xác thực QR nhận hàng: ${it.message}"
                            }
                        }
                    }
                ) {
                    Text("Xác Nhận Bàn Giao Vật Lý Cho Khách (HANDED_OVER)")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "2. Kiểm Nhận Kiện Hàng Từ Tài Xế (DRIVER_TO_AGENT — AT-08)",
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tripStopIdInput,
                        onValueChange = { tripStopIdInput = it },
                        label = { Text("TripStop ID") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = packageIdInput,
                        onValueChange = { packageIdInput = it },
                        label = { Text("Package ID") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ACCEPTED", "DAMAGED", "MISSING").forEach { status ->
                        FilterChip(
                            selected = conditionStatus == status,
                            onClick = { conditionStatus = status },
                            label = { Text(status) }
                        )
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val res = apiService.confirmDriverToAgentHandover(
                                    tripStopId = tripStopIdInput.toLongOrNull() ?: 1L,
                                    request = AgentHandoverRequestDto(
                                        driverId = 4L,
                                        agentUserId = 6L,
                                        inspections = listOf(
                                            PackageInspectionInputDto(
                                                packageId = packageIdInput.toLongOrNull() ?: 1L,
                                                conditionStatus = conditionStatus
                                            )
                                        ),
                                        note = "Kiểm nhận qua ứng dụng Android Đại lý (ML Kit)"
                                    )
                                )
                                resultMessage = res.message
                            }.onFailure {
                                resultMessage = "Lỗi ký biên bản đại lý: ${it.message}"
                            }
                        }
                    }
                ) {
                    Text("Ký Biên Bản Kiểm Nhận Đại Lý")
                }
            }
        }

        if (resultMessage.isNotBlank()) {
            Text(text = resultMessage, color = MaterialTheme.colorScheme.primary)
        }
    }
}
