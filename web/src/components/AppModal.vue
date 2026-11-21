<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { X } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    subtitle?: string;
    allowLightDismiss?: boolean;
  }>(),
  {
    subtitle: '',
    allowLightDismiss: true,
  }
);

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const dialogRef = ref<HTMLDialogElement | null>(null);
const titleId = `dialog-title-${Math.random().toString(36).slice(2, 9)}`;
let pressedOutside = false;

function isPointerOutside(event: PointerEvent, dialog: HTMLDialogElement): boolean {
  const rect = dialog.getBoundingClientRect();
  return (
    event.clientX < rect.left ||
    event.clientX > rect.right ||
    event.clientY < rect.top ||
    event.clientY > rect.bottom
  );
}

function handlePointerDown(event: PointerEvent) {
  if (!props.allowLightDismiss || !dialogRef.value) return;
  pressedOutside = isPointerOutside(event, dialogRef.value);
}

function handlePointerUp(event: PointerEvent) {
  if (!props.allowLightDismiss || !dialogRef.value) return;
  if (
    !('closedBy' in HTMLDialogElement.prototype) &&
    pressedOutside &&
    isPointerOutside(event, dialogRef.value)
  ) {
    dialogRef.value.close();
  }
  pressedOutside = false;
}

function handleNativeClose() {
  if (dialogRef.value) {
    dialogRef.value.returnValue = '';
  }
  if (props.open) {
    emit('close');
  }
}

watch(
  () => props.open,
  (isOpen) => {
    const el = dialogRef.value;
    if (!el) return;
    if (isOpen && !el.open) {
      el.showModal();
    } else if (!isOpen && el.open) {
      el.close();
    }
  }
);

onMounted(() => {
  if (props.open && dialogRef.value && !dialogRef.value.open) {
    dialogRef.value.showModal();
  }
});

onBeforeUnmount(() => {
  if (dialogRef.value?.open) {
    dialogRef.value.close();
  }
});
</script>

<template>
  <dialog
    ref="dialogRef"
    class="app-dialog"
    :aria-labelledby="titleId"
    :closedby="allowLightDismiss ? 'any' : 'closerequest'"
    @close="handleNativeClose"
    @pointerdown="handlePointerDown"
    @pointerup="handlePointerUp"
  >
    <div class="flex items-start justify-between gap-4 border-b hairline px-6 py-4">
      <div>
        <h2 :id="titleId" class="text-base font-medium text-ink">{{ title }}</h2>
        <p v-if="subtitle" class="mt-0.5 text-xs text-ink-secondary">{{ subtitle }}</p>
      </div>
      <button
        type="button"
        aria-label="Đóng hộp thoại"
        @click="emit('close')"
        class="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-ink-muted hover:bg-raised hover:text-ink"
      >
        <X :size="16" :stroke-width="1.5" />
      </button>
    </div>

    <div class="overflow-y-auto px-6 py-5">
      <slot />
    </div>

    <div v-if="$slots.footer" class="border-t hairline bg-canvas/60 px-6 py-3.5 flex items-center justify-end gap-2">
      <slot name="footer" />
    </div>
  </dialog>
</template>
