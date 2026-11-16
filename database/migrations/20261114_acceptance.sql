-- 已有库升级；停应用、备份并在副本验证后执行。重复执行不会覆盖已登记类型。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='credential' AND column_name='certificate_type')=0,
 'ALTER TABLE credential ADD COLUMN certificate_type VARCHAR(80) NOT NULL DEFAULT ''未登记'' CHECK(CHAR_LENGTH(TRIM(certificate_type))>0) AFTER certificate_no', 'DO 0');
PREPARE acceptance_stmt FROM @ddl;
EXECUTE acceptance_stmt;
DEALLOCATE PREPARE acceptance_stmt;
ALTER TABLE credential ALTER COLUMN certificate_type DROP DEFAULT;
-- 当前建库脚本以 idx_plan_assessment 支持按评估定位计划；移除历史遗留索引。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='care_plan' AND index_name='idx_plan_elder')>0,
 'ALTER TABLE care_plan DROP INDEX idx_plan_elder', 'DO 0');
PREPARE acceptance_stmt FROM @ddl;
EXECUTE acceptance_stmt;
DEALLOCATE PREPARE acceptance_stmt;
SET @ddl = NULL;
-- 旧 dispatch 表规范化后残留的单列状态索引，当前结构使用 idx_dispatch_shift。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='dispatch' AND index_name='idx_dispatch_worker')>0,
 'ALTER TABLE dispatch DROP INDEX idx_dispatch_worker', 'DO 0');
PREPARE acceptance_stmt FROM @ddl;
EXECUTE acceptance_stmt;
DEALLOCATE PREPARE acceptance_stmt;
SET @ddl = NULL;
