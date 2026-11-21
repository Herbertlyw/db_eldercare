import { useAvailablePage } from "../../hooks/useAvailablePage";
import { useState } from "react";
import { Button, Progress, Select, Table } from "@arco-design/web-react";
import { IconPlus, IconUserGroup } from "@arco-design/web-react/icon";
import {
  HomeHeader,
  Load,
  Panel,
  Metric,
  Pager,
  RowList,
  useHome,
  useRows,
} from "./shared";
export function RegulatorHome() {
  const h = useHome(),
    [page, setPage] = useState(1),
    [community, setCommunity] = useState<number>(),
    [search, setSearch] = useState("");
  const communities = useRows("communities", { size: 30, search });
  const filter = community ? { field: "community_id", value: community } : {};
  const stats = useRows("jurisdictionStats", {
    ...(community ? { field: "id", value: community } : {}),
    page: page - 1,
    size: 5,
  });
  const summary = useRows(
    community ? "jurisdictionStats" : "jurisdictionSummary",
    { ...(community ? { field: "id", value: community } : {}), size: 1 },
  );
  useAvailablePage(stats.data, setPage);
  const row = summary.data?.rows[0];
  const inspections = useRows("inspections", { status: "REVIEW", size: 3 });
  const events = useRows("pendingEvents", { ...filter, size: 2 });
  return (
    <>
      <HomeHeader title="辖区监管" subtitle="服务覆盖、分级审核与质量监管" />
      <div className="home-toolbar regulator-scope">
        <label>授权社区</label>
        <Select
          aria-label="监管社区范围"
          showSearch
          filterOption={false}
          onSearch={setSearch}
          allowClear
          placeholder="全部授权社区"
          value={community}
          onChange={(v) => {
            setCommunity(v);
            setPage(1);
          }}
          options={communities.data?.rows.map((r) => ({
            label: r.name,
            value: r.id,
          }))}
          loading={communities.loading}
        />
        {communities.error && (
          <Button onClick={communities.retry}>重新加载社区</Button>
        )}
        <span>统计月份 {h.profile?.period || "—"}</span>
      </div>
      <div className="regulator-summary">
        <Load resource={summary} empty="暂无授权社区统计">
          <div className="scope-stat">
            <IconUserGroup />
            <span>统计社区</span>
            <strong>
              {community ? stats.data?.total : row?.community_count}
            </strong>
            <small>{community ? "当前选定社区" : "全部授权范围"}</small>
          </div>
          <div className="rate-stat">
            <div className="rate-copy">
              <span>本月服务覆盖率</span>
              <strong>
                {row?.coverage_rate == null ? "—" : row.coverage_rate + "%"}
              </strong>
              <small>
                {row?.served_elders} / {row?.active_elders} 位在册老人
              </small>
            </div>
            {row?.coverage_rate != null && (
              <Progress
                className="rate-ring"
                type="circle"
                width={82}
                strokeWidth={7}
                percent={Number(row.coverage_rate)}
                showText={false}
                color="#4779d4"
                trailColor="#eaf0fa"
              />
            )}
          </div>
          <div className="rate-stat">
            <div className="rate-copy">
              <span>及时响应率</span>
              <strong>
                {row?.response_rate == null ? "—" : row.response_rate + "%"}
              </strong>
              <small>
                {row?.timely_responses} / {row?.response_denominator} 起有效事件
              </small>
            </div>
            {row?.response_rate != null && (
              <Progress
                className="rate-ring"
                type="circle"
                width={82}
                strokeWidth={7}
                percent={Number(row.response_rate)}
                showText={false}
                color="#7c91c5"
                trailColor="#edf0f7"
              />
            )}
          </div>
        </Load>
      </div>
      <div className="home-grid regulator-grid">
        <div className="home-stack">
          <Panel
            className="regulator-coverage"
            title="辖区服务情况"
            onMore={() =>
              h.navigate(
                "jurisdictionStats",
                community ? { field: "id", value: community } : {},
              )
            }
          >
            <Load resource={stats} empty="没有符合范围的社区">
              <Table
                size="small"
                rowKey="id"
                border={false}
                data={stats.data?.rows || []}
                pagination={false}
                scroll={{ x: 540 }}
                columns={[
                  { title: "社区", dataIndex: "name" },
                  { title: "在册老人", dataIndex: "active_elders" },
                  { title: "本月已服务", dataIndex: "served_elders" },
                  {
                    title: "覆盖率",
                    render: (_, r) => (
                      <div className="coverage-cell">
                        <span>
                          {r.coverage_rate == null
                            ? "—"
                            : r.coverage_rate + "%"}
                        </span>
                        {r.coverage_rate != null && (
                          <Progress
                            color="#165DFF"
                            percent={Number(r.coverage_rate)}
                            showText={false}
                          />
                        )}
                      </div>
                    ),
                  },
                ]}
              />
              <Pager resource={stats} page={page} onChange={setPage} />
            </Load>
          </Panel>
          <Panel
            className="regulator-review"
            title="整改复核 · 全部授权范围"
            onMore={() => h.navigate("inspections", { status: "REVIEW" })}
          >
            <RowList
              resource={inspections}
              kind="inspections"
              title={(r) => r.issue || `抽查 #${r.id}`}
              subtitle={(r) => r.rectification}
            />
            <Button
              type="primary"
              icon={<IconPlus />}
              onClick={() => h.open("inspection.batch")}
            >
              创建抽查批次
            </Button>
          </Panel>
        </div>
        <aside className="home-stack regulator-actions">
          <h2 className="regulator-action-title">审核与拨付</h2>
          <Metric
            label="补贴终审"
            kind="claims"
            status="STREET_APPROVED"
            query={filter}
          />
          <Metric
            label="拨付登记"
            kind="claims"
            status="APPROVED"
            query={filter}
          />
          <Metric
            label="机构材料 · 全部授权范围"
            kind="organizationReports"
            status="SUBMITTED"
          />
          <Panel
            title="待跟进应急"
            onMore={() => h.navigate("pendingEvents", filter)}
          >
            <RowList
              resource={events}
              kind="events"
              title={(r) => `事件 #${r.id} · ${r.level}级`}
              subtitle={(r) =>
                r.arrived_at
                  ? "已到场，待结案"
                  : r.status === "RESPONDED"
                    ? "已响应，待到场"
                    : r.remaining_seconds < 0
                      ? "尚未响应，已超时"
                      : "等待响应"
              }
            />
          </Panel>
          <Button onClick={() => h.navigate("publicNotices")}>
            查看质量公示
          </Button>
        </aside>
      </div>
    </>
  );
}
