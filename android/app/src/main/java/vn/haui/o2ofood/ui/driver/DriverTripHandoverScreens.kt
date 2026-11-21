package vn.haui.o2ofood.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.haui.o2ofood.data.O2oFoodApiService
import vn.haui.o2ofood.data.WarehouseHandoverRequestDto

/**
 * Màn hình Phân hệ Tài xế trên Android:
 * - Xác nhận nhận chuyến hàng tại kho trung tâm từ 08:00 D+1 (WAREHOUSE_TO_DRIVER).
 * - Theo dõi danh sách điểm dừng đại lý (TripStops) và đối chiếu QR kiện hàng.
 */
@Composable
fun DriverTripHandoverScreen(apiService: O2oFoodApiService) {
    val scope = rememberCoroutineScope()

    var tripIdInput by remember { mutableStateOf("1") }
    var warehouseUserIdInput by remember { mutableStateOf("2") }
    var driverIdInput by remember { mutableStateOf("4") }
    var noteInput by remember { mutableStateOf("Tài xế đã quét QR đủ kiện hàng tại kho lúc 08:00 D+1") }
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Phân Hệ Tài Xế Giao Nhận (ROLE_DRIVER)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ký Nhận Kiện Hàng Xuất Kho Trung Tâm (WAREHOUSE_TO_DRIVER — 08:00 D+1)",
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tripIdInput,
                        onValueChange = { tripIdInput = it },
                        label = { Text("Trip ID") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = driverIdInput,
                        onValueChange = { driverIdInput = it },
                        label = { Text("Driver ID") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Ghi chú biên bản nhận chuyến") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val res = apiService.confirmWarehouseToDriverHandover(
                                    tripId = tripIdInput.toLongOrNull() ?: 1L,
                                    request = WarehouseHandoverRequestDto(
                                        warehouseUserId = warehouseUserIdInput.toLongOrNull() ?: 2L,
                                        driverId = driverIdInput.toLongOrNull() ?: 4L,
                                        note = noteInput
                                    )
                                )
                                statusMessage = res.message
                            }.onFailure {
                                statusMessage = "Lỗi xác nhận xuất kho: ${it.message}"
                            }
                        }
                    }
                ) {
                    Text("Xác Nhận Nhận Chuyến & Bắt Đầu Giao (IN_TRANSIT)")
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Text(text = statusMessage, color = MaterialTheme.colorScheme.primary)
        }
    }
}
