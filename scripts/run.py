#!/usr/bin/env python3
import os,json,subprocess,sys
from pathlib import Path
root=Path(__file__).resolve().parents[1];c=json.loads((root/'.local/config.json').read_text())
env={**os.environ,'DB_PASSWORD':c['app'],'FIELD_KEY':c['key'],'DEMO_PASSWORD':c['demo']}
args=['java','-jar',str(root/'target/eldercare-1.0.0.jar')]+sys.argv[1:]
raise SystemExit(subprocess.call(args,env=env,cwd=root))
