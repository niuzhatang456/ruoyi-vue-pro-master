import request from '@/config/axios'

// ============================================================
// 数据类型
// ============================================================
export interface QueryFormTypeVO {
  value: string
  label: string
  supported: boolean
}

// ============================================================
// 部门
// ============================================================
export interface QueryDepartmentVO {
  value: string
  label: string
}

// ============================================================
// 普通查询 — 向后兼容（ATTENDANCE 专用）
// ============================================================
export interface JijianAttendancePageReqVO {
  formType: string
  department: string
  timeRange: string
  pageNo: number
  pageSize: number
}

export interface AttendanceItemVO {
  id: number
  employeeName?: string
  employeeNo?: string
  department?: string
  attendanceDate?: string
  checkinTime?: string
  checkinResult?: string
  checkoutTime?: string
  checkoutResult?: string
}

export interface DepartmentCountDTO {
  department: string
  count: number
}

export interface StatusCountDTO {
  status: string
  count: number
}

export interface AttendanceSummaryDTO {
  totalCount: number
  departmentCount: number
  byDepartment: DepartmentCountDTO[]
  byAttendanceStatus: StatusCountDTO[]
}

export interface JijianAttendancePageRespVO {
  pageResult: {
    list: AttendanceItemVO[]
    total: number
  }
  summary: AttendanceSummaryDTO
}

// ============================================================
// 通用分页查询
// ============================================================
export interface JijianQueryPageReqVO {
  formType: string
  department: string
  timeRange: string
  pageNo: number
  pageSize: number
}

export interface ColumnDef {
  key: string
  label: string
  type: 'text' | 'number' | 'date'
}

export interface JijianQueryPageRespVO {
  pageResult: {
    list: Record<string, unknown>[]
    total: number
  }
  summary: Record<string, unknown> | AttendanceSummaryDTO | null
  columns: ColumnDef[]
}

// ============================================================
// AI 受控查询
// ============================================================
export interface ChatHistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export interface JijianQueryChatReqVO {
  conversationId?: string
  formType: string
  department: string
  timeRange: string
  message: string
  history: ChatHistoryItem[]
}

export interface JijianMetricVO {
  key: string
  label: string
  value: unknown
  unit?: string
  description?: string
}

export interface JijianChartSeriesVO { name: string; data: unknown[] }

export interface JijianChartVO {
  type: 'pie' | 'bar' | 'line'
  title: string
  description?: string
  xAxis?: string[]
  series?: JijianChartSeriesVO[]
  data?: Array<{ name: string; value: number }>
}

export interface JijianAnalysisTableColumnVO { key: string; label: string }

export interface JijianAnalysisTableVO {
  title: string
  columns: JijianAnalysisTableColumnVO[]
  rows: Record<string, unknown>[]
}

export interface JijianDatabaseContextMetaVO {
  tablesUsed?: string[]
  rowCounts?: Record<string, number>
  dataSource?: string
  sensitiveFieldsRemoved?: boolean
  truncated?: boolean
  timeRange?: string
}

export interface JijianQueryChatRespVO {
  conversationId: string
  answer: string
  formType?: string
  queryIntent: Record<string, unknown>
  /** 具体结构依 formType 而定 */
  data: Record<string, unknown> | null
  /** 新结构：聚合统计，前端优先读取；旧版本可回退到 data */
  summary?: Record<string, unknown> | null
  columns?: ColumnDef[]
  pageResult?: { list: Record<string, unknown>[]; total: number }
  aiMode: string
  // 图表化分析新增字段
  metrics?: JijianMetricVO[]
  charts?: JijianChartVO[]
  tables?: JijianAnalysisTableVO[]
  databaseContextMeta?: JijianDatabaseContextMetaVO
}

// ============================================================
// API
// ============================================================
export interface JijianQueryFilterOptionsVO {
  departments: string[]
  months: string[]
  dateRange: { min: string | null; max: string | null }
  hasDepartment: boolean
  hasDateField: boolean
}

export const JijianQueryApi = {
  /** 获取支持查询的数据类型列表 */
  getFormTypes: async (): Promise<QueryFormTypeVO[]> => {
    return await request.get({ url: '/jijian/query/form-types' })
  },

  /** 获取部门列表（按 formType 路由） */
  getDepartments: async (formType: string): Promise<QueryDepartmentVO[]> => {
    return await request.get({ url: '/jijian/query/departments', params: { formType } })
  },

  /** 考勤分页查询（向后兼容旧接口） */
  pageAttendance: async (data: JijianAttendancePageReqVO): Promise<JijianAttendancePageRespVO> => {
    return await request.post({ url: '/jijian/query/attendance/page', data })
  },

  /** 通用分页查询 */
  page: async (data: JijianQueryPageReqVO): Promise<JijianQueryPageRespVO> => {
    return await request.post({ url: '/jijian/query/page', data })
  },

  /** 获取真实过滤选项（部门 / 月份 / 日期范围，来自数据库） */
  getFilterOptions: async (type: string): Promise<JijianQueryFilterOptionsVO> => {
    return await request.get({ url: '/jijian/query/filter-options', params: { type } })
  },

  /** 受控 AI 自然语言查询 */
  chat: async (data: JijianQueryChatReqVO): Promise<JijianQueryChatRespVO> => {
    return await request.post({ url: '/jijian/query/chat', data })
  }
}
