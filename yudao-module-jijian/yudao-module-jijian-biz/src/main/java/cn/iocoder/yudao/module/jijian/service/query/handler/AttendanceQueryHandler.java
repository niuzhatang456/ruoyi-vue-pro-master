package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.*;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.JijianAttendanceQueryService;
import cn.iocoder.yudao.module.jijian.service.query.dto.AttendanceSummaryDTO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 查询 Handler — 考勤信息（ATTENDANCE）。
 *
 * <p>委托 {@link JijianAttendanceQueryService} 执行实际查询，
 * 不重复实现 Mapper 逻辑，保持单一职责。
 */
@Component
public class AttendanceQueryHandler implements JijianFormQueryHandler {

    @Resource
    private JijianAttendanceQueryService attendanceQueryService;

    @Override
    public JijianQueryFormTypeEnum getFormType() {
        return JijianQueryFormTypeEnum.ATTENDANCE;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public List<JijianQueryDepartmentVO> getDepartments() {
        List<JijianQueryDepartmentVO> list = new ArrayList<>();
        list.add(new JijianQueryDepartmentVO("ALL", "全单位"));
        attendanceQueryService.listDepartments().forEach(dept ->
                list.add(new JijianQueryDepartmentVO(dept, dept)));
        return list;
    }

    /** 向后兼容的专用 attendance 分页查询（老接口 /attendance/page 使用）。 */
    @Override
    public JijianAttendancePageRespVO pageQuery(JijianAttendancePageReqVO req) {
        return attendanceQueryService.queryPage(req);
    }

    /** 通用分页查询：把 AttendanceItemVO 转为 Map，附加列定义和摘要。 */
    @Override
    public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
        // 复用 attendance 专用请求 VO 的分页和过滤逻辑
        JijianAttendancePageReqVO attReq = new JijianAttendancePageReqVO();
        attReq.setPageNo(req.getPageNo());
        attReq.setPageSize(req.getPageSize());
        attReq.setDepartment(req.getDepartment());
        attReq.setTimeRange(req.getTimeRange());

        JijianAttendancePageRespVO attResp = attendanceQueryService.queryPage(attReq);

        // 把 AttendanceItemVO 转为 Map<String, Object>
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JijianAttendancePageRespVO.AttendanceItemVO item : attResp.getPageResult().getList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("department",      item.getDepartment());
            row.put("employeeName",    item.getEmployeeName());
            row.put("attendanceDate",  item.getAttendanceDate());
            row.put("checkinResult",   item.getCheckinResult());
            row.put("checkoutResult",  item.getCheckoutResult());
            rows.add(row);
        }

        // 列定义
        List<JijianQueryPageRespVO.ColumnDef> columns = Arrays.asList(
                JijianQueryPageRespVO.ColumnDef.of("department",     "部门",     "text"),
                JijianQueryPageRespVO.ColumnDef.of("employeeName",   "姓名",     "text"),
                JijianQueryPageRespVO.ColumnDef.of("attendanceDate", "考勤日期", "date"),
                JijianQueryPageRespVO.ColumnDef.of("checkinResult",  "签到结果", "text"),
                JijianQueryPageRespVO.ColumnDef.of("checkoutResult", "签退结果", "text")
        );

        JijianQueryPageRespVO resp = new JijianQueryPageRespVO();
        resp.setPageResult(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(
                rows, attResp.getPageResult().getTotal()));
        resp.setSummary(attResp.getSummary());
        resp.setColumns(columns);
        return resp;
    }

    @Override
    public Object summaryByIntent(JijianAiQueryIntent intent) {
        return attendanceQueryService.querySummary(intent);
    }

    // ── 工具方法 ──

    /** 把 JijianQueryPageReqVO 转成 AttendancePageReqVO（共享部门/时间/分页字段） */
    private JijianAttendancePageReqVO toAttReq(JijianQueryPageReqVO req) {
        JijianAttendancePageReqVO r = new JijianAttendancePageReqVO();
        r.setPageNo(req.getPageNo());
        r.setPageSize(req.getPageSize());
        r.setDepartment(req.getDepartment());
        r.setTimeRange(req.getTimeRange());
        return r;
    }
}
