-- 可选：仅用于本项目 Seed / ScenarioCare 生成的虚构课程数据。
-- 业务库的真实证书不得执行此脚本，应按原始证书补录。
UPDATE credential x JOIN caregiver c ON c.id=x.caregiver_id JOIN account a ON a.id=c.account_id
SET x.certificate_type='养老护理员'
WHERE x.certificate_type='未登记' AND (
 (x.certificate_no=CONCAT('DEMO-CERT-',c.id) AND a.username=CONCAT('caregiver',c.id))
 OR (a.username REGEXP '^scene_caregiver[1-5]$' AND x.certificate_no REGEXP '^COURSE-SCENE-CERT-[0-4]-[0-9]+$'
     AND SUBSTRING_INDEX(SUBSTRING_INDEX(x.certificate_no,'-',4),'-',-1)+1=SUBSTRING(a.username,16)+0)
);
