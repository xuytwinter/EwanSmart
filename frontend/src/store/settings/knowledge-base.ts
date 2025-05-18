import { chunkSize } from '~/constant'
import { UploadStatus } from '~/enum'
import { calculateMD5 } from '~/utils'

export const useKnowledgeBaseStore = defineStore('knowledge-base', () => {
  const tasks = ref<Api.File.UploadTask[]>([])
  const activeUploads = ref<Set<string>>(new Set())

  async function uploadChunk(
    task: Api.File.UploadTask,
  ): Promise<boolean> {
    try {
      const totalChunks = Math.ceil(task.totalSize / chunkSize)

      const chunkStart = task.chunkIndex * chunkSize
      const chunkEnd = Math.min(chunkStart + chunkSize, task.totalSize)
      const chunk = task.file.slice(chunkStart, chunkEnd)

      task.chunk = chunk

      const res = await fetchUploadChunk(task)
      if (!res)
        throw new Error('分片上传失败')

      // 更新任务状态
      const updatedTask = tasks.value.find(t => t.fileMd5 === task.fileMd5)!
      updatedTask.uploadedChunks = res.uploaded
      updatedTask.progress = res.progress

      if (res.uploaded.length === totalChunks) {
        const success = await mergeFile(task)
        if (!success)
          throw new Error('文件合并失败')
      }

      return true
    }
    catch (error) {
      console.error('上传失败：', error)
      return false
    }
  }

  async function mergeFile(task: Api.File.UploadTask) {
    try {
      const res = await fetchMergeChunk({ fileMd5: task.fileMd5, fileName: task.fileName })
      if (!res)
        return false

      // 更新任务状态为已完成
      const index = tasks.value.findIndex(t => t.fileMd5 === task.fileMd5)
      tasks.value[index].status = UploadStatus.Completed
      return true
    }
    catch {
      return false
    }
  }

  // 入队上传
  async function enqueueUpload(form: Api.File.Form) {
    const file = form.fileList![0] as unknown as File
    const md5 = await calculateMD5(file)

    // 检查是否已存在相同文件
    const existingTask = tasks.value.find(t => t.fileMd5 === md5)
    if (existingTask) {
      return existingTask
    }

    const newTask: Api.File.UploadTask = {
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
      orgTag: form.orgTag,
    }

    tasks.value.push(newTask)
    startUpload()
    return newTask
  }

  async function startUpload() {
    if (activeUploads.value.size >= 3)
      return

    const pendingTasks = tasks.value.filter(t => t.status === UploadStatus.Pending && !activeUploads.value.has(t.fileMd5))

    if (pendingTasks.length === 0)
      return

    const task = pendingTasks[0]
    task.status = UploadStatus.Uploading
    activeUploads.value.add(task.fileMd5)

    const totalChunks = Math.ceil(task.totalSize / chunkSize)

    try {
      for (let i = 0; i < totalChunks; i++) {
        if (task.uploadedChunks.includes(i))
          continue

        task.chunkIndex = i
        const success = await uploadChunk(task)
        if (!success)
          throw new Error('分片上传失败')
      }
    }
    catch {
      const index = tasks.value.findIndex(t => t.fileMd5 === task.fileMd5)
      tasks.value[index].status = UploadStatus.Break
    }
    finally {
      // 无论成功或失败，都从活跃队列中移除
      activeUploads.value.delete(task.fileMd5)
      startUpload() // 继续下一个任务
    }
  }

  return {
    tasks,
    activeUploads,
    enqueueUpload,
    startUpload,
  }
})
