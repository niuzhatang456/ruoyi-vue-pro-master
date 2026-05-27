package cn.iocoder.yudao.module.system.service.ai;

import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AiQueryAttendanceRespVO;

/**
 * 智能查询 Service
 */
public interface AiQueryService {

    /**
     * 自然语言查询考勤日报表
     *
     * @param message 自然语言
     * @return 查询结果
     */
    AiQueryAttendanceRespVO queryAttendance(String message);

}
