'use strict';
Object.assign(names,{complaints:'评价与投诉',recruitments:'护工招聘',trainings:'岗前培训',trainingRecords:'培训成绩',inspectionBatches:'抽查批次',organizationReports:'机构考核材料',publicNotices:'质量公示',jurisdictionStats:'辖区服务统计',communities:'社区服务范围',accounts:'家属账号',metrics:'监测指标',areas:'护工服务区域',managerDispatchPool:'本机构超时订单'});
menus.ADMIN.push('complaints','communities','accounts','metrics','publicNotices');
menus.FAMILY.push('complaints','publicNotices');
menus.CAREGIVER.push('trainingRecords','trainings','areas','publicNotices');
menus.MANAGER.push('complaints','recruitments','trainings','trainingRecords','organizationReports','areas','managerDispatchPool','communities','publicNotices');
menus.STREET.push('jurisdictionStats','communities','publicNotices');
menus.REGULATOR.push('complaints','inspectionBatches','organizationReports','jurisdictionStats','communities','publicNotices');
groups.push(['人员与服务质量',['recruitments','trainings','trainingRecords','areas','managerDispatchPool','complaints','inspectionBatches','organizationReports','jurisdictionStats','publicNotices']],['资料维护',['communities','accounts','metrics']]);
Object.assign(actions,{
 'account.create':['创建家属账号',['ADMIN'],['username:登录账号','password:初始密码（至少10个字符）:password']],
 'elder.create':['建立老人档案',['ADMIN'],['name:姓名','identity:身份证号（课程虚构编号）','birthDate:出生日期:date','address:住址','x:住址横坐标（米）:number','y:住址纵坐标（米）:number']],
 'elder.update':['更新老人住址',['ADMIN'],['elderId:老人编号:number','address:新住址','x:住址横坐标（米）:number','y:住址纵坐标（米）:number']],
 'elder.transfer':['迁移所属社区',['ADMIN'],['elderId:老人编号:number','communityId:目标社区编号:number','address:新住址','x:住址横坐标（米）:number','y:住址纵坐标（米）:number']],
 'elder.tags':['维护老人标签',['ADMIN'],['elderId:老人编号:number','tag:特殊标签','enabled:是否保留此标签']],
 'guardian.update':['维护家属联系方式',['ADMIN'],['guardianId:家属关联编号:number','relation:与老人关系','phone:联系电话','active:关联是否有效']],
 'plan.revise':['调整照护计划',['ADMIN'],['planId:原计划编号:number','elderId:老人编号:number','assessmentId:评估编号:number','bundleId:服务包编号:number','caregiverId:责任护工编号:number','startsAt:新计划开始时间:datetime-local','endsAt:新计划结束时间:datetime-local','items:项目和每周次数（留空沿用服务包）:json:optional','reason:调整原因']],
 'plan.state':['暂停、恢复或终止计划',['ADMIN'],['planId:计划编号:number','status:计划状态','reason:操作原因']],
 'catalog.create':['登记服务项目',['ADMIN'],['name:项目名称','category:服务类别','unit:计价单位','price:指导价（元）:number','duration:标准时长（分钟）:number','subsidizable:是否可补贴']],
 'bundle.create':['登记照护服务包',['ADMIN'],['name:服务包名称','level:适用能力等级','items:服务项目与每周次数:json']],
 'metric.create':['登记监测指标',['ADMIN'],['name:指标名称与单位','low:下限:number','high:上限:number','seconds:初始响应时限（秒）:number']],
 'order.create':['预约上门服务',['ADMIN','FAMILY'],['elderId:老人编号:number','itemId:服务项目编号:number','startsAt:预约时间:datetime-local','channel:下单渠道']],
 'order.checkin':['上门签到',['CAREGIVER'],['orderId:订单编号:number','location:位置说明','x:当前横坐标（米）:number','y:当前纵坐标（米）:number']],
 'order.finish':['结束服务并签退',['CAREGIVER'],['orderId:订单编号:number','notes:服务情况记录','x:当前横坐标（米）:number','y:当前纵坐标（米）:number']],
 'order.release':['将订单退回社区调度',['MANAGER'],['orderId:订单编号:number','reason:退回原因']],
 'order.reject':['退回无法承接的订单',['CAREGIVER'],['orderId:订单编号:number','reason:退回原因']],
 'caregiver.area':['维护护工服务区域',['MANAGER'],['caregiverId:护工编号:number','communityId:社区编号:number','enabled:是否可以在该社区服务']],
 'recruitment.create':['登记应聘人员',['MANAGER'],['name:应聘人姓名','phone:联系电话']],
 'recruitment.review':['处理招聘申请',['MANAGER'],['recruitmentId:招聘申请编号:number','approve:是否录用','reason:审核说明','username:录用后的登录账号:text:optional','password:录用后的初始密码:password:optional']],
 'training.create':['安排岗前培训',['MANAGER'],['title:培训名称','startsAt:开始时间:datetime-local','endsAt:结束时间:datetime-local']],
 'training.enroll':['登记参训人员',['MANAGER'],['trainingId:培训编号:number','caregiverId:护工编号:number']],
 'training.result':['登记培训成绩',['MANAGER'],['recordId:参训记录编号:number','score:成绩（课程规则60分通过）:number']],
 'complaint.assign':['受理并交机构处理',['ADMIN'],['complaintId:投诉编号:number','organizationId:承接机构编号（社区自办留空）:number:optional','reason:受理意见']],
 'complaint.reply':['回复投诉处理结果',['MANAGER','ADMIN'],['complaintId:投诉编号:number','result:调查情况与处理结果']],
 'complaint.close':['确认投诉处理结束',['ADMIN','FAMILY'],['complaintId:投诉编号:number','reason:结案说明']],
 'device.unbind':['解除设备绑定',['ADMIN'],['deviceId:设备编号:number']],
 'device.status':['更新设备状态',['ADMIN'],['deviceId:设备编号:number','status:设备状态']],
 'simulator.run':['采集一批演示数据',['ADMIN'],['metricId:监测指标编号:number']],
 'event.confirm':['确认异常告警',['ADMIN','CAREGIVER'],['eventId:事件编号:number','reason:核实情况']],
 'event.dismiss':['登记误报',['ADMIN'],['eventId:事件编号:number','reason:误报核实依据']],
 'event.arrive':['登记应急处置到场',['ADMIN','CAREGIVER'],['eventId:事件编号:number','note:到场情况']],
 'claim.evidence':['归档补贴证明材料',['ADMIN'],['claimId:申报编号:number','name:材料名称','content:证明内容与关联编号']],
 'claim.resubmit':['补充材料重新申报',['ADMIN'],['claimId:已驳回申报编号:number','reason:补充说明（保留原订单与审核记录）']],
 'policy.create':['登记补贴政策',['REGULATOR'],['name:政策名称（课程演示）','itemId:服务项目编号:number','level:适用能力等级','rate:补贴标准（元）:number','startsAt:生效时间:datetime-local','endsAt:失效时间:datetime-local','cycle:结算周期','minimumAge:最低年龄:number','tag:附加对象标签（可选无）','communityIds:适用社区编号（如 [1,2]）:json']],
 'inspection.batch':['建立比例抽查批次',['REGULATOR'],['communityId:社区编号:number','target:抽查对象','startsAt:服务开始时间:datetime-local','endsAt:服务截止时间（不含）:datetime-local','ratio:抽查比例（0至1，如0.2）:number']],
 'inspection.record':['登记批次抽查结果',['REGULATOR'],['inspectionId:抽查编号:number','method:抽查方式','score:评分:number','issue:发现的问题','requiresRectification:是否需要整改','rectification:整改要求（无需整改可填无）']],
 'rectification.submit':['提交整改材料',['MANAGER'],['inspectionId:抽查编号:number','measures:整改措施','evidence:整改证据与关联记录']],
 'inspection.close':['复核机构整改结果',['REGULATOR'],['inspectionId:抽查编号:number','approve:是否通过','result:复核意见']],
 'organization.submit':['提交机构考核材料',['MANAGER'],['period:考核月份（YYYY-MM）','summary:本月服务与问题处理情况','evidence:相关服务和整改记录']],
 'organization.review':['审核机构考核材料',['REGULATOR'],['reportId:材料编号:number','approve:是否通过','reason:考核意见']],
 'notice.publish':['公示已完成抽查结果',['REGULATOR'],['inspectionId:抽查编号:number']],
 'notice.withdraw':['撤回公示并留存原因',['REGULATOR'],['noticeId:公示编号:number','reason:撤回原因']]
});
actions['claim.submit'][2][2]='period:结算周期（月2026-11、季2026-Q4、年2026）';
Object.assign(columns,{tags:'老人标签',phone:'联系电话',relation_name:'家属关系',active:'是否有效',position_x:'住址横坐标（米）',position_y:'住址纵坐标（米）',center_x:'社区中心横坐标',center_y:'社区中心纵坐标',radius_m:'服务范围半径（米）',fence_m:'打卡范围半径（米）',district:'所属区县',street_name:'所属街道',title:'名称',body:'公示内容',candidate_name:'应聘人',review_note:'审核意见',reviewed_at:'审核时间',training_id:'培训编号',recorded_at:'成绩登记时间',weekly_frequency:'每周次数',handler_id:'经办人',content:'内容',batch_id:'抽查批次',target_type:'抽查对象',ratio:'抽查比例',population:'符合条件数量',sample_count:'抽中数量',sample_seed:'抽样复核编号',initial_deadline:'首次响应截止',closed_at:'关闭时间',checkout_fence_ok:'签退围栏结果',checkin_x:'签到横坐标',checkin_y:'签到纵坐标',checkout_x:'签退横坐标',checkout_y:'签退纵坐标',period_start:'周期开始',period_end:'周期截止（不含）',settlement_cycle:'结算周期',minimum_age:'最低年龄',required_tag:'对象标签',organization_id:'机构编号',published_at:'公示时间',summary:'情况说明',evidence:'证明材料',measures:'整改措施',decision:'复核结论',active_elders:'在册老人数',served_elders:'本月已服务老人数',events_total:'本月预警数',response_denominator:'有原始时限的非误报预警数',timely_responses:'按时响应数',coverage_rate:'服务覆盖率（%）',response_rate:'及时响应率（%）',notes:'服务记录',confirmation:'家属确认说明',confirmed_at:'家属确认时间',reviewer_name:'审核人',actor_name:'经办人',hours_status:'工时状态'});
Object.assign(values,{PASS:'通过',FAIL:'未通过',APPLIED:'待审查',HIRED:'已录用',REPLIED:'已回复',RETURNED:'已退回',ORDER:'服务订单',CAREGIVER:'护工',QUARTER:'季度',YEAR:'年度',ALONE:'独居',DISABLED:'失能',LOW_INCOME:'低保',ACCEPT:'通过',RETURN:'退回'});
Object.assign(descriptions,{complaints:'查看服务评价、投诉受理及机构处理结果。',recruitments:'登记应聘人员，录用后建立护工账号和档案。',trainings:'安排本机构岗前培训，成绩通过后仍须具备相应服务证书。',trainingRecords:'查看参加培训和成绩记录。',inspectionBatches:'按服务期间确定总体，按比例向上取整抽样，保存选样依据。',jurisdictionStats:'按授权辖区汇总本月情况。覆盖率分母为当前在册老人；响应统计排除误报和缺少原始时限的旧记录。',communities:'课程演示采用平面坐标，单位为米。住址必须位于社区服务范围内。',publicNotices:'仅展示已完成抽查的脱敏结果，隐去老人、护工及健康和投诉明细。'});
const detailKinds={elders:'elders',plans:'plans',orders:'orders',claims:'claims',claimProgress:'claims',complaints:'complaints',devices:'devices',currentDevices:'devices',events:'events',pendingEvents:'events',inspections:'inspections',rectifications:'inspections',inspectionBatches:'inspectionBatches',caregivers:'caregivers',bundles:'bundles'};
const dialog=document.createElement('dialog');dialog.className='detail-dialog';dialog.innerHTML='<div class="detail-heading"><h2>业务详情</h2><button type="button" class="secondary">关闭</button></div><div class="detail-content"></div>';document.body.append(dialog);dialog.querySelector('button').onclick=()=>dialog.close();
function renderDetail(title,value){
 const content=dialog.querySelector('.detail-content');content.replaceChildren();dialog.querySelector('h2').textContent=title;
 const sections=Array.isArray(value)?{'公示记录':value}:value;
 for(const [label,part]of Object.entries(sections)){
  content.append(node('h3',label));
  if(!Array.isArray(part)){const list=node('dl');list.className='detail-facts';for(const [key,val]of Object.entries(part)){const item=node('div');item.append(node('dt',columns[key]||key),node('dd',displayValue(key,val)));list.append(item);}content.append(list);continue;}
  if(!part.length){content.append(node('p','暂无记录'));continue;}
  const table=node('table'),wrap=node('div');wrap.className='detail-scroll';const head=node('tr'),keys=Object.keys(part[0]);
  keys.forEach(key=>head.append(node('th',columns[key]||key)));table.append(head);
  for(const row of part){const tr=node('tr');keys.forEach(key=>{const td=node('td',displayValue(key,row[key]));if(['message','content','notes','evidence','measures','result'].includes(key))td.className='detail-prose';tr.append(td);});table.append(tr);}
  wrap.append(table);content.append(wrap);
 }
 dialog.showModal();
}
async function showDetails(kind,id){try{renderDetail(names[kind]||'业务详情',await api('/details/'+detailKinds[kind]+'/'+id));}catch(e){$('result').textContent=e.message;$('result').className='error';}}
const baseLoad=load;load=async function(){await baseLoad();if(!detailKinds[kind]||!$('thead').children.length)return;const header=$('thead').querySelector('tr');header.append(node('th','详情'));for(const row of $('tbody').querySelectorAll('tr')){if(row.querySelector('.empty-row'))continue;const id=row.children[0]?.textContent;if(!/^\d+$/.test(id))continue;const cell=node('td'),button=node('button','查看');button.type='button';button.className='detail-link';const currentKind=kind;button.onclick=()=>showDetails(currentKind,id);cell.append(button);row.append(cell);}};
const baseFields=fields;fields=function(){baseFields();const action=$('action').value;for(const field of actions[action]?.[2]||[]){const [key,label,type,optional]=field.split(':');const input=$('fields').querySelector('[name="'+key+'"]');if(!input)continue;if(optional==='optional'){input.required=false;input.previousSibling?.remove();}if(type==='password')input.type='password';if(type==='number')input.step='any';if(type==='json'&&key==='items')input.placeholder='例如：[{"itemId":1,"frequency":2}]';if(key==='communityIds')input.placeholder='例如：[1, 2]';let options=null;if(['active','enabled','subsidizable','requiresRectification'].includes(key))options=[['true','是'],['false','否']];if(key==='tag')options=[['ALONE','独居'],['DISABLED','失能'],['LOW_INCOME','低保'],...(action==='policy.create'?[['NONE','无附加标签']]:[])];if(key==='level'&&['bundle.create','policy.create'].includes(action))options=[['SELF','自理'],['PARTIAL','部分失能'],['SEVERE','重度失能']];if(key==='unit')options=[['VISIT','每次'],['HOUR','每小时']];if(key==='cycle')options=[['MONTH','月'],['QUARTER','季度'],['YEAR','年度']];if(key==='channel')options=user?.role==='FAMILY'?[['FAMILY','家属下单']]:[['COMMUNITY','社区代下单'],['PHONE','电话预约']];if(key==='target')options=[['ORDER','已完成订单'],['CAREGIVER','有已完成服务的护工']];if(key==='status'&&action==='plan.state')options=[['PAUSED','暂停'],['ACTIVE','恢复生效'],['ENDED','终止']];if(key==='status'&&action==='device.status')options=[['ACTIVE','正常'],['OFFLINE','离线'],['RETIRED','停用']];if(options){const select=node('select');select.name=key;select.dataset.type='text';select.required=input.required;for(const [v,t]of options){const o=node('option',t);o.value=v;select.append(o);}input.replaceWith(select);}}};
$('action').onchange=fields;
const publicButton=node('button','查看服务质量公示');publicButton.type='button';publicButton.className='public-entry';publicButton.onclick=async()=>{try{renderDetail('服务质量公示',await api('/public/notices'));}catch(e){$('login-error').textContent=e.message;}};$('login-form').append(publicButton);

