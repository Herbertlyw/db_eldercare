// Academic defense deck. All diagrams and tables use editable native objects.
const C={ink:'#25323A',accent:'#75583C',navy:'#274C60',muted:'#687782',line:'#C4CDD1',light:'#F1F4F5',blue:'#1565C0',green:'#185B44'};
function tx(s,v,x,y,w,h,size=26,color=C.ink,bold=false,align='left',face='Microsoft YaHei'){
 return s.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0},text:{text:v,style:{fontSize:size,typeface:face,color,bold,alignment:align,verticalAlignment:'top',insets:0,wrap:'square',autoFit:'none'}}});
}
function rule(s,x,y,w,color=C.line,width=1){return s.shapes.add({geometry:'line',position:{left:x,top:y,width:w,height:0},line:{fill:color,width}});}
function rect(s,x,y,w,h,fill='white',stroke=C.line){return s.shapes.add({geometry:'rect',position:{left:x,top:y,width:w,height:h},fill,line:{fill:stroke,width:1.2}});}
function node(s,label,x,y,w,h=64,fill='white',size=25){let a=rect(s,x,y,w,h,fill);tx(s,label,x+10,y+15,w-20,h-20,size,C.ink,false,'center');return a;}
function link(s,a,b,fs='right',ts='left',dashed=false){return s.shapes.connect(a,b,{kind:'straight',fromSide:fs,toSide:ts,line:{fill:C.navy,width:1.6,style:dashed?'dashed':'solid'},tail:{type:'arrow',length:'sm',width:'sm'}});}
function arrow(s,x,y,w,h=0){return s.shapes.add({geometry:'connector',kind:'straight',position:{left:x,top:y,width:w,height:h},line:{fill:C.navy,width:1.5},tail:{type:'arrow',width:'sm',length:'sm'}});}
function base(p,i,title){
 let s=p.slides.getItem(i);s.shapes.deleteAll();for(const t of [...s.tables.items])s.tables.deleteById(t.id);for(const image of [...s.images.items])image.delete();s.setViewportSize(1280,720);s.background.fill='#FFFFFF';
 tx(s,String(i).padStart(2,'0'),58,44,62,55,32,C.accent);tx(s,title,138,42,1080,61,40,C.ink,true,'left','宋体');rule(s,60,118,1160,C.ink,1.2);
 tx(s,'数据库系统课程设计',60,677,800,24,16,C.muted);tx(s,String(i+1).padStart(2,'0'),1160,675,60,27,18,C.muted,false,'right');return s;
}
function table(s,rows,x,y,width,colWidths,rowHeight=66){
 let t=s.tables.add({rows:rows.length,columns:rows[0].length,left:x,top:y,width,height:rowHeight*rows.length,columnWidths:colWidths,values:rows});
 t.borders.assign({fill:'#D8DFE2',width:0.7,style:'solid'});
 for(let r=0;r<rows.length;r++)for(let c=0;c<rows[0].length;c++){
  const cell=t.getCell(r,c);cell.fill=r===0?C.navy:r%2===0?'#F3F5F6':'#FFFFFF';
  cell.text.style={fontSize:24,typeface:'Microsoft YaHei',color:r===0?'#FFFFFF':C.ink,bold:r===0,verticalAlignment:'middle',insets:{top:10,right:15,bottom:10,left:15}};
 }return t;
}
function entity(s,title,name,keys,x,y,w=235){
 let a=rect(s,x,y,w,140);rect(s,x,y,w,43,C.light);tx(s,title,x+12,y+7,w-24,34,25,C.ink,true,'center');tx(s,name,x+12,y+52,w-24,26,20,C.muted,false,'center');rule(s,x,y+86,w);tx(s,keys,x+15,y+94,w-30,42,17,C.ink);return a;
}
function code(s,lines,x,y,w,font=23){
 lines.forEach((v,j)=>{tx(s,String(j+1).padStart(2,'0'),x,y+j*34,36,31,18,'#9BA5AC',false,'right','Courier New');tx(s,v,x+56,y+j*34,w-56,32,font,/^(SELECT|FROM|LEFT|GROUP|UPDATE|SET|WHERE)/.test(v)?C.navy:C.ink,false,'left','Courier New');});
}
export async function editDeck({presentation:p,input}){
 for(const i of input.slides){let s;
 if(i===0){
  s=base(p,0,'');s.shapes.deleteAll();s.background.fill='#FFFFFF';
  s.images.add({...(input.assets?.crest?{blob:input.assets.crest,contentType:'image/png'}:{path:'/Users/herbert/Developer/db_project/docs/assets/课程模板校徽.png'}),position:{left:76,top:58,width:78,height:78},fit:'contain'});
  tx(s,'北京林业大学',177,65,850,42,30,C.green,true,'left','宋体');tx(s,'数据库系统课程设计',177,109,850,31,21,C.muted);
  tx(s,'社区居家养老',77,255,1120,87,60,C.ink,true,'left','宋体');tx(s,'智慧服务平台',77,345,1120,87,60,C.ink,true,'left','宋体');
  tx(s,'左邻右舍 · 数据库设计与系统实现',82,456,1115,43,28,C.muted);rule(s,80,552,1120,C.navy,1.5);
  tx(s,'指导教师  张晓宇',80,589,420,33,24);tx(s,'2026 年 11 月',1000,590,200,33,23,C.ink,false,'right');
  tx(s,'小组名称、成员姓名及学号：待填写',80,638,1120,34,22,C.blue);
  s.speakerNotes.text='约 10 秒。本次课程设计的题目是社区居家养老智慧服务平台。网站名称为左邻右舍，程序使用 Java 和 MySQL，下面介绍业务需求、数据库设计和关键功能的实现。校徽来自课程报告原模板。真实成员名单须在录制前补齐。';
 }
 if(i===1){
  s=base(p,i,'需求分析');
  tx(s,'系统管理老人档案、上门服务和补贴记录，主要业务分为以下五部分。',60,153,1158,45,27);
  table(s,[['业务模块','主要操作','主要使用人员'],['档案与评估','建档、能力评估、制定照护计划','社区管理员、家属'],['服务订单','预约、派单、服务记录、家属确认','社区管理员、护工、家属'],['护工与排班','招聘培训、证书排班、工时复核','机构负责人、护工'],['监测与应急','设备绑定、体征采样、事件处置','社区管理员、护工'],['补贴与监管','周期补贴、抽查整改、辖区统计','社区、街道、民政']],60,224,1160,[235,555,370],62);
  tx(s,'主要业务顺序：建档评估 → 预约派单 → 上门服务 → 家属确认 → 补贴申报',60,620,1160,39,24,C.navy);
  s.speakerNotes.text='约 20 秒。系统围绕上门服务记录展开，覆盖五条业务线。家属查询关联老人并预约服务，管理员派单，护工记录服务，家属确认后再用于补贴申报。机构负责人员资质和班次，街道和民政分别审核。来源：任务书、application/service 下七个业务服务。';
 }
 if(i===2){
  s=base(p,i,'系统架构');
  tx(s,'Java 17   Spring Boot   Spring JDBC   MySQL',60,151,1160,36,27,C.navy);
  const rows=[
   [205,70,'接口层','interfaces/web','Api 接收 HTTP 请求，Errors 返回错误'],
   [305,85,'应用层','application','CareService / ReadService，七个业务服务与 Auth'],
   [420,85,'领域层','domain','业务状态、计算规则与 Repository 仓储接口'],
   [550,85,'基础设施层','infrastructure','Jdbc*Repository、Store、查询适配器与定时任务']
  ];
  let boxes=[];
  for(const [y,h,title,path,detail] of rows){
   boxes.push(rect(s,60,y,1160,h,'#F7F9FA'));
   tx(s,title,78,y+12,218,35,28,C.navy,true);tx(s,path,78,y+47,218,26,19,C.muted);
   tx(s,detail,325,y+24,870,42,26);
  }
  link(s,boxes[0],boxes[1],'bottom','top');link(s,boxes[1],boxes[2],'bottom','top');
  link(s,boxes[3],boxes[2],'top','bottom',true);
  tx(s,'JDBC 实现领域仓储接口，SQL 在基础设施中执行',669,513,551,29,20,C.muted);
  s.speakerNotes.text='约 25 秒。程序采用分层单体。接口层处理请求和错误，应用层安排事务、授权和业务步骤，领域层保存状态及计算规则。基础设施实现仓储接口，通过 JDBC 访问 MySQL。图中向上的虚线表示接口实现，不代表领域代码依赖 JDBC。数据库继续执行跨行约束、过程和触发器。来源：src/main/java/edu/bjfu/eldercare 四层包及 docs/Java架构与开发约定.md。';
 }
 if(i===3){
  s=base(p,i,'概念结构设计');
  tx(s,'系统包含 47 张基础表，下图展示计划和派单的主要实体关系。',60,151,1158,42,27);
  const a=entity(s,'老人档案','elder','PK  id',60,233),b=entity(s,'能力评估','assessment','FK  elder_id',460,233),c=entity(s,'照护计划','care_plan','FK  assessment_id',860,233);
  const d=entity(s,'服务订单','service_order','FK  elder_id',60,472),e=entity(s,'派单记录','dispatch','FK  order_id · shift_id',460,472),f=entity(s,'护工排班','shift','FK  caregiver_id',860,472);
  link(s,a,b);link(s,b,c);link(s,a,d,'bottom','top');link(s,d,e);link(s,f,e,'left','right');
  for(const [x,y,t] of [[331,268,'1 : N'],[732,268,'1 : N'],[331,507,'1 : N'],[732,507,'N : 1'],[193,411,'1 : N']])tx(s,t,x,y,99,28,21,C.navy);
  tx(s,'计划引用具体评估；订单保留多次派单记录。完整主外键及可选性见报告 UML 图。',60,632,1158,33,23,C.muted);
  s.speakerNotes.text='约 20 秒。一个老人可有多次评估，计划引用其中一次评估。订单与派单为一对多，从而保留改派历史；派单引用班次，班次决定护工。此页用 1:N 简写关系，零条记录的可选性及全部外键在报告图中展开。来源：01_schema.sql、foreign-keys.tsv。';
 }
 if(i===4){
  s=base(p,i,'逻辑结构设计');
  tx(s,'归属关系由外键确定，基础表避免重复保存可以推导的字段。',60,151,1158,42,27);
  const r1=[node(s,'照护计划\ncare_plan',60,242,260,98,'white',25),node(s,'能力评估\nassessment',490,242,260,98,'white',25),node(s,'老人档案\nelder',920,242,260,98,'white',25)];link(s,r1[0],r1[1]);link(s,r1[1],r1[2]);tx(s,'assessment_id',334,253,151,31,20,C.muted);tx(s,'elder_id',791,253,126,31,20,C.muted);
  tx(s,'care_plan 不重复保存 elder_id，老人归属通过 assessment 查询。',60,365,1158,40,25);
  const r2=[node(s,'派单记录\ndispatch',60,450,260,98,'white',25),node(s,'护工排班\nshift',490,450,260,98,'white',25),node(s,'护工\ncaregiver',920,450,260,98,'white',25)];link(s,r2[0],r2[1]);link(s,r2[1],r2[2]);tx(s,'shift_id',351,461,130,31,20,C.muted);tx(s,'caregiver_id',776,461,145,31,20,C.muted);
  tx(s,'dispatch 不重复保存 caregiver_id，护工归属通过 shift 查询。',60,575,1158,40,25);
  s.speakerNotes.text='约 15 秒。这两处对应第三范式分析。计划引用评估以后，老人字段可通过评估得到；派单引用班次以后，护工字段可通过班次得到。两个内部视图根据外键关联提供业务查询所需的归属。来源：docs/逻辑设计与范式分析.md。';
 }
 if(i===5){
  s=base(p,i,'派单事务实现');
  tx(s,'派单由 Java 事务调用 assign_order 存储过程完成。',60,151,1158,42,27);
  tx(s,'处理过程',60,222,480,35,27,C.navy,true);rule(s,60,269,480);
  const items=[['1','锁定订单和护工记录'],['2','检查资质、区域及预约冲突'],['3','改派时释放旧名额、保留历史'],['4','条件更新班次并写入新派单']];
  items.forEach((r,j)=>{tx(s,r[0],63,297+j*70,45,36,26,C.accent);tx(s,r[1],115,296+j*70,425,44,25);});
  tx(s,'班次容量检查',620,222,600,35,27,C.navy,true);rule(s,620,269,600);
  code(s,['UPDATE shift','SET used_count = used_count + 1','WHERE id = p_shift','  AND used_count < capacity;'],620,299,600,23);
  tx(s,'更新行数不是 1 时抛出异常，\n整个事务回滚。',678,462,536,80,26);
  rule(s,60,587,1160);tx(s,'改派失败时，旧派单和原有班次占用同时恢复。',60,614,1160,39,27,C.navy);
  s.speakerNotes.text='约 30 秒。先锁定订单和护工，再验证资质、区域和预约冲突。改派保留原记录。右侧是容量条件更新的节选，完整 SQL 还检查班次状态与时段。更新失败就抛异常，由外层事务回滚。并发争抢最后一个名额已在真实 MySQL 中测试。来源：02_routines.sql 的 assign_order。';
 }
 if(i===6){
  s=base(p,i,'业务查询设计');
  tx(s,'示例：统计每位老人的评估次数，包括没有评估记录的老人。',60,151,1158,42,27);
  tx(s,'实现一  左连接与分组统计',60,226,550,36,27,C.navy,true);tx(s,'实现二  相关子查询',668,226,550,36,27,C.navy,true);
  rule(s,60,278,550);rule(s,668,278,550);
  code(s,['SELECT e.id, COUNT(a.id) n','FROM elder e','LEFT JOIN assessment a','  ON a.elder_id = e.id','GROUP BY e.id;'],60,309,550,23);
  code(s,['SELECT e.id,','  (SELECT COUNT(*)','   FROM assessment a','   WHERE a.elder_id=e.id) n','FROM elder e;'],668,309,550,23);
  tx(s,'COUNT(a.id) 不统计左连接产生的 NULL；子查询没有匹配记录时返回 0。',60,532,1160,45,25);
  tx(s,'已完成 15 组双实现查询，30 段 SQL 的对应结果均逐行比较一致。',60,608,1160,39,25,C.navy);
  s.speakerNotes.text='约 25 秒。两种 SQL 都返回每位老人及评估次数。第一种必须统计 a.id，不能直接 COUNT(*)，否则没有评估的老人也会被计为一次。第二种相关子查询在无记录时返回零。项目一共提供 15 组比较，结果均一致。来源：database/queries.json 的 A2，query-comparison.json。';
 }
 if(i===7){
  s=base(p,i,'历史记录与补贴核算');
  tx(s,'设备绑定历史',60,162,530,42,30,C.navy,true);tx(s,'补贴明细',688,162,530,42,30,C.navy,true);
  node(s,'同一设备',60,303,154,66);node(s,'绑定记录 A',296,225,228,66);node(s,'绑定记录 B',296,381,228,66);
  rule(s,214,336,46,C.navy,1.5);s.shapes.add({geometry:'line',position:{left:260,top:258,width:0,height:156},line:{fill:C.navy,width:1.5}});arrow(s,260,258,36);arrow(s,260,414,36);
  tx(s,'老人甲 · 原绑定区间',296,304,270,34,22,C.muted);tx(s,'老人乙 · 换绑后区间',296,460,270,34,22,C.muted);
  tx(s,'体征保留原绑定记录。跨社区迁移\n先解绑，设备仍归原社区管理。',60,542,540,81,26);
  table(s,[['明细字段','保存内容'],['order_id','本次核算的订单'],['policy_id','本次采用的政策'],['rate_snapshot','核算时的单价'],['effective_minutes','认定的有效工时']],688,231,530,[265,265],66);
  tx(s,'核算前检查服务确认、有效工时\n和重复申报；不合格时整体回滚。',688,584,530,69,23);
  s.speakerNotes.text='约 20 秒。左边说明设备换绑历史。体征关联采样时的绑定记录，而非设备现在的使用者。右边是补贴明细中的核算依据，单价和有效工时需要保存当时的值。这里的甲乙是说明关系的示例，不是实际服务对象。来源：device_binding、vital、claim_item 与 submit_claim。';
 }
 if(i===8){
  s=base(p,i,'投诉办理与整改复核');
  tx(s,'投诉办理',60,168,1160,42,30,C.navy,true);
  const complaint=['家属提交问题','社区受理并转办','经办单位调查回复','家属或社区结案'].map((label,j)=>node(s,label,60+j*300,245,260,70,C.light,24));
  for(let j=0;j<3;j++)link(s,complaint[j],complaint[j+1]);
  tx(s,'每一步保存经办人、时间与说明。服务评价在已完成订单中单独登记。',60,344,1160,45,24,C.muted);
  tx(s,'整改复核',60,421,1160,42,30,C.navy,true);
  const review=['监管发现问题','机构提交材料','监管复核结果','通过后公示结论'].map((label,j)=>node(s,label,60+j*300,496,260,70,'white',24));
  for(let j=0;j<3;j++)link(s,review[j],review[j+1]);
  tx(s,'退回后追加新材料，保留上次提交与审核意见；整改完成前限制接单。',60,603,1160,46,24,C.navy);
  s.speakerNotes.text='约 25 秒。投诉由家属提出，社区受理后转给实际承接服务的机构；尚未派单的问题由社区自行处理。机构回复后，家属或社区确认结案。整改由机构提交材料，监管可以通过或退回。两种流程都保留每次办理记录。来源：application/service/FeedbackService.java、GovernanceService.java，complaint_log、rectification_submission；浏览器已走通两条流程。';
 }
 if(i===9){
  s=base(p,i,'辖区授权与补贴周期');
  tx(s,'按账号授权多个社区',60,170,535,42,30,C.navy,true);
  const authority=node(s,'民政监管账号',60,254,535,67,C.light,26);
  const areas=[0,1,2,3].map(j=>node(s,'社区 '+(j+1),60+j*140,422,115,65,'white',23));
  for(const area of areas)link(s,authority,area,'bottom','top');
  tx(s,'account_scope 决定查询范围；\n升级通知按本社区责任发送。',60,577,535,78,24,C.muted);
  tx(s,'按政策确定申报周期',675,170,545,42,30,C.navy,true);
  table(s,[['周期','输入示例'],['月度','2026-11'],['季度','2026-Q4'],['年度','2026']],675,254,545,[245,300],65);
  tx(s,'同一老人、同一项目的周期不能重叠。\n补件重报保留原订单、工时和审核。',675,561,545,90,24,C.navy);
  s.speakerNotes.text='约 25 秒。监管范围由账号与社区的关联表确定，不能只用账号所属社区过滤。统计提供覆盖率和及时响应率，并说明分母。补贴规则可以按月、季或年结算，跨周期也检查重复；驳回后在原申请补充材料。应急通知按项目规则送达本社区责任人员：一级为家属、护工和社区管理员，二级及以上增加本社区监管。跨社区查询权不等于通知订阅。来源：infrastructure/security/JdbcScope.java、infrastructure/query/GovernanceQueries.java，escalate_events、submit_claim；月季年和越权测试已通过。';
 }
 if(i===10){
  s=base(p,i,'系统测试');
  tx(s,`自动化测试 ${input.testCount} 项通过：126 项集成测试，37 项独立测试。`,60,151,1158,42,27);
  table(s,[['测试场景','核对内容','结果'],['并发创建计划','同一老人的重叠计划只能成功一份','通过'],['并发派单','最后一个班次名额不能被重复占用','通过'],['历史保护','终态派单、应急升级日志禁止改写','通过'],['复评与迁移','旧计划暂停；迁移前先解绑设备','通过'],['补贴核算','月季年周期、重报与金额追溯','通过']],60,222,1160,[270,725,165],60);
  tx(s,`浏览器验证 ${input.readRoutes} 个角色查询入口、${input.browserActions} 次业务提交。`,60,603,1160,32,23,C.navy);tx(s,'全量备份与 binlog 在独立库恢复，47 张表逐行内容一致。',60,638,1160,29,22,C.muted);
  s.speakerNotes.text='约 25 秒。自动化测试共 163 项，失败、错误和跳过均为零。125 项真实 MySQL 业务测试覆盖权限、回滚、并发和历史保护，1 项启动后核对路由契约。37 项独立测试覆盖领域规则、路由边界和分层依赖。浏览器验证 109 个角色查询入口、26 次业务提交。备份恢复另有独立演练记录，47 张表逐行内容一致。来源：evidence/test-summary.json、completion-browser.json、recovery.json。';
 }
 if(i===11){
  s=base(p,i,'运行说明与待完成事项');
  tx(s,'运行步骤',60,164,515,42,31,C.navy,true);
  [['01','安装 Java 17 和 MySQL 8'],['02','执行初始化脚本，建立数据库'],['03','启动 JAR，在浏览器访问系统']].forEach((r,j)=>{tx(s,r[0],60,253+j*99,65,41,28,C.accent);tx(s,r[1],148,254+j*99,460,67,27);});
  tx(s,'需要补充的内容',704,164,515,42,31,C.navy,true);['真实成员名单与实际分工','个人报告中的贡献说明','全员发言视频','Windows 电脑上的运行验证'].forEach((v,j)=>tx(s,v,704,252+j*59,514,43,27,C.blue));
  rule(s,60,585,1160);tx(s,'演示使用虚构业务数据，设备采样、定位围栏和拨付均按课程场景模拟。',60,617,1160,41,24,C.muted);
  s.speakerNotes.text='约 20 秒。提供数据库脚本、JAR 与 Windows 初始化和启动脚本，运行不依赖 Docker。已在本机验证程序，Windows 目标电脑仍需实测。真实成员、实际贡献和发言视频需要小组补齐，蓝色内容用于标记这些待完成项。';
 }
 if(i===12){
  s=base(p,i,'接单流程与代码职责');
  tx(s,'入口  POST /api/actions/order.accept',60,151,1160,42,27,C.navy);
  const steps=[['CareService.execute','开启事务，设置审计身份并分发操作'],['OrderService.acceptOrder','校验角色，锁定记录并检查访问范围'],['ServiceOrder.requireAcceptable','检查订单是否处于待接单状态'],['JdbcOrderRepository','执行资格过程，更新派单与订单状态']];
  steps.forEach(([name,detail],i)=>{tx(s,String(i+1),60,230+i*88,40,34,26,C.accent);tx(s,name,110,230+i*88,1060,34,27,C.ink,true);tx(s,detail,110,269+i*88,1060,34,25,C.muted);});
  rule(s,60,610,1160);tx(s,'所有写入属于同一个事务，任一步骤失败时整体回滚。',60,630,1160,35,25,C.navy);
  s.speakerNotes.text='约 25 秒。以接单为例，HTTP 请求先进入 CareService 的公开事务方法，再调用 OrderService。应用服务校验权限和加锁，领域对象检查订单状态，JDBC 仓储调用数据库资格过程并更新记录。事务覆盖整个调用链，中途失败时统一回滚。来源：application/CareService.java 的 execute，application/service/OrderService.java 的 acceptOrder，domain/order/ServiceOrder.java 的 requireAcceptable，infrastructure/persistence/JdbcOrderRepository.java。';
 }

 }
 return {edited:input.slides,slideCount:p.slides.items.length};
}
