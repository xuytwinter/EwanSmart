<script setup lang="tsx">
import type { DataTableColumns, FormRules, PaginationProps, SelectOption } from 'naive-ui';
import { NButton, NInput, NInputNumber, NPopconfirm, NTag } from 'naive-ui';
import { fetchCreateInviteCode, fetchDisableInviteCode, fetchGetInviteCodeList } from '@/service/api';

const appStore = useAppStore();

interface InviteCodeFormModel {
  code: string;
  count: number | null;
  maxUses: number | null;
  expiresAt: number | null;
}

const enabledOptions: SelectOption[] = [
  { label: '全部状态', value: 'all' },
  { label: '仅启用', value: 'enabled' },
  { label: '仅禁用', value: 'disabled' }
];

const loading = ref(false);
const data = ref<Api.InviteCode.Item[]>([]);

const filterStatus = ref<'all' | 'enabled' | 'disabled'>('all');
const visible = ref(false);
const submitting = ref(false);
const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const model = ref<InviteCodeFormModel>(createDefaultModel());

const rules = ref<FormRules>({
  code: [
    {
      validator(_, value: string) {
        return !(value?.trim() && Number(model.value.count) > 1);
      },
      message: '批量创建时不能指定自定义邀请码',
      trigger: 'blur'
    }
  ],
  count: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return Number.isInteger(value) && value > 0 && value <= 100;
      },
      message: '批量数量必须是 1 到 100 的整数',
      trigger: 'change'
    }
  ],
  maxUses: [
    defaultRequiredRule,
    {
      validator(_, value) {
        return Number.isInteger(value) && value > 0;
      },
      message: '最大使用次数必须是大于 0 的整数',
      trigger: 'change'
    }
  ],
  expiresAt: [
    {
      validator(_, value: number | null) {
        return value === null || value > Date.now();
      },
      message: '过期时间必须晚于当前时间',
      trigger: 'change'
    }
  ]
});

const pagination = reactive<PaginationProps>({
  page: 1,
  pageSize: 10,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 15, 20, 25, 30],
  onUpdatePage: async (page: number) => {
    pagination.page = page;
    await getData();
  },
  onUpdatePageSize: async (pageSize: number) => {
    pagination.pageSize = pageSize;
    pagination.page = 1;
    await getData();
  }
});

const mobilePagination = computed(() => ({
  ...pagination,
  pageSlot: appStore.isMobile ? 3 : 9
}));

const columns = computed<DataTableColumns<Api.InviteCode.Item>>(() => [
  {
    key: 'index',
    title: '序号',
    width: 64,
    render: (_, index) => (Number(pagination.page) - 1) * Number(pagination.pageSize) + index + 1
  },
  {
    key: 'code',
    title: '邀请码',
    minWidth: 220,
    render: row => (
      <div class="flex items-center gap-2">
        <span class="font-mono text-3">{row.code}</span>
        <NButton
          size="tiny"
          quaternary
          onClick={() => {
            navigator.clipboard.writeText(row.code);
            window.$message?.success('邀请码已复制');
          }}
        >
          复制
        </NButton>
      </div>
    )
  },
  {
    key: 'usage',
    title: '使用情况',
    width: 140,
    render: row => `${row.usedCount}/${row.maxUses}`
  },
  {
    key: 'remaining',
    title: '剩余次数',
    width: 100,
    render: row => Math.max(0, row.maxUses - row.usedCount)
  },
  {
    key: 'expiresAt',
    title: '过期时间',
    width: 180,
    render: row => (row.expiresAt ? dayjs(row.expiresAt).format('YYYY-MM-DD HH:mm:ss') : '长期有效')
  },
  {
    key: 'enabled',
    title: '状态',
    width: 100,
    render: row => <NTag type={row.enabled ? 'success' : 'default'}>{row.enabled ? '已启用' : '已禁用'}</NTag>
  },
  {
    key: 'availability',
    title: '可用性',
    width: 120,
    render: row => {
      if (!row.enabled) return <NTag type="default">不可用</NTag>;
      if (row.expiresAt && dayjs(row.expiresAt).isBefore(dayjs())) return <NTag type="warning">已过期</NTag>;
      if (row.usedCount >= row.maxUses) return <NTag type="error">已耗尽</NTag>;
      return <NTag type="success">可使用</NTag>;
    }
  },
  {
    key: 'createdBy',
    title: '创建人',
    width: 120,
    render: row => row.createdBy?.username || '-'
  },
  {
    key: 'createdAt',
    title: '创建时间',
    width: 180,
    render: row => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')
  },
  {
    key: 'operate',
    title: '操作',
    width: 120,
    render: row =>
      row.enabled ? (
        <NPopconfirm onPositiveClick={() => handleDisable(row.id)}>
          {{
            default: () => '禁用后该邀请码将无法继续使用，确认继续吗？',
            trigger: () => (
              <NButton type="error" ghost size="small">
                禁用
              </NButton>
            )
          }}
        </NPopconfirm>
      ) : (
        <NTag type="default">已停用</NTag>
      )
  }
]);

