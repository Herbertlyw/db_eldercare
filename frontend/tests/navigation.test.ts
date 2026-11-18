import { primaryNavigation } from "../src/pages/workspaces/navigation";
import { describe, it, expect } from "vitest";
import {
  readLocation,
  writeLocation,
} from "../src/pages/workspaces/navigation";
describe("role workspace navigation", () => {
  it("opens the role home by default and refuses another role route", () => {
    expect(readLocation("FAMILY", "").kind).toBe("home");
    expect(readLocation("FAMILY", "#recruitments").kind).toBe("home");
    expect(readLocation("MANAGER", "#recruitments").kind).toBe("recruitments");
  });
  it("preserves linked elder, status and date criteria across reload", () => {
    const criteria = {
      field: "elder_id",
      value: 1001,
      status: "CONFIRM",
      from: "2026-11-17",
      to: "2026-11-17",
      search: "周 惠琴",
    };
    const route = readLocation("FAMILY", writeLocation("orders", criteria, 3));
    expect(route).toEqual({
      kind: "orders",
      criteria: { ...criteria, value: "1001" },
      page: 3,
    });
  });
  it("does not carry business filters into the home route", () => {
    expect(writeLocation("home", { field: "elder_id", value: 1 }, 9)).toBe(
      "#home",
    );
    expect(readLocation("FAMILY", "#orders?page=-2").page).toBe(1);
  });
});

import { menus } from "../src/model/registry";
import type { Role } from "../src/model/types";
it("keeps primary navigation within role permissions without duplicate routes", () => {
  for (const role of Object.keys(primaryNavigation) as Role[]) {
    const keys = primaryNavigation[role].map(([k]) => k);
    expect(new Set(keys).size).toBe(keys.length);
    expect(keys.every((k) => menus[role].includes(k))).toBe(true);
  }
});
