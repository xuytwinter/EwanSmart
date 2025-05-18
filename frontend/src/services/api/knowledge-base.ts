export function fetchGetFileList() {
  return alova.Get<Api.File.UploadTask[]>('/v1/documents/uploads')
}

export function fetchUploadChunk(data: Api.File.UploadTask) {
  return alova.Post<Api.File.Progress>('/v1/upload/chunk', { file: data.chunk }, {
    headers: {
      'X-File-Md5': data.fileMd5,
      'X-Chunk-Index': data.chunkIndex,
      'X-Total-Size': data.totalSize,
      'X-File-Name': data.fileName,
      'X-Org-Tag': data.orgTag,
      'X-Is-Public': data.isPublic,
    },
  })
}

export function fetchGetUploadStatus(fileMd5: string) {
  return alova.Get<Api.File.Progress>(`/v1/upload/status`, { params: { fileMd5 } })
}

export function fetchMergeChunk(params: Api.File.Merge) {
  return alova.Post<Api.File.Result>('/v1/upload/merge', { params })
}

export function fetchDeleteFile(fileMd5: string) {
  return alova.Delete(`/v1/documents/${fileMd5}`)
}
