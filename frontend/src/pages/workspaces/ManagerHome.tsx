import { useState } from "react";
import { Button, DatePicker, Space, Table, Tag } from "@arco-design/web-react";
import { IconCalendar } from "@arco-design/web-react/icon";
import {
  HomeHeader,
  Panel,
  Load,
  Metric,
  RowList,
  Pager,
  dateText,
  timeText,
  useHome,
  useRows,
  todayInShanghai,
} from "./shared";
export function ManagerHome() {
  const h = useHome(),
    [day, setDay] = useState(dateText(h.profile?.today || todayInShanghai())),
    [page, setPage] = useState(1);
  const shifts = useRows("workspaceShifts", {
      from: day,
      to: day,
      page: page - 1,
      size: 5,
    }),
    hours = useRows("hoursReview", { size: 2 }),
    overdue = useRows("managerDispatchPool", { size: 2 }),
    inspections = useRows("inspections", { status: "OPEN", size: 2 }),
    recruitment = useRows("recruitments", { status: "APPLIED", size: 2 }),
    trainings = useRows("trainings", { size: 2 }),
    reports = useRows("organizationReports", { size: 2 });
  return (
    <>
      <HomeHeader title="机构工作台" subtitle={h.profile?.organization_name}>
        <Button
          type="primary"
          icon={<IconCalendar />}
          onClick={() => h.open("shift.create")}
        >
          安排排班
        </Button>
      </HomeHeader>
      <div className="home-metrics">
        <Metric label="在岗护工" kind="caregivers" status="ACTIVE" />
        <Metric label="工时待复核" kind="hoursReview" />
        <Metric label="证书到期提醒" kind="credentialDue" />
        <Metric label="待整改" kind="inspections" status="OPEN" />
      </div>
      <div className="home-grid">
        <div className="home-stack">
          <Panel
            className="manager-roster"
            title="排班总览"
            onMore={() => h.navigate("shifts", { from: day, to: day })}
          >
            <div className="roster-heading">
              <span>班次安排</span>
              <strong>
                {shifts.error ? "加载失败" : (shifts.data?.total ?? "—")}
                <small> 个班次</small>
              </strong>
            </div>
            <div className="home-toolbar">
              <DatePicker
                aria-label="排班日期"
                allowClear={false}
                value={day}
                onChange={(d) => {
                  setDay(d);
                  setPage(1);
                }}
              />
            </div>
            <Load resource={shifts} empty="所选日期暂无排班">
              <Table
                rowKey="id"
                size="small"
                border={false}
                pagination={false}
                scroll={{ x: 620 }}
                data={shifts.data?.rows || []}
                columns={[
                  {
                    title: "护工",
                    dataIndex: "caregiver_name",
                    render: (name) => (
                      <span className="roster-worker">
                        <span>{String(name || "护").slice(0, 1)}</span>
                        <strong>{name}</strong>
                      </span>
                    ),
                  },
                  {
                    title: "班次",
                    render: (_, r) =>
                      `${timeText(r.starts_at)}—${timeText(r.ends_at)}`,
                  },
                  {
                    title: "已接 / 容量",
                    render: (_, r) => (
                      <span className="capacity-count">
                        {r.used_count} / {r.capacity}
                      </span>
                    ),
                  },
                  {
                    title: "状态",
                    render: (_, r) => (
                      <Tag
                        color={
                          r.worker_status === "LEAVE" ? "orange" : "arcoblue"
                        }
                      >
                        {r.worker_status === "LEAVE"
                          ? "请假"
                          : r.worker_status === "LEFT"
                            ? "离职"
                            : r.status === "CLOSED"
                              ? "班次关闭"
                              : Number(r.used_count) >= Number(r.capacity)
                                ? "已满"
                                : "在岗"}
                      </Tag>
                    ),
                  },
                  {
                    title: "操作",
                    render: (_, r) => (
                      <Button
                        type="text"
                        size="small"
                        onClick={() => h.details("shifts", r)}
                      >
                        查看排班
                      </Button>
                    ),
                  },
                ]}
              />
              <Pager resource={shifts} page={page} onChange={setPage} />
            </Load>
          </Panel>
          <Panel title="招聘与培训">
            <h3 className="section-label">待审应聘</h3>
            <RowList
              resource={recruitment}
              kind="recruitments"
              title={(r) => r.candidate_name}
            />
            <h3 className="section-label">培训安排</h3>
            <RowList
              resource={trainings}
              kind="trainings"
              title={(r) => r.title}
              subtitle={(r) => dateText(r.starts_at)}
            />
            <Space wrap>
              <Button onClick={() => h.navigate("recruitments")}>
                全部招聘
              </Button>
              <Button onClick={() => h.navigate("trainings")}>全部培训</Button>
            </Space>
          </Panel>
        </div>
        <aside className="home-stack">
          <Panel title="需要处理" className="manager-attention">
            <h3 className="section-label">超时未接单</h3>
            <RowList
              resource={overdue}
              kind="managerDispatchPool"
              title={(r) => `${r.caregiver_name} · 订单 #${r.id}`}
              subtitle={(r) =>
                `${dateText(r.starts_at)} ${timeText(r.starts_at)}`
              }
            />
            <h3 className="section-label">异常工时</h3>
            <RowList
              resource={hours}
              kind="hours"
              title={(r) => `订单 #${r.order_id}`}
              subtitle={(r) => r.location_note || "签到或签退需要复核"}
            />
            <Button type="text" onClick={() => h.navigate("hoursReview")}>
              全部异常工时
            </Button>
          </Panel>
          <Panel
            className="manager-rectification"
            title="整改待提交"
            onMore={() => h.navigate("inspections", { status: "OPEN" })}
          >
            <RowList
              resource={inspections}
              kind="inspections"
              title={(r) => r.issue || `抽查 #${r.id}`}
              subtitle={(r) => r.rectification}
            />
          </Panel>
          <Button onClick={() => h.navigate("complaints")}>
            查看投诉与回复
          </Button>
          <Panel
            title="机构考核材料"
            onMore={() => h.navigate("organizationReports")}
          >
            <RowList
              resource={reports}
              kind="organizationReports"
              title={(r) => `${r.period} 考核材料`}
              subtitle={(r) => r.summary}
            />
            <Button
              type="primary"
              onClick={() => h.open("organization.submit")}
            >
              提交考核材料
            </Button>
          </Panel>
        </aside>
      </div>
    </>
  );
}
