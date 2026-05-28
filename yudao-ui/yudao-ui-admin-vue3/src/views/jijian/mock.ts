import type {
  AccountInfo,
  FavoriteQuery,
  ImportRecord,
  ImportSourceType,
  QueryHistoryItem,
  SmartQueryResult
} from './types'

export const mockImportRecords: ImportRecord[] = [
  {
    id: 'IMP-20260527-001',
    fileName: '办公楼资产台账.xlsx',
    sourceType: 'excel',
    detectedFormType: '房产信息',
    status: 'success',
    createdAt: '2026-05-27 09:20:00'
  },
  {
    id: 'IMP-20260527-002',
    fileName: '合同审批材料.pdf',
    sourceType: 'ocr',
    detectedFormType: '合同管理',
    status: 'processing',
    createdAt: '2026-05-27 09:35:00'
  },
  {
    id: 'IMP-20260527-003',
    fileName: '报销单据批量.zip',
    sourceType: 'drag',
    detectedFormType: '报销信息',
    status: 'pending',
    createdAt: '2026-05-27 09:48:00'
  },
  {
    id: 'IMP-20260527-004',
    fileName: '5月考勤异常汇总.xlsx',
    sourceType: 'excel',
    detectedFormType: '考勤信息',
    status: 'success',
    createdAt: '2026-05-27 10:05:00'
  }
]

export const mockHistoryList: QueryHistoryItem[] = [
  {
    id: 'HIS-001',
    question: '查询某人员名下房产信息',
    summary: '命中 2 条结构化记录，包含房产地址、产权信息和导入时间。',
    createdAt: '2026-05-27 09:10:00'
  },
  {
    id: 'HIS-002',
    question: '统计某部门考勤异常情况',
    summary: '模拟统计迟到 3 次、缺卡 1 次，后续接入智能查询接口生成正式结果。',
    createdAt: '2026-05-27 09:25:00'
  },
  {
    id: 'HIS-003',
    question: '查询某合同的审批记录',
    summary: '展示审批节点、经办人和审批意见的结果占位。',
    createdAt: '2026-05-27 09:40:00'
  }
]

export const mockFavorites: FavoriteQuery[] = [
  {
    id: 'FAV-001',
    name: '房产核查',
    content: '查询某人员名下房产信息',
    updatedAt: '2026-05-27 09:00:00'
  },
  {
    id: 'FAV-002',
    name: '考勤异常统计',
    content: '统计某部门考勤异常情况',
    updatedAt: '2026-05-27 09:05:00'
  },
  {
    id: 'FAV-003',
    name: '合同审批查询',
    content: '查询某合同的审批记录',
    updatedAt: '2026-05-27 09:15:00'
  }
]

export const mockAccountInfo: AccountInfo = {
  name: '纪检管理员',
  department: '纪检办公室',
  role: '系统管理员',
  username: 'admin',
  status: '正常'
}

export const smartQueryExamples = [
  '查询某人员名下房产信息',
  '统计某部门考勤异常情况',
  '查询某合同的审批记录'
]

const detectedFormTypes = ['房产信息', '考勤信息', '合同管理', '报销信息', '人员信息']

const formatDateTime = (date: Date) => {
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export const createMockImportRecord = (
  fileName: string,
  sourceType: ImportSourceType,
  index = 0
): ImportRecord => ({
  id: `IMP-${Date.now()}-${index + 1}`,
  fileName,
  sourceType,
  detectedFormType: detectedFormTypes[(Date.now() + index) % detectedFormTypes.length],
  status: 'processing',
  createdAt: formatDateTime(new Date())
})

export const createMockQueryResult = (question: string): SmartQueryResult => {
  const relatedFormTypes = smartQueryExamples
    .filter((item) => question.includes(item.slice(0, 2)) || item.includes(question.slice(0, 2)))
    .map((item) =>
      item.includes('房产') ? '房产信息' : item.includes('考勤') ? '考勤信息' : '合同管理'
    )

  return {
    answer: question
      ? `已生成“${question}”的模拟查询结果。后续接入智能查询接口后，将返回真实问答、统计和明细数据。`
      : '请输入自然语言问题后发起查询。',
    relatedFormTypes: relatedFormTypes.length ? relatedFormTypes : ['房产信息', '考勤信息', '合同管理'],
    records: [
      { label: '命中记录', value: '3 条' },
      { label: '数据来源', value: 'OCR、Excel、拖拽导入记录' },
      { label: '处理状态', value: '模拟结果，待接入后端接口' }
    ]
  }
}
