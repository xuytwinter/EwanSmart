<script setup lang="ts">
defineOptions({
  name: 'SearchDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const { formRef, restoreValidation } = useNaiveForm();

const model = ref<Api.KnowledgeBase.SearchParams>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.SearchParams {
  return {
    query: '',
    topK: 10
  };
}

const list = ref<Api.KnowledgeBase.SearchResult[]>([
  {
    fileMd5: '123',
    chunkId: 123,
    textContent:
      '却说张飞饮了数杯闷酒，乘马从馆驿前过，见五六十个老人，皆在门前痛哭。飞问其故，众老人答日:吏，欲害刘公;我等皆来苦告，不得放入，反遭把门人赶打!“张飞大怒，睁圆环眼，咬碎钢牙，滚鞍下马，径入馆驿，把门人那里阻挡得住，真奔后堂，见智邮正坐厅上，将县变绑倒在地，飞大喝:“害民贼!认得我么?"督邮未及开言，早被张飞揪住头发，扯出馆驿，直到县前马上缚住:攀下柳条，去督邮两上着打，一连打折柳条十数枝。玄德正纳闷间，听得县前喧闹，问左右，答日:“张将军绑一人在县前痛打。“玄德忙去观之，见绑缚者乃督邮也。玄德惊问其故，飞曰:“此等害民贼，不打死等甚!"督邮告曰:"玄德公教我性命!"玄德',
    score: 0.95,
    fileName: '三国演义.txt'
  },
  {
    fileMd5: '123',
    chunkId: 123,
    textContent:
      '却说张飞饮了数杯闷酒，乘马从馆驿前过，见五六十个老人，皆在门前痛哭。飞问其故，众老人答日:吏，欲害刘公;我等皆来苦告，不得放入，反遭把门人赶打!“张飞大怒，睁圆环眼，咬碎钢牙，滚鞍下马，径入馆驿，把门人那里阻挡得住，真奔后堂，见智邮正坐厅上，将县变绑倒在地，飞大喝:“害民贼!认得我么?"督邮未及开言，早被张飞揪住头发，扯出馆驿，直到县前马上缚住:攀下柳条，去督邮两上着打，一连打折柳条十数枝。玄德正纳闷间，听得县前喧闹，问左右，答日:“张将军绑一人在县前痛打。“玄德忙去观之，见绑缚者乃督邮也。玄德惊问其故，飞曰:“此等害民贼，不打死等甚!"督邮告曰:"玄德公教我性命!"玄德',
    score: 0.95,
    fileName: '三国演义.txt'
  }
]);

const patterns = ref<string[]>([]);
function highlight(text: string) {
  if (!model.value.query) return false;
  if (text.includes(model.value.query)) return true;
  return false;
}

async function search() {
  loading.value = true;
  const { error, data } = await request<Api.KnowledgeBase.SearchResult[]>({
    url: '/search/hybrid',
    params: model.value
  });
  if (!error) {
    list.value = data;
    patterns.value = [model.value.query];
  }
  loading.value = false;
}

function reset() {
  model.value = createDefaultModel();
  patterns.value = [];
  restoreValidation();
}
watch(visible, () => {
  if (visible.value) {
    reset();
    list.value = [];
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="知识库检索"
    :show-icon="false"
    :mask-closable="false"
    class="w-1000px!"
  >
    <NForm ref="formRef" :model="model" label-placement="left" :label-width="60" inline :show-feedback="false">
      <NGrid>
        <NFormItemGi label="topK" path="topK" class="pr-24px" span="6">
          <NInputNumber
            v-model:value="model.topK"
            placeholder="请输入topK"
            clearable
            :min="1"
            :precision="0"
            :step="10"
          />
        </NFormItemGi>
        <NFormItemGi label="关键字" path="query" class="pr-24px" span="12">
          <NInput v-model:value="model.query" placeholder="请输入关键字" clearable />
        </NFormItemGi>
        <NFormItemGi span="6">
          <NSpace class="w-full" justify="end">
            <NButton @click="reset">
              <template #icon>
                <icon-ic-round-refresh class="text-icon" />
              </template>
              重置
            </NButton>
            <NButton type="primary" ghost @click="search">
              <template #icon>
                <icon-ic-round-search class="text-icon" />
              </template>
              搜索
            </NButton>
          </NSpace>
        </NFormItemGi>
      </NGrid>
    </NForm>
    <NSpin :show="loading">
      <NEmpty v-if="list.length === 0" description="暂无数据" class="py-100px" />
      <NScrollbar v-else class="max-h-500px">
        <NCard
          v-for="item in list"
          :key="item.fileMd5"
          class="mt-8"
          embedded
          :segmented="{
            content: true,
            footer: 'soft'
          }"
        >
          <div class="relative">
            <NHighlight
              v-if="highlight(item.textContent)"
              highlight-class="bg-[rgb(var(--primary-400-color))] color-white px-2 mx-1 rd-sm"
              :text="item.textContent"
              :patterns="patterns"
            />
            <span v-else>{{ item.textContent }}</span>
            <NTag
              :bordered="false"
              draggable
              class="absolute right-0 top-0 bg-[rgb(var(--primary-color)/.9)] color-white hover:bg-transparent hover:color-transparent"
            >
              Score: {{ item.score }}
            </NTag>
          </div>
          <template #footer>
            <span>来源：{{ item.fileName }}</span>
          </template>
        </NCard>
      </NScrollbar>
    </NSpin>
  </NModal>
</template>

<style scoped></style>
