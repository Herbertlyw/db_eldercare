import { Spin } from "@arco-design/web-react";
import {
  IconClockCircle,
  IconCheckCircle,
  IconExclamationCircle,
  IconCloseCircle,
} from "@arco-design/web-react/icon";
import type { HoursTotals } from "../../model/types";
import { duration } from "./metrics";

export function HoursSummary({
  stats,
  loading,
}: {
  stats?: HoursTotals;
  loading: boolean;
}) {
  return (
    <section className="hours-overview" aria-label="工时汇总">
      <div className="hours-overview-heading">
        <strong>工时概览</strong>
        <span>按当前筛选统计全部记录，仅有效工时计入合计</span>
      </div>
      <Spin loading={loading} style={{ width: "100%" }}>
        <div className="hours-metrics">
          <div className="hours-metric primary">
            <IconClockCircle />
            <div>
              <span>总有效工时</span>
              <strong data-metric="duration">
                {stats ? duration(stats.minutes) : "—"}
              </strong>
              <small>{stats ? `${stats.minutes} 分钟` : "正在汇总"}</small>
            </div>
          </div>
          <div className="hours-metric">
            <IconCheckCircle />
            <div>
              <span>有效记录</span>
              <strong data-metric="valid">
                {stats?.valid ?? "—"}
                <small> 笔</small>
              </strong>
            </div>
          </div>
          <div className="hours-metric pending">
            <IconExclamationCircle />
            <div>
              <span>待核验</span>
              <strong data-metric="pending">
                {stats?.pending ?? "—"}
                <small> 笔</small>
              </strong>
            </div>
          </div>
          <div className="hours-metric muted">
            <IconCloseCircle />
            <div>
              <span>无效记录</span>
              <strong data-metric="invalid">
                {stats?.invalid ?? "—"}
                <small> 笔</small>
              </strong>
            </div>
          </div>
        </div>
      </Spin>
    </section>
  );
}
