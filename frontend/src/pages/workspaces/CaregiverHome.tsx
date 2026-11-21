import { useAvailablePage } from "../../hooks/useAvailablePage";
import { useState } from "react";
import { Button, DatePicker, Radio } from "@arco-design/web-react";
import {
  IconCalendar,
  IconClockCircle,
  IconExclamationCircle,
  IconHeart,
} from "@arco-design/web-react/icon";
import { certificateUrl } from "../workforce/metrics";
import { Person } from "../../components/Media";
import {
  HomeHeader,
  Load,
  Panel,
  Pager,
  RowActions,
  RowList,
  Status,
  dateText,
  timeText,
  useHome,
  useRows,
  todayInShanghai,
} from "./shared";
export function CaregiverHome() {
  const h = useHome(),
    today = dateText(h.profile?.today || todayInShanghai());
  const [day, setDay] = useState(today),
    [tab, setTab] = useState("today"),
    [page, setPage] = useState(1);
  const schedule = useRows("workspaceSchedule", {
    ...(tab === "today" ? { from: day, to: day } : { status: "ASSIGNED" }),
    size: 5,
    page: page - 1,
  });
  const todayCount = useRows("workspaceSchedule", {
    from: today,
    to: today,
    size: 1,
  });
  const waiting = useRows("workspaceSchedule", { status: "ASSIGNED", size: 1 });
  const hours = useRows("workspaceHours", { size: 1 });
  const shifts = useRows("workspaceShifts", { from: day, to: day, size: 5 });
  const credentials = useRows("credentials", { size: 2 });
  const carryover = useRows("workspaceCarryover", { size: 3 });
  useAvailablePage(schedule.data, setPage);
  const noDuties =
    tab === "today" &&
    [schedule, shifts, carryover].every(
      (r) => !r.loading && !r.error && r.data?.total === 0,
    );
  return (
    <>
      <HomeHeader title="今日服务" subtitle={h.profile?.display_name}>
        <Button
          icon={<IconExclamationCircle />}
          onClick={() => h.open("event.create")}
        >
          上报异常
        </Button>
      </HomeHeader>
      <div className="home-metrics three">
        {[
          {
            label: "今日安排",
            r: todayCount,
            icon: <IconCalendar />,
            click: () => {
              setTab("today");
              setDay(today);
              setPage(1);
            },
          },
          {
            label: "待接单",
            r: waiting,
            icon: <IconFileSymbol />,
            click: () => {
              setTab("waiting");
              setPage(1);
            },
          },
          {
            label: "本月有效工时",
            r: hours,
            icon: <IconClockCircle />,
            click: () => h.navigate("monthlyHours"),
          },
        ].map(({ label, r, icon, click }, i) => (
          <button
            className="home-metric"
            key={label}
            onClick={() => (r.error ? r.retry() : click())}
          >
            <span className="home-metric-icon">{icon}</span>
            <span>
              <span className="metric-label">{label}</span>
              <strong>
                {r.error
                  ? "加载失败"
                  : r.loading
                    ? "—"
                    : i === 2
                      ? `${Math.floor(Number(r.data?.rows[0]?.effective_minutes || 0) / 60)}小时${Number(r.data?.rows[0]?.effective_minutes || 0) % 60}分`
                      : (r.data?.total ?? "—")}
              </strong>
            </span>
          </button>
        ))}
      </div>
      <div className="home-grid caregiver-grid">
        <div className="home-stack">
          <Panel title="服务日程" className="caregiver-schedule">
            <div className="duty-heading">
              <div>
                <span>{day.slice(0, 7).replace("-", " 年 ")} 月</span>
                <strong>
                  {day.slice(8)}
                  <small>
                    日 ·{" "}
                    {new Date(`${day}T12:00:00+08:00`).toLocaleDateString(
                      "zh-CN",
                      { weekday: "long", timeZone: "Asia/Shanghai" },
                    )}
                  </small>
                </strong>
              </div>
              <Button
                size="small"
                disabled={day === today && tab === "today"}
                onClick={() => {
                  setDay(today);
                  setTab("today");
                  setPage(1);
                }}
              >
                回到今天
              </Button>
            </div>
            <div className="home-toolbar">
              <DatePicker
                aria-label="服务日期"
                allowClear={false}
                value={day}
                onChange={(d) => {
                  setDay(d);
                  setPage(1);
                  setTab("today");
                }}
              />
              <Radio.Group
                type="button"
                value={tab}
                onChange={(v) => {
                  setTab(v);
                  setPage(1);
                }}
              >
                <Radio value="today">当日安排</Radio>
                <Radio value="waiting">待接单</Radio>
              </Radio.Group>
            </div>
            <Load
              resource={schedule}
              empty={
                noDuties
                  ? "当天没有服务或班次，也没有遗留任务"
                  : tab === "today"
                    ? "当天暂无服务安排"
                    : "暂无待接单任务"
              }
            >
              <div className="schedule-list">
                {schedule.data?.rows.map((r) => (
                  <article
                    className={
                      "visit-row " + (r.status === "ACCEPTED" ? "next" : "")
                    }
                    data-order-id={r.id}
                    key={r.id}
                  >
                    <div className="visit-time">
                      <strong>{timeText(r.starts_at)}</strong>
                      <span>{timeText(r.ends_at)}</span>
                      {tab === "waiting" && (
                        <small>{dateText(r.starts_at)}</small>
                      )}
                    </div>
                    <div className="visit-person">
                      <Person id={r.elder_id} name={r.elder_name} />
                      <span className="visit-service">{r.item_name}</span>
                      <p>{r.address}</p>
                    </div>
                    <div className="visit-action">
                      <Status row={r} kind="orders" />
                      <RowActions kind="orders" row={r} />
                    </div>
                  </article>
                ))}
              </div>
              <Pager resource={schedule} page={page} onChange={setPage} />
            </Load>
          </Panel>
          {!noDuties && (
            <Panel title="此前未完成的任务" onMore={() => h.navigate("orders")}>
              <RowList
                resource={carryover}
                kind="orders"
                title={(r) => `${r.elder_name} · ${r.item_name}`}
                subtitle={(r) =>
                  `${dateText(r.starts_at)} ${timeText(r.starts_at)}`
                }
                empty="此前没有待继续办理的服务"
              />
            </Panel>
          )}
        </div>
        <aside className="home-stack">
          {!noDuties && (
            <Panel title="我的班次" onMore={() => h.navigate("shifts")}>
              <Load resource={shifts} empty="所选日期暂无班次">
                {shifts.data?.rows.map((r) => (
                  <div className="shift-summary" key={r.id}>
                    <strong>
                      {timeText(r.starts_at)} — {timeText(r.ends_at)}
                    </strong>
                    <div>
                      容量 <b>{r.capacity}</b> 单{" "}
                      <span>
                        已接 <b>{r.used_count}</b> 单
                      </span>
                    </div>
                    <Status kind="shifts" row={r} />
                  </div>
                ))}
              </Load>
            </Panel>
          )}
          <Panel
            title="职业证书"
            className="caregiver-credentials"
            onMore={() => h.navigate("credentials")}
          >
            <Load resource={credentials} empty="暂无职业证书">
              {credentials.data?.rows.map((r) => (
                <div className="credential-row" key={r.id}>
                  <button
                    className="credential-thumb"
                    aria-label={`查看${r.certificate_type}证书图片`}
                    onClick={() =>
                      h.navigate("credentials", { field: "id", value: r.id })
                    }
                  >
                    <img src={certificateUrl(r)} alt="证书登记预览" />
                  </button>
                  <strong>{r.certificate_type || "职业证书"}</strong>
                  <span>有效至 {dateText(r.valid_until)}</span>
                  <Button
                    type="text"
                    onClick={() => h.details("credentials", r)}
                  >
                    查看证书
                  </Button>
                </div>
              ))}
            </Load>
          </Panel>
          <Panel title="现场协助" className="caregiver-help">
            {" "}
            <Button
              className="report-shortcut"
              icon={<IconHeart />}
              onClick={() =>
                h.open("event.create", { description: "健康异常：" })
              }
            >
              健康异常上报
            </Button>
            <Button
              className="report-shortcut"
              icon={<IconExclamationCircle />}
              onClick={() =>
                h.open("event.create", { description: "居家环境隐患：" })
              }
            >
              居家隐患上报
            </Button>
            <Button onClick={() => h.navigate("trainingRecords")}>
              查看培训记录
            </Button>
          </Panel>{" "}
        </aside>
      </div>
    </>
  );
}
function IconFileSymbol() {
  return <IconCalendar />;
}
