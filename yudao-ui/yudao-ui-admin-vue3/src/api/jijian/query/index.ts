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
// 通用分页查询（当前仅支持 ATTENDANCE）
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
  summary: AttendanceSummaryDTO | null
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

export interface JijianQueryChatRespVO {
  conversationId: string
  answer: string
  queryIntent: Record<string, unknown>
  /** 具体结构依 formType 而定；当前为 AttendanceSummaryDTO */
  data: Record<string, unknown> | null
  aiMode: string
}

// ============================================================
// API
// ============================================================
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

  /** 通用分页查询（当前仅支持 ATTENDANCE） */
  page: async (data: JijianQueryPageReqVO): Promise<JijianQueryPageRespVO> => {
    return await request.post({ url: '/jijian/query/page', data })
  },

  /** 受控 AI 自然语言查询 */
  chat: async (data: JijianQueryChatReqVO): Promise<JijianQueryChatRespVO> => {
    return await request.post({ url: '/jijian/query/chat', data })
  }
}
