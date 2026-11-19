package com.ty.dp.core.model;

/**
 * One condition inside a complex rule.
 *
 * <p>Items with the same {@code groupNo} are combined with OR at the top level.
 * Inside a group, items with the same {@code subGroupNo} are combined with the
 * item level {@link LogicOperator}; subgroups themselves are combined with AND.</p>
 */
public class DataPermissionRuleItem {

    private String id;
    private String ruleId;
    private Integer sortOrder = 0;
    private Integer groupNo = 1;
    private Integer subGroupNo = 1;
    private LogicOperator logicOperator = LogicOperator.AND;
    private String leftType = "FIELD";
    private String leftExpr;
    private String operator;
    private RightValueType rightType = RightValueType.CONST;
    private String rightExpr;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public Integer getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(Integer groupNo) {
        this.groupNo = groupNo == null ? 1 : groupNo;
    }

    public Integer getSubGroupNo() {
        return subGroupNo;
    }

    public void setSubGroupNo(Integer subGroupNo) {
        this.subGroupNo = subGroupNo == null ? 1 : subGroupNo;
    }

    public LogicOperator getLogicOperator() {
        return logicOperator;
    }

    public void setLogicOperator(LogicOperator logicOperator) {
        this.logicOperator = logicOperator == null ? LogicOperator.AND : logicOperator;
    }

    public String getLeftType() {
        return leftType;
    }

    public void setLeftType(String leftType) {
        this.leftType = leftType;
    }

    public String getLeftExpr() {
        return leftExpr;
    }

    public void setLeftExpr(String leftExpr) {
        this.leftExpr = leftExpr;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public RightValueType getRightType() {
        return rightType;
    }

    public void setRightType(RightValueType rightType) {
        this.rightType = rightType == null ? RightValueType.CONST : rightType;
    }

    public String getRightExpr() {
        return rightExpr;
    }

    public void setRightExpr(String rightExpr) {
        this.rightExpr = rightExpr;
    }
}
