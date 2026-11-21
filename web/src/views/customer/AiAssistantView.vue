<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  Bot,
  Check,
  ChefHat,
  Clock,
  Lock,
  Send,
  ShoppingCart,
  SlidersHorizontal,
} from 'lucide-vue-next';
import httpClient, { ApiEnvelope } from '@/api/httpClient';
import { useCycleStore } from '@/stores/cycleStore';
import { useCartStore } from '@/stores/cartStore';
import { useDemoStore } from '@/stores/demoStore';
import { useAuthStore } from '@/stores/authStore';
import { useNotificationStore } from '@/stores/notificationStore';
import type { RecipeSuggestionPlan } from '@/types/domain';
import { formatVnd } from '@/utils/formatters';

const cycleStore = useCycleStore();
const cartStore = useCartStore();
const demoStore = useDemoStore();
const authStore = useAuthStore();
const notificationStore = useNotificationStore();

const chatInput = ref('');
const chatHistory = ref<
  Array<{
    role: 'user' | 'assistant';
    text: string;
    intentType?: string;
    executedTool?: string | null;
    denied?: boolean;
  }>
>([
  {
    role: 'assistant',
    text: 'Xin chào. Bạn có thể hỏi về chính sách chốt đơn 23:59, giữ tồn 30 phút, giờ nhận 16:00 D+1, quy tắc hoàn tiền, hoặc tra cứu trực tiếp trạng thái đơn hàng chính chủ.',
  },
]);

const quickPrompts = [
  'Giờ chốt đơn 23:59 và nhận hàng 16:00 hoạt động ra sao?',
  'Chính sách tự hủy đơn và hoàn tiền VNPAY?',
  'Tra cứu đơn hàng của tôi',
  'Kiểm tra đơn ORD-20261121-004 của người khác',
];

const servings = ref(3);
const budgetVnd = ref(150000);
const selectedAllergies = ref<string[]>([]);
const customAllergy = ref('');
const pantryProductIds = ref<number[]>([]);
const recipePlans = ref<RecipeSuggestionPlan[]>([]);
const loadingChat = ref(false);
const loadingRecipes = ref(false);

const pantryOptions = computed(() => demoStore.products.slice(0, 12));
const commonAllergies = ['Tôm', 'Cá', 'Trứng', 'Đậu nành'];

onMounted(() => {
  fetchRecipeSuggestions();
});

function toggleAllergy(tag: string) {
  const idx = selectedAllergies.value.indexOf(tag);
  if (idx >= 0) selectedAllergies.value.splice(idx, 1);
  else selectedAllergies.value.push(tag);
}

function togglePantry(productId: number) {
  const idx = pantryProductIds.value.indexOf(productId);
  if (idx >= 0) pantryProductIds.value.splice(idx, 1);
  else pantryProductIds.value.push(productId);
}

async function sendChatMessage(overridePrompt?: string) {
  const msg = (overridePrompt ?? chatInput.value).trim();
  if (!msg) return;
  chatHistory.value.push({ role: 'user', text: msg });
  if (!overridePrompt) chatInput.value = '';
  loadingChat.value = true;

  try {
    if (demoStore.isDemoMode) {
      const reply = demoStore.askAiAssistant(msg, authStore.user);
      chatHistory.value.push({
        role: 'assistant',
        text: reply.replyMarkdown,
        intentType: reply.intentType,
        executedTool: reply.executedTool,
        denied: reply.denied,
      });
      return;
    }

    const res = await httpClient.post<
      ApiEnvelope<{
        intentType: string;
        executedTool: string | null;
        replyMarkdown: string;
      }>
    >('/ai/chat', { message: msg });
    const data = res.data.data;
    chatHistory.value.push({
      role: 'assistant',
      text: data.replyMarkdown,
      intentType: data.intentType,
      executedTool: data.executedTool,
    });
  } catch (err: any) {
    chatHistory.value.push({
      role: 'assistant',
      text:
        err?.response?.data?.message ||
        'Hệ thống từ chối truy vấn đơn hàng không thuộc tài khoản đang đăng nhập (403 Forbidden).',
      intentType: 'ACCESS_DENIED',
      denied: true,
    });
  } finally {
    loadingChat.value = false;
  }
}

