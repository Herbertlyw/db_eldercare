"""Build report figures from the verified schema and explicit business flows."""
from pathlib import Path
import csv, json, html
R=Path(__file__).resolve().parents[1]; OUT=R/'docs/diagrams'
cols=[{k.lower():v for k,v in r.items()} for r in csv.DictReader((R/'evidence/schema-columns.tsv').open(),delimiter='\t')]
cols=[c for c in cols if not c['table_name'].startswith('v_')]
fks=[{k.lower():v for k,v in r.items()} for r in csv.DictReader((R/'evidence/foreign-keys.tsv').open(),delimiter='\t')]
cn=json.loads((OUT/'diagram-index.json').read_text())['tables']
groups={
 'A':('档案与评估',['elder','elder_tag','guardian','assessment','care_bundle','bundle_item','care_plan','plan_item','service_item']),
 'B':('下单与派单',['elder','service_item','service_order','dispatch','service_record','attendance','complaint','complaint_log','shift']),
 'C':('护工与排班',['organization','account','caregiver','credential','caregiver_area','community','service_item','shift','attendance','dispatch','recruitment','training','training_record']),
 'D':('监测与应急',['elder','device','device_binding','metric_rule','vital','emergency','emergency_log','notification','event_step']),
 'E':('补贴与监管',['elder','service_order','policy','claim','claim_item','claim_review','payment','inspection','policy_area','claim_evidence','inspection_batch','rectification_submission','organization_report','public_notice'])}
