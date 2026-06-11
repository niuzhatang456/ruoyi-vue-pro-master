package cn.iocoder.yudao.module.jijian.service.disposalrecord;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordCreateReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.disposalrecord.DisposalRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.queryhistory.QueryHistoryDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.disposalrecord.DisposalRecordMapper;
import cn.iocoder.yudao.module.jijian.service.queryhistory.QueryHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.DISPOSAL_RECORD_NOT_EXISTS;

@Service
@Validated
public class DisposalRecordServiceImpl implements DisposalRecordService {

    @Resource
    private DisposalRecordMapper disposalRecordMapper;
    @Resource
    private QueryHistoryService queryHistoryService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public Long createDisposalRecord(DisposalRecordCreateReqVO createReqVO) {
        QueryHistoryDO history = createReqVO.getQueryHistoryId() == null
                ? null : queryHistoryService.getQueryHistory(createReqVO.getQueryHistoryId());
        DisposalRecordDO record = new DisposalRecordDO();
        record.setQueryHistoryId(history == null ? null : history.getId());
        record.setQueryQuestion(history == null ? createReqVO.getQueryQuestion() : history.getQuestion());
        record.setQueryAnswer(history == null ? createReqVO.getQueryAnswer() : history.getAnswer());
        record.setQueryResultJson(history == null ? createReqVO.getQueryResultJson() : history.getQueryResultJson());
        record.setDisposalOpinion(createReqVO.getDisposalOpinion().trim());
        record.setDisposalUserId(SecurityFrameworkUtils.getLoginUserId());
        record.setDisposalUserName(SecurityFrameworkUtils.getLoginUserNickname());
        record.setDisposalTime(LocalDateTime.now());
        record.setSourceModule(StrUtil.blankToDefault(createReqVO.getSourceModule(), "smart_query"));
        record.setRemark(createReqVO.getRemark());
        disposalRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public PageResult<DisposalRecordDO> getDisposalRecordPage(DisposalRecordPageReqVO pageReqVO) {
        return disposalRecordMapper.selectPage(pageReqVO,
                isJijianAdmin() ? null : SecurityFrameworkUtils.getLoginUserId());
    }

    @Override
    public DisposalRecordDO getDisposalRecord(Long id) {
        DisposalRecordDO record = isJijianAdmin() ? disposalRecordMapper.selectById(id)
                : disposalRecordMapper.selectByIdAndUserId(id, SecurityFrameworkUtils.getLoginUserId());
        if (record == null) {
            throw exception(DISPOSAL_RECORD_NOT_EXISTS);
        }
        return record;
    }

    @Override
    public void deleteDisposalRecord(Long id) {
        DisposalRecordDO record = getDisposalRecord(id);
        disposalRecordMapper.deleteById(record.getId());
    }

    private boolean isJijianAdmin() {
        return securityFrameworkService.hasAnyRoles("jijian_admin", "super_admin");
    }
}
