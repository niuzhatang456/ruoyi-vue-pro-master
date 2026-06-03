package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageRespVO;
import cn.iocoder.yudao.module.jijian.service.query.dto.AttendanceSummaryDTO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;

import java.util.List;

public interface JijianAttendanceQueryService {

    /** 分页查询 + 聚合统计（普通查询接口） */
    JijianAttendancePageRespVO queryPage(JijianAttendancePageReqVO req);

    /** 仅聚合统计（AI 查询接口内部调用） */
    AttendanceSummaryDTO querySummary(JijianAiQueryIntent intent);

    /** 获取部门下拉列表 */
    List<String> listDepartments();
}
