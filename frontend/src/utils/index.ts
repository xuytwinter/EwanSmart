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
