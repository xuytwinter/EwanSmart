import SparkMD5 from 'spark-md5'

type WithChildren<T> = T & {
  children?: WithChildren<T>[]
}

export function flatArray<T>(data: WithChildren<T>[]): T[] {
  let result: T[] = []

  data.forEach((item) => {
    const newItem = { ...item }
    delete newItem.children
    result.push(newItem)
    if (item.children && item.children.length > 0) {
      result = result.concat(flatArray(item.children))
    }
  })

  return result
}

// 文件大小转换，根据文件大小转换为K、M、G
export function fileSize(size: number) {
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(2)}K`
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)}M`
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(2)}G`
}

export async function calculateMD5(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunkSize = 5 * 1024 * 1024 // 5MB
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()

    let currentChunk = 0

    const loadNext = () => {
      const start = currentChunk * chunkSize
      const end = Math.min(start + chunkSize, file.size)

      if (start >= file.size) {
        resolve(spark.end())
        return
      }

      const blob = file.slice(start, end)
      reader.readAsArrayBuffer(blob)
    }

    reader.onload = (e) => {
      spark.append(e.target?.result as ArrayBuffer)
      currentChunk++
      loadNext()
    }

    reader.onerror = () => reject(new Error('文件读取失败'))
    loadNext()
  })
}
