package cn.iocoder.yudao.module.jijian.enums;

/**
 * 录入模块业务表单类型常量
 * 与解析数据 formType 字段以及确认写入路由保持一致
 */
public interface FormTypeConstants {

    /** 房产情况 */
    String PROPERTY       = "房产信息";
    /** 租赁人员 */
    String LESSEE         = "租赁人员";
    /** 租赁合同 */
    String LEASE_CONTRACT = "租赁合同";
    /** 考勤日报 */
    String ATTENDANCE     = "考勤日报";
    /** 疗休养假 */
    String LEAVE_HEALTH   = "疗休养假";
    /** 事假 */
    String LEAVE_PERSONAL = "事假记录";
    /** 出差 */
    String BUSINESS_TRIP  = "出差记录";
    /** 调休 */
    String COMPENSATORY   = "调休记录";
    /** 食堂供应 */
    String CANTEEN        = "食堂供应";
    /** 民生商品市场零售价格公告 */
    String CANTEEN_MARKET_PRICE = "民生价格公告";

}
