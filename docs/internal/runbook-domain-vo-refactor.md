# 域级 VO 改造操作手册

把一个业务域的 HTTP 出口从「直接返回领域 DTO」改为「返回域内 VO + 装配器」。
`knowledge` 域已按本手册完整走通（8 个控制器 / 69 个端点），可作为参照实现。

## 1. 前提与边界

| 项 | 约束 |
|---|---|
| 前端 | **不动**。VO 的字段名与声明顺序必须与改造前逐字节一致，前端无感 |
| 表结构 | **不动**。本改造只在 Java 层重排对象，不改任何列 |
| 判据 | **HTTP 金片（`*ContractTest`）逐字节不变**。没有第二种判据 |

需要改前端或表结构时，先说明影响面再决定，不自行推进。

## 2. 什么该 VO 化，什么不该

判据只有一条：**类型住在哪个模块**。

| 类型位置 | 处理 | 理由 |
|---|---|---|
| `backend/app/<域>/dto/**` | **VO 化** | 域内 DTO 是内部演进物，不该出现在 HTTP 契约上 |
| `backend/framework/.../shared/**` | **直出，不 VO 化** | 跨模块共享形状；复制一份进域内等于把共享契约私有化 |
| 控制器内部静态类（如 `ChunkerPreviewController.PreviewResponse`） | **不动** | 它本就是响应专用对象，已满足分层 |
| `Map<String,Object>` / `JsonNode` / `ObjectNode` | **不动** | 不透明载荷，VO 化等于重定义整棵树 |
| 枚举（如 `ParseStatus`） | **保留原类型** | 稳定值集，重建只引入映射风险，且可能改 JSON 字面量 |
| `ApiResponse<Void>` | **不动** | 无响应体，外壳由框架统一 |

## 3. 批次步骤

### 3.1 侦察（必做，避免试探）

```bash
# 端点与返回类型
grep -oE "ResponseEntity<[A-Za-z<>?, ]+>|ApiResponse<[A-Za-z<>?, ]+>" \
  backend/app/src/main/java/com/ragagent/<域>/controller/<X>Controller.java | sort | uniq -c

# 每个返回类型的定义位置与规模
for n in <类型名...>; do
  f=$(find backend -name "$n.java" -not -path "*/target/*" | head -1); echo "$n -> $f"
done
```

先确认每个类型在 §2 表里的归属，再决定改哪些端点。

### 3.2 生成 VO

从 `record` 定义机械生成，**不要手抄**——字段顺序错一个字节金片就红。

```text
controller/vo/<域>RespVo.java     一个源 record 对应一个 VO，组件声明顺序原样照抄
assembler/<域>Assembler.java      toVo(源) + 泛型 toVoList(list, mapper) + toVoPage(page, mapper)
```

生成前先看域内是否已有同形状 VO（如单字段 `taskId` 的 `TaskIdRespVo`）：**直接复用，不造重复 record**。

生成后必须检查三件事：

1. **嵌套类型也要 VO 化**——若 VO 里出现 `List<SomeDomainDto>`，说明生成器只抄了外壳
2. **嵌套列表要逐元素映射**——装配器里写 `src.items()` 会编译失败，需 `toVoList(src.items(), X::toVo)`
3. **不要内联全限定名**——`java.util.List.of()` 会被 `check-fully-qualified-names.py` 拦下

### 3.3 改控制器

```bash
# 改前先存一份，便于对照
git diff --stat
```

改法：返回类型换成 VO；构造点 `new XxxResponse(...)` 换成 `new XxxRespVo(...)` 或 `Assembler.toVo(...)`。

### 3.4 快闸（每批必跑，约 1 分钟）

```bash
mvn -B -q -pl backend/app spotless:apply
mvn -B -pl backend/app -am test -Dtest='<本域全部 *ContractTest>' -Dsurefire.failIfNoSpecifiedTests=false
bash scripts/run-guards.sh
```

金片不变 + 守卫 50 条全绿 → 提交。

### 3.5 全闸门（每**域**收尾一次，约 4 分钟）

```bash
mvn -B clean verify
```

只在整域改完时跑。中途每批跑它，收益低于等待成本；但**跨模块改动**（动 `framework`/引擎）不受此豁免，必须立即全量。

## 4. 陷阱清单（每条都实际踩过）

