import { Button, Space, Table } from "@arco-design/web-react";
import { IconPlus, IconThunderbolt } from "@arco-design/web-react/icon";
import { useMobile } from "../../hooks/useMobile";
import {
  RowActions,
  HomeHeader,
  Panel,
  Load,
  Metric,
  RowList,
  Status,
  dateText,
  timeText,
  useHome,
  useRows,
} from "./shared";
export function AdminHome() {
  const h = useHome();
  const mobile = useMobile();
  const dispatchable = {
    status: "PENDING",
    field: "dispatch_window",
    value: 1,
  };
  const missed = { ...dispatchable, value: 2 };
  const orders = useRows("workspaceSchedule", dispatchable),
    missedOrders = useRows("orders", { ...missed, size: 1 }),
    overdue = useRows("dispatchPool", { size: 1 }),
    pending = useRows("pendingEvents", { size: 2 }),
    assessment = useRows("assessmentDue", { size: 3 }),
    complaints = useRows("complaints", { status: "SUBMITTED", size: 2 }),
    eligible = useRows("eligibleClaims", { size: 1 });
  return (
    <>
      <HomeHeader title="社区工作台" subtitle={h.profile?.community_name}>
        <Button
          type="primary"
          icon={<IconPlus />}
          onClick={() => h.open("elder.create")}
        >
          建立老人档案
        </Button>
      </HomeHeader>
      <div className="emergency-strip">
        <IconThunderbolt />
        <strong>待处理应急</strong>
        <Load resource={pending} empty="当前没有待处理应急">
          {pending.data?.rows.map((r) => (
            <Button
              key={r.id}
              type="text"
              status="danger"
              onClick={() => h.details("events", r)}
            >
              事件 #{r.id} · {r.level}级 <Status kind="events" row={r} />
            </Button>
          ))}
          <Button onClick={() => h.navigate("pendingEvents")}>
            查看全部 {pending.data?.total}
          </Button>
        </Load>
      </div>
      <div className="home-metrics">
        <Metric label="待派单" kind="orders" query={dispatchable} />
        <Metric label="待复评提醒" kind="assessmentDue" />
        <Metric label="待受理投诉" kind="complaints" status="SUBMITTED" />
        <Metric label="可申报订单" kind="eligibleClaims" />
      </div>
      <div className="home-grid">
        <div className="home-stack">
          <Panel
            title="服务调度"
            onMore={() => h.navigate("orders", dispatchable)}
          >
            <div className="panel-context">
              待派单 · 按预约时间排列{" "}
              <Button type="text" onClick={() => h.navigate("orders", missed)}>
                已过预约时段 · 待联系{" "}
                {missedOrders.error
                  ? "加载失败"
                  : (missedOrders.data?.total ?? "—")}
              </Button>
              <Button type="text" onClick={() => h.navigate("dispatchPool")}>
                超时调度池{" "}
                {overdue.error ? "加载失败" : (overdue.data?.total ?? "—")}
              </Button>
            </div>
            {mobile ? (
              <RowList
                resource={orders}
                kind="orders"
                title={(r) => (
                  <>
                    {r.elder_name}{" "}
                    <span className="home-note">{r.item_name}</span>
                  </>
                )}
                subtitle={(r) =>
                  `${dateText(r.starts_at)} ${timeText(r.starts_at)} · ${r.address}`
                }
              />
            ) : (
              <Load resource={orders} empty="暂无待派单服务">
                <Table
                  className="dispatch-table"
                  size="small"
                  border={false}
                  rowKey="id"
                  pagination={false}
                  data={orders.data?.rows || []}
                  scroll={{ x: 620 }}
                  columns={[
                    { title: "老人", dataIndex: "elder_name", width: 95 },
                    { title: "服务项目", dataIndex: "item_name", width: 155 },
                    {
                      title: "预约时间",
                      render: (_, r) =>
                        `${dateText(r.starts_at).slice(5)} ${timeText(r.starts_at)}`,
                      width: 125,
                    },
                    {
                      title: "状态",
                      render: (_, r) => <Status kind="orders" row={r} />,
                      width: 80,
                    },
                    {
                      title: "操作",
                      render: (_, r) => <RowActions kind="orders" row={r} />,
                      width: 165,
                    },
                  ]}
                />
              </Load>
            )}
          </Panel>
        </div>
        <aside className="home-stack">
          <Panel title="评估与计划" onMore={() => h.navigate("assessmentDue")}>
            <Load resource={assessment} empty="未来30天没有评估到期提醒">
              <div className="home-rows">
                {assessment.data?.rows.map((r) => (
                  <div className="home-row" key={r.id}>
                    <div>
                      <strong>{r.name}</strong>
                      <p className="home-note">
                        {dateText(r.valid_until)} 到期
                      </p>
                    </div>
                    <Button
                      onClick={() =>
                        h.open("assessment.create", { elderId: r.id })
                      }
                    >
                      安排复评
                    </Button>
                  </div>
                ))}
              </div>
            </Load>
            <Button
              type="text"
              onClick={() => h.navigate("plans", { status: "PAUSED" })}
            >
              查看暂停的照护计划
            </Button>
          </Panel>
          <Panel
            title="投诉待受理"
            onMore={() => h.navigate("complaints", { status: "SUBMITTED" })}
          >
            <RowList
              resource={complaints}
              kind="complaints"
              title={(r) => r.content || `投诉 #${r.id}`}
              subtitle={(r) => dateText(r.created_at)}
            />
          </Panel>
        </aside>
      </div>
      <div className="admin-claim-panel">
        <Panel title="补贴材料准备">
          <Load resource={eligible} empty="暂无符合条件的可申报订单">
            <div className="claim-ready">
              <div>
                <strong>{eligible.data?.total} 笔可申报订单</strong>
                <p>核对服务记录、有效工时和家属确认，再归档申报材料。</p>
              </div>
              <Space wrap>
                <Button onClick={() => h.navigate("eligibleClaims")}>
                  查看可申报订单
                </Button>
                <Button type="primary" onClick={() => h.open("claim.submit")}>
                  准备申报材料
                </Button>
              </Space>
            </div>
          </Load>
        </Panel>
      </div>
    </>
  );
}
