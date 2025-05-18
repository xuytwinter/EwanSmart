import { useWebSocket } from '@vueuse/core';

export const useChatStore = defineStore(SetupStoreId.Chat, () => {
  const input = ref<Api.Chat.Input>({ message: '' });

  const list = ref<Api.Chat.Message[]>([]);

  const {
    status: wsStatus,
    data: wsData,
    send: wsSend,
    open: wsOpen,
    close: wsClose
  } = useWebSocket('ws://localhost:8080/ws/chat', {
    autoReconnect: true
  });

  return {
    input,
    list,
    wsStatus,
    wsData,
    wsSend,
    wsOpen,
    wsClose
  };
});
