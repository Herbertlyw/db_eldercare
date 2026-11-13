# 左邻右舍社区居家养老服务平台

数据库系统课程设计，Java 17、Spring Boot 3.4.4、Spring JDBC、MySQL 8.0.16 及以上。本机验证环境为原生 MySQL 8.4.11，Docker 不是运行依赖。业务、政策、设备和体征数据均为课程虚构示例。

小组成员及 Agent 修改项目之前，请先阅读 [协作规范 AGENTS.md](AGENTS.md)。每项任务使用独立分支，完成自检和测试、同步最新 main 后，在本地合并并推送 main；不要求创建 PR 或等待他人审批。

源码、SQL、报告和 PPT 保存在本仓库。[Releases 中的运行包](https://github.com/Herbertlyw/db_eldercare/releases)与各自的发布标签对应；GitHub 自动生成的 Source code 压缩包不包含编译好的 JAR。当前源码可按下文构建，完整课程运行包包含 JAR、初始化脚本和课程材料。

## Windows 运行

1. 安装 Java 17，安装并启动 MySQL 8，确认 `java -version` 和 `mysql --version` 可用。
2. 解压完整运行包。首次双击 `初始化Windows.bat`，输入本机 MySQL 管理员连接信息。脚本只创建 `eldercare` 课设库及专用账户；若同名库已存在会停止，避免覆盖。
3. 双击 `启动Windows.bat`，浏览器访问 <http://127.0.0.1:18080>。
4. 打开 `.local/演示账号.txt` 获取本机随机生成的演示密码。`admin1`、`family1`、`caregiver1`、`manager1`、`street1`、`regulator1` 分别表示社区管理员、家属、护工、机构负责人、街道审核和民政监管。

初始化脚本不安装 Docker，不要求 Python 或 Maven。PowerShell 脚本已静态检查，尚未在真实 Windows 机器执行；遇到版本或 PATH 差异请保留错误信息。若 MySQL 在远程主机，需由该数据库管理员配置来源主机授权，当前自动建用户脚本按本机 MySQL 设计。

## 本项目工作目录运行

本机已经配置独立 MySQL 实例，端口 `33316`，应用端口 `18080`。连接秘密和密钥在 `.local/config.json`，不加入提交包。

```sh
mvn -DskipTests package
python3 scripts/run.py
```

若数据库服务停止，使用 `.local` 中的已有数据启动实例，不要重新执行初始化。原有实验用 MySQL 和其他数据库不属于本项目，不应停止或清理。

```sh
python3 scripts/start-local-db.py
python3 scripts/run.py
```

## 从源码部署到任意 MySQL

数据库管理员创建空库及专用用户，然后依次执行 `database/01_schema.sql`、`02_routines.sql`、`03_views.sql`。连接需支持触发器、存储过程和 CHECK 约束，不能使用 SQLite 或 H2 代替验收数据库。

先执行 `mvn -DskipTests package` 构建源码，将 `target/eldercare-1.0.0.jar` 复制为运行目录中的 `eldercare.jar`。完整运行包已包含该文件。

设置环境变量 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`FIELD_KEY`（64 个十六进制字符）、`DEMO_PASSWORD` 后运行：

```sh
java -jar eldercare.jar --app.seed=true --app.jobs=false --spring.main.web-application-type=none
java -jar eldercare.jar
```

种子只接受空库。重新初始化测试请使用独立测试库，不能清空演示或其他用户数据库。数据库服务端时间须与应用按同一时区解释，当前采用 Asia/Shanghai；Windows 安装时检查 MySQL `@@time_zone` 与系统时区。

## 目录

- `src/main/java/edu/bjfu/eldercare`：按 interfaces、application、domain、infrastructure 分层的 Java 单体，持久化使用 Spring JDBC。
- `src/main/resources/static`：本地演示界面，无外部 CDN 依赖。
- `database`：建表、过程和触发器、15 个视图、15 组双实现查询。
- `src/test`：领域规则、分层依赖和路由测试，以及真实 MySQL 权限、边界、回滚和并发案例。
- `evidence`：测试日志、数据数量、SQL 等价验证、执行计划与备份恢复证据。
- `docs`：需求、设计、数据字典、运维、图件和答辩讲解材料。
- `outputs`：按教师模板生成的报告与提交材料。

## 验证

本机 `python3 scripts/test.py` 会重新建立独立的 `eldercare_test` 数据库并运行 Maven 测试，绝不重置 `eldercare`。该辅助脚本依赖本机 `.local/config.json`，其他机器可用自己的测试库与环境变量执行 `mvn test`。

`python3 scripts/recovery_drill.py` 在独立恢复库演练全量加实际 binlog 增量恢复。`python3 scripts/capture_evidence.py` 重新采集表结构、行数、视图和查询对比结果。

恢复演练需先停止本项目应用和其他写入，保证本次受控实验中的全量备份与 binlog 起点之间没有并发写入。真实生产备份应从一致性快照直接取得日志位置，不能照搬这个小型演示脚本作为灾备系统。

管理员界面的“批量上传模拟体征”接受 JSON 数组，例如 `[{"bindingId":1,"metricId":1,"sampleKey":"demo-001","collectedAt":"2026-11-12T18:30:00","value":80}]`。请将绑定编号、指标编号和采样时间改成当前库中有效的值；同一绑定下的采样键不得重复。

## 业务口径

有效期和排班统一采用左闭右开区间。预约必须处于有效照护计划和评估范围；护工资质还需覆盖当前接单和预约服务时间。服务实际签退与家属确认分别记录，工时异常不计入补贴。补贴按政策采用月、季、年周期，并拒绝同一老人同项目的重叠周期申报。驳回后补充材料重新提交，保留原订单与审核记录。围栏采用虚构米制坐标，由服务端分别计算签到和签退距离。

数据库数据保护由约束、受控业务方法、历史禁止删除、已完成订单及已拨付申请的不可修改规则和审计共同完成。应用账号属于可信服务端，不向最终用户提供数据库口令；数据库超级管理员仍有管理能力，不能宣称绝对防篡改。

## 提交前必须由本人补齐

组名、4—5 名真实成员、实际分工和个人贡献、真实会议与协作记录、全员发言的答辩录像、教师提问后的反馈。模板中的这些项目不会伪造。报告中的待确认位置必须在提交前处理。

## 业务范围与课程材料

系统包含档案与评估、订单与投诉、护工招聘培训及排班、设备与应急、补贴与质量监管。数据库为 47 张基础表、15 个业务视图、2 个内部视图、10 个存储过程和 74 个触发器。种子包含 24 个社区以及自理、部分失能、重度失能样例。

- [需求分析与整体设计](docs/需求与整体设计思路.md)：业务、权限、状态及系统边界。
- [课程要求与实现对照](docs/需求追踪与模板差异.md)：任务书、模板和评分标准的对应材料。
- [功能验收清单](docs/功能验收清单.md)：实际入口与代表性测试。
- [运行维护与验收](docs/运行维护与验收.md)：配置、备份、安全与升级操作。
- [代码与需求复查记录](docs/代码与需求复查记录.md)：验证范围、实际问题和回归案例。
- [报告与 PPT](outputs)：小组及个人报告、13 页答辩 PPTX 和 PDF。

2026 年 11 月 13 日的自动化记录为 163 项通过：125 项真实 MySQL 业务测试、1 项 Spring 路由契约集成测试、37 项独立测试。实际方法和结果见 `evidence/test-summary.json`，浏览器记录见 `evidence/completion-browser.json`。这些结果对应记录中的执行环境及日期。

开发环境安装 Playwright 后可运行 `node scripts/verify_browser.cjs`。先将应用连接独立测试库，关闭定时任务并启动在测试端口；脚本会写入虚构业务数据，使用 `DEMO_PASSWORD` 或本机开发配置中的演示密码。不得对展示库执行。

## 已有数据库升级

新安装在空库按 01、02、03 的顺序导入 SQL，不执行历史迁移。已有数据库按 [迁移说明](database/migrations/README.md) 确认适用结构，先停应用、备份并在副本演练。迁移脚本不用于重复初始化，也不以重建业务库替代升级。

## Java 代码结构与开发

业务操作按七个模块拆分，状态与计算规则放在领域层，SQL 放在 JDBC 仓储和查询适配器。`CareService.execute` 保留统一事务边界。持久层使用 Spring JDBC，数据库中的过程、触发器和并发约束参与业务校验。具体目录、调用过程和开发约定见 [Java 架构与开发约定](docs/Java架构与开发约定.md)，初学者可先看 [从零讲解与代码导读](docs/从零讲解与代码导读.md)。

运行 `mvn spotless:apply` 统一 Java 格式；构建会自动检查格式。测试可通过 `TEST_DATABASE=eldercare_<名称>_test` 选择独立可重建测试库，浏览器测试通过 `TEST_PORT` 选择 18081—18089 的独立端口。不要复用其他正在运行的任务所用的测试库。

运行包包含当前分层实现的 JAR、数据库脚本、报告和 PPTX。包内 SHA256SUMS.json 记录各文件的校验值，evidence/delivery-verification.json 记录 JAR 校验值及独立测试库的运行验证范围。
