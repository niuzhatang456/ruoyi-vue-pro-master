export type ImportSourceType = 'ocr' | 'excel' | 'drag'

export type ImportStatus = 'pending' | 'processing' | 'success' | 'failed'

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
  confidence?: number
  status: ImportStatus
  errorMsg?: string
  createdAt: string
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
