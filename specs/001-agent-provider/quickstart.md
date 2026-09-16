# Quickstart: Agent Provider（第16节）

## 前置

- JDK 21、Maven 3.9+。
- 实现前已跑 `mvn dependency:tree -pl fourfeetcat-provider` 确认 `spring-ai-model`/`spring-ai-openai` 在锁定 BOM 内可解析（H3 门禁）。

## 自动化验证（判卷即此）

```bash
# 全量门禁（含 Spotless/PMD/Checkstyle/SpotBugs），实现完成的定义
mvn clean verify

# 只跑本节单测
mvn test -pl fourfeetcat-core,fourfeetcat-provider,fourfeetcat-storage
```

预期：五个测试类全绿，其中 `ProviderServiceTest` 的三个课件钉坑测试（路由不串台 / 失败审计先落账 / 自动执行关闭+schema 带上）是关键回归点。

## 人工冒烟（集成）

```bash
DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration -pl fourfeetcat-provider
```

预期：`ProviderSmokeIT` 真调一次，非空响应，`llm_calls` 多一条 success=1。缺 key 时该测试自动跳过（`assumeTrue`），不算失败。

## 明文 key 检查

```bash
grep -rn "sk-" --include="*.yaml" --include="*.java" fourfeetcat-*/src
```

预期：无输出（key 只走 `${ENV}` 占位）。
