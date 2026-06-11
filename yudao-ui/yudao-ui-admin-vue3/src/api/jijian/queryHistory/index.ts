import request from '@/config/axios'

export interface QueryHistoryVO {
  id: number
  userId?: number
  userName?: string
  question: string
  answer?: string
  queryType?: string
  formType?: string
  querySql?: string
  queryResultJson?: string
  databaseContextMetaJson?: string
  modelName?: string
  success: boolean
  errorMessage?: string
  remark?: string
  createTime: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

export const QueryHistoryApi = {
  getPage: (params: { pageNo: number; pageSize: number; question?: string }) =>
    request.get<PageResult<QueryHistoryVO>>({
      url: '/jijian/query-history/page',
      params
    }),
  get: (id: number) =>
    request.get<QueryHistoryVO>({
      url: '/jijian/query-history/get',
      params: { id }
    }),
  delete: (id: number) =>
    request.delete<boolean>({
      url: '/jijian/query-history/delete',
      params: { id }
    })
}
