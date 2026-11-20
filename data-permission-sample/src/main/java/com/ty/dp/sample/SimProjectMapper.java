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
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from PUBLIC.sim_project p order by p.id")
    List<SimProject> selectSchemaQualified();

    @DataPermission(appPoint = "sim")
    @Select("select a.id, a.name, a.dept_id, a.owner_no, a.create_by, a.status, a.amount "
            + "from sim_project a cross join sim_project b where b.id = 101 order by a.id")
    List<SimProject> selectSelfJoin();

    @DataPermission(appPoint = "sim")
    @Select("select a.id, a.name, a.dept_id, a.owner_no, a.create_by, a.status, a.amount "
            + "from sim_project a cross join sim_project b cross join sim_project c "
            + "where b.id = 101 and c.id = 102 order by a.id")
    List<SimProject> selectSelfJoinThree();

    @DataPermission(appPoint = "sim")
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from (select id from sim_project where id = 101) x "
            + "cross join sim_project p order by p.id")
    List<SimProject> selectFromSubqueryWithOuterTable();

    @DataPermission(appPoint = "sim")
    @Select("select (select count(*) from sim_project) as total "
            + "from sim_project p where p.id = 101")
    Long selectScalarSubqueryCount();

    @DataPermission(appPoint = "sim")
    @Select("select p.id, p.name, p.dept_id, p.owner_no, p.create_by, p.status, p.amount "
            + "from sim_project p where exists "
            + "(select 1 from sim_project q where q.id = 106) order by p.id")
    List<SimProject> selectExistsSubquery();

    @DataPermission(appPoint = "sim")
    @Select("with c as (select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project) "
            + "select id, name, dept_id, owner_no, create_by, status, amount from c order by id")
    List<SimProject> selectCte();

    @DataPermission(appPoint = "sim")
    @Select("select * from (with c as (select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project) "
            + "select id, name, dept_id, owner_no, create_by, status, amount from c) q order by id")
    List<SimProject> selectNestedCte();

    @DataPermission(appPoint = "sim")
    @Select("with c as (select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project), d as (select * from c) "
            + "select id, name, dept_id, owner_no, create_by, status, amount from d order by id")
    List<SimProject> selectEarlierCteFromLaterCte();

    @DataPermission(appPoint = "sim")
    @Select("with c as (with d as (select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project) select * from d) "
            + "select id, name, dept_id, owner_no, create_by, status, amount from c order by id")
    List<SimProject> selectCteNestedInsideCte();

    @DataPermission(appPoint = "sim")
    @Select("select (select count(*) from "
            + "(with c as (select id from sim_project) select id from c) x) as total "
            + "from sim_project p where p.id = 101")
    Long selectCteInsideScalarSubquery();

    @DataPermission(appPoint = "sim")
    @Select("with recursive c(id) as ("
            + "select id from sim_project where id = 101 "
            + "union all "
            + "select p.id from sim_project p join c on p.id = c.id + 1 where p.id <= 103"
            + ") select id from c order by id")
    List<Long> selectRecursiveCte();

    @DataPermission(appPoint = "sim")
    @Select("with recursive a as (select 1 as x from sim_project where id = 101), "
            + "b(n) as (select id from sim_project where id = 101 "
            + "union all select n + 1 from b where n < 103) "
            + "select n from b order by n")
    List<Long> selectRecursiveCteNotFirst();

    @DataPermission(appPoint = "sim")
    @Select("select (select count(*) from sim_project) as total "
            + "from (sim_project a cross join sim_project b) "
            + "where a.id = 101 and b.id = 101")
    Long selectSubJoinScalarTotal();

    @DataPermission(appPoint = "sim")
    @Select("select q.id from sim_project p "
            + "join (sim_project q join sim_project r on q.id = r.id) on p.id = 101 "
            + "where q.id in (101, 104) and r.id in (101, 104) order by q.id")
    List<Long> selectSubJoinIds();

    @DataPermission(appPoint = "sim")
    @Select("<script>select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project where id in "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "order by id</script>")
    List<SimProject> selectByIds(@Param("ids") List<Long> ids);

    @DataPermission(appPoint = "sim")
    @Select("<script><bind name='boundDept' value='deptId'/>"
            + "select id, name, dept_id, owner_no, create_by, status, amount "
            + "from sim_project where dept_id = #{boundDept} order by id</script>")
    List<SimProject> selectWithBind(@Param("deptId") Long deptId);
}
