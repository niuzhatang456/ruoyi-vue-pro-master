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
  confidence?: number
  status: 'pending' | 'processing' | 'success' | 'failed'
  errorMsg?: string
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
