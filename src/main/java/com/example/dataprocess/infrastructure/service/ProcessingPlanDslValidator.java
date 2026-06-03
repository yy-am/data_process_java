package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.ActualColumnMapping;
import com.example.dataprocess.domain.model.DslGenerationContext;
import com.example.dataprocess.domain.model.ProcessingPlanColumn;
import com.example.dataprocess.domain.model.ProcessingPlanDsl;
import com.example.dataprocess.domain.model.TargetColumnGenerationContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 加工计划 DSL 校验服务。
 *
 * <p>它是 AI 输出进入 SQL 拼接层前的安全闸门：AI 只能返回目标列表达式片段，
 * 不能返回完整 SQL、表名、写入语句或越权字段引用。</p>
 */
@Service
public class ProcessingPlanDslValidator {

    private static final Pattern DANGEROUS_SQL_KEYWORD_PATTERN = Pattern.compile(
            "(?i)\\b(SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|TRUNCATE|COPY|CREATE|GRANT|REVOKE|JOIN|UNION|GROUP|HAVING|ORDER|LIMIT)\\b"
    );
    private static final Pattern ELASTIC_COLUMN_PATTERN = Pattern.compile("(?i)\\bcol\\d+\\b");
    private static final Pattern SINGLE_QUOTED_LITERAL_PATTERN = Pattern.compile("'([^']|'')*'");

    /**
     * 校验并返回通过安全约束的加工计划 DSL。
     */
    public ProcessingPlanDsl validate(ProcessingPlanDsl plan, DslGenerationContext context) {
        if (plan == null) {
            throw new IllegalStateException("加工计划 DSL 生成服务未返回结果。");
        }
        if (context == null) {
            throw new IllegalStateException("校验加工计划 DSL 前必须提供 DSL 生成上下文。");
        }

        validatePlanHeader(plan, context);

        Map<String, TargetColumnGenerationContext> targetContextMap = buildTargetContextMap(context);
        Map<String, ProcessingPlanColumn> planColumnMap = buildPlanColumnMap(plan);
        validateTargetCoverage(targetContextMap.keySet(), planColumnMap.keySet());

        for (ProcessingPlanColumn column : plan.columns()) {
            TargetColumnGenerationContext targetContext = targetContextMap.get(column.targetColumn());
            validateColumn(column, targetContext);
        }

        return plan;
    }

    /**
     * 校验 DSL 头部字段必须与当前任务上下文完全一致。
     */
    private void validatePlanHeader(ProcessingPlanDsl plan, DslGenerationContext context) {
        requireNonBlank(plan.dslVersion(), "加工计划 DSL 缺少 dslVersion。");
        requireEquals(context.taskId(), plan.taskId(), "加工计划 DSL 的 taskId 与上下文不一致。");
        requireEquals(context.presetTemplateCode(), plan.presetTemplateCode(), "加工计划 DSL 的 presetTemplateCode 与上下文不一致。");
        requireEquals(context.standardTemplateCode(), plan.standardTemplateCode(), "加工计划 DSL 的 standardTemplateCode 与上下文不一致。");
        if (plan.columns() == null || plan.columns().isEmpty()) {
            throw new IllegalStateException("加工计划 DSL 缺少目标列计划。");
        }
    }

    /**
     * 按目标列构建上下文索引，并拒绝重复目标列。
     */
    private Map<String, TargetColumnGenerationContext> buildTargetContextMap(DslGenerationContext context) {
        if (context.targetColumns() == null || context.targetColumns().isEmpty()) {
            throw new IllegalStateException("DSL 生成上下文缺少目标列。");
        }

        Map<String, TargetColumnGenerationContext> targetContextMap = new LinkedHashMap<>();
        for (TargetColumnGenerationContext targetContext : context.targetColumns()) {
            requireNonBlank(targetContext.targetColumn(), "DSL 生成上下文存在空目标列名。");
            if (targetContextMap.putIfAbsent(targetContext.targetColumn(), targetContext) != null) {
                throw new IllegalStateException("DSL 生成上下文存在重复目标列: " + targetContext.targetColumn());
            }
        }
        return targetContextMap;
    }

    /**
     * 按目标列构建 AI 输出索引，并拒绝重复目标列。
     */
    private Map<String, ProcessingPlanColumn> buildPlanColumnMap(ProcessingPlanDsl plan) {
        Map<String, ProcessingPlanColumn> planColumnMap = new LinkedHashMap<>();
        for (ProcessingPlanColumn column : plan.columns()) {
            requireNonBlank(column.targetColumn(), "加工计划 DSL 存在空目标列名。");
            if (planColumnMap.putIfAbsent(column.targetColumn(), column) != null) {
                throw new IllegalStateException("加工计划 DSL 存在重复目标列: " + column.targetColumn());
            }
        }
        return planColumnMap;
    }

