-- 停应用、备份并在副本验证；不修改已有密码及角色。
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account' AND column_name='password_version')=0,
 'ALTER TABLE account ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0 AFTER password_hash','DO 0');
PREPARE password_stmt FROM @ddl; EXECUTE password_stmt; DEALLOCATE PREPARE password_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='account' AND column_name='must_change_password')=0,
 'ALTER TABLE account ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER password_version','DO 0');
PREPARE password_stmt FROM @ddl; EXECUTE password_stmt; DEALLOCATE PREPARE password_stmt;
SET @ddl=NULL;
