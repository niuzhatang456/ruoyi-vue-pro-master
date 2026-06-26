import request from '@/config/axios'
import type { ImportRecordVO } from '@/api/jijian/import'

const buildUpload = (url: string) => async (file: File): Promise<ImportRecordVO> => {
  const form = new FormData()
  form.append('file', file)
  return await request.post({ url, data: form, headersType: 'multipart/form-data' })
}

/** 房产情况表 - 拖拽上传（服务端硬绑定 PROPERTY_INFO） */
export const dragUploadPropertyInfo = buildUpload('/jijian/input/property-info/drag-upload')

/** 租赁人员表 - 拖拽上传（服务端硬绑定 LESSEE） */
export const dragUploadLessee = buildUpload('/jijian/input/lessee/drag-upload')

/** 租赁合同表 - 拖拽上传（服务端硬绑定 LEASE_CONTRACT） */
export const dragUploadLeaseContract = buildUpload('/jijian/input/lease-contract/drag-upload')

/** 考勤日报表 - 拖拽上传（服务端硬绑定 ATTENDANCE_DAILY） */
export const dragUploadAttendanceDaily = buildUpload('/jijian/input/attendance-daily/drag-upload')

/** 疗休养请假表 - 拖拽上传（服务端硬绑定 RECUPERATION_LEAVE） */
export const dragUploadRecuperationLeave = buildUpload('/jijian/input/recuperation-leave/drag-upload')

/** 事假表 - 拖拽上传（服务端硬绑定 PERSONAL_LEAVE） */
export const dragUploadPersonalLeave = buildUpload('/jijian/input/personal-leave/drag-upload')

/** 出差表 - 拖拽上传（服务端硬绑定 BUSINESS_TRIP） */
export const dragUploadBusinessTrip = buildUpload('/jijian/input/business-trip/drag-upload')

/** 调休表 - 拖拽上传（服务端硬绑定 COMPENSATORY_LEAVE） */
export const dragUploadCompensatoryLeave = buildUpload('/jijian/input/compensatory-leave/drag-upload')

/** 食堂供应商信息表 - 拖拽上传（服务端硬绑定 CANTEEN_SUPPLIER） */
export const dragUploadCanteenSupplier = buildUpload('/jijian/input/canteen-supplier/drag-upload')

/** 民生商品市场零售价格公告 - 拖拽上传（服务端硬绑定 CANTEEN_MARKET_PRICE） */
export const dragUploadCanteenMarketPrice = buildUpload('/jijian/input/canteen-market-price/drag-upload')
