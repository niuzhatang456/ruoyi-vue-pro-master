package cn.iocoder.yudao.module.jijian.service.Property;

import java.util.*;
import javax.validation.*;
import cn.iocoder.yudao.module.jijian.controller.admin.Property.vo.*;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;

/**
 * 房产情况 Service 接口
 *
 * @author admin
 */
public interface PropertyService {

    /**
     * 创建房产情况
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createProperty(@Valid PropertySaveReqVO createReqVO);

    /**
     * 更新房产情况
     *
     * @param updateReqVO 更新信息
     */
    void updateProperty(@Valid PropertySaveReqVO updateReqVO);

    /**
     * 删除房产情况
     *
     * @param id 编号
     */
    void deleteProperty(Long id);

    /**
    * 批量删除房产情况
    *
    * @param ids 编号
    */
    void deletePropertyListByIds(List<Long> ids);

    /**
     * 获得房产情况
     *
     * @param id 编号
     * @return 房产情况
     */
    PropertyDO getProperty(Long id);

    /**
     * 获得房产情况分页
     *
     * @param pageReqVO 分页查询
     * @return 房产情况分页
     */
    PageResult<PropertyDO> getPropertyPage(PropertyPageReqVO pageReqVO);

}