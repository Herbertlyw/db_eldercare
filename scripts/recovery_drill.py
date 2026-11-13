"""Verify an actual full snapshot plus binlog recovery in eldercare_restore only.
Run against the local course MySQL 8.4 instance with the application stopped.
The snapshot's own source-data coordinates, not its completion time, anchor replay.
"""
from db import sql, CONFIG, ROOT
import subprocess, os, json, hashlib, datetime, re, uuid

out = ROOT / '.local/backups'
out.mkdir(exist_ok=True)
env = {**os.environ, 'MYSQL_PWD': CONFIG['root']}
args = [CONFIG['mysqldump'], '--no-defaults', '--socket=' + CONFIG['socket'], '-uroot',
        '--single-transaction', '--source-data=2', '--routines', '--triggers', '--events',
        '--set-gtid-purged=OFF', '--no-tablespaces', 'eldercare']
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
    sql('DROP DATABASE IF EXISTS eldercare_restore; CREATE DATABASE eldercare_restore CHARACTER SET utf8mb4;')
    sql(full.read_text(), 'eldercare_restore')
    base_count = int(sql('SELECT COUNT(*) FROM service_item;', 'eldercare_restore').stdout.splitlines()[-1])
    logs = [line.split('\t')[0] for line in sql('SHOW BINARY LOGS;').stdout.splitlines()[1:]]
    selected = logs[logs.index(logname):logs.index(end_log) + 1]
    replay = subprocess.run([CONFIG['mysqlbinlog'], '--no-defaults', '--start-position=' + str(pos),
                             '--stop-position=' + str(stop), '--rewrite-db=eldercare->eldercare_restore',
                             *[str(ROOT / '.local/native-mysql' / name) for name in selected]], capture_output=True, check=True)
    (out / 'incremental.sql').write_bytes(replay.stdout)
    sql(replay.stdout.decode(), 'eldercare_restore')
    assert int(sql(f'SELECT COUNT(*) FROM service_item WHERE id={marker_id};', 'eldercare_restore').stdout.splitlines()[-1]) == 1
    tables = sql("SELECT table_name FROM information_schema.tables WHERE table_schema='eldercare' AND table_type='BASE TABLE' ORDER BY table_name;").stdout.splitlines()[1:]
    counts = []
    for table in tables:
        source = sql(f'SELECT * FROM `{table}` ORDER BY id;').stdout
        restored = sql(f'SELECT * FROM `{table}` ORDER BY id;', 'eldercare_restore').stdout
        assert source == restored, table
        count = int(sql(f'SELECT COUNT(*) FROM `{table}`;').stdout.splitlines()[-1])
        counts.append({'table': table, 'source': count, 'restored': count, 'content_equal': True,
                       'content_sha256': hashlib.sha256(source.encode()).hexdigest()})
    for query in json.loads((ROOT / 'database/queries.json').read_text()):
        a = sql(query['a'] + ';', 'eldercare_restore').stdout.splitlines()[1:]
        b = sql(query['b'] + ';', 'eldercare_restore').stdout.splitlines()[1:]
        assert sorted(a) == sorted(b), query['id']
    report = {'time': datetime.datetime.now().isoformat(), 'backup_sha256': hashlib.sha256(full.read_bytes()).hexdigest(),
              'binlog': logname, 'end_binlog': end_log, 'start_position': pos, 'end_position': stop,
              'position_source': 'mysqldump --source-data=2 snapshot coordinates',
              'base_service_items': base_count, 'incremental_marker_recovered': True, 'counts': counts,
              'restored_query_pairs': 15, 'scope': f'full snapshot plus actual binlog insert; {len(counts)} tables compared row by row in an independent database'}
    (ROOT / 'evidence/recovery.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print('Full + incremental recovery: all', len(counts), 'table contents and 15 query pairs verified')
finally:
    sql(f'DELETE FROM service_item WHERE id={marker_id};')
