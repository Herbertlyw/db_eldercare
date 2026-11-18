import { recordKey } from "../src/model/presentation";
import { describe, expect, it } from "vitest";
import { certificateSvg, duration } from "../src/pages/workforce/metrics";

describe("service hours and credential presentation", () => {
  it("同一护工的不同月份有独立行标识", () => {
    expect(recordKey("monthlyHours", { id: 5, period: "2026-09" })).not.toBe(
      recordKey("monthlyHours", { id: 5, period: "2026-10" }),
    );
    expect(recordKey("hours", { id: 5 })).toBe("5");
  });
  it("shows exact full hours without decimal rounding", () => {
    expect(duration(60)).toBe("1 小时");
    expect(duration(59)).toBe("0 小时 59 分钟");
    expect(duration(0)).toBe("0 小时");
  });
  it("escapes stored text in downloadable certificate images and retains dates", () => {
    const svg = certificateSvg({
      caregiver_name: '林<script>&"',
      certificate_no: "CERT-200",
      certificate_type: "养老护理员",
      certificate_level: "初级",
      valid_from: "2026-11-17T10:20:00",
      valid_until: "2027-11-17T10:20:00",
    });
    expect(svg).not.toContain("<script>");
    expect(svg).toContain("林&lt;script&gt;&amp;&quot;");
    expect(svg).toContain("CERT-200");
    expect(svg).toContain("2027-11-17 10:20");
    expect(svg).toContain("非发证机构原件");
  });
});
