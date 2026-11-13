# 左邻右舍社区居家养老服务平台

数据库系统课程设计，Java 17、Spring Boot 3.4.4、Spring JDBC、MySQL 8.0.16 及以上。已在本机原生 MySQL 8.4.11 运行，Docker 不是运行依赖。业务、政策、设备和体征数据均为课程虚构示例。

小组成员及 Agent 修改项目之前，请先阅读 [协作规范 AGENTS.md](AGENTS.md)。每项任务使用独立分支，完成自检和测试、同步最新 main 后，在本地合并并推送 main；不要求创建 PR 或等待他人审批。

源码、SQL、报告和 PPT 保存在本仓库。直接运行请到 [Releases 下载完整运行包](https://github.com/Herbertlyw/db_eldercare/releases/latest)，选择“左邻右舍养老平台_运行与课程材料.zip”；GitHub 自动生成的 Source code 压缩包不包含编译好的 JAR。

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

设置环境变量 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`FIELD_KEY`（64 个十六进制字符）、`DEMO_PASSWORD` 后运行：

```sh
java -jar eldercare.jar --app.seed=true --app.jobs=false --spring.main.web-application-type=none
java -jar eldercare.jar
```

种子只接受空库。重新初始化测试请使用独立测试库，不能清空演示或其他用户数据库。数据库服务端时间须与应用按同一时区解释，当前采用 Asia/Shanghai；Windows 安装时检查 MySQL `@@time_zone` 与系统时区。

## 目录

- `src/main/java/edu/bjfu/eldercare`：按 interfaces、application、domain、infrastructure 分层的 Java 单体，持久化仍为 Spring JDBC。
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

## 2026 年 11 月 13 日功能补齐

五条业务线补入招聘培训、投诉办理、比例抽查、整改复核、公示、机构考核、多社区授权及统计；补齐档案与计划维护、详情查询、设备解绑与定时模拟、应急到场和关闭、补贴证明归档及驳回重报。结构为 47 张基础表、15 个业务视图及 2 个内部视图。全部测试方法和实际结果见 `evidence/test-summary.json`，浏览器操作证据见 `evidence/completion-browser.json`。

已有本机库通过 `database/migrations/20261113_completion.sql` 保留数据升级；迁移前停应用并备份。新安装只按最新 01、02、03 顺序导入，不再执行历史迁移。新库种子包含 24 个社区以及自理、部分失能、重度失能样例；本机旧库保留其 20 个社区和原业务记录。

开发环境可安装 Playwright 后运行 `node scripts/verify_browser.cjs`，先将独立测试应用接到 `eldercare_test` 并启动在 `18081`，关闭其定时任务。该脚本会新增虚构业务记录，使用 `DEMO_PASSWORD` 环境变量或本机开发配置中的演示密码。不要将测试端口连接到展示库。

新增操作说明与逐项对应关系见 `docs/功能验收清单.md`。原先发现的缺口保存在复查记录的历史部分，不能把旧状态当作当前版本结论。


## 已有数据库升级

v1.0.1 补齐历史记录保护，并明确复评、设备迁移和应急通知规则。已有 v1.0.0 的 47 表数据库，先停止应用、做全量备份，再执行 `database/migrations/20261113_history_rules.sql` 并更换 JAR。脚本仅替换存储对象，不重建业务表；新安装不需要执行迁移。当前 125 项真实 MySQL 集成测试通过，详细规则和验证范围见 `docs/运行维护与验收.md` 与 `docs/功能验收清单.md`。

## Java 代码结构与开发

业务操作按七个模块拆分，状态与计算规则放在领域层，SQL 放在 JDBC 仓储和查询适配器。`CareService.execute` 保留统一事务边界。项目没有改用 ORM，数据库中的过程、触发器和并发约束继续生效。具体目录、调用过程和开发约定见 [Java 架构与开发约定](docs/Java架构与开发约定.md)，初学者可先看 [从零讲解与代码导读](docs/从零讲解与代码导读.md)。

运行 `mvn spotless:apply` 统一 Java 格式；构建会自动检查格式。测试可通过 `TEST_DATABASE=eldercare_<名称>_test` 选择独立可重建测试库，浏览器测试通过 `TEST_PORT` 选择 18081—18089 的独立端口。不要复用其他正在运行的任务所用的测试库。

当前源码的重构不包含重新发布 Release，下载页中的旧运行包与当前源码结构可能不同。