async function fetchRecipeSuggestions() {
  loadingRecipes.value = true;
  try {
    const extra = customAllergy.value
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    const allergies = [...selectedAllergies.value, ...extra];

    if (demoStore.isDemoMode) {
      recipePlans.value = demoStore.suggestRecipes({
        servings: servings.value,
        budgetVnd: budgetVnd.value,
        pantryProductIds: pantryProductIds.value,
        allergies,
      });
      return;
    }

    const cycleId = cycleStore.selectedPoint?.activeCycle?.cycleId || 1;
    const res = await httpClient.post<ApiEnvelope<{ suggestions: RecipeSuggestionPlan[] }>>(
      '/ai/recipe-suggestions',
      {
        cycleId,
        servings: servings.value,
        budgetVnd: budgetVnd.value,
        pantryProductIds: pantryProductIds.value,
        allergies,
      }
    );
    recipePlans.value = res.data.data.suggestions;
  } finally {
    loadingRecipes.value = false;
  }
}

function addPlanToCart(plan: RecipeSuggestionPlan) {
  let addedPackages = 0;
  for (const item of plan.cartItems) {
    if (item.coveredByPantry || item.packagesToBuy <= 0) continue;
    cartStore.addFixedPackItem(
      {
        productId: item.productId,
        variantId: item.variantId,
        productName: item.productName,
        packName: item.packName,
        packWeightGrams: item.packWeightGrams,
        unitPrice: item.officialUnitPrice,
        availableQty: item.availableStockInCycle,
      },
      item.packagesToBuy
    );
    addedPackages += item.packagesToBuy;
  }
  notificationStore.pushToast(
    `Đã thêm thực đơn "${plan.recipeName}"`,
    `Đã đưa ${addedPackages} gói nguyên liệu cố định vào giỏ hàng đợt hiện tại.`
  );
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-medium tracking-tight text-ink">Trợ lý mua sắm & Gợi ý thực đơn</h1>
      <p class="mt-1 text-sm text-ink-secondary">
        Hỏi đáp chính sách có trích dẫn, tra cứu đơn hàng chính chủ và tính giỏ nguyên liệu đóng gói cố định theo ngân sách
      </p>
    </div>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-12">
      <!-- Left: Policy RAG & Safe Order Tool-Calling Chat -->
      <section class="flex h-[680px] flex-col rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-5">
        <div class="border-b hairline pb-3.5">
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2">
              <Bot :size="18" :stroke-width="1.5" class="text-forest-700" />
              <h2 class="text-base font-medium text-ink">Hỏi đáp chính sách & Tra cứu đơn</h2>
            </div>
            <span class="inline-flex items-center gap-1 rounded-md bg-canvas border hairline px-2 py-0.5 text-[11px] text-ink-muted">
              <Lock :size="11" :stroke-width="1.5" />
              <span>Kiểm quyền chính chủ</span>
            </span>
          </div>
          <p class="mt-1 text-xs text-ink-muted">
            Trợ lý không suy đoán dữ liệu — mọi truy vấn đơn đi qua kiểm tra quyền sở hữu tài khoản
          </p>
        </div>

        <!-- Messages -->
        <div class="mt-3 flex-1 space-y-3 overflow-y-auto pr-1">
          <div
            v-for="(msg, idx) in chatHistory"
            :key="idx"
            :class="[
              'rounded-xl px-3.5 py-3 text-xs leading-relaxed whitespace-pre-line',
              msg.role === 'user'
                ? 'ml-10 bg-forest-700 text-white'
                : msg.denied
                  ? 'mr-6 border border-danger-100 bg-danger-50 text-danger-700'
                  : 'mr-6 border hairline bg-canvas text-ink',
            ]"
          >
            <div
              v-if="msg.executedTool"
              class="mb-1.5 font-mono text-[11px] text-ink-muted"
            >
              {{ msg.intentType }} · {{ msg.executedTool }}
            </div>
            <div>{{ msg.text }}</div>
          </div>
        </div>

        <!-- Quick Prompts -->
        <div class="mt-3 flex flex-wrap gap-1.5 border-t hairline pt-3">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            type="button"
            @click="sendChatMessage(prompt)"
            class="rounded-lg border hairline bg-canvas px-2.5 py-1 text-[11px] text-ink-secondary hover:bg-raised hover:text-ink"
          >
            {{ prompt }}
          </button>
        </div>

        <form @submit.prevent="sendChatMessage()" class="mt-3 flex gap-2">
          <input
            v-model="chatInput"
            type="text"
            placeholder="Hỏi chính sách hoặc nhập mã đơn ORD-..."
            class="h-10 flex-1 rounded-lg border hairline bg-canvas/50 px-3 text-xs text-ink focus:bg-surface"
          />
          <button
            type="submit"
            :disabled="loadingChat"
            aria-label="Gửi tin nhắn"
            class="inline-flex h-10 items-center gap-1.5 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800 disabled:opacity-60"
          >
            <Send :size="14" :stroke-width="1.5" />
            <span>Gửi</span>
          </button>
        </form>
      </section>

      <!-- Right: Deterministic Recipe-to-Cart Planner -->
      <section class="rounded-2xl border hairline bg-surface p-5 shadow-card lg:col-span-7 space-y-5">
        <div class="flex flex-wrap items-start justify-between gap-3 border-b hairline pb-4">
          <div class="flex items-center gap-2">
            <ChefHat :size="18" :stroke-width="1.5" class="text-forest-700" />
            <div>
              <h2 class="text-base font-medium text-ink">Gợi ý thực đơn & Quy đổi gói cố định</h2>
              <p class="text-xs text-ink-muted">
                Công thức tính xác định <span class="font-mono">số gói = CEIL(định lượng cần / trọng lượng gói)</span> từ tồn kho đợt hiện tại
              </p>
            </div>
          </div>
        </div>

        <!-- Planner Controls -->
        <div class="rounded-xl border hairline bg-canvas p-4 space-y-4 text-xs">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label for="servings-input" class="font-medium text-ink-secondary">Số người ăn</label>
                <span class="font-mono font-medium text-ink tabular-nums">{{ servings }} người</span>
              </div>
              <input
                id="servings-input"
                v-model.number="servings"
                type="range"
                min="1"
                max="8"
                class="w-full"
              />
            </div>

            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label for="budget-input" class="font-medium text-ink-secondary">Ngân sách dự kiến</label>
                <span class="font-mono font-medium text-forest-800 tabular-nums">
                  {{ formatVnd(budgetVnd) }}
                </span>
              </div>
              <input
                id="budget-input"
                v-model.number="budgetVnd"
                type="range"
                min="60000"
                max="400000"
                step="10000"
                class="w-full"
              />
            </div>
          </div>

          <!-- Allergies -->
          <div class="space-y-2">
            <div class="font-medium text-ink-secondary">Loại trừ dị ứng / món kiêng</div>
            <div class="flex flex-wrap items-center gap-2">
              <button
                v-for="tag in commonAllergies"
                :key="tag"
                type="button"
                @click="toggleAllergy(tag)"
                :class="[
                  'inline-flex h-7 items-center gap-1 rounded-md border px-2.5 text-xs font-medium',
                  selectedAllergies.includes(tag)
                    ? 'border-danger-100 bg-danger-50 text-danger-700'
                    : 'hairline bg-surface text-ink-secondary hover:text-ink',
                ]"
              >
                <Check v-if="selectedAllergies.includes(tag)" :size="12" :stroke-width="1.5" />
                <span>Không {{ tag }}</span>
              </button>
              <input
                v-model="customAllergy"
                type="text"
                placeholder="Thêm món kiêng khác..."
                class="h-7 rounded-md border hairline bg-surface px-2.5 text-xs text-ink"
              />
            </div>
          </div>

          <!-- Pantry Checkboxes -->
          <div class="space-y-2">
            <div class="font-medium text-ink-secondary">
              Nguyên liệu sẵn có tại nhà (sẽ tự động trừ khỏi giỏ mua)
            </div>
            <div class="grid grid-cols-2 gap-1.5 sm:grid-cols-3">
              <label
                v-for="prod in pantryOptions"
                :key="prod.productId"
                class="flex cursor-pointer items-center gap-2 rounded-lg border hairline bg-surface px-2.5 py-1.5 text-[11px] text-ink-secondary hover:text-ink"
              >
                <input
                  type="checkbox"
                  class="brand-check h-3.5 w-3.5"
                  :checked="pantryProductIds.includes(prod.productId)"
                  @change="togglePantry(prod.productId)"
                />
                <span class="truncate">{{ prod.productName }}</span>
              </label>
            </div>
          </div>

          <button
            type="button"
            :disabled="loadingRecipes"
            @click="fetchRecipeSuggestions"
            class="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-forest-700 px-4 text-xs font-medium text-white hover:bg-forest-800"
          >
            <SlidersHorizontal :size="15" :stroke-width="1.5" />
            <span>{{ loadingRecipes ? 'Đang quy đổi định lượng...' : 'Cập nhật thực đơn & tính giỏ hàng cố định' }}</span>
          </button>
        </div>

        <!-- Suggested Recipe Cards -->
        <div class="max-h-[420px] space-y-4 overflow-y-auto pr-1">
          <div
            v-if="recipePlans.length === 0"
            class="rounded-xl border hairline bg-canvas p-6 text-center text-xs text-ink-secondary"
          >
            Không tìm thấy công thức phù hợp sau khi loại trừ dị ứng. Hãy nới lỏng bộ lọc và thử lại.
          </div>

          <article
            v-for="plan in recipePlans"
            :key="plan.recipeId"
            class="rounded-xl border hairline bg-canvas/50 p-4 space-y-3 text-xs"
          >
            <div class="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h3 class="text-sm font-medium text-ink">{{ plan.recipeName }}</h3>
                <div class="mt-0.5 flex items-center gap-2 text-[11px] text-ink-muted">
                  <span class="inline-flex items-center gap-1">
                    <Clock :size="12" :stroke-width="1.5" />
                    <span>{{ plan.cookingTimeMinutes }} phút</span>
                  </span>
                  <span>·</span>
                  <span>Điều chỉnh cho {{ plan.requestedServings }} người</span>
                </div>
              </div>
              <span
                :class="[
                  'rounded-md border px-2.5 py-1 font-mono text-xs font-medium tabular-nums',
                  plan.withinBudget
                    ? 'border-forest-200 bg-forest-50 text-forest-800'
                    : 'border-amberish-200 bg-amberish-50 text-amberish-800',
                ]"
              >
                {{ formatVnd(plan.totalCartAmount) }}
              </span>
            </div>

            <p class="text-ink-secondary leading-relaxed">{{ plan.aiExplanation }}</p>

            <div class="overflow-x-auto rounded-lg border hairline bg-surface">
              <table class="w-full text-left text-[11px]">
                <thead class="border-b hairline bg-canvas text-ink-muted">
                  <tr>
                    <th class="px-3 py-2 font-medium">Nguyên liệu (quy cách cố định)</th>
                    <th class="px-2 py-2 font-medium text-right">Cần dùng</th>
                    <th class="px-2 py-2 font-medium text-right">Số gói mua</th>
                    <th class="px-2 py-2 font-medium text-right">Dư sau nấu</th>
                    <th class="px-3 py-2 font-medium text-right">Thành tiền</th>
                  </tr>
                </thead>
                <tbody class="divide-y hairline">
                  <tr v-for="ci in plan.cartItems" :key="ci.variantId">
                    <td class="px-3 py-2 text-ink">
                      {{ ci.productName }}
                      <span class="text-ink-muted">({{ ci.packName }})</span>
                    </td>
                    <td class="px-2 py-2 text-right font-mono tabular-nums text-ink-secondary">
                      {{ ci.requiredWeightGrams }}g
                    </td>
                    <td class="px-2 py-2 text-right font-mono font-medium tabular-nums">
                      <span v-if="ci.coveredByPantry" class="text-ink-muted">Có sẵn</span>
                      <span v-else class="text-forest-800">{{ ci.packagesToBuy }} gói</span>
                    </td>
                    <td class="px-2 py-2 text-right font-mono tabular-nums text-ink-muted">
                      {{ ci.leftoverWeightGrams }}g
                    </td>
                    <td class="px-3 py-2 text-right font-mono font-medium tabular-nums text-ink">
                      {{ formatVnd(ci.lineTotal) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <button
              type="button"
              :disabled="!plan.allInStock"
              @click="addPlanToCart(plan)"
              class="inline-flex h-9 w-full items-center justify-center gap-1.5 rounded-lg bg-ink px-3 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50"
            >
              <ShoppingCart :size="14" :stroke-width="1.5" />
              <span>Thêm toàn bộ gói nguyên liệu vào giỏ</span>
            </button>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>
