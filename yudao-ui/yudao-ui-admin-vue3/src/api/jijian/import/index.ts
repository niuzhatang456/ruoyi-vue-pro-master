import request from '@/config/axios'

export type ImportSourceType = 'ocr' | 'excel' | 'drag'

export interface ImportRecordVO {
  id: number
  fileName: string
  sourceType: ImportSourceType
  detectedFormType: string
  status: 'pending' | 'processing' | 'success' | 'failed'
  createdAt: string
}

export interface ParsedDataVO {
  id: number
  importRecordId: number
  fileName: string
  sourceType: ImportSourceType
  detectedFormType: string
  formType: string
  rawText: string
  parsedJson: string
  correctedJson?: string
  confidence?: number
  /** 解析状态 */
  status: 'pending' | 'processing' | 'success' | 'failed' | 'confirmed'
  /** 确认状态（独立字段：pending | confirmed） */
  confirmStatus?: 'pending' | 'confirmed'
  errorMsg?: string
  confirmedPropertyId?: number
  /** 写入的正式业务表名 */
  businessTable?: string
  /** 写入的正式业务记录 ID 列表（JSON 数组字符串） */
  businessIds?: string
  confirmTime?: string
  createdAt: string
}

const appendFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return formData
}

export const uploadOcrImportFile = async (file: File) => {
  return await request.post<ImportRecordVO>({
    url: '/jijian/import/ocr',
    data: appendFile(file),
    headersType: 'multipart/form-data'
  })
}

export const uploadExcelImportFile = async (file: File) => {
  return await request.post<ImportRecordVO>({
    url: '/jijian/import/excel',
    data: appendFile(file),
    headersType: 'multipart/form-data'
  })
}

export const uploadDragImportFiles = async (files: File[]) => {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return await request.post<ImportRecordVO[]>({
    url: '/jijian/import/drag',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const uploadImportFile = async (file: File, sourceType: ImportSourceType) => {
  const formData = appendFile(file)
  formData.append('sourceType', sourceType)
  return await request.post<ImportRecordVO>({
    url: '/jijian/import/upload',
    data: formData,
    headersType: 'multipart/form-data'
  })
}

export const getImportRecordList = async () => {
  return await request.get<ImportRecordVO[]>({ url: '/jijian/import/list' })
}

export const getImportRecord = async (id: number) => {
  return await request.get<ImportRecordVO>({ url: `/jijian/import/${id}` })
}

export const getParsedData = async (importRecordId: number | string) => {
  return await request.get<ParsedDataVO>({ url: `/jijian/import/parsed/${importRecordId}` })
}

export const getParsedDataList = async (importRecordId?: number | string) => {
  return await request.get<ParsedDataVO[]>({
    url: '/jijian/import/parsed/list',
    params: importRecordId ? { importRecordId } : undefined
  })
}

export interface ConfirmPropertyVO {
  propertyId: number
  parsedDataId: number
}

export interface ConfirmWriteResultVO {
  parsedDataId: number
  formType: string
  businessTable?: string
  confirmedIds: number[]
  confirmedCount: number
  idempotent: boolean
}

/** 通用确认写入（所有9种业务类型） */
export const confirmWrite = async (parsedDataId: number | string) => {
  return await request.post<ConfirmWriteResultVO>({
    url: `/jijian/import/parsed/${parsedDataId}/confirm`
  })
}

/** 向后兼容：专用房产确认（旧端点） */
export const confirmProperty = async (parsedDataId: number | string) => {
  return await request.post<ConfirmPropertyVO>({
    url: `/jijian/import/parsed/${parsedDataId}/confirm-property`
  })
}

/** 保存用户校正 JSON */
export const saveCorrection = async (parsedDataId: number | string, correctedJson: string) => {
  return await request.put<boolean>({
    url: `/jijian/import/parsed/${parsedDataId}/correction`,
    data: { correctedJson }
  })
}

export const getParsedDataDetail = async (parsedDataId: number | string) => {
  return await request.get<ParsedDataVO>({
    url: `/jijian/import/parsed/detail/${parsedDataId}`
  })
}
