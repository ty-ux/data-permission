package com.ty.dp.sample;

import com.ty.dp.mybatis.DataPermission;
import com.ty.dp.mybatis.DataPermissionIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DemoProjectMapper {

    @DataPermission(appPoint = "demo_project")
    @Select("select id, name, dept_id, owner_no, amount from demo_project order by id")
    List<DemoProject> selectAll();

    @DataPermissionIgnore
    @Select("select count(*) from demo_project")
    long countAll();
}
