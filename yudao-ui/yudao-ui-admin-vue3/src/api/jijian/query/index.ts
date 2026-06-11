import request from '@/config/axios'

// ===== 类型定义（与后端 VO 对应） =====

export interface JijianMetricVO {
  key: string
  label: string
  value: any
  unit?: string
  description?: string
}

export interface JijianChartSeriesVO {
  name: string
  data: any[]
}

export interface JijianChartVO {
  type: 'pie' | 'bar' | 'line' | 'table'
  title?: string
  description?: string
  xAxis?: string[]
  series?: JijianChartSeriesVO[]
  data?: Array<{ name: string; value: any; [key: string]: any }>
}

export interface JijianAnalysisTableVO {
  title?: string
  columns: Array<{ key: string; label: string }>
  rows: Array<Record<string, any>>
}

export interface JijianDatabaseContextMetaVO {
  tablesUsed?: string[]
  rowCounts?: Record<string, number>
  dataSource?: string
  sensitiveFieldsRemoved?: boolean
  truncated?: boolean
  timeRange?: string
}

export interface JijianChatHistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export interface JijianQueryChatReqVO {
  conversationId?: string | null
  formType?: string | null
  department?: string | null
  timeRange?: string
  message: string
  history?: JijianChatHistoryItem[]
}

export interface JijianSqlTraceVO {
  purpose: string
  sql: string
  rowCount: number
  error?: string
}

export interface JijianQueryChatRespVO {
  queryHistoryId?: number
  conversationId: string
  answer?: string
  formType?: string
  aiMode?: string
  data?: any
  summary?: any
  columns?: Array<{ key: string; label: string }>
  pageResult?: { list: Array<Record<string, any>>; total: number }
  metrics?: JijianMetricVO[]
  charts?: JijianChartVO[]
  tables?: JijianAnalysisTableVO[]
  databaseContextMeta?: JijianDatabaseContextMetaVO
  sqlTrace?: JijianSqlTraceVO[]
}

// ===== API 方法 =====

export const JijianQueryApi = {
  chat: (req: JijianQueryChatReqVO) =>
    request.post<JijianQueryChatRespVO>({
      url: '/jijian/query/chat',
      data: req,
    }),

  agentChat: (req: JijianQueryChatReqVO) =>
    request.post<JijianQueryChatRespVO>({
      url: '/jijian/query/agent-chat',
      data: req,
    }),
}