    /**
     * 校验 AI 输出目标列集合必须与上下文目标列集合完全一致。
     */
    private void validateTargetCoverage(Set<String> expectedTargets, Set<String> actualTargets) {
        if (!expectedTargets.equals(actualTargets)) {
            throw new IllegalStateException(
                    "加工计划 DSL 目标列集合不一致，期望 " + expectedTargets + "，实际 " + actualTargets
            );
        }
    }

    /**
     * 校验单个目标列的表达式片段和字段引用范围。
     */
    private void validateColumn(ProcessingPlanColumn column, TargetColumnGenerationContext targetContext) {
        requireNonBlank(column.expressionSql(), "目标列缺少 SQL 表达式片段: " + column.targetColumn());
        validateExpressionSafety(column);

        Set<String> allowedElasticColumns = extractElasticColumns(targetContext.actualColumnMappings());
        validateElasticColumnScope(column, allowedElasticColumns);
        validateActualColumnNotUsed(column, targetContext.actualColumnMappings());
    }

    /**
     * 校验表达式只能是 SELECT 列表中的片段，不能夹带完整 SQL 或注释。
     */
    private void validateExpressionSafety(ProcessingPlanColumn column) {
        String expression = column.expressionSql();
        if (expression.contains(";") || expression.contains("--") || expression.contains("/*") || expression.contains("*/")) {
            throw new IllegalStateException("SQL 表达式片段不能包含分号或注释: " + column.targetColumn());
        }
        if (DANGEROUS_SQL_KEYWORD_PATTERN.matcher(expression).find()) {
            throw new IllegalStateException("SQL 表达式片段包含完整 SQL 关键字: " + column.targetColumn());
        }
    }

    /**
     * 校验表达式中出现的弹性域字段必须来自当前目标列允许使用的字段映射。
     */
    private void validateElasticColumnScope(ProcessingPlanColumn column, Set<String> allowedElasticColumns) {
        Matcher matcher = ELASTIC_COLUMN_PATTERN.matcher(column.expressionSql());
        while (matcher.find()) {
            String elasticColumn = matcher.group();
            if (!allowedElasticColumns.contains(normalizeIdentifier(elasticColumn))) {
                throw new IllegalStateException(
                        "SQL 表达式片段引用了未授权的弹性域字段: " + elasticColumn + "，目标列: " + column.targetColumn()
                );
            }
        }
    }

    /**
     * 校验 AI 没有把用户上传表头当作真实 SQL 字段名使用。
     */
    private void validateActualColumnNotUsed(ProcessingPlanColumn column, List<ActualColumnMapping> mappings) {
        String expressionWithoutLiterals = SINGLE_QUOTED_LITERAL_PATTERN.matcher(column.expressionSql()).replaceAll("''");
        for (ActualColumnMapping mapping : safeMappings(mappings)) {
            if (isBlank(mapping.actualColumn()) || mapping.actualColumn().equalsIgnoreCase(mapping.elasticColumn())) {
                continue;
            }
            if (containsActualColumnIdentifier(expressionWithoutLiterals, mapping.actualColumn())) {
                throw new IllegalStateException(
                        "SQL 表达式片段不能直接引用用户上传表头，只能引用弹性域字段: " + mapping.actualColumn()
                );
            }
        }
    }

    /**
     * 提取当前目标列允许使用的弹性域字段名。
     */
    private Set<String> extractElasticColumns(List<ActualColumnMapping> mappings) {
        return safeMappings(mappings).stream()
                .map(ActualColumnMapping::elasticColumn)
                .filter(value -> !isBlank(value))
                .map(this::normalizeIdentifier)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 判断表达式是否直接引用了 actualColumn 这个用户上传表头。
     */
    private boolean containsActualColumnIdentifier(String expressionSql, String actualColumn) {
        String lowerExpression = expressionSql.toLowerCase(Locale.ROOT);
        String lowerActualColumn = actualColumn.toLowerCase(Locale.ROOT);
        if (lowerExpression.contains("\"" + lowerActualColumn + "\"")
                || lowerExpression.contains("`" + lowerActualColumn + "`")
                || lowerExpression.contains("[" + lowerActualColumn + "]")) {
            return true;
        }
        if (!actualColumn.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return false;
        }
        return Pattern.compile("(?i)\\b" + Pattern.quote(actualColumn) + "\\b").matcher(expressionSql).find();
    }

    /**
     * 保护性返回字段映射列表，减少调用侧空值处理。
     */
    private List<ActualColumnMapping> safeMappings(List<ActualColumnMapping> mappings) {
        return mappings == null ? List.of() : mappings;
    }

    /**
     * 统一字段名比较口径。
     */
    private String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验字符串非空。
     */
    private void requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 校验两个上下文字段必须一致。
     */
    private void requireEquals(String expected, String actual, String message) {
        if (!String.valueOf(expected).equals(actual)) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 判断字符串是否为空白。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