function createDefaultModel(): InviteCodeFormModel {
  return {
    code: '',
    count: 1,
    maxUses: 1,
    expiresAt: dayjs().add(7, 'day').valueOf()
  };
}

async function getData() {
  loading.value = true;

  const { data: payload, error } = await fetchGetInviteCodeList({
    page: Number(pagination.page),
    size: Number(pagination.pageSize),
    enabled: filterStatus.value === 'all' ? null : filterStatus.value === 'enabled'
  });

  if (!error && payload) {
    data.value = payload.records || [];
    pagination.page = payload.current || Number(pagination.page);
    pagination.pageSize = payload.size || Number(pagination.pageSize);
    pagination.itemCount = payload.total || 0;
  }

  loading.value = false;
}

async function handleFilterChange(value: 'all' | 'enabled' | 'disabled') {
  filterStatus.value = value;
  pagination.page = 1;
  await getData();
}

function openCreateDialog() {
  model.value = createDefaultModel();
  visible.value = true;
  nextTick(() => {
    restoreValidation();
  });
}

function closeDialog() {
  visible.value = false;
}

function normalizeDateTime(value: number | null) {
  if (!value) return null;
  return dayjs(value).format('YYYY-MM-DDTHH:mm:ss');
}

async function handleCreate() {
  await validate();

  submitting.value = true;
  const { error } = await fetchCreateInviteCode({
    code: model.value.code.trim() || undefined,
    count: Number(model.value.count),
    maxUses: Number(model.value.maxUses),
    expiresAt: normalizeDateTime(model.value.expiresAt)
  });

  if (!error) {
    window.$message?.success(`已创建 ${Number(model.value.count)} 个邀请码`);
    closeDialog();
    await getData();
  }

  submitting.value = false;
}

async function handleDisable(id: number) {
  const { error } = await fetchDisableInviteCode(id);

  if (!error) {
    window.$message?.success('邀请码已禁用');
    await getData();
  }
}

onMounted(() => {
  getData();
});
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <NCard title="邀请码管理" :bordered="false" size="small" class="sm:flex-1-hidden card-wrapper">
      <template #header-extra>
        <NSpace :size="12" align="center" wrap>
          <NSelect
            v-model:value="filterStatus"
            :options="enabledOptions"
            class="w-140px"
            @update:value="handleFilterChange"
          />
          <NButton type="primary" @click="openCreateDialog">创建邀请码</NButton>
          <NButton @click="getData">刷新</NButton>
        </NSpace>
      </template>

      <div class="mb-4 text-13px text-#8a6b43">
        默认一周后过期。邀请码留空时，后端会自动生成 16 位随机码；批量创建时会连续生成多条随机邀请码。
      </div>

      <NDataTable
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="1240"
        :loading="loading"
        remote
        :row-key="row => row.id"
        :pagination="mobilePagination"
        class="sm:h-full"
      />
    </NCard>

    <NModal
      v-model:show="visible"
      preset="dialog"
      title="创建邀请码"
      :show-icon="false"
      :mask-closable="false"
      class="w-520px!"
    >
      <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="110" mt-10>
        <NFormItem label="邀请码" path="code">
          <NInput
            v-model:value="model.code"
            placeholder="单条创建可自定义，批量创建时请留空"
            maxlength="64"
            clearable
          />
        </NFormItem>
        <NFormItem label="批量数量" path="count">
          <NInputNumber v-model:value="model.count" class="w-full" :min="1" :max="100" :precision="0" placeholder="默认 1" />
        </NFormItem>
        <NFormItem label="最大使用次数" path="maxUses">
          <NInputNumber v-model:value="model.maxUses" class="w-full" :min="1" :precision="0" placeholder="请输入次数" />
        </NFormItem>
        <NFormItem label="过期时间" path="expiresAt">
          <NDatePicker
            v-model:value="model.expiresAt"
            type="datetime"
            clearable
            class="w-full"
            placeholder="默认一周后过期"
          />
        </NFormItem>
      </NForm>

      <template #action>
        <NSpace :size="12">
          <NButton @click="closeDialog">取消</NButton>
          <NButton type="primary" :loading="submitting" @click="handleCreate">创建</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped></style>
