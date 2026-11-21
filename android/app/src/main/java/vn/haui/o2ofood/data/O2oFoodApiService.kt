package vn.haui.o2ofood.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.math.BigDecimal

data class ApiEnvelope<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T?,
    val serverTime: String
)

data class LoginRequestDto(val email: String, val password: String)
data class UserProfileDto(val userId: Long, val email: String, val fullName: String, val roles: Set<String>)
data class AuthTokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val user: UserProfileDto
)

data class ActivePointCycleDto(
    val cycleId: Long,
    val cycleCode: String,
    val deliveryDate: String,
    val cutoffAt: String,
    val pickupStartAt: String,
    val autoCompleteAt: String,
    val maxOrders: Int,
    val currentOrders: Int,
    val rolledOverToNextCycle: Boolean
)

data class PickupPointDto(
    val id: Long,
    val code: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val coldStorageCapacity: Int,
    val status: String,
    val activeCycle: ActivePointCycleDto?
)

data class CatalogProductDto(
    val productId: Long,
    val variantId: Long,
    val sku: String,
    val productName: String,
    val category: String,
    val storageType: String,
    val packName: String,
    val packWeightGrams: Int,
    val unitPrice: BigDecimal,
    val availableQty: Int,
    val inStock: Boolean
)

data class CheckoutItemInput(val variantId: Long, val quantity: Int)
data class CheckoutRequestDto(val pickupPointId: Long, val cycleId: Long, val items: List<CheckoutItemInput>)
data class CheckoutResponseDto(
    val orderId: Long,
    val orderCode: String,
    val txnRef: String,
    val orderStatus: String,
    val paymentStatus: String,
    val totalAmount: BigDecimal,
    val reservationExpiresAt: String,
    val cutoffAt: String,
    val pickupStartAt: String,
    val vnpayPaymentUrl: String
)

data class OrderSummaryDto(
    val orderId: Long,
    val orderCode: String,
    val pickupPointId: Long,
    val cycleId: Long,
    val orderStatus: String,
    val fulfillmentStatus: String,
    val paymentStatus: String,
    val totalAmount: BigDecimal,
    val cutoffAt: String,
    val pickupStartAt: String
)

data class ScanQrRequestDto(val qrToken: String)
data class PackageInspectionInputDto(
    val packageId: Long,
    val conditionStatus: String,
    val evidenceImageUrl: String? = null
)
data class AgentHandoverRequestDto(
    val driverId: Long,
    val agentUserId: Long,
    val inspections: List<PackageInspectionInputDto>,
    val note: String
)
data class WarehouseHandoverRequestDto(
    val warehouseUserId: Long,
    val driverId: Long,
    val note: String
)

data class AiChatRequestDto(val message: String)
data class AiChatResponseDto(
    val intentType: String,
    val executedTool: String?,
    val replyMarkdown: String
)

interface O2oFoodApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): ApiEnvelope<AuthTokenResponseDto>

    @GET("pickup-points")
    suspend fun getPickupPoints(): ApiEnvelope<List<PickupPointDto>>

    @GET("catalog/products")
    suspend fun getCatalogByCycle(@Query("cycleId") cycleId: Long): ApiEnvelope<List<CatalogProductDto>>

    @POST("orders/checkout")
    suspend fun checkout(@Body request: CheckoutRequestDto): ApiEnvelope<CheckoutResponseDto>

    @GET("orders")
    suspend fun getMyOrders(): ApiEnvelope<List<OrderSummaryDto>>

    @POST("orders/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: Long): ApiEnvelope<Any>

    @POST("fulfillment/orders/{orderId}/customer-confirm-received")
    suspend fun confirmCustomerReceived(@Path("orderId") orderId: Long): ApiEnvelope<Any>

    @POST("fulfillment/orders/{orderId}/agent-scan-qr")
    suspend fun scanCustomerQrByAgent(
        @Path("orderId") orderId: Long,
        @Body request: ScanQrRequestDto
    ): ApiEnvelope<Any>

    @POST("fulfillment/trip-stops/{tripStopId}/agent-handover")
    suspend fun confirmDriverToAgentHandover(
        @Path("tripStopId") tripStopId: Long,
        @Body request: AgentHandoverRequestDto
    ): ApiEnvelope<Any>

    @POST("fulfillment/trips/{tripId}/warehouse-handover")
    suspend fun confirmWarehouseToDriverHandover(
        @Path("tripId") tripId: Long,
        @Body request: WarehouseHandoverRequestDto
    ): ApiEnvelope<Any>

    @POST("ai/chat")
    suspend fun chatWithAi(@Body request: AiChatRequestDto): ApiEnvelope<AiChatResponseDto>

    companion object {
        fun create(baseUrl: String, tokenManager: TokenManager): O2oFoodApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val token = tokenManager.getAccessToken()
                    val reqBuilder = chain.request().newBuilder()
                    if (!token.isNullOrBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $token")
                    }
                    chain.proceed(reqBuilder.build())
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(O2oFoodApiService::class.java)
        }
    }
}