Object.assign(actions,{
 'review.create':['服务评价',['FAMILY'],['orderId:已完成订单编号:number','rating:评分（1至5）:number','content:评价']],
 'device.simulation':['设置定时模拟采样',['ADMIN'],['deviceId:设备编号:number','enabled:是否开启','metricId:指标编号:number','seconds:采样间隔秒数（30至3600）:number']]
});
for(const key of ['inspection.create','inspection.order'])actions[key][2].push('requiresRectification:是否需要整改');
actions['complaint.create'][0]='提交服务投诉';
Object.assign(columns,{kind:'反馈类型',simulation_enabled:'定时采样开关',simulation_metric_id:'模拟指标',simulation_seconds:'采样间隔（秒）',last_simulated_at:'上次模拟采样'});

Object.assign(values,{MONTH:'月度',COMPLAINT:'投诉'});
const baseDisplay=displayValue;displayValue=function(key,value){if(key==='kind'&&value==='REVIEW')return '服务评价';if(key==='status'&&value==='ASSIGNED'&&kind==='complaints')return '已受理';return baseDisplay(key,value);};
const notificationSummary=node('button','通知记录');notificationSummary.className='secondary';notificationSummary.type='button';notificationSummary.onclick=()=>{kind='notifications';page=0;load();};document.querySelector('.workspace-topline').append(notificationSummary);
async function refreshNoticeCount(){if(!user||$('workspace').hidden)return;try{const n=await api('/data/notifications?page=0&size=1');notificationSummary.textContent='通知记录 '+n.total;}catch(e){/* The next user request handles an expired session. */}}
const baseEnter=enter;enter=async function(){await baseEnter();await refreshNoticeCount();};setInterval(refreshNoticeCount,30000);

Object.assign(columns,{rating:'家属评分',updated_at:'更新时间',complaint_id:'投诉编号',actor_id:'经办账号',recorded_by:'成绩登记人',submitted_by:'提交账号',publisher_id:'发布账号',withdrawn_at:'撤回时间',withdraw_reason:'撤回原因',inspection_id:'抽查编号',content_sha256:'材料摘要',action:'操作',reviewed_at:'复核时间',period:'周期',paid_subsidy:'本月已拨付（元）'});
