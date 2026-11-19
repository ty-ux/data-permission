package com.ty.dp.sample;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ty.dp.mybatis.DataPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * Type-level annotation also covers inherited BaseMapper methods such as
 * selectList and selectCount.
 */
@Mapper
@DataPermission(appPoint = "sim")
public interface SimProjectPlusMapper extends BaseMapper<SimProject> {
}
