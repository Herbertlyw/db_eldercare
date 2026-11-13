from db import sql,ROOT,CONFIG
import json,datetime,subprocess,os
out=ROOT/'evidence'
(out/'database-version.txt').write_text(sql('SELECT VERSION(),@@transaction_isolation,@@time_zone;').stdout)
tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='eldercare' AND table_type='BASE TABLE' ORDER BY table_name;").stdout.splitlines()[1:]
counts={t:int(sql(f'SELECT COUNT(*) FROM `{t}`;').stdout.splitlines()[-1]) for t in tables}
(out/'row-counts.json').write_text(json.dumps(counts,indent=2))
(out/'schema-columns.tsv').write_text(sql("SELECT table_name,column_name,column_type,is_nullable,column_default,column_key,extra FROM information_schema.columns WHERE table_schema='eldercare' ORDER BY table_name,ordinal_position;").stdout)
(out/'foreign-keys.tsv').write_text(sql("SELECT table_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema='eldercare' AND referenced_table_name IS NOT NULL ORDER BY table_name,column_name;").stdout)
(out/'objects.tsv').write_text(sql("SELECT 'VIEW' kind,table_name name FROM information_schema.views WHERE table_schema='eldercare' UNION ALL SELECT 'PROCEDURE',routine_name FROM information_schema.routines WHERE routine_schema='eldercare' UNION ALL SELECT 'TRIGGER',trigger_name FROM information_schema.triggers WHERE trigger_schema='eldercare';").stdout)
(out/'check-constraints.tsv').write_text(sql("SELECT tc.table_name,cc.constraint_name,cc.check_clause FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema='eldercare' AND tc.constraint_type='CHECK' ORDER BY tc.table_name,cc.constraint_name").stdout)
qs=json.loads((ROOT/'database/queries.json').read_text());report=[]
for q in qs:
 a=sql(q['a']+';').stdout;b=sql(q['b']+';').stdout
 assert sorted(a.splitlines()[1:])==sorted(b.splitlines()[1:]),q['id']
 report.append({'id':q['id'],'title':q['title'],'equal':True,'rows':len(a.splitlines()[1:]),'A':a,'B':b,'planA':sql('EXPLAIN '+q['a']+';').stdout,'planB':sql('EXPLAIN '+q['b']+';').stdout})
(out/'query-comparison.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
# Capture index use without claiming significant timings from a small fixture.
q="SELECT id FROM service_order WHERE status='PENDING' AND starts_at>=NOW() AND starts_at<NOW()+INTERVAL 2 DAY"
(out/'index-comparison.txt').write_text('Baseline ignoring target index\n'+sql('EXPLAIN '+q.replace('service_order WHERE','service_order IGNORE INDEX (idx_order_status_time) WHERE')+';').stdout+'\nIndexed execution\n'+sql('EXPLAIN '+q+';').stdout)
print('Evidence captured:',len(tables),'tables,',len(qs),'query pairs')
