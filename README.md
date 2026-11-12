# 邻安社区居家养老智慧服务平台

数据库系统课程设计，Java 17、Spring Boot 3.4.4、Spring JDBC、MySQL 8.0.16 及以上。已在本机原生 MySQL 8.4.11 运行，Docker 不是运行依赖。业务、政策、设备和体征数据均为课程虚构示例。

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

## 从源码部署到任意 MySQL

数据库管理员创建空库及专用用户，然后依次执行 `database/01_schema.sql`、`02_routines.sql`、`03_views.sql`。连接需支持触发器、存储过程和 CHECK 约束，不能使用 SQLite 或 H2 代替验收数据库。

设置环境变量 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`FIELD_KEY`（64 个十六进制字符）、`DEMO_PASSWORD` 后运行：

```sh
java -jar eldercare.jar --app.seed=true --app.jobs=false --spring.main.web-application-type=none
java -jar eldercare.jar
```

种子只接受空库。重新初始化测试请使用独立测试库，不能清空演示或其他用户数据库。数据库服务端时间须与应用按同一时区解释，当前采用 Asia/Shanghai；Windows 安装时检查 MySQL `@@time_zone` 与系统时区。

## 目录

- `src/main/java/edu/bjfu/eldercare`：应用、JDBC DAO、业务事务、身份验证、接口和定时任务。
- `src/main/resources/static`：本地演示界面，无外部 CDN 依赖。
- `database`：建表、过程和触发器、15 个视图、15 组双实现查询。
- `src/test`：真实 MySQL 集成测试，含权限、边界、回滚和并发案例。
- `evidence`：测试日志、数据数量、SQL 等价验证、执行计划与备份恢复证据。
- `docs`：需求、设计、数据字典、运维、图件和答辩讲解材料。
- `outputs`：按教师模板生成的报告与提交材料。

## 验证

本机 `python3 scripts/test.py` 会重新建立独立的 `eldercare_test` 数据库并运行 Maven 测试，绝不重置 `eldercare`。该辅助脚本依赖本机 `.local/config.json`，其他机器可用自己的测试库与环境变量执行 `mvn test`。

`python3 scripts/recovery_drill.py` 在独立恢复库演练全量加实际 binlog 增量恢复。`python3 scripts/capture_evidence.py` 重新采集表结构、行数、视图和查询对比结果。

## 业务口径

有效期和排班统一采用左闭右开区间。预约必须处于有效照护计划和评估范围；护工资质还需覆盖当前接单和预约服务时间。服务实际签退与家属确认分别记录，工时异常不计入补贴。补贴按服务发生月份结算，同一老人同一项目同一月份一份申报；拒绝的申请暂不提供重报界面，应在课程口径确认后扩展版本历史。演示地理围栏结果由模拟输入提供，不宣称连接真实定位服务。

数据库数据保护由约束、受控业务方法、历史禁止删除、已完成订单及已拨付申请的不可修改规则和审计共同完成。应用账号属于可信服务端，不向最终用户提供数据库口令；数据库超级管理员仍有管理能力，不能宣称绝对防篡改。

## 提交前必须由本人补齐

组名、4—5 名真实成员、实际分工和个人贡献、真实会议与协作记录、全员发言的答辩录像、教师提问后的反馈。模板中的这些项目不会伪造。报告中的待确认位置必须在提交前处理。
