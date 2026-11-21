# 左邻右舍社区居家养老服务平台

数据库系统课程设计，Java 17、Spring Boot 3.4.4、Spring JDBC、MySQL 8.0.16 及以上。前端使用 React、TypeScript 和 Arco Design React。本机验证环境为原生 MySQL 8.4.11，Docker 不是运行依赖。业务、政策、设备和体征数据均为课程虚构示例。

小组成员及 Agent 修改项目之前，请先阅读 [协作规范 AGENTS.md](AGENTS.md)。每项任务使用独立分支，完成自检和测试、同步最新 main 后，在本地合并并推送 main；不要求创建 PR 或等待他人审批。

源码、SQL、报告和 PPT 保存在本仓库。[Releases 中的运行包](https://github.com/Herbertlyw/db_eldercare/releases)与各自的发布标签对应；GitHub 自动生成的 Source code 压缩包不包含编译好的 JAR。当前源码可按下文构建，完整课程运行包包含 JAR、初始化脚本和课程材料。

## Windows 运行

1. 安装 Java 17，安装并启动 MySQL 8，确认 `java -version` 和 `mysql --version` 可用。
2. 解压完整运行包。首次双击 `初始化Windows.bat`，输入本机 MySQL 管理员连接信息。脚本只创建 `eldercare` 课设库及专用账户；若同名库已存在会停止，避免覆盖。
3. 双击 `启动Windows.bat`，浏览器访问 <http://127.0.0.1:18080>。
4. 打开 `.local/演示账号.txt` 获取本机随机生成的演示密码。`admin1`、`family1`、`caregiver1`、`manager1`、`street1`、`regulator1` 分别表示社区管理员、家属、护工、机构负责人、街道审核和民政监管。

初始化脚本不安装 Docker，不要求 Python 或 Maven。PowerShell 脚本已静态检查，尚未在真实 Windows 机器执行；遇到版本或 PATH 差异请保留错误信息。若 MySQL 在远程主机，需由该数据库管理员配置来源主机授权，当前自动建用户脚本按本机 MySQL 设计。

## 开发依赖

| 用途 | 依赖 | 安装与使用 |
| --- | --- | --- |
| Java 后端 | JDK 17、Maven 3.9 | `java -version`、`mvn -version` 检查 PATH；Java 依赖由 Maven 安装 |
| 数据库 | MySQL 8.0.16 及以上 | 初始化表、视图、过程和触发器；应用继续使用 Spring JDBC |
| 前端源码构建 | Node.js 22.12 及以上、npm | `npm --prefix frontend ci` 使用已提交的锁文件 |
| 前端组件 | React 18.3.1、Arco Design React 2.66.18 | 版本在 `frontend/package.json`，传递依赖在 `package-lock.json` |
| 前端开发与测试 | TypeScript、Vite、Vitest、Playwright | 随 `npm ci` 安装；浏览器验收另外需要 Chrome |
| 已构建 JAR 运行 | Java 17、MySQL | 无需 Node.js、npm 或独立前端服务器 |

前端开发、检查命令和目录职责见 [frontend/README.md](frontend/README.md)。不要提交 `node_modules`、`frontend/dist`、`target` 或本机 `.local` 配置。安装或升级 npm 依赖时同时提交 `package.json` 和 `package-lock.json`；不要只修改其中一个文件。

## 本项目工作目录运行

源码构建需要 Java 17、Maven 3.9、Node.js 22.12 及以上和 npm；Maven 会安装前端锁定依赖并将页面打入 JAR。运行已构建的 JAR 不需要 Node.js。

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

### 已有数据库升级

拉取代码不会自动修改数据库。已有 47 表数据库先停止应用并备份，在独立副本核对后，按[迁移说明](database/migrations/README.md)执行适用脚本：证书字段使用 `20261114_acceptance.sql`，密码维护使用 `20261116_password_management.sql`；内置课程数据可选执行 `20261114_demo_credential_types.sql` 和 `20261116_seed_names.sql`。已经具备对应结构的数据库按脚本适用条件检查，不重新初始化业务库。

已有库的派单过程使用 [20261118_dispatch_window.sql](database/migrations/20261118_dispatch_window.sql) 升级，让派单和改派都检查预约结束时间。先在备份副本验证，再暂停派单并执行脚本。

社区到期检查使用 [20261121_community_maintenance.sql](database/migrations/20261121_community_maintenance.sql) 提供的按社区过程。已有库先备份，在副本验证后暂停定时和手动检查，执行脚本并核对过程。

构建后启动配套 JAR，核对登录、列表及办理入口。升级保持现有账号和密码；已有业务记录按各自状态继续办理。社区、机构、服务和政策的初始化名称在 `src/main/resources/seed-labels.json` 中维护，数据来源说明见 [演示数据说明](docs/演示数据说明.md)。

### 丰富本地演示数据

已初始化的课程库可选择导入邻里示例：

```sh
java -jar eldercare.jar --app.demo-content=true --app.jobs=false --spring.main.web-application-type=none
```

执行前停止应用，备份数据库并在副本演练。该命令与 `--app.seed=true` 分开执行；正常启动不会自动导入。它为原种子人物使用自然的虚构中文姓名，并在青禾社区增加 12 位老人、12 个家属账号、12 份评估、12 份照护计划、12 笔待派单预约和 3 个照护包，涵盖三种能力等级。家属账号为 `neighbor_family1` 至 `neighbor_family12`，初始密码与导入时 `family1` 相同，各自只能查看绑定的老人。人物定义在 `src/main/resources/static/demo-people.json`，不含真实身份信息。

导入在同一事务中完成，编号或账号冲突会回滚。重复执行不重复新增，也不会覆盖已录入档案的后续修改。已有种子人物只有在身份摘要匹配且姓名仍为默认占位名时才更名。新增老人使用编号 1001—1012；该命令不改变表结构、不删除原有数据，适用于保留 20 或 24 个社区的课程种子库。预约时间相对首次导入日期生成，日常预约和后续调整通过正常业务界面办理。

业务状态示例使用独立命令，可在基础种子初始化后导入，也可与邻里示例一起保留：

```sh
java -jar eldercare.jar --app.demo-scenarios=true --app.jobs=false --spring.main.web-application-type=none
```

执行前同样须停应用、备份并在副本演练。示例包含不同状态的订单、工时、投诉、招聘培训、设备应急、补贴和抽查整改，覆盖基础表中 30 个枚举字段的 100 个取值。所有记录均为课程虚构数据，导入不修改原有业务记录；首次导入整批提交，再次执行保持已有数据。详细内容、账号和验证方式见 [演示数据说明](docs/演示数据说明.md)。

## 目录

- `src/main/java/edu/bjfu/eldercare`：按 interfaces、application、domain、infrastructure 分层的 Java 单体，持久化使用 Spring JDBC。
- `frontend`：React + Arco 页面源码、TypeScript 配置、依赖锁文件和前端测试。
- `src/main/resources/static`：人物资料、头像、社区照片和品牌插画；页面无需外部 CDN。
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

系统包含档案与评估、订单与投诉、护工招聘培训及排班、设备与应急、补贴与质量监管。数据库为 47 张基础表、15 个业务视图、2 个内部视图、12 个存储过程和 74 个触发器。种子包含 24 个社区以及自理、部分失能、重度失能样例。

- [需求分析与整体设计](docs/需求与整体设计思路.md)：业务、权限、状态及系统边界。
- [课程要求与实现对照](docs/需求追踪与模板差异.md)：任务书、模板和评分标准的对应材料。
- [功能验收清单](docs/功能验收清单.md)：实际入口与代表性测试。
- [运行维护与验收](docs/运行维护与验收.md)：配置、备份、安全与升级操作。
- [代码与需求复查记录](docs/代码与需求复查记录.md)：验证范围、实际问题和回归案例。
- [报告与 PPT](outputs)：小组及个人报告、13 页答辩 PPTX 和 PDF。

当前 Java/MySQL 验证记录见 `evidence/test-summary.json`。React 浏览器脚本位于 `scripts/react`，覆盖角色页面、表单、跨角色业务流、密码维护和照片交互。使用方法见 [前端开发说明](frontend/README.md)，业务办理证据位于 `evidence/react`，自然名称和逐角色页面证据位于 `evidence/natural-names`。所有写入测试必须连接独立 MySQL 测试库，并关闭定时任务；页面入口检查不能代替真实业务回归。

可通过 `npm --prefix frontend run dev` 启动本地前端开发服务，默认地址为 `http://127.0.0.1:18089`，API 代理至 `18080` 的 Java 应用。正式运行由 Java 同时提供页面和 API。

## 已有数据库升级

新安装在空库按 01、02、03 的顺序导入 SQL，不执行历史迁移。已有数据库按 [迁移说明](database/migrations/README.md) 确认适用结构，先停应用、备份并在副本演练。迁移脚本不用于重复初始化，也不以重建业务库替代升级。

## Java 代码结构与开发

业务操作按七个模块拆分，状态与计算规则放在领域层，SQL 放在 JDBC 仓储和查询适配器。`CareService.execute` 保留统一事务边界。持久层使用 Spring JDBC，数据库中的过程、触发器和并发约束参与业务校验。具体目录、调用过程和开发约定见 [Java 架构与开发约定](docs/Java架构与开发约定.md)，初学者可先看 [从零讲解与代码导读](docs/从零讲解与代码导读.md)。

运行 `mvn spotless:apply` 统一 Java 格式；构建会自动检查格式。测试可通过 `TEST_DATABASE=eldercare_<名称>_test` 选择独立可重建测试库，浏览器测试通过 `TEST_PORT` 选择 18081—18089 的独立端口。不要复用其他正在运行的任务所用的测试库。

运行包包含当前分层实现的 JAR、数据库脚本、报告和 PPTX。包内 SHA256SUMS.json 记录各文件的校验值，evidence/delivery-verification.json 记录 JAR 校验值及独立测试库的运行验证范围。

证书类型与等级分别保存；旧库按 [数据库迁移说明](database/migrations/README.md)执行 `20261114_acceptance.sql` 后启动配套程序。机构负责人可在职业证书页面登记或补录类型。正常演示启用自动任务，`--app.jobs=false` 只用于需要固定数据的隔离测试。

## 账号密码维护

用户登录后可在菜单底部修改密码。忘记密码可通过登录页“忘记密码？”查看联系办法；社区管理员重置本社区家属账号，机构负责人重置本机构护工账号，管理人员由维护工具恢复。重置后的临时密码必须在首次登录时修改，旧登录会话失效。操作说明见 [运行维护与验收](docs/运行维护与验收.md)。旧库先执行 `database/migrations/20261116_password_management.sql`，新建库已包含相关字段。

## 页面操作

登录后进入六种角色各自的工作台：家属照护、护工日程、社区调度、机构管理、街道初审和民政监管。可从首页待办进入对应记录，在记录旁办理当前阶段的业务。表单支持按姓名和名称查找；列表可按关键词、状态和日期查询全部授权记录。具体操作及验证方法见 [页面交互与角色操作](docs/页面交互与角色操作.md)。
