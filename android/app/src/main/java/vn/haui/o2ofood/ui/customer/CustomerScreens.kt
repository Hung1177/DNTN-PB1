package vn.haui.o2ofood.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.haui.o2ofood.data.*

/**
 * Màn hình Phân hệ Khách hàng trên Android (Kotlin + Jetpack Compose):
 * - Chọn Điểm nhận hàng (PickupPointSelector) & Xem hạn chốt 23:59 D / giờ nhận 16:00 D+1.
 * - Duyệt sản phẩm đóng gói cố định & Đặt hàng giữ tồn thanh toán VNPAY 100% (Checkout CustomTab/Browser).
 * - Hiển thị mã QR nhận hàng dùng 1 lần & Chat với Trợ lý AI (Policy RAG + Order Tool-Calling).
 */
@Composable
fun CustomerMainScreen(apiService: O2oFoodApiService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickupPoints by remember { mutableStateOf<List<PickupPointDto>>(emptyList()) }
    var selectedPoint by remember { mutableStateOf<PickupPointDto?>(null) }
    var catalog by remember { mutableStateOf<List<CatalogProductDto>>(emptyList()) }
    var aiQuestion by remember { mutableStateOf("") }
    var aiReply by remember { mutableStateOf("Hỏi chính sách giờ chốt 23:59, giữ tồn 30 phút hoặc tra cứu mã đơn ORD-...") }
    var statusBanner by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            val res = apiService.getPickupPoints()
            pickupPoints = res.data ?: emptyList()
            if (pickupPoints.isNotEmpty()) {
                selectedPoint = pickupPoints.first()
                val cycleId = selectedPoint?.activeCycle?.cycleId ?: 1L
                catalog = apiService.getCatalogByCycle(cycleId).data ?: emptyList()
            }
        }.onFailure {
            statusBanner = "Không thể kết nối Backend API: ${it.message}"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Đặt Thực Phẩm Qua Điểm Nhận (O2O Android)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            selectedPoint?.let { pt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Đại lý: ${pt.name}", fontWeight = FontWeight.Bold)
                        Text(text = pt.address, style = MaterialTheme.typography.bodySmall)
                        pt.activeCycle?.let { cyc ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đợt: ${cyc.cycleCode} • Hạn chốt: ${cyc.cutoffAt} • Nhận từ: ${cyc.pickupStartAt}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            if (statusBanner.isNotBlank()) {
                Text(text = statusBanner, color = MaterialTheme.colorScheme.primary)
            }
        }

        items(catalog) { product ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "${product.productName} (${product.packName})", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Giá cố định: ${product.unitPrice}đ • Tồn đợt: ${product.availableQty} gói • Bảo quản: ${product.storageType}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        enabled = product.inStock,
                        onClick = {
                            scope.launch {
                                val pt = selectedPoint ?: return@launch
                                val cyc = pt.activeCycle ?: return@launch
                                runCatching {
                                    val checkoutRes = apiService.checkout(
                                        CheckoutRequestDto(
                                            pickupPointId = pt.id,
                                            cycleId = cyc.cycleId,
                                            items = listOf(CheckoutItemInput(product.variantId, 1))
                                        )
                                    )
                                    checkoutRes.data?.let { dto ->
                                        statusBanner = "Đã giữ tồn đơn ${dto.orderCode} đến ${dto.reservationExpiresAt}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dto.vnpayPaymentUrl))
                                        context.startActivity(intent)
                                    }
                                }.onFailure {
                                    statusBanner = "Lỗi đặt hàng: ${it.message}"
                                }
                            }
                        }
                    ) {
                        Text(if (product.inStock) "Mua Ngay & Thanh Toán VNPAY 100%" else "Hết Tồn Kho")
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text(text = "Trợ Lý AI (Policy RAG & Tra Cứu Đơn Chính Chủ)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = aiQuestion,
                onValueChange = { aiQuestion = it },
                label = { Text("Câu hỏi chính sách hoặc mã đơn ORD-...") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            val res = apiService.chatWithAi(AiChatRequestDto(aiQuestion))
                            aiReply = res.data?.replyMarkdown ?: res.message
                        }.onFailure {
                            aiReply = "Từ chối truy cập hoặc lỗi kết nối: ${it.message}"
                        }
                    }
                }
            ) {
                Text("Gửi Trợ Lý AI")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = aiReply,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