INK='#223747'; GREEN='#24695D'; GREY='#6B7B87'; LINE='#9AAAB2'; PALE='#EDF4F1'; ORANGE='#A16B38'
class SVG:
 def __init__(self,w,h):
  self.parts=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">','<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M1,1 L8,5 L1,9" fill="none" stroke="#6B7B87" stroke-width="1.4"/></marker></defs>',f'<rect width="{w}" height="{h}" fill="white"/>']
 def text(self,x,y,s,size=23,color=INK,weight=400,anchor='start'):
  for i,line in enumerate(s.split('\n')):self.parts.append(f'<text x="{x}" y="{y+i*size*1.4}" font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" fill="{color}">{html.escape(line)}</text>')
 def rect(self,x,y,w,h,fill='white',stroke=LINE,rx=0):self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>')
 def path(self,d,arrow=False,dash=False,color=GREY):self.parts.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2"'+(' marker-end="url(#arrow)"' if arrow else '')+(' stroke-dasharray="6 5"' if dash else '')+'/>')
 def actor(self,x,y,label):
  self.parts.append(f'<circle cx="{x}" cy="{y}" r="12" fill="white" stroke="{INK}" stroke-width="2"/>');self.path(f'M{x},{y+12} v35 M{x-23},{y+28} h46 M{x},{y+47} l-19,26 M{x},{y+47} l19,26',color=INK);self.text(x,y+105,label,22,anchor='middle')
 def save(self,name):(OUT/(name+'.svg')).write_text('\n'.join(self.parts+['</svg>']))
usecases={
 'A':(['社区管理员','家属'],['建立档案与维护标签','登记评估与复评提醒','制定、调整和暂停计划','维护家属及联系方式','查看评估、频次和计划'],[(0,0),(0,1),(0,2),(0,3),(1,4)]),
 'B':(['家属','社区管理员','护工','机构负责人'],['电话或家属预约','推荐、派单与保留改派历史','接单、签到签退及记录服务','确认完成并评价','受理、回复及结案投诉','处理超时订单和退回派单'],[(0,0),(1,0),(1,1),(2,2),(0,3),(0,4),(1,4),(3,4),(1,5),(3,5)]),
 'C':(['机构负责人','护工'],['招聘申请与录用','培训安排、报名及成绩','管理证书和服务区域','安排班次与接单名额','签到签退与工时复核','查询月度工时与服务评分'],[(0,0),(0,1),(0,2),(0,3),(0,4),(1,1),(1,3),(1,4),(1,5)]),
 'D':(['社区管理员','护工','家属'],['设备登记、绑定与解绑','配置指标及定时模拟采样','查看体征、预警和通知','确认异常或登记误报','响应、登记到场与处置结果','未及时响应时自动升级'],[(0,0),(0,1),(0,3),(0,4),(1,2),(1,3),(1,4),(2,2),(0,5)]),
 'E':(['社区管理员','街道审核人员','民政监管人员','机构负责人'],['维护政策及适用辖区','周期申报与证明归档','分级审核、补件和拨付','按比例抽查与登记结果','提交整改、退回和复核','提交机构考核材料','公示抽查结论','查看辖区覆盖率及响应情况'],[(2,0),(0,1),(0,2),(1,2),(2,2),(2,3),(2,4),(3,4),(2,5),(3,5),(2,6),(1,7),(2,7)])}
for k,(actors,uses,edges) in usecases.items():
 height=110+90*len(uses);s=SVG(1200,height);s.rect(290,25,880,height-45);s.text(315,62,groups[k][0],25,GREEN,600)
 ys=[80+i*(height-200)/max(1,len(actors)-1) for i in range(len(actors))];centers=[(775,108+i*90) for i in range(len(uses))]
 for a,u in edges:
  ux,uy=centers[u];s.path(f'M170,{ys[a]+30} L{ux-290},{uy}',color='#C0CCD2')
 for i,a in enumerate(actors):s.actor(135,ys[i],a)
 for (x,y),u in zip(centers,uses):
  s.parts.append(f'<ellipse cx="{x}" cy="{y}" rx="290" ry="31" fill="{PALE}" stroke="{GREEN}" stroke-width="1.5"/>');s.text(x,y+8,u,24,anchor='middle')
 s.save(k+'-usecase')
robust={
 'A':('社区管理员',[('建档页面','身份查重与家属关联','老人档案 · 家属关联'),('评估页面','评估有效期检查','能力评估'),('计划页面','能力匹配与区间校验','照护计划 · 计划明细')]),
 'B':('社区管理员／护工／家属',[('派单入口','资格检查与名额锁定','订单 · 排班 · 派单'),('服务记录入口','接单状态与工时检查','派单 · 工时 · 服务记录'),('家属确认入口','关联关系与凭据检查','服务记录 · 订单')]),
 'C':('机构负责人／护工',[('资质管理页面','项目与资质有效期检查','护工 · 项目资质'),('排班页面','班次重叠与容量检查','护工 · 排班'),('打卡与复核入口','时段校验与工时认定','派单 · 工时记录')]),
 'D':('社区管理员／护工',[('采样上传入口','采样时绑定与阈值检查','绑定历史 · 规则 · 体征'),('事件响应入口','响应状态与处置校验','应急事件 · 升级记录'),('超时任务入口','超时检查与分级通知','应急事件 · 通知')]),
 'E':('社区管理员／审核人员',[('申报页面','资格核验与逐单核算','政策 · 订单 · 申报明细'),('分级审核入口','街道初审与民政终审','申报 · 审核记录 · 拨付'),('抽查整改入口','整改状态与接单限制','质量抽查 · 护工')])}
for k,(actor,rows) in robust.items():
 s=SVG(1000,540);s.text(20,31,groups[k][0]+'的主要协作',24,INK,600)
 for x,title in [(75,'参与者'),(260,'«boundary»'),(535,'«control»'),(850,'«entity»')]:s.text(x,78,title,22,GREY,anchor='middle')
 role_rows={'A':['社区管理员']*3,'B':['社区管理员','护工','家属'],'C':['机构负责人','机构负责人','护工／负责人'],'D':['社区管理员','管理员／护工','定时任务'],'E':['社区管理员','街道／民政','民政监管人员']}
 for i,(b,c,e) in enumerate(rows):
  y=115+i*125;s.actor(75,y,role_rows[k][i])
  for x,w,label in [(165,190,b),(405,260,c),(715,270,e)]:s.rect(x,y+7,w,75,PALE if x==405 else 'white',GREEN if x==405 else LINE,6);s.text(x+w/2,y+53,label,20,anchor='middle')
  s.path(f'M100,{y+30} H160',True);s.path(f'M355,{y+44} H400',True);s.path(f'M665,{y+44} H710',True)
 s.text(25,520,'边界接收请求，控制组织校验和事务，实体保存业务记录。',21,GREY);s.save(k+'-robustness')
def uml(tables,name,global_diagram=False):
 s='digraph G { graph [rankdir=TB,bgcolor="white",nodesep=0.45,ranksep=0.65,pad=0.18,splines=polyline,outputorder=edgesfirst]; node [fontname="Noto Sans CJK SC",fontsize=16]; edge [fontname="Noto Sans CJK SC",fontsize=11,color="#80949e",arrowsize=0.5,penwidth=1.1,labeldistance=1.9];\n'
 for t in tables:
  fields=[c for c in cols if c['table_name']==t and (c['column_key']=='PRI' or any(f['table_name']==t and f['column_name']==c['column_name'] for f in fks))];rows=[]
  for c in fields:
   fk=next((f for f in fks if f['table_name']==t and f['column_name']==c['column_name']),None);mark='PK' if c['column_key']=='PRI' else 'FK';label=c['column_name']+(' ↗' if fk and fk['referenced_table_name'] not in tables else '')
   rows.append(f'<TR><TD ALIGN="LEFT" WIDTH="40"><FONT COLOR="#697C88">{mark}</FONT></TD><TD ALIGN="LEFT" WIDTH="{max(165,len(label)*9+20)}">{html.escape(label)}</TD></TR>')
  if t=='audit_log':rows.append('<TR><TD COLSPAN="2">table_name · row_id</TD></TR>')
  s+=f'{t} [shape=plain,label=<<TABLE BORDER="1" COLOR="#9AAAB2" CELLBORDER="0" CELLSPACING="0" CELLPADDING="7"><TR><TD COLSPAN="2" WIDTH="{max(205,len(cn[t])*20+30,len(t)*9+30)}" BGCOLOR="#EDF4F1"><FONT POINT-SIZE="19"><B>{cn[t]}</B></FONT><BR/><FONT POINT-SIZE="14">{t}</FONT></TD></TR>{"".join(rows)}</TABLE>>];\n'
 for f in fks:
  if f['table_name'] in tables and f['referenced_table_name'] in tables:
   c=next(c for c in cols if c['table_name']==f['table_name'] and c['column_name']==f['column_name'])
   s+=f'{f["referenced_table_name"]} -> {f["table_name"]} [arrowhead=none,taillabel="{"0..1" if c["is_nullable"]=="YES" else "1"}",headlabel="{"0..1" if c["column_key"]=="UNI" else "0..*"}"];\n'
 ranks={'A':[['elder','care_bundle','service_item'],['elder_tag','guardian','assessment','bundle_item'],['care_plan'],['plan_item']], 'B':[['elder','service_item','shift'],['service_order'],['dispatch','complaint'],['service_record','attendance']], 'C':[['community','service_item'],['organization'],['account','caregiver_area'],['caregiver'],['credential','shift'],['dispatch'],['attendance']], 'D':[['elder','device','metric_rule'],['device_binding'],['vital'],['emergency'],['emergency_log','notification','event_step']], 'E':[['elder','service_order','policy'],['claim'],['claim_item','claim_review','payment','inspection']]}
 if name[0] in ranks and not global_diagram:
  for row in ranks[name[0]]:
   included=[t for t in row if t in tables]
   if included:s+='{rank=same;'+ ';'.join(included)+';}\n'
 if global_diagram:s+='{rank=same;community;service_item;care_bundle;device;metric_rule;audit_log;}\n'
 (OUT/(name+'.dot')).write_text(s+'}\n')
uml(list(cn),'global-uml',True)
for key,(_,tables) in groups.items():
 if key=='A':
  uml(['elder','elder_tag','guardian','assessment','care_plan'],'A-uml')
  uml(['care_bundle','bundle_item','service_item','care_plan','plan_item'],'A-bundle-uml')
 elif key=='C':uml(['caregiver','credential','caregiver_area','community','service_item','shift'],'C-uml')
 elif key=='E':
  uml(['elder','claim','claim_review','payment'],'E-uml')
  uml(['claim','claim_item','policy','service_order','inspection'],'E-detail-uml')
 elif key=='B':uml(['elder','service_item','service_order','dispatch','service_record','attendance','shift'],key+'-uml')
 else:uml(tables,key+'-uml')
uml(['community','organization','account','account_scope','audit_log'],'foundation-uml')
uml(['service_order','complaint','complaint_log'],'B-feedback-uml')
uml(['organization','caregiver','recruitment','training','training_record'],'C-training-uml')
uml(['policy','policy_area','community','service_item'],'E-policy-uml')
uml(['inspection','inspection_batch','rectification_submission','public_notice','organization_report','organization','caregiver','service_order'],'E-quality-uml')
uml(['claim','claim_item','claim_evidence','claim_review','payment'],'E-evidence-uml')
s=SVG(1000,990);s.text(20,38,'全局数据模型分区',30,INK,600);s.text(20,75,'47 张基础表按主要用途归类；跨模块通过外键关联。',23,GREY)
partitions=[('公共基础','community · organization · account\naccount_scope · service_item · audit_log',6),('A  档案与评估','elder · elder_tag · guardian · assessment\ncare_bundle · bundle_item · care_plan · plan_item',8),('B  下单与派单','service_order · dispatch · service_record\ncomplaint · complaint_log',5),('C  护工与排班','caregiver · credential · caregiver_area · shift\nattendance · recruitment · training · training_record',8),('D  监测与应急','device · device_binding · metric_rule · vital\nemergency · emergency_log · notification · event_step',8),('E  补贴与监管','policy · policy_area · claim · claim_item · claim_review\npayment · claim_evidence · inspection · inspection_batch\nrectification_submission · organization_report · public_notice',12)]
for i,(title,items,count) in enumerate(partitions):
 y=112+i*138;s.rect(20,y,960,121);s.rect(20,y,245,121,PALE,'none');s.text(42,y+42,title,24,GREEN,600);s.text(42,y+83,f'{count} 张表',21,GREY);s.text(285,y+32,items,21)
s.text(20,975,'关联主线：老人 → 评估／计划 → 服务订单 → 派单／工时 → 补贴明细',22,GREEN,600);s.save('model-overview')
s=SVG(1000,470);s.text(20,35,'派单与改派的事务边界',28,INK,600)
steps=[('锁定数据','订单和护工记录'),('检查资格','资质、区域、时段'),('调整派单','释放旧名额并保留历史'),('写入结果','条件更新班次并写派单')]
for i,(a,b) in enumerate(steps):
 x=25+i*246;s.rect(x,110,220,165,PALE,GREEN,5);s.text(x+18,148,f'0{i+1}',22,GREY);s.text(x+18,193,a,27,INK,600);s.text(x+18,237,b,18)
 if i<3:s.path(f'M{x+220},193 H{x+241}',True)
s.path('M135,286 V333 H870 V286',False,True);s.text(500,370,'任一步失败 → 整体回滚，旧派单和占用名额一并恢复',25,ORANGE,600,'middle');s.text(500,427,'校验通过后统一提交；两个订单竞争最后一个名额时，只允许一个成功。',22,GREY,anchor='middle');s.save('dispatch-transaction')
(OUT/'diagram-index.json').write_text(json.dumps({'tables':cn,'groups':groups,'usecases':{k:v[:2] for k,v in usecases.items()}},ensure_ascii=False,indent=2))
print('Built UML sources, ten business diagrams and two overview diagrams')
for key in groups:
 for suffix in ['-usecase.dot','-robustness.dot']:
  (OUT/(key+suffix)).unlink(missing_ok=True)

for name,title,labels in [('B-complaint-flow','投诉办理过程',['家属提交问题','社区受理并转机构','经办单位调查回复','家属或社区确认结案']),('E-quality-flow','抽查、整改与公示',['监管按比例抽查','机构提交整改材料','监管复核通过','发布脱敏质量公示'])]:
 s=SVG(1200,390);s.text(25,40,title,30,INK,600)
 for i,label in enumerate(labels):
  x=25+i*295;s.rect(x,125,270,100,PALE,GREEN,4);s.text(x+135,181,label,24,anchor='middle')
  if i<3:s.path(f'M{x+270},175 H{x+290}',True)
 if name.startswith('E'):
  s.path('M750,240 V285 H455 V240',True,True);s.text(600,330,'复核退回：保留原材料，机构补充后再次提交。',23,ORANGE,anchor='middle')
 else:s.text(600,310,'每个步骤保存经办账号、时间和说明；评价单独登记。',23,GREY,anchor='middle')
 s.save(name)
