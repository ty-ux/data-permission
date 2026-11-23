package com.ty.dp.sample;

import com.ty.dp.mybatis.DataPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SimProjectMapper {

    @DataPermission(appPoint = "sim")
    @Select("select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project order by id")
    List<SimProject> selectAll();

    @DataPermission(appPoint = "sim")
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from sim_project p order by p.id")
    List<SimProject> selectAliased();

    @DataPermission(appPoint = "sim", tableAlias = {"sim_project:p"})
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from sim_project p order by p.id")
    List<SimProject> selectWithAnnotationAlias();

    @DataPermission(appPoint = "sim")
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from sim_project p join sim_dept d on d.id = p.dept_id order by p.id")
    List<SimProject> selectWithJoin();

    @DataPermission(appPoint = "sim")
    @Select("select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project where status = 'A' or status = 'B' order by id")
    List<SimProject> selectWithOrCondition();

    @DataPermission(appPoint = "sim")
    @Select("select id from sim_project where id <= 103 "
            + "union all select id from sim_project where id >= 104 order by id")
    List<Long> selectUnionIds();

    @DataPermission(appPoint = "sim")
    @Select("select * from (select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project) t order by id")
    List<SimProject> selectFromSubquery();

    @DataPermission(appPoint = "sim")
    @Select("select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project where dept_id = #{deptId} order by id")
    List<SimProject> selectByDeptParam(@Param("deptId") Long deptId);

    @DataPermission(appPoint = "sim")
    @Select({"<script>select id, name, dept_id, owner_no, create_by, status, amount from sim_project where id in",
            "<foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach> order by id</script>"})
    List<SimProject> selectIds(@Param("ids") List<Long> ids);

    @DataPermission(appPoint = "sim")
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from PUBLIC.sim_project p order by p.id")
    List<SimProject> selectSchemaQualified();
}
