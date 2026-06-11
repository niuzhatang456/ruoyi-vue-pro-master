import request from '@/config/axios'
import type { PageResult } from '@/api/jijian/queryHistory'

export interface DisposalRecordVO {
  id: number
  queryHistoryId?: number
  queryQuestion: string
  queryAnswer?: string
  queryResultJson?: string
  disposalOpinion: string
  disposalUserId: number
  disposalUserName?: string
  disposalTime: string
  sourceModule: string
  remark?: string
  createTime: string
}

export interface DisposalRecordCreateReqVO {
  queryHistoryId?: number
  queryQuestion?: string
  queryAnswer?: string
  queryResultJson?: string
  disposalOpinion: string
  sourceModule?: string
  remark?: string
}

export const DisposalRecordApi = {
  create: (data: DisposalRecordCreateReqVO) =>
    request.post<number>({
      url: '/jijian/disposal-record/create',
      data
    }),
  getPage: (params: { pageNo: number; pageSize: number; queryQuestion?: string }) =>
    request.get<PageResult<DisposalRecordVO>>({
      url: '/jijian/disposal-record/page',
      params
    }),
  get: (id: number) =>
    request.get<DisposalRecordVO>({
      url: '/jijian/disposal-record/get',
      params: { id }
    }),
  delete: (id: number) =>
    request.delete<boolean>({
      url: '/jijian/disposal-record/delete',
      params: { id }
    })
}
