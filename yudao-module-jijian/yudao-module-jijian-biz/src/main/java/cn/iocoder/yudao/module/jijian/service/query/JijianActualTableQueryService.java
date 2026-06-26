package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip.BusinessTripDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract.LeaseContractDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal.LeavePersonalDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.lessee.LesseeDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.Property.PropertyMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily.AttendanceDailyMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.businesstrip.BusinessTripMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.canteensupplier.CanteenSupplierMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave.CompensatoryLeaveMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leasecontract.LeaseContractMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth.LeaveHealthMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavepersonal.LeavePersonalMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.lessee.LesseeMapper;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryFilterOptionsVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JijianActualTableQueryService {

    private static final String ALL = "ALL";
    private static final String UNKNOWN = "\u672a\u586b\u5199";
    private static final int EXPIRING_SOON_DAYS = 30;

    @Resource
    private PropertyMapper propertyMapper;
    @Resource
    private LesseeMapper lesseeMapper;
    @Resource
    private LeaseContractMapper leaseContractMapper;
    @Resource
    private AttendanceDailyMapper attendanceDailyMapper;
    @Resource
    private LeaveHealthMapper leaveHealthMapper;
    @Resource
    private LeavePersonalMapper leavePersonalMapper;
    @Resource
    private BusinessTripMapper businessTripMapper;
    @Resource
    private CompensatoryLeaveMapper compensatoryLeaveMapper;
    @Resource
    private CanteenSupplierMapper canteenSupplierMapper;

    public JijianQueryPageRespVO queryPage(JijianQueryFormTypeEnum formType, JijianQueryPageReqVO req) {
        LocalDateTime startTime = resolveStartTime(req.getTimeRange());
        switch (formType) {
            case PROPERTY_INFO:
                return propertyPage(req, startTime);
            case LESSEE:
                return lesseePage(req, startTime);
            case LEASE_CONTRACT:
                return leaseContractPage(req, startTime);
            case ATTENDANCE_DAILY:
                return attendanceDailyPage(req, resolveStartDate(req.getTimeRange()));
            case RECUPERATION_LEAVE:
                return recuperationLeavePage(req, startTime);
            case PERSONAL_LEAVE:
                return personalLeavePage(req, startTime);
            case BUSINESS_TRIP:
                return businessTripPage(req, startTime);
            case COMPENSATORY_LEAVE:
                return compensatoryLeavePage(req, startTime);
            case CANTEEN_SUPPLIER:
                return canteenSupplierPage(req, startTime);
            default:
                return emptyPage();
        }
    }

    public Object querySummary(JijianQueryFormTypeEnum formType, JijianAiQueryIntent intent) {
        LocalDateTime startTime = resolveStartTime(intent.getTimeRange());
        switch (formType) {
            case PROPERTY_INFO:
                return propertySummary(propertyMapper.selectListForSummary(startTime));
            case LESSEE:
                return lesseeSummary(lesseeMapper.selectListForSummary(startTime));
            case LEASE_CONTRACT:
                return leaseContractSummary(leaseContractMapper.selectListForSummary(startTime));
            case ATTENDANCE_DAILY:
                return attendanceDailySummary(attendanceDailyMapper.selectListForSummary(intent.getDepartment(), resolveStartDate(intent.getTimeRange())));
            case RECUPERATION_LEAVE:
                return leaveHealthSummary(leaveHealthMapper.selectListForQuery(intent.getDepartment(), startTime));
            case PERSONAL_LEAVE:
                return leavePersonalSummary(leavePersonalMapper.selectListForQuery(intent.getDepartment(), startTime));
            case BUSINESS_TRIP:
                return businessTripSummary(businessTripMapper.selectListForQuery(intent.getDepartment(), startTime));
            case COMPENSATORY_LEAVE:
                return compensatoryLeaveSummary(compensatoryLeaveMapper.selectListForQuery(intent.getDepartment(), startTime));
            case CANTEEN_SUPPLIER:
                return canteenSupplierSummary(canteenSupplierMapper.selectListForQuery(startTime));
            default:
                return Collections.emptyMap();
        }
    }

    public List<JijianQueryDepartmentVO> listDepartments(JijianQueryFormTypeEnum formType) {
        if (!hasDepartment(formType)) {
            return Collections.singletonList(new JijianQueryDepartmentVO(ALL, "\u5168\u90e8"));
        }
        List<String> departments;
        switch (formType) {
            case ATTENDANCE_DAILY:
                departments = attendanceDailyMapper.selectDistinctDepartments();
                break;
            case RECUPERATION_LEAVE:
                departments = leaveHealthMapper.selectDistinctDepartments();
                break;
            case PERSONAL_LEAVE:
                departments = leavePersonalMapper.selectDistinctDepartments();
                break;
            case BUSINESS_TRIP:
                departments = businessTripMapper.selectDistinctDepartments();
                break;
            case COMPENSATORY_LEAVE:
                departments = compensatoryLeaveMapper.selectDistinctDepartments();
                break;
            default:
                departments = Collections.emptyList();
        }
        List<JijianQueryDepartmentVO> result = new ArrayList<>();
        result.add(new JijianQueryDepartmentVO(ALL, "\u5168\u90e8"));
        for (String department : departments) {
            if (present(department)) {
                result.add(new JijianQueryDepartmentVO(department, department));
            }
        }
        return result;
    }

    /** 真实时间过滤下限，代表"全部数据"（不依赖 delete/status，仅靠极早日期） */
    private static final LocalDateTime ALL_DATA_START = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public JijianQueryFilterOptionsVO getFilterOptions(JijianQueryFormTypeEnum formType) {
        boolean hasDept = hasDepartment(formType);
        List<String> departments = new ArrayList<>();
        Set<String> monthsSet = new TreeSet<>();
        String minDate = null, maxDate = null;

        switch (formType) {
            case PROPERTY_INFO: {
                List<PropertyDO> all = propertyMapper.selectListForSummary(ALL_DATA_START);
                for (PropertyDO r : all) {
                    addMonth(monthsSet, r.getBuildingTime());
                }
                DateBound b = dateBound(all, r -> r.getBuildingTime());
                minDate = b.min; maxDate = b.max;
                break;
            }
            case LESSEE: {
                List<LesseeDO> all = lesseeMapper.selectListForSummary(ALL_DATA_START);
                DateBound b = dateBoundDT(all.stream().map(r -> r.getCreateTime()).collect(Collectors.toList()));
                minDate = b.min; maxDate = b.max;
                break;
            }
            case LEASE_CONTRACT: {
                List<LeaseContractDO> all = leaseContractMapper.selectListForSummary(ALL_DATA_START);
                for (LeaseContractDO r : all) {
                    addMonth(monthsSet, r.getContractStartTime());
                }
                DateBound b = dateBound(all, r -> r.getContractStartTime());
                minDate = b.min; maxDate = b.max;
                break;
            }
            case ATTENDANCE_DAILY: {
                departments = attendanceDailyMapper.selectDistinctDepartments();
                List<AttendanceDailyDO> all = attendanceDailyMapper.selectListForSummary("ALL", LocalDate.of(2000, 1, 1));
                for (AttendanceDailyDO r : all) {
                    if (r.getAttendanceDate() != null) {
                        monthsSet.add(r.getAttendanceDate().format(DateTimeFormatter.ofPattern("yyyy-MM")));
                    }
                }
                if (!all.isEmpty()) {
                    LocalDate mn = all.stream().map(AttendanceDailyDO::getAttendanceDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
                    LocalDate mx = all.stream().map(AttendanceDailyDO::getAttendanceDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                    if (mn != null) minDate = mn.toString();
                    if (mx != null) maxDate = mx.toString();
                }
                break;
            }
            case RECUPERATION_LEAVE: {
                departments = leaveHealthMapper.selectDistinctDepartments();
                List<LeaveHealthDO> all = leaveHealthMapper.selectListForQuery("ALL", ALL_DATA_START);
                for (LeaveHealthDO r : all) {
                    addMonth(monthsSet, r.getStartTime() != null ? r.getStartTime() : r.getCreateTime());
                }
                DateBound b = dateBoundDT(all.stream()
                        .map(r -> r.getStartTime() != null ? r.getStartTime() : r.getCreateTime())
                        .collect(Collectors.toList()));
                minDate = b.min; maxDate = b.max;
                break;
            }
            case PERSONAL_LEAVE: {
                departments = leavePersonalMapper.selectDistinctDepartments();
                List<LeavePersonalDO> all = leavePersonalMapper.selectListForQuery("ALL", ALL_DATA_START);
                for (LeavePersonalDO r : all) {
                    addMonth(monthsSet, r.getStartTime() != null ? r.getStartTime() : r.getCreateTime());
                }
                DateBound b = dateBoundDT(all.stream()
                        .map(r -> r.getStartTime() != null ? r.getStartTime() : r.getCreateTime())
                        .collect(Collectors.toList()));
                minDate = b.min; maxDate = b.max;
                break;
            }
            case BUSINESS_TRIP: {
                departments = businessTripMapper.selectDistinctDepartments();
                List<BusinessTripDO> all = businessTripMapper.selectListForQuery("ALL", ALL_DATA_START);
                for (BusinessTripDO r : all) {
                    addMonth(monthsSet, businessTripStart(r));
                }
                DateBound b = dateBoundDT(all.stream()
                        .map(this::businessTripStart)
                        .collect(Collectors.toList()));
                minDate = b.min; maxDate = b.max;
                break;
            }
            case COMPENSATORY_LEAVE: {
                departments = compensatoryLeaveMapper.selectDistinctDepartments();
                List<CompensatoryLeaveDO> all = compensatoryLeaveMapper.selectListForQuery("ALL", ALL_DATA_START);
                for (CompensatoryLeaveDO r : all) {
                    addMonth(monthsSet, r.getCompensatoryStartTime() != null ? r.getCompensatoryStartTime() : r.getCreateTime());
                }
                DateBound b = dateBoundDT(all.stream()
                        .map(r -> r.getCompensatoryStartTime() != null ? r.getCompensatoryStartTime() : r.getCreateTime())
                        .collect(Collectors.toList()));
                minDate = b.min; maxDate = b.max;
                break;
            }
            case CANTEEN_SUPPLIER: {
                List<CanteenSupplierDO> all = canteenSupplierMapper.selectListForQuery(ALL_DATA_START);
                DateBound b = dateBoundDT(all.stream().map(r -> r.getCreateTime()).collect(Collectors.toList()));
                minDate = b.min; maxDate = b.max;
                break;
            }
            default:
                break;
        }

        List<String> allDepts = new ArrayList<>();
        for (String d : departments) {
            if (present(d)) allDepts.add(d);
        }
        return JijianQueryFilterOptionsVO.builder()
                .hasDepartment(hasDept)
                .hasDateField(!monthsSet.isEmpty() || minDate != null)
                .departments(allDepts)
                .months(new ArrayList<>(monthsSet))
                .dateRange(JijianQueryFilterOptionsVO.DateRange.builder().min(minDate).max(maxDate).build())
                .build();
    }

    private void addMonth(Set<String> set, LocalDateTime dt) {
        if (dt != null) set.add(dt.format(MONTH_FMT));
    }

    private static class DateBound { String min; String max; }

    private <T> DateBound dateBound(List<T> list, Function<T, LocalDateTime> getter) {
        DateBound b = new DateBound();
        List<LocalDateTime> times = list.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toList());
        if (!times.isEmpty()) {
            b.min = times.stream().min(Comparator.naturalOrder()).get().format(DATE_FMT);
            b.max = times.stream().max(Comparator.naturalOrder()).get().format(DATE_FMT);
        }
        return b;
    }

    private DateBound dateBoundDT(List<LocalDateTime> times) {
        DateBound b = new DateBound();
        List<LocalDateTime> filtered = times.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (!filtered.isEmpty()) {
            b.min = filtered.stream().min(Comparator.naturalOrder()).get().format(DATE_FMT);
            b.max = filtered.stream().max(Comparator.naturalOrder()).get().format(DATE_FMT);
        }
        return b;
    }

    public boolean hasDepartment(JijianQueryFormTypeEnum formType) {
        return formType == JijianQueryFormTypeEnum.ATTENDANCE_DAILY
                || formType == JijianQueryFormTypeEnum.RECUPERATION_LEAVE
                || formType == JijianQueryFormTypeEnum.PERSONAL_LEAVE
                || formType == JijianQueryFormTypeEnum.BUSINESS_TRIP
                || formType == JijianQueryFormTypeEnum.COMPENSATORY_LEAVE;
    }

    private JijianQueryPageRespVO propertyPage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<PropertyDO> page = propertyMapper.selectPageForQuery(req, startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PropertyDO item : safeList(page.getList())) {
            rows.add(row(
                    "propertyAddress", item.getPropertyAddress(),
                    "propertyName", item.getPropertyName(),
                    "ownershipInfo", item.getOwnershipInfo(),
                    "buildingTime", item.getBuildingTime(),
                    "area", item.getArea(),
                    "leaseStatus", item.getLeaseStatus(),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), propertySummary(propertyMapper.selectListForSummary(startTime)), columns(
                col("propertyAddress", "\u623f\u4ea7\u5730\u5740", "text"),
                col("propertyName", "\u623f\u4ea7\u540d\u79f0", "text"),
                col("ownershipInfo", "\u4ea7\u6743\u4fe1\u606f", "text"),
                col("buildingTime", "\u5efa\u7b51\u65f6\u95f4", "date"),
                col("area", "\u9762\u79ef", "number"),
                col("leaseStatus", "\u79df\u8d41\u60c5\u51b5", "text"),
                col("remark", "\u5907\u6ce8", "text")));
    }

    private JijianQueryPageRespVO lesseePage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<LesseeDO> page = lesseeMapper.selectPageForQuery(req, startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LesseeDO item : safeList(page.getList())) {
            rows.add(row(
                    "entityType", item.getEntityType(),
                    "contactName", item.getContactName(),
                    "phonePresent", present(item.getPhone()),
                    "idCardPresent", present(item.getIdCard()),
                    "businessLicensePresent", present(item.getBusinessLicense()),
                    "internalStaff", Boolean.TRUE.equals(item.getIsInternalStaff()),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), lesseeSummary(lesseeMapper.selectListForSummary(startTime)), columns(
                col("entityType", "\u4e2a\u4eba/\u7ec4\u7ec7", "text"),
                col("contactName", "\u8054\u7cfb\u4eba", "text"),
                col("phonePresent", "\u624b\u673a\u53f7\u662f\u5426\u586b\u5199", "text"),
                col("idCardPresent", "\u8eab\u4efd\u8bc1\u662f\u5426\u586b\u5199", "text"),
                col("businessLicensePresent", "\u8425\u4e1a\u6267\u7167\u662f\u5426\u586b\u5199", "text"),
                col("internalStaff", "\u662f\u5426\u5355\u4f4d\u5185\u90e8\u4eba\u5458", "text"),
                col("remark", "\u5907\u6ce8", "text")));
    }

    private JijianQueryPageRespVO leaseContractPage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<LeaseContractDO> page = leaseContractMapper.selectPageForQuery(req, startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaseContractDO item : safeList(page.getList())) {
            rows.add(row(
                    "propertyId", item.getPropertyId(),
                    "lesseeId", item.getLesseeId(),
                    "contractStartTime", item.getContractStartTime(),
                    "contractEndTime", item.getContractEndTime(),
                    "amount", item.getAmount(),
                    "paymentStatus", item.getPaymentStatus(),
                    "waterElectricityMgmt", item.getWaterElectricityMgmt(),
                    "contractSummary", item.getContractSummary(),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), leaseContractSummary(leaseContractMapper.selectListForSummary(startTime)), columns(
                col("propertyId", "\u623f\u4ea7", "text"),
                col("lesseeId", "\u79df\u8d41\u4eba\u5458", "text"),
                col("contractStartTime", "\u5408\u540c\u5f00\u59cb\u65f6\u95f4", "date"),
                col("contractEndTime", "\u5408\u540c\u7ed3\u675f\u65f6\u95f4", "date"),
                col("amount", "\u91d1\u989d", "number"),
                col("paymentStatus", "\u652f\u4ed8\u60c5\u51b5", "text"),
                col("waterElectricityMgmt", "\u6c34\u7535\u8d39\u7ba1\u7406", "text"),
                col("contractSummary", "\u5408\u540c\u5185\u5bb9\u6458\u8981", "text"),
                col("remark", "\u5907\u6ce8", "text")));
    }

    private JijianQueryPageRespVO attendanceDailyPage(JijianQueryPageReqVO req, LocalDate startDate) {
        PageResult<AttendanceDailyDO> page = attendanceDailyMapper.selectPageForQuery(toAttendanceReq(req), startDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AttendanceDailyDO item : safeList(page.getList())) {
            rows.add(row(
                    "department", item.getDepartment(),
                    "employeeName", item.getEmployeeName(),
                    "employeeNo", item.getEmployeeNo(),
                    "attendanceDate", item.getAttendanceDate(),
                    "checkinTime", item.getCheckinTime(),
                    "checkinResult", item.getCheckinResult(),
                    "checkoutTime", item.getCheckoutTime(),
                    "checkoutResult", item.getCheckoutResult()));
        }
        return resp(rows, page.getTotal(), attendanceDailySummary(attendanceDailyMapper.selectListForSummary(req.getDepartment(), startDate)), columns(
                col("department", "\u90e8\u95e8", "text"),
                col("employeeName", "\u59d3\u540d", "text"),
                col("employeeNo", "\u5458\u5de5\u7f16\u53f7", "text"),
                col("attendanceDate", "\u8003\u52e4\u65e5\u671f", "date"),
                col("checkinTime", "\u7b7e\u5230\u65f6\u95f4", "date"),
                col("checkinResult", "\u7b7e\u5230\u7ed3\u679c", "text"),
                col("checkoutTime", "\u7b7e\u9000\u65f6\u95f4", "date"),
                col("checkoutResult", "\u7b7e\u9000\u7ed3\u679c", "text")));
    }

    private JijianQueryPageRespVO recuperationLeavePage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<LeaveHealthDO> page = leaveHealthMapper.selectPageForQuery(req, req.getDepartment(), startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeaveHealthDO item : safeList(page.getList())) {
            rows.add(row(
                    "department", item.getDepartment(),
                    "applicantName", item.getApplicantName(),
                    "employeeNo", item.getEmployeeNo(),
                    "leaveLocation", item.getLeaveLocation(),
                    "startTime", item.getStartTime(),
                    "endTime", item.getEndTime(),
                    "leaveDays", item.getLeaveDays(),
                    "workYears", item.getWorkYears(),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), leaveHealthSummary(leaveHealthMapper.selectListForQuery(req.getDepartment(), startTime)), commonLeaveColumns("leaveLocation"));
    }

    private JijianQueryPageRespVO personalLeavePage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<LeavePersonalDO> page = leavePersonalMapper.selectPageForQuery(req, req.getDepartment(), startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LeavePersonalDO item : safeList(page.getList())) {
            rows.add(row(
                    "department", item.getDepartment(),
                    "applicantName", item.getApplicantName(),
                    "employeeNo", item.getEmployeeNo(),
                    "leaveType", item.getLeaveType(),
                    "leaveReason", item.getLeaveReason(),
                    "startTime", item.getStartTime(),
                    "endTime", item.getEndTime(),
                    "leaveDays", item.getLeaveDays(),
                    "outside", Boolean.TRUE.equals(item.getIsOutside()),
                    "outsideLocation", item.getOutsideLocation(),
                    "leaveStatus", item.getLeaveStatus(),
                    "leaveMonth", item.getLeaveMonth(),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), leavePersonalSummary(leavePersonalMapper.selectListForQuery(req.getDepartment(), startTime)), leaveTripColumns());
    }

    private JijianQueryPageRespVO businessTripPage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<BusinessTripDO> page = businessTripMapper.selectPageForQuery(req, req.getDepartment(), startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BusinessTripDO item : safeList(page.getList())) {
            rows.add(row(
                    "department", item.getDepartment(),
                    "applicantName", item.getApplicantName(),
                    "employeeNo", item.getEmployeeNo(),
                    "tripReason", item.getTripReason(),
                    "departurePlace", item.getDeparturePlace(),
                    "destination", item.getDestination(),
                    "startTime", businessTripStart(item),
                    "endTime", businessTripEnd(item),
                    "tripDays", item.getTripDays(),
                    "tripPersonnel", item.getTripPersonnel(),
                    "tripPeopleCount", item.getTripPeopleCount(),
                    "outside", item.getIsOutside(),
                    "outsideLocation", item.getOutsideLocation(),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), businessTripSummary(businessTripMapper.selectListForQuery(req.getDepartment(), startTime)), businessTripColumns());
    }

    private JijianQueryPageRespVO compensatoryLeavePage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<CompensatoryLeaveDO> page = compensatoryLeaveMapper.selectPageForQuery(req, req.getDepartment(), startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CompensatoryLeaveDO item : safeList(page.getList())) {
            rows.add(row(
                    "department", item.getDepartment(),
                    "applicantName", item.getApplicantName(),
                    "employeeNo", item.getEmployeeNo(),
                    "overtimeStartTime", item.getOvertimeStartTime(),
                    "overtimeEndTime", item.getOvertimeEndTime(),
                    "compensatoryStartTime", item.getCompensatoryStartTime(),
                    "compensatoryEndTime", item.getCompensatoryEndTime(),
                    "compensatoryDuration", item.getCompensatoryDuration(),
                    "outside", Boolean.TRUE.equals(item.getIsOutside()),
                    "outsideLocation", item.getOutsideLocation(),
                    "remark", item.getRemark()));
        }
        return resp(rows, page.getTotal(), compensatoryLeaveSummary(compensatoryLeaveMapper.selectListForQuery(req.getDepartment(), startTime)), columns(
                col("department", "\u90e8\u95e8", "text"),
                col("applicantName", "\u7533\u8bf7\u4eba", "text"),
                col("employeeNo", "\u5458\u5de5\u7f16\u53f7", "text"),
                col("overtimeStartTime", "\u52a0\u73ed\u5f00\u59cb\u65f6\u95f4", "date"),
                col("overtimeEndTime", "\u52a0\u73ed\u7ed3\u675f\u65f6\u95f4", "date"),
                col("compensatoryStartTime", "\u8c03\u4f11\u5f00\u59cb\u65f6\u95f4", "date"),
                col("compensatoryEndTime", "\u8c03\u4f11\u7ed3\u675f\u65f6\u95f4", "date"),
                col("compensatoryDuration", "\u8c03\u4f11\u65f6\u957f", "text"),
                col("outside", "\u662f\u5426\u51fa\u4e49", "text"),
                col("outsideLocation", "\u51fa\u4e49\u5730\u70b9", "text"),
                col("remark", "\u5907\u6ce8", "text")));
    }

    private JijianQueryPageRespVO canteenSupplierPage(JijianQueryPageReqVO req, LocalDateTime startTime) {
        PageResult<CanteenSupplierDO> page = canteenSupplierMapper.selectPageForQuery(req, startTime);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CanteenSupplierDO item : safeList(page.getList())) {
            rows.add(row(
                    "itemName", item.getItemName(),
                    "specLevel", item.getSpecLevel(),
                    "unit", item.getUnit(),
                    "price", item.getPrice(),
                    "purchasePoint", item.getPurchasePoint()));
        }
        return resp(rows, page.getTotal(), canteenSupplierSummary(canteenSupplierMapper.selectListForQuery(startTime)), columns(
                col("itemName", "\u9879\u76ee\u540d\u79f0", "text"),
                col("specLevel", "\u89c4\u683c/\u7b49\u7ea7", "text"),
                col("unit", "\u5355\u4f4d", "text"),
                col("price", "\u4ef7\u683c", "number"),
                col("purchasePoint", "\u91c7\u4ef7\u70b9", "text")));
    }

    private Map<String, Object> propertySummary(List<PropertyDO> all) {
        Map<String, Object> m = base("PROPERTY_INFO", all.size());
        m.put("propertyTotalCount", all.size());
        m.put("totalArea", sum(all, PropertyDO::getArea));
        m.put("byLeaseStatus", group(all, i -> norm(i.getLeaseStatus()), "leaseStatus"));
        m.put("byOwnershipInfo", group(all, i -> norm(i.getOwnershipInfo()), "ownershipInfo"));
        m.put("byBuildingYear", group(all, i -> year(i.getBuildingTime()), "year"));
        return m;
    }

    private Map<String, Object> lesseeSummary(List<LesseeDO> all) {
        Map<String, Object> m = base("LESSEE", all.size());
        m.put("tenantTotalCount", all.size());
        m.put("byTenantType", group(all, i -> norm(i.getEntityType()), "tenantType"));
        m.put("internalTenantCount", all.stream().filter(i -> Boolean.TRUE.equals(i.getIsInternalStaff())).count());
        m.put("externalTenantCount", all.stream().filter(i -> !Boolean.TRUE.equals(i.getIsInternalStaff())).count());
        m.put("contactNamePresentCount", all.stream().filter(i -> present(i.getContactName())).count());
        m.put("phonePresentCount", all.stream().filter(i -> present(i.getPhone())).count());
        m.put("idCardPresentCount", all.stream().filter(i -> present(i.getIdCard())).count());
        m.put("businessLicensePresentCount", all.stream().filter(i -> present(i.getBusinessLicense())).count());
        return m;
    }

    private Map<String, Object> leaseContractSummary(List<LeaseContractDO> all) {
        Map<String, Object> m = base("LEASE_CONTRACT", all.size());
        m.put("contractTotalCount", all.size());
        m.put("totalContractAmount", sum(all, LeaseContractDO::getAmount));
        m.put("byPaymentStatus", group(all, i -> norm(i.getPaymentStatus()), "paymentStatus"));
        m.put("byUtilityManagement", group(all, i -> norm(i.getWaterElectricityMgmt()), "utilityManagement"));
        m.put("byContractStartYear", group(all, i -> year(i.getContractStartTime()), "year"));
        m.put("byContractEndYear", group(all, i -> year(i.getContractEndTime()), "year"));
        m.put("expiredContractCount", all.stream().filter(i -> i.getContractEndTime() != null && i.getContractEndTime().isBefore(LocalDateTime.now())).count());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusDays(EXPIRING_SOON_DAYS);
        m.put("expiringSoonContractCount", all.stream()
                .filter(i -> i.getContractEndTime() != null)
                .filter(i -> !i.getContractEndTime().isBefore(now) && !i.getContractEndTime().isAfter(soon))
                .count());
        List<BigDecimal> amounts = all.stream().map(LeaseContractDO::getAmount).filter(Objects::nonNull).collect(Collectors.toList());
        m.put("maxAmount", amounts.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
        m.put("minAmount", amounts.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
        m.put("avgAmount", avg(amounts));
        return m;
    }

    private Map<String, Object> attendanceDailySummary(List<AttendanceDailyDO> all) {
        Map<String, Object> m = base("ATTENDANCE_DAILY", all.size());
        m.put("departmentCount", all.stream().map(AttendanceDailyDO::getDepartment).filter(this::present).collect(Collectors.toSet()).size());
        m.put("byDepartment", group(all, i -> norm(i.getDepartment()), "department"));
        m.put("byAttendanceStatus", group(all, i -> norm(i.getCheckinResult()), "status"));
        m.put("byCheckoutStatus", group(all, i -> norm(i.getCheckoutResult()), "status"));
        m.put("abnormalCount", all.stream().filter(i -> isAbnormal(i.getCheckinResult()) || isAbnormal(i.getCheckoutResult())).count());
        return m;
    }

    private Map<String, Object> leaveHealthSummary(List<LeaveHealthDO> all) {
        Map<String, Object> m = base("RECUPERATION_LEAVE", all.size());
        m.put("totalLeaveDays", sum(all, LeaveHealthDO::getLeaveDays));
        m.put("byDepartment", group(all, i -> norm(i.getDepartment()), "department"));
        m.put("byLeaveLocation", group(all, i -> norm(i.getLeaveLocation()), "leaveLocation"));
        m.put("byWorkYears", group(all, i -> norm(i.getWorkYears()), "workYears"));
        return m;
    }

    private Map<String, Object> leavePersonalSummary(List<LeavePersonalDO> all) {
        Map<String, Object> m = base("PERSONAL_LEAVE", all.size());
        m.put("totalLeaveDays", sum(all, LeavePersonalDO::getLeaveDays));
        m.put("byDepartment", group(all, i -> norm(i.getDepartment()), "department"));
        m.put("byLeaveType", group(all, i -> norm(i.getLeaveType()), "leaveType"));
        m.put("byLeaveStatus", group(all, i -> norm(i.getLeaveStatus()), "leaveStatus"));
        m.put("outsideCount", all.stream().filter(i -> Boolean.TRUE.equals(i.getIsOutside())).count());
        return m;
    }

    private Map<String, Object> businessTripSummary(List<BusinessTripDO> all) {
        Map<String, Object> m = base("BUSINESS_TRIP", all.size());
        m.put("totalTripDays", sum(all, BusinessTripDO::getTripDays));
        m.put("totalTripPeople", all.stream()
                .map(BusinessTripDO::getTripPeopleCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        m.put("byDepartment", group(all, i -> norm(i.getDepartment()), "department"));
        m.put("byDestination", group(all, i -> norm(i.getDestination()), "destination"));
        m.put("outsideCount", all.stream().filter(i -> isBusinessTripOutside(i.getIsOutside())).count());
        return m;
    }

    private boolean isBusinessTripOutside(String value) {
        return value != null && (value.contains("出义") || value.contains("外出") || value.contains("是"));
    }

    private LocalDateTime businessTripStart(BusinessTripDO item) {
        return item.getStartDate() != null ? item.getStartDate()
                : item.getStartTime() != null ? item.getStartTime() : item.getCreateTime();
    }

    private LocalDateTime businessTripEnd(BusinessTripDO item) {
        return item.getEndDate() != null ? item.getEndDate() : item.getEndTime();
    }

    private Map<String, Object> compensatoryLeaveSummary(List<CompensatoryLeaveDO> all) {
        Map<String, Object> m = base("COMPENSATORY_LEAVE", all.size());
        m.put("byDepartment", group(all, i -> norm(i.getDepartment()), "department"));
        m.put("byDuration", group(all, i -> norm(i.getCompensatoryDuration()), "duration"));
        m.put("outsideCount", all.stream().filter(i -> Boolean.TRUE.equals(i.getIsOutside())).count());
        return m;
    }

    private Map<String, Object> canteenSupplierSummary(List<CanteenSupplierDO> all) {
        Map<String, Object> m = base("CANTEEN_SUPPLIER", all.size());
        m.put("totalProjectCount", all.stream().map(i -> norm(i.getItemName())).collect(Collectors.toCollection(LinkedHashSet::new)).size());
        m.put("byProjectName", group(all, i -> norm(i.getItemName()), "projectName"));
        m.put("byPurchasePoint", group(all, i -> norm(i.getPurchasePoint()), "purchasePoint"));
        List<Map<String, Object>> variance = priceVariance(all);
        m.put("priceVarianceCount", variance.stream().filter(i -> asBigDecimal(i.get("priceDiff")).compareTo(BigDecimal.ZERO) > 0).count());
        m.put("priceVarianceItems", variance);
        return m;
    }

    private List<Map<String, Object>> priceVariance(List<CanteenSupplierDO> all) {
        Map<String, List<CanteenSupplierDO>> grouped = all.stream()
                .filter(i -> i.getPrice() != null)
                .collect(Collectors.groupingBy(i -> norm(i.getItemName()) + "|" + norm(i.getSpecLevel()) + "|" + norm(i.getUnit()),
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<CanteenSupplierDO> items : grouped.values()) {
            BigDecimal min = items.stream().map(CanteenSupplierDO::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal max = items.stream().map(CanteenSupplierDO::getPrice).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal avg = avg(items.stream().map(CanteenSupplierDO::getPrice).collect(Collectors.toList()));
            BigDecimal diff = max.subtract(min);
            CanteenSupplierDO first = items.get(0);
            result.add(row(
                    "projectName", norm(first.getItemName()),
                    "specLevel", norm(first.getSpecLevel()),
                    "unit", norm(first.getUnit()),
                    "recordCount", items.size(),
                    "minPrice", min,
                    "maxPrice", max,
                    "avgPrice", avg,
                    "priceDiff", diff,
                    "diffRate", min.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : diff.divide(min, 4, RoundingMode.HALF_UP),
                    "purchasePoints", items.stream().map(i -> norm(i.getPurchasePoint())).collect(Collectors.toCollection(LinkedHashSet::new))));
        }
        result.sort((a, b) -> asBigDecimal(b.get("priceDiff")).compareTo(asBigDecimal(a.get("priceDiff"))));
        return result;
    }

    private cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO toAttendanceReq(JijianQueryPageReqVO req) {
        cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO r =
                new cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO();
        r.setPageNo(req.getPageNo());
        r.setPageSize(req.getPageSize());
        r.setDepartment(req.getDepartment());
        r.setTimeRange(req.getTimeRange());
        return r;
    }

    private List<JijianQueryPageRespVO.ColumnDef> commonLeaveColumns(String locationKey) {
        return columns(
                col("department", "\u90e8\u95e8", "text"),
                col("applicantName", "\u7533\u8bf7\u4eba", "text"),
                col("employeeNo", "\u5458\u5de5\u7f16\u53f7", "text"),
                col(locationKey, "\u5730\u70b9", "text"),
                col("startTime", "\u5f00\u59cb\u65f6\u95f4", "date"),
                col("endTime", "\u7ed3\u675f\u65f6\u95f4", "date"),
                col("leaveDays", "\u5929\u6570", "number"),
                col("workYears", "\u5de5\u4f5c\u5e74\u9650", "text"),
                col("remark", "\u5907\u6ce8", "text"));
    }

    private List<JijianQueryPageRespVO.ColumnDef> leaveTripColumns() {
        return columns(
                col("department", "\u90e8\u95e8", "text"),
                col("applicantName", "\u7533\u8bf7\u4eba", "text"),
                col("employeeNo", "\u5458\u5de5\u7f16\u53f7", "text"),
                col("leaveType", "\u7c7b\u578b", "text"),
                col("leaveReason", "\u4e8b\u7531", "text"),
                col("startTime", "\u5f00\u59cb\u65f6\u95f4", "date"),
                col("endTime", "\u7ed3\u675f\u65f6\u95f4", "date"),
                col("leaveDays", "\u5929\u6570", "number"),
                col("outside", "\u662f\u5426\u51fa\u4e49", "text"),
                col("outsideLocation", "\u51fa\u4e49\u5730\u70b9", "text"),
                col("leaveStatus", "\u72b6\u6001", "text"),
                col("leaveMonth", "\u6240\u5c5e\u6708\u4efd", "text"),
                col("remark", "\u5907\u6ce8", "text"));
    }

    private List<JijianQueryPageRespVO.ColumnDef> businessTripColumns() {
        return columns(
                col("department", "\u90e8\u95e8", "text"),
                col("applicantName", "\u7533\u8bf7\u4eba", "text"),
                col("employeeNo", "\u5458\u5de5\u7f16\u53f7", "text"),
                col("tripReason", "\u51fa\u5dee\u4e8b\u7531", "text"),
                col("departurePlace", "\u51fa\u53d1\u5730", "text"),
                col("destination", "\u76ee\u7684\u5730", "text"),
                col("startTime", "\u51fa\u5dee\u5f00\u59cb\u65f6\u95f4", "date"),
                col("endTime", "\u51fa\u5dee\u7ed3\u675f\u65f6\u95f4", "date"),
                col("tripDays", "\u51fa\u5dee\u5929\u6570", "number"),
                col("tripPersonnel", "\u51fa\u5dee\u4eba\u5458", "text"),
                col("tripPeopleCount", "\u51fa\u5dee\u4eba\u6570", "number"),
                col("outside", "\u662f\u5426\u51fa\u4e49", "text"),
                col("outsideLocation", "\u51fa\u4e49\u5730\u70b9", "text"),
                col("remark", "\u5907\u6ce8", "text"));
    }

    private JijianQueryPageRespVO resp(List<Map<String, Object>> rows, Long total, Object summary,
                                       List<JijianQueryPageRespVO.ColumnDef> columns) {
        JijianQueryPageRespVO resp = new JijianQueryPageRespVO();
        resp.setPageResult(new PageResult<>(rows == null ? Collections.emptyList() : rows, total == null ? 0L : total));
        resp.setSummary(summary == null ? Collections.emptyMap() : summary);
        resp.setColumns(columns == null ? Collections.emptyList() : columns);
        return resp;
    }

    private JijianQueryPageRespVO emptyPage() {
        return resp(Collections.emptyList(), 0L, Collections.emptyMap(), Collections.emptyList());
    }

    private Map<String, Object> base(String formType, int totalCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formType", formType);
        m.put("totalCount", totalCount);
        return m;
    }

    private <T> List<Map<String, Object>> group(List<T> all, Function<T, String> classifier, String keyName) {
        Map<String, Long> grouped = all.stream().collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((key, count) -> result.add(row(keyName, key, "count", count)));
        return result;
    }

    private <T> BigDecimal sum(List<T> all, Function<T, BigDecimal> mapper) {
        return all.stream().map(mapper).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal avg(List<BigDecimal> amounts) {
        List<BigDecimal> safe = amounts.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (safe.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return safe.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(safe.size()), 2, RoundingMode.HALF_UP);
    }

    private boolean isAbnormal(String value) {
        return present(value) && (value.contains("\u5f02\u5e38") || value.contains("\u7f3a") || value.contains("\u8fdf\u5230") || value.contains("\u65e9\u9000"));
    }

    private String year(LocalDateTime time) {
        return time == null ? UNKNOWN : String.valueOf(time.getYear());
    }

    private String norm(String value) {
        return present(value) ? value.trim() : UNKNOWN;
    }

    private boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal asBigDecimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.ZERO;
    }

    private LocalDate resolveStartDate(String timeRange) {
        JijianQueryTimeRangeEnum e = JijianQueryTimeRangeEnum.of(timeRange);
        return (e == null ? JijianQueryTimeRangeEnum.ONE_MONTH : e).startDate();
    }

    private LocalDateTime resolveStartTime(String timeRange) {
        return resolveStartDate(timeRange).atStartOfDay();
    }

    @SafeVarargs
    private final List<JijianQueryPageRespVO.ColumnDef> columns(JijianQueryPageRespVO.ColumnDef... columns) {
        return Arrays.asList(columns);
    }

    private JijianQueryPageRespVO.ColumnDef col(String key, String label, String type) {
        return JijianQueryPageRespVO.ColumnDef.of(key, label, type);
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            row.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return row;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
