from pathlib import Path
import csv,json,html
root=Path(__file__).resolve().parents[1]
cols=[{k.lower():v for k,v in r.items()} for r in csv.DictReader((root/'evidence/schema-columns.tsv').open(),delimiter='\t')];cols=[c for c in cols if not c['table_name'].startswith('v_')]
fks=[{k.lower():v for k,v in r.items()} for r in csv.DictReader((root/'evidence/foreign-keys.tsv').open(),delimiter='\t')]
cn=dict(zip(['community','organization','account','elder','elder_tag','guardian','assessment','service_item','care_bundle','bundle_item','caregiver','credential','caregiver_area','care_plan','plan_item','shift','service_order','dispatch','service_record','attendance','device','device_binding','metric_rule','vital','emergency','emergency_log','notification','policy','claim','claim_item','claim_review','payment','inspection','complaint','audit_log'],['社区','服务机构','账号','老人档案','老人标签','家属关联','能力评估','服务项目','照护服务包','服务包明细','护工','项目资质','护工服务区域','照护计划','计划明细','排班','服务订单','派单记录','服务与确认','工时记录','设备','绑定历史','指标规则','体征数据','应急事件','升级记录','通知','补贴政策','补贴申报','申报明细','审核记录','拨付','质量抽查','评价投诉','操作审计']))
groups={
 'A':('档案与评估',['elder','elder_tag','guardian','assessment','care_bundle','bundle_item','care_plan','plan_item','account']),
 'B':('下单与派单',['elder','service_item','service_order','dispatch','service_record','complaint','shift']),
 'C':('护工与排班',['organization','account','caregiver','credential','caregiver_area','community','shift','attendance','dispatch']),
 'D':('监测与应急',['elder','device','device_binding','metric_rule','vital','emergency','emergency_log','notification']),
 'E':('补贴与监管',['elder','service_order','policy','claim','claim_item','claim_review','payment','inspection','account'])}
base='digraph G { graph [rankdir=LR, bgcolor="white",nodesep=0.35,ranksep=0.7]; node [fontname="Noto Sans CJK SC",fontsize=14,color="#31574e"]; edge [fontname="Noto Sans CJK SC",fontsize=10,color="#719087",arrowsize=0.6];\n'
def uml(tables,name,compact=False):
 s=base
 for t in tables:
  fields=[c for c in cols if c['table_name']==t and (c['column_key'] in ['PRI','UNI'] or any(f['table_name']==t and f['column_name']==c['column_name'] for f in fks))]
  width=max(150,int(max(sum(1 if ord(ch)>127 else 0.58 for ch in value) for value in [cn[t],t]+[c['column_name']+'  FK' for c in fields])*16+30))
  rows=''.join('<TR><TD ALIGN="LEFT" WIDTH="'+str(width)+'">'+html.escape(c['column_name'])+('  PK' if c['column_key']=='PRI' else '  FK' if any(f['table_name']==t and f['column_name']==c['column_name'] for f in fks) else '  UQ')+'</TD></TR>' for c in fields[:(2 if compact else 7)])
  s+=f'{t} [shape=plain,label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="6"><TR><TD BGCOLOR="#e7efea" WIDTH="{width}"><B>{cn[t]}</B><BR/>{t}</TD></TR>{rows}</TABLE>>];\n'
 for f in fks:
  if f['table_name'] in tables and f['referenced_table_name'] in tables:
   col=next(c for c in cols if c['table_name']==f['table_name'] and c['column_name']==f['column_name'])
   s+=f"{f['referenced_table_name']} -> {f['table_name']} [arrowhead=none,taillabel=\"{'0..1' if col['is_nullable']=='YES' else '1'}\",headlabel=\"{'0..1' if col['column_key']=='UNI' else '0..*'}\"];\n"
 (root/'docs/diagrams'/f'{name}.dot').write_text(s+'}\n')
uml(list(cn),'global-uml',True)
usecases={
'A':(['社区管理员','家属'],['建立档案与家属关联','登记能力评估','制定照护计划','查询评估与计划']),
'B':(['社区管理员','家属','护工'],['预约服务','派单与改派','接单与服务记录','确认服务及评价']),
'C':(['机构负责人','护工'],['登记职业资质','安排班次','签到签退','复核异常工时']),
'D':(['社区管理员','护工','家属'],['绑定设备与上传数据','查看异常信息','响应应急事件','回填处置结果']),
'E':(['社区管理员','街道审核人员','民政监管人员'],['提交补贴申报','街道初审','民政审核与拨付','质量抽查与整改'])}
controls={'A':['档案唯一性校验','评估与计划有效期校验'],'B':['派单资格与名额事务','服务确认校验'],'C':['排班重叠检查','工时核验及复核'],'D':['绑定区间与阈值检查','响应时限与升级'],'E':['申报资格与金额核算','分级审核状态控制']}
for key,(name,tables) in groups.items():
 uml(tables,key+'-uml')
 actors,uses=usecases[key]
 s=base
 for i,a in enumerate(actors):s+=f'a{i} [shape=plaintext,label="«actor»\\n{a}"];\n'
 s+='subgraph cluster_system { label="'+name+'";color="#9cae9f";'
 for i,u in enumerate(uses):s+=f'u{i} [shape=ellipse,label="{u}"];'
 s+='}\n'
 edges={'A':[(0,0),(0,1),(0,2),(1,3)],'B':[(0,0),(0,1),(1,0),(1,3),(2,2)],'C':[(0,0),(0,1),(0,3),(1,2)],'D':[(0,0),(0,2),(0,3),(1,2),(1,3),(2,1)],'E':[(0,0),(1,1),(2,2),(2,3)]}[key]
 for a,u in edges:s+=f'a{a}->u{u} [arrowhead=none];'
 (root/'docs/diagrams'/f'{key}-usecase.dot').write_text(s+'}')
 s=base
 s+=f'actor [shape=plaintext,label="«actor»\\n{actors[0]}"];boundary [shape=box,margin="0.35,0.2",label="«boundary»\\n{name}操作入口"];actor->boundary;'
 for i,c in enumerate(controls[key]):s+=f'control{i} [shape=ellipse,label="«control»\\n{c}"];boundary->control{i};'
 core={'A':['elder','guardian','assessment','care_plan','plan_item'],'B':['service_order','dispatch','shift','service_record'],'C':['caregiver','shift','dispatch','attendance'],'D':['device_binding','vital','metric_rule','emergency','emergency_log'],'E':['policy','claim','claim_item','claim_review','payment','inspection']}[key]
 for i,t in enumerate(core):s+=f'e{i} [shape=box,margin="0.35,0.2",label="«entity»\\n{cn[t]}"];control{0 if i<2 else 1}->e{i};'
 (root/'docs/diagrams'/f'{key}-robustness.dot').write_text(s+'}')
(root/'docs/diagrams/diagram-index.json').write_text(json.dumps({'tables':cn,'groups':groups,'usecases':usecases},ensure_ascii=False,indent=2))
print('Generated global class diagram, 5 local class diagrams, 5 use case and 5 robustness sources')
