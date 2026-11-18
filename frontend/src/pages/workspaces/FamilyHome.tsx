import { useEffect, useState } from "react";
import { Button, Select, Space } from "@arco-design/web-react";
import {
  IconCalendar,
  IconExclamationCircle,
  IconMessage,
} from "@arco-design/web-react/icon";
import { CommunityCard, Person } from "../../components/Media";

import {
  HomeHeader,
  Load,
  Panel,
  RowList,
  Status,
  dateText,
  timeText,
  money,
  useHome,
  useRows,
} from "./shared";
function Care({ elderId }: { elderId: number }) {
  const h = useHome(),
    query = { field: "elder_id", value: elderId };
  const care = useRows("workspaceFamily", {
    field: "id",
    value: elderId,
    size: 1,
  });
  const next = useRows("workspaceUpcoming", { ...query, size: 1 });
  const confirmation = useRows("workspaceSchedule", {
    ...query,
    status: "CONFIRM",
    size: 3,
  });
  const recent = useRows("orders", { ...query, status: "COMPLETED", size: 3 });
  const emergency = useRows("workspaceEvents", { ...query, size: 1 });
  const e = care.data?.rows[0];
  return (
    <>
      <Load resource={care} empty="当前关联已失效，请联系社区管理员核实。">
        {e && (
          <>
            <div className="elder-overview">
              <div className="family-person">
                <span className="profile-caption">正在照护</span>
                <Person id={e.id} name={e.name} large />
              </div>
              <div className="elder-facts">
                <div>
                  <span>所属社区</span>
                  <strong>{e.community_name}</strong>
                </div>
                <div>
                  <span>责任护工</span>
                  <strong>{e.caregiver_name || "暂无"}</strong>
                </div>
                <div>
                  <span>评估有效至</span>
                  <strong>{dateText(e.valid_until)}</strong>
                </div>
              </div>
              <div className="elder-plan">
                <span>{e.bundle_name || "暂无照护计划"}</span>
                {e.plan_status && (
                  <span>
                    {{
                      ACTIVE: "生效中",
                      PAUSED: "已暂停",
                      EXPIRED: "已到期",
                      ENDED: "已结束",
                    }[String(e.plan_status)] || e.plan_status}
                  </span>
                )}
                <Button onClick={() => h.navigate("plans", query)}>
                  查看照护计划
                </Button>
              </div>
            </div>
            {(emergency.error || emergency.data?.total !== 0) && (
              <Panel title="应急事项" className="family-alert">
                <RowList
                  resource={emergency}
                  kind="events"
                  title={(r) => (
                    <>
                      待处理事件 #{r.id} <Status row={r} kind="events" />
                    </>
                  )}
                />
              </Panel>
            )}
            <div className="home-grid family-grid">
              <div className="home-stack">
                <Panel
                  className="family-next-panel"
                  title="下一次服务"
                  onMore={() => h.navigate("orders", query)}
                  more="全部预约"
                >
                  <Load resource={next} empty="暂无后续预约">
                    <div className="next-visit">
                      {next.data?.rows.map((r) => (
                        <div key={r.id}>
                          <div className="date-tile">
                            <span>{dateText(r.starts_at).slice(5, 7)}月</span>
                            <strong>{dateText(r.starts_at).slice(8)}日</strong>
                          </div>
                          <div className="next-copy">
                            <strong>
                              {timeText(r.starts_at)} — {timeText(r.ends_at)}
                            </strong>
                            <h3>{r.item_name}</h3>
                            <span>护工：{r.caregiver_name || "待安排"}</span>
                          </div>
                          <div>
                            <Status row={r} kind="orders" />
                            <Button
                              type="text"
                              onClick={() => h.details("orders", r)}
                            >
                              查看预约
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </Load>
                  {!next.loading && !next.error && next.data?.total === 0 && (
                    <Button
                      type="primary"
                      onClick={() => h.open("order.create", { elderId })}
                    >
                      预约服务
                    </Button>
                  )}
                </Panel>
                <Panel
                  className="family-confirm-panel"
                  title="待你确认"
                  onMore={() =>
                    h.navigate("orders", { ...query, status: "CONFIRM" })
                  }
                >
                  <RowList
                    resource={confirmation}
                    kind="orders"
                    title={(r) => r.item_name}
                    subtitle={(r) =>
                      `${dateText(r.starts_at)} ${timeText(r.starts_at)} · 服务记录已提交`
                    }
                  />
                </Panel>
                <Panel
                  title="最近服务"
                  onMore={() =>
                    h.navigate("orders", { ...query, status: "COMPLETED" })
                  }
                >
                  <RowList
                    resource={recent}
                    kind="orders"
                    title={(r) => r.item_name}
                    subtitle={(r) =>
                      `${dateText(r.starts_at)} · ${r.elder_name}`
                    }
                    empty="暂无已完成服务"
                  />
                </Panel>
              </div>
              <aside className="home-stack">
                <Panel title="费用与补贴" className="family-cost-panel">
                  <div className="amount-caption">累计已完成服务</div>
                  <dl className="amount-list">
                    <div>
                      <dt>服务费用</dt>
                      <dd>¥{money(e.service_amount)}</dd>
                    </div>
                    <div>
                      <dt>已拨补贴</dt>
                      <dd>¥{money(e.paid_subsidy)}</dd>
                    </div>
                    <div className="amount-total">
                      <dt>扣除已拨补贴后</dt>
                      <dd>
                        ¥
                        {money(
                          Number(e.service_amount) - Number(e.paid_subsidy),
                        )}
                      </dd>
                    </div>
                  </dl>
                  <p className="home-note">
                    待审核、待拨付金额不计入已拨补贴。
                  </p>
                  <Space wrap>
                    <Button
                      onClick={() =>
                        h.navigate("selfPay", {
                          field: "elder_id",
                          value: elderId,
                        })
                      }
                    >
                      费用明细
                    </Button>
                    <Button onClick={() => h.navigate("claimProgress", query)}>
                      补贴进度
                    </Button>
                  </Space>
                </Panel>
                <Panel title="联系与反馈" className="family-contact-panel">
                  <div className="home-shortcuts">
                    <Button
                      icon={<IconMessage />}
                      onClick={() => h.navigate("complaints", query)}
                    >
                      评价与服务反馈
                    </Button>
                    <Button
                      icon={<IconExclamationCircle />}
                      onClick={() => h.open("event.create", { elderId })}
                    >
                      上报异常
                    </Button>
                  </div>
                </Panel>
                <CommunityCard />
              </aside>
            </div>
          </>
        )}
      </Load>
    </>
  );
}
export function FamilyHome() {
  const h = useHome();
  const [search, setSearch] = useState("");
  const elders = useRows("workspaceFamily", { size: 20, search });
  const id = h.selectedElder;
  useEffect(() => {
    if (id === undefined && !search && elders.data?.rows[0])
      h.selectElder(elders.data.rows[0].id);
  }, [id, search, elders.data, h.selectElder]);
  return (
    <>
      <HomeHeader title="老人照护" subtitle="预约、服务记录与补贴进度">
        <Button
          type="primary"
          icon={<IconCalendar />}
          disabled={!id}
          onClick={() => h.open("order.create", { elderId: id })}
        >
          预约上门服务
        </Button>
      </HomeHeader>
      <div className="elder-picker">
        <label>当前老人</label>
        <Select
          aria-label="切换老人"
          showSearch
          filterOption={false}
          onSearch={setSearch}
          value={id}
          onChange={h.selectElder}
          loading={elders.loading}
          placeholder="按姓名查找关联老人"
          options={elders.data?.rows.map((r) => ({
            value: r.id,
            label: r.name,
          }))}
        />
        {elders.error && <Button onClick={elders.retry}>重试</Button>}
      </div>
      {id ? (
        <Care key={id} elderId={id} />
      ) : (
        <Load
          resource={elders}
          empty="尚未关联老人，请联系社区管理员办理关联。"
        >
          {null}
        </Load>
      )}
    </>
  );
}
