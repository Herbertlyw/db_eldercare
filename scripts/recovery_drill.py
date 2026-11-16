"""Verify a full snapshot plus binlog replay in a new, isolated restore database.
Run against the local course MySQL 8.4 instance with the application stopped.
The snapshot's own source-data coordinates, not its completion time, anchor replay.
"""
from db import sql as db_sql, CONFIG, ROOT
import subprocess, os, json, hashlib, datetime, re, uuid, time

source_database = os.environ.get('RECOVERY_SOURCE', 'eldercare')
restore_database = os.environ.get('RECOVERY_TARGET', 'eldercare_restore')
if not re.fullmatch(r'eldercare(?:_[a-z0-9_]+)?', source_database):
    raise SystemExit('Invalid RECOVERY_SOURCE')
if not re.fullmatch(r'eldercare(?:_[a-z0-9_]+)?_restore', restore_database) or source_database == restore_database:
    raise SystemExit('RECOVERY_TARGET must be a separate eldercare_*_restore database')
# Never overwrite an existing restore database or touch another running application's target.
if db_sql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{restore_database}'").stdout.splitlines()[-1] != '0':
    raise SystemExit('Restore target already exists; choose a new RECOVERY_TARGET')
def sql(query, database=None):
    return db_sql(query, database or source_database)
if sql(f"SELECT COUNT(*) FROM information_schema.processlist WHERE db='{source_database}' AND id<>CONNECTION_ID()").stdout.splitlines()[-1] != '0':
    raise SystemExit('Source database is in use; stop its application before this controlled drill')
if sql('SELECT @@binlog_format').stdout.splitlines()[-1] != 'ROW':
    raise SystemExit('This controlled recovery drill requires ROW binlog format')
started = time.monotonic()
out = ROOT / '.local/backups' / restore_database
out.mkdir(parents=True, exist_ok=True)
env = {**os.environ, 'MYSQL_PWD': CONFIG['root']}
args = [CONFIG['mysqldump'], '--no-defaults', '--socket=' + CONFIG['socket'], '-uroot',
        '--single-transaction', '--source-data=2', '--routines', '--triggers', '--events',
        '--set-gtid-purged=OFF', '--no-tablespaces', source_database]
r = subprocess.run(args, env=env, capture_output=True, check=True)
full = out / 'full.sql'
full.write_bytes(r.stdout)
match = re.search(r"(?:SOURCE|MASTER)_LOG_FILE='([^']+)', (?:SOURCE|MASTER)_LOG_POS=(\d+)", r.stdout.decode())
if not match:
    raise RuntimeError('The dump did not contain snapshot binlog coordinates')
logname, pos = match.group(1), int(match.group(2))
marker = '恢复演练-' + uuid.uuid4().hex
marker_id = int(sql("INSERT INTO service_item(name,category,unit,price,duration_minutes,subsidizable) VALUES('" + marker + "','演练','VISIT',1,1,0); SELECT LAST_INSERT_ID();").stdout.splitlines()[-1])
try:
    end = sql('SHOW BINARY LOG STATUS;').stdout.splitlines()[1].split('\t')
    end_log, stop = end[0], int(end[1])
    sql(f'CREATE DATABASE `{restore_database}` CHARACTER SET utf8mb4;')
    sql(full.read_text(), restore_database)
    base_count = int(sql('SELECT COUNT(*) FROM service_item;', restore_database).stdout.splitlines()[-1])
    logs = [line.split('\t')[0] for line in sql('SHOW BINARY LOGS;').stdout.splitlines()[1:]]
    selected = logs[logs.index(logname):logs.index(end_log) + 1]
    replay = subprocess.run([CONFIG['mysqlbinlog'], '--no-defaults', '--start-position=' + str(pos),
                             '--stop-position=' + str(stop), f'--rewrite-db={source_database}->{restore_database}',
                             '--database=' + restore_database,
                             *[str(ROOT / '.local/native-mysql' / name) for name in selected]], capture_output=True, check=True)
    (out / 'incremental.sql').write_bytes(replay.stdout)
    sql(replay.stdout.decode(), restore_database)
    assert int(sql(f'SELECT COUNT(*) FROM service_item WHERE id={marker_id};', restore_database).stdout.splitlines()[-1]) == 1
    tables = sql("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;").stdout.splitlines()[1:]
    counts = []
    for table in tables:
        source = sql(f'SELECT * FROM `{table}` ORDER BY id;').stdout
        restored = sql(f'SELECT * FROM `{table}` ORDER BY id;', restore_database).stdout
        assert source == restored, table
        count = int(sql(f'SELECT COUNT(*) FROM `{table}`;').stdout.splitlines()[-1])
        counts.append({'table': table, 'source': count, 'restored': count, 'content_equal': True,
                       'content_sha256': hashlib.sha256(source.encode()).hexdigest()})
    for query in json.loads((ROOT / 'database/queries.json').read_text()):
        a = sql(query['a'] + ';', restore_database).stdout.splitlines()[1:]
        b = sql(query['b'] + ';', restore_database).stdout.splitlines()[1:]
        assert sorted(a) == sorted(b), query['id']
    report = {'time': datetime.datetime.now().isoformat(), 'source_database': source_database, 'restore_database': restore_database, 'elapsed_seconds': round(time.monotonic()-started,2), 'backup_sha256': hashlib.sha256(full.read_bytes()).hexdigest(),
              'binlog': logname, 'end_binlog': end_log, 'start_position': pos, 'end_position': stop,
              'position_source': 'mysqldump --source-data=2 snapshot coordinates',
              'binlog_database_filter': restore_database,
              'base_service_items': base_count, 'incremental_marker_recovered': True, 'counts': counts,
              'restored_query_pairs': 15, 'scope': f'full snapshot plus actual binlog insert; {len(counts)} tables compared row by row in an independent database'}
    (ROOT / 'evidence/recovery.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print('Full + incremental recovery: all', len(counts), 'table contents and 15 query pairs verified')
finally:
    sql(f'DELETE FROM service_item WHERE id={marker_id};')
