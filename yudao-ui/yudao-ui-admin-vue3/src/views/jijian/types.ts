export type ImportSourceType = 'ocr' | 'excel' | 'drag'

export type ImportStatus = 'pending' | 'processing' | 'success' | 'failed' | 'confirmed'

export interface ImportRecord {
  id: string | number
  fileName: string
  sourceType: ImportSourceType
  detectedFormType: string
  status: ImportStatus
  createdAt: string
}

export interface ParsedData {
  id: string | number
  importRecordId: string | number
  fileName: string
  sourceType: ImportSourceType
  detectedFormType: string
  formType: string
  rawText: string
  parsedJson: string
  correctedJson?: string
  totalRows?: number
  confidence?: number
  /** 解析状态: success | failed */
  status: ImportStatus
  /** 确认状态: pending | confirmed（独立于解析状态）*/
  confirmStatus?: 'pending' | 'confirmed'
  errorMsg?: string
  confirmedPropertyId?: number
  /** 写入的正式业务表名，如 jijian_canteen_supplier */
  businessTable?: string
  /** 写入的正式业务记录 ID 列表（JSON 数组字符串） */
  businessIds?: string
  confirmTime?: string
  createdAt: string
}

export interface ConfirmWriteResult {
  parsedDataId: number
  formType: string
  businessTable?: string
  confirmedIds: number[]
  confirmedCount: number
  idempotent: boolean
  /** 原始解析总行数 */
  totalRows?: number
  /** 跳过行数（空白行/合计行） */
  skippedRows?: number
  /** 失败行数（有业务字段但关键字段缺失或解析异常） */
  failedRows?: number
  /** 跳过行原因列表（最多 20 条） */
  skippedMessages?: string[]
  /** 失败行原因列表（最多 20 条） */
  failedMessages?: string[]
}

export interface QueryHistoryItem {
  id: string
  question: string
  summary: string
  createdAt: string
}

export interface FavoriteQuery {
  id: string
  name: string
  content: string
  updatedAt: string
}

export interface AccountInfo {
  name: string
  department: string
  role: string
  username: string
  status: string
}

export interface SmartQueryResult {
  answer: string
  relatedFormTypes: string[]
  records: Array<{
    label: string
    value: string
  }>
}
