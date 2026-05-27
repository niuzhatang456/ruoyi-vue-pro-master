import request from '@/config/axios'
import type { Dayjs } from 'dayjs';

/** 房产情况信息 */
export interface Property {
          id: number; // ä¸»é”®ç¼–å·
          propertyAddress?: string; // æˆ¿äº§åœ°å€
          propertyName?: string; // æˆ¿äº§åç§°
          ownershipInfo?: string; // äº§æƒä¿¡æ¯
          buildingTime: string | Dayjs; // å»ºç­‘æ—¶é—´
          area: number; // å»ºç­‘é¢ç§¯ï¼ˆå¹³æ–¹ç±³ï¼‰
          leaseStatus: string; // ç§Ÿèµæƒ…å†µ
          remark: string; // å¤‡æ³¨
  }

// 房产情况 API
export const PropertyApi = {
  // 查询房产情况分页
  getPropertyPage: async (params: any) => {
    return await request.get({ url: `/jijian/property/page`, params })
  },

  // 查询房产情况详情
  getProperty: async (id: number) => {
    return await request.get({ url: `/jijian/property/get?id=` + id })
  },

  // 新增房产情况
  createProperty: async (data: Property) => {
    return await request.post({ url: `/jijian/property/create`, data })
  },

  // 修改房产情况
  updateProperty: async (data: Property) => {
    return await request.put({ url: `/jijian/property/update`, data })
  },

  // 删除房产情况
  deleteProperty: async (id: number) => {
    return await request.delete({ url: `/jijian/property/delete?id=` + id })
  },

  /** 批量删除房产情况 */
  deletePropertyList: async (ids: number[]) => {
    return await request.delete({ url: `/jijian/property/delete-list?ids=${ids.join(',')}` })
  },

  // 导出房产情况 Excel
  exportProperty: async (params) => {
    return await request.download({ url: `/jijian/property/export-excel`, params })
  }
}