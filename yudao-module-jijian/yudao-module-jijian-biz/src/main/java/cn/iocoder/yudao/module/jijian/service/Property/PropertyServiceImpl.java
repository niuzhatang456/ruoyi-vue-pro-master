package cn.iocoder.yudao.module.jijian.service.Property;

import cn.hutool.core.collection.CollUtil;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import cn.iocoder.yudao.module.jijian.controller.admin.Property.vo.*;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;

import cn.iocoder.yudao.module.jijian.dal.mysql.Property.PropertyMapper;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.diffList;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

/**
 * 房产情况 Service 实现类
 *
 * @author admin
 */
@Service
@Validated
public class PropertyServiceImpl implements PropertyService {

    @Resource
    private PropertyMapper propertyMapper;

    @Override
    public Long createProperty(PropertySaveReqVO createReqVO) {
        // 插入
        PropertyDO property = BeanUtils.toBean(createReqVO, PropertyDO.class);
        propertyMapper.insert(property);

        // 返回
        return property.getId();
    }

    @Override
    public void updateProperty(PropertySaveReqVO updateReqVO) {
        // 校验存在
        validatePropertyExists(updateReqVO.getId());
        // 更新
        PropertyDO updateObj = BeanUtils.toBean(updateReqVO, PropertyDO.class);
        propertyMapper.updateById(updateObj);
    }

    @Override
    public void deleteProperty(Long id) {
        // 校验存在
        validatePropertyExists(id);
        // 删除
        propertyMapper.deleteById(id);
    }

    @Override
        public void deletePropertyListByIds(List<Long> ids) {
        // 删除
        propertyMapper.deleteByIds(ids);
        }


    private void validatePropertyExists(Long id) {
        if (propertyMapper.selectById(id) == null) {
            throw exception(PROPERTY_NOT_EXISTS);
        }
    }

    @Override
    public PropertyDO getProperty(Long id) {
        return propertyMapper.selectById(id);
    }

    @Override
    public PageResult<PropertyDO> getPropertyPage(PropertyPageReqVO pageReqVO) {
        return propertyMapper.selectPage(pageReqVO);
    }

}