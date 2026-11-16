# 数据库迁移操作说明

新安装只在空库依次执行 `database/01_schema.sql`、`02_routines.sql`、`03_views.sql`，再初始化种子数据。下面的脚本只适用于相应的已有结构。数据库结构与代码必须匹配；无法确定当前结构时先检查表、字段、例程和触发器，不试着把全部迁移顺序执行一遍。

| 文件 | 适用条件 | 执行注意事项 |
| --- | --- | --- |
| 20261112_integrity.sql | 35 表结构中需要微秒绑定、打卡时间及完整性例程的数据库 | 检查 device_binding、attendance 字段及脚本所依赖的视图；须在副本演练 |
| 20261113_completion.sql | 35 张基础表、尚无招聘培训等关系的结构 | 执行一次；文件中包含例程和视图定义。与当前源码配套时再应用 history_rules 脚本。开发机编排见 scripts/migrate_completion.py |
| 20261113_history_rules.sql | 47 表结构、需要装配对应历史保护例程的数据库 | 替换 11 个存储对象，不重建业务表；使用匹配 JAR |
| 20261114_acceptance.sql | 当前 47 表结构 | 增加证书类型，历史值标为未登记；移除两个历史遗留索引；允许重复执行 |
| 20261114_demo_credential_types.sql | 可选，仅 Seed / ScenarioCare 的虚构课程数据 | 在 acceptance 后执行，按模拟账号及证书编号识别示例；真实业务库不执行 |
| 20261116_password_management.sql | 当前 47 表结构 | 增加密码版本和首次改密标志；不改变原有密码、角色和启用状态；允许重复执行 |
| 20261116_seed_names.sql | 可选，仅内置课程数据 | 为社区、机构、服务项目、照护包、政策、设备与培训采用自然名称；按来源标识和旧值匹配，保留人工编辑；不改账号、业务状态或历史凭证。先备份并在副本演练，可重复执行 |

## 操作顺序

1. 确认数据库主机、端口、库名及结构，停止应用和业务写入。
2. 保存包含表、数据、过程和触发器的全量备份，保管字段密钥；备份不提交 Git。
3. 将备份恢复到独立副本，先演练适用迁移。开发机 Python 编排脚本依赖本机配置，其他成员不能照搬连接信息。
4. 核对行数、既有列逐行内容、主外键、视图及例程，执行受影响流程、拒绝与回滚检查。
5. 在已授权的目标库执行同样步骤，部署匹配 JAR；确认结果后恢复业务访问。

MySQL DDL 可能隐式提交，失败后不能假定整份脚本自动回滚。停止后续写入并保留错误和当前结构，在独立库恢复备份、核对后再切换连接。不要通过删除业务库解决迁移问题。

对应演练记录：`evidence/migration-eldercare.json`、`evidence/history-migration-eldercare.json`。这些是已记录环境的结果，不能替代其他机器的实际升级验证。

本地验收迁移证据见 `evidence/acceptance-migration.json` 和 `evidence/acceptance-display-migration.json`。证书类型与等级是两个字段，升级不改变原证书编号、有效期或业务记录。

## 47 表数据库与当前源码配套

1. 已有库缺少 `credential.certificate_type` 或保留历史索引时，执行 `20261114_acceptance.sql`。内置课程数据可再执行 `20261114_demo_credential_types.sql`；业务人员实际登记的资料不按示例补录。
2. 缺少 `account.password_version`、`account.must_change_password` 时，执行 `20261116_password_management.sql`，保留账号密码、角色与启用状态。
3. 内置名称可选执行 `20261116_seed_names.sql`。该脚本按原占位值与来源标识匹配，不覆盖人工更名，不改历史评估、审核日志与已确认凭证。
4. 构建并启动当前源码对应的 JAR。新安装直接使用当前 `01_schema.sql`、`02_routines.sql`、`03_views.sql` 和 Seed，不额外重放旧版本迁移。

上述步骤都需先备份并在副本演练。名称迁移的 47 表逐字段核对、重复执行与人工编辑保护记录见 `evidence/natural-names-migration.json`，本地应用记录见 `evidence/natural-names-display.json`；密码迁移记录见 `evidence/password-migration.json`。React 构建本身不修改 MySQL 数据。
