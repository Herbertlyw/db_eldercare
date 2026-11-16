from pathlib import Path
from db import sql as db_sql,ROOT,CONFIG
import json,datetime,subprocess,os,re,hashlib
database=os.environ.get('EVIDENCE_DATABASE','eldercare')
if not re.fullmatch(r'eldercare(?:_[a-z0-9_]+)?',database):
 raise SystemExit('Invalid EVIDENCE_DATABASE')
def sql(query):
 return db_sql(query,database)
out=Path(os.environ.get('EVIDENCE_DIR',str(ROOT/'evidence')))
out.mkdir(parents=True,exist_ok=True)
(out/'database-version.txt').write_text(sql('SELECT VERSION(),@@transaction_isolation,@@time_zone;').stdout)
tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;").stdout.splitlines()[1:]
counts={t:int(sql(f'SELECT COUNT(*) FROM `{t}`;').stdout.splitlines()[-1]) for t in tables}
(out/'row-counts.json').write_text(json.dumps(counts,indent=2))
(out/'schema-columns.tsv').write_text(sql("SELECT table_name,column_name,column_type,is_nullable,column_default,column_key,extra FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position;").stdout)
(out/'foreign-keys.tsv').write_text(sql("SELECT table_name,column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_name IS NOT NULL ORDER BY table_name,column_name;").stdout)
(out/'objects.tsv').write_text(sql("SELECT 'VIEW' kind,table_name name FROM information_schema.views WHERE table_schema=DATABASE() UNION ALL SELECT 'PROCEDURE',routine_name FROM information_schema.routines WHERE routine_schema=DATABASE() UNION ALL SELECT 'TRIGGER',trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE();").stdout)
(out/'check-constraints.tsv').write_text(sql("SELECT tc.table_name,cc.constraint_name,cc.check_clause FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK' ORDER BY tc.table_name,cc.constraint_name").stdout)
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

(out/'schema-indexes.tsv').write_text(sql("SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index").stdout)
(out/'capture-context.json').write_text(json.dumps({
 'time':datetime.datetime.now().isoformat(),'database':database,
 'git_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
 'working_tree_dirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)),
 'schema_sha256':hashlib.sha256((ROOT/'database/01_schema.sql').read_bytes()).hexdigest(),
 'tables':len(tables),'query_pairs':len(qs)
},ensure_ascii=False,indent=2))