| # | 陷阱 | 症状 | 正确做法 |
|---|---|---|---|
| 1 | 逐端点 VO 化 | 泛型返回类型不协变，`ResponseEntity<Vo>` 无法 override `ResponseEntity<Dto>` | 整控制器替换，或先建影子控制器（见 §5） |
| 2 | VO 字段顺序 | 金片红，diff 显示字段次序不同 | Jackson 按 record 组件顺序输出，必须原样照抄 |
| 3 | VO 泄露领域 DTO | VO 里出现 `List<SomeDto>`，分层没落地 | 给嵌套类型也生成 VO |
| 4 | 嵌套列表未映射 | 编译失败：`List<Dto>` 无法转 `List<Vo>` | `toVoList(src.items(), X::toVo)` |
| 5 | 私有 helper 未跟改 | 编译失败：返回 `List<Dto>` 但调用处要 `List<Vo>` | 批量改写必须同时改私有方法与三元分支 |
| 6 | 二次装配 | `no suitable method found for toVo(Record)` | 检查 `var` 分支是否已被包过一层 |
| 7 | 局部变量与参数重名 | 编译失败：`variable page is already defined` | 换名（`pageResult`） |
| 8 | 生成类名大小写 | `KBCloneProgress` 生成成 `KbCloneProgressRespVo` | 引用处按生成结果写 |
| 9 | 内联全限定名 | 全闸门才红（`check-fully-qualified-names.py`） | 快闸已含此检查，import 后用短名 |
| 10 | 分页包裹差一层 | 金片红，响应多/少一层嵌套 | `PageResult.of(items, total, page, pageSize)` 四项都要传 |
| 11 | 嵌套字段 null 语义被改写 | 金片红：可空列表由 `null` 变 `[]` | 装配器对**可空嵌套列表**保持 `null` 透传；只有端点级"空列表兜底"才用 `List.of()` |

## 5. 高风险改造：先建影子控制器

当改动面大（> 15 端点）或该域金片稀疏时，用 A/B 双实现把风险降到零：

```text
旧控制器  @Profile("!<域>-new")   默认生效
新控制器  @Profile("<域>-new")    同一批路由，独立实现
影子测试  继承原 *ContractTest，加 @ActiveProfiles("<域>-new")
```

同一批金片跑两遍，两侧全绿即等效。**必须配一条红态探针**：

```java
assertEquals(1, context.getBeanNamesForType(新控制器.class).length);
assertEquals(0, context.getBeanNamesForType(旧控制器.class).length);
```

没有这条断言，父类金片全部通过也可能只是又跑了一遍默认实现。

验证完毕后把新实现落回生产控制器、删除影子与影子测试。

## 6. 验收清单

- [ ] 该域全部 `*ContractTest` 通过，金片逐字节不变
- [ ] `bash scripts/run-guards.sh` 全绿（含内联全限定名、未使用 import、端口形状三条）
- [ ] `mvn -B clean verify` 全绿（域收尾时）
- [ ] 控制器不再直接返回域内 DTO（`grep "ResponseEntity<.*Response>" ` 应为空）
- [ ] 没有新增对 `framework/shared` 类型的 VO 复制
- [ ] 未触碰前端与表结构

## 7. 参考实现

`knowledge` 域的最终形态（2026-11-23 实测）：

```text
backend/app/src/main/java/com/ragagent/knowledge/
├── controller/        8 个控制器，69 端点全部返回 VO
├── controller/vo/     23 个 VO
└── assembler/         5 个装配器（FaqEntry / FaqExport / FaqImport / Chunk / Knowledge）
```

按本手册推进的其他域（2026-11-23）：

| 域 | VO | 装配器 | VO 化端点 | 备注 |
|---|---|---|---|---|
| `favorite` | 1 | 1 | 1（另 2 个是空体） | 最小样板 |
| `memory` | 6 | 1 | 7 | 含泛型分页壳 `MemoryListRespVo<T>`；导出空 `items` 必须保持 null |
| `storage` | 4 | 2 | 6 | 另 5 个是空体 / `Map` / 文件流，按 §2 不动 |

已知盲区：本手册 §3.1 的 grep 只抓 `ResponseEntity<...>` / `ApiResponse<...>`，**裸返回类型会漏**（`memory` 的 `MemoryListResponse<T>`、`MemoryTopicView` 等就是这样漏掉的）。侦察时补一条：`grep -E "public [A-Z][A-Za-z]+<" 控制器`。

对照提交：`refactor(faq)` / `refactor(chunk)` / `refactor(knowledge)` 系列。
