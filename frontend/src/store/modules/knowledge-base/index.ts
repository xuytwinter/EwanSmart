export const useKnowledgeBaseStore = defineStore(SetupStoreId.KnowledgeBase, () => {
  const tasks = ref<Api.KnowledgeBase.UploadTask[]>([]);
  const activeUploads = ref<Set<string>>(new Set());

  async function uploadChunk(task: Api.KnowledgeBase.UploadTask): Promise<void> {
    const totalChunks = Math.ceil(task.totalSize / chunkSize);

    const chunkStart = task.chunkIndex * chunkSize;
    const chunkEnd = Math.min(chunkStart + chunkSize, task.totalSize);
    const chunk = task.file.slice(chunkStart, chunkEnd);

    task.chunk = chunk;

    const { error, data } = await request<Api.KnowledgeBase.Progress>({
      url: '/upload/chunk',
      method: 'POST',
      data: { file: task.chunk },
      headers: {
        'X-File-Md5': task.fileMd5,
        'X-Chunk-Index': task.chunkIndex,
        'X-Total-Size': task.totalSize,
        'X-File-Name': task.fileName,
        'X-Org-Tag': task.orgTag,
        'X-Is-Public': task.isPublic ?? false,
        'Content-Type': 'multipart/form-data'
      }
    });

    if (error) throw new Error('分片上传失败');

    // 更新任务状态
    const updatedTask = tasks.value.find(t => t.fileMd5 === task.fileMd5)!;
    updatedTask.uploadedChunks = data.uploaded;
    updatedTask.progress = data.progress;

    if (data.uploaded.length === totalChunks) {
      const success = await mergeFile(task);
      if (!success) throw new Error('文件合并失败');
    }
    Promise.resolve();
  }

  async function mergeFile(task: Api.KnowledgeBase.UploadTask) {
    try {
      const res = await request({ url: '/upload/merge', data: { fileMd5: task.fileMd5, fileName: task.fileName } });
      if (!res) return false;

      // 更新任务状态为已完成
      const index = tasks.value.findIndex(t => t.fileMd5 === task.fileMd5);
      tasks.value[index].status = UploadStatus.Completed;
      return true;
    } catch {
      return false;
    }
  }

  /**
   * 异步函数：将上传请求加入队列
   *
   * 本函数处理上传任务的排队和初始化工作它首先检查是否存在相同的文件， 如果不存在，则创建一个新的上传任务，并将其添加到任务队列中最后启动上传流程
   *
   * @param form 包含上传信息的表单，包括文件列表和是否公开的标签
   * @returns 返回一个上传任务对象，无论是已存在的还是新创建的
   */
  async function enqueueUpload(form: Api.KnowledgeBase.Form) {
    console.log('%c [ 👉 form 👈 ]-65', 'font-size:16px; background:#192cfe; color:#5d70ff;', form);
    // 获取文件列表中的第一个文件
    const file = form.fileList![0].file!;
    // 计算文件的MD5值，用于唯一标识文件
    const md5 = await calculateMD5(file);

    // 检查是否已存在相同文件
    const existingTask = tasks.value.find(t => t.fileMd5 === md5);
    if (existingTask) {
      // 如果存在相同文件，直接返回该上传任务
      return existingTask;
    }

    // 创建新的上传任务对象
    const newTask: Api.KnowledgeBase.UploadTask = {
      file,
      chunk: null,
      chunkIndex: 0,
      fileMd5: md5,
      fileName: file.name,
      totalSize: file.size,
      isPublic: form.isPublic,
      uploadedChunks: [],
      progress: 0,
      status: UploadStatus.Pending,
      orgTag: form.orgTag
    };

    // 将新的上传任务添加到任务队列中
    tasks.value.push(newTask);
    // 启动上传流程
    startUpload();
    // 返回新的上传任务
    return newTask;
  }

  /** 启动文件上传的异步函数 该函数负责从待上传队列中启动文件上传任务，并管理并发上传的数量 */
  async function startUpload() {
    // 限制可同时上传的文件个数
    if (activeUploads.value.size >= 3) return;

    // 获取待上传的文件
    const pendingTasks = tasks.value.filter(
      t => t.status === UploadStatus.Pending && !activeUploads.value.has(t.fileMd5)
    );

    // 如果没有待上传的文件，则直接返回
    if (pendingTasks.length === 0) return;

    // 获取第一个待上传的文件
    const task = pendingTasks[0];
    task.status = UploadStatus.Uploading;
    activeUploads.value.add(task.fileMd5);

    // 计算文件总片数
    const totalChunks = Math.ceil(task.totalSize / chunkSize);

    try {
      const promises = [];
      // 遍历所有片数
      for (let i = 0; i < totalChunks; i += 1) {
        // 如果未上传，则上传
        if (!task.uploadedChunks.includes(i)) {
          task.chunkIndex = i;
          promises.push(uploadChunk(task));
        }
      }
      await Promise.all(promises);

      console.log('%c [ 👉  Promise.all 👈 ]-137', 'font-size:16px; background:#f76da8; color:#ffb1ec;');
    } catch {
      console.log('%c [ 👉  catch 👈 ]-140', 'font-size:16px; background:#14a626; color:#58ea6a;');
      // 如果上传失败，则将任务状态设置为中断
      const index = tasks.value.findIndex(t => t.fileMd5 === task.fileMd5);
      tasks.value[index].status = UploadStatus.Break;
    } finally {
      // 无论成功或失败，都从活跃队列中移除
      activeUploads.value.delete(task.fileMd5);
      // 继续下一个任务
      startUpload();
    }
  }

  return {
    tasks,
    activeUploads,
    enqueueUpload,
    startUpload
  };
});
