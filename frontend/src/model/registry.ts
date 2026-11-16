import data from "./registry.json";
import type { ActionDefinition, Role } from "./types";
export const names: Record<string, string> = data.names;
export const menus = data.menus as Record<Role, string[]>;
export const actions = data.actions as unknown as Record<
  string,
  ActionDefinition
>;
export const columns: Record<string, string> = data.columns;
export const values: Record<string, string> = data.values;
export const groups = data.groups as [string, string[]][];
export const roleNames = data.roleNames as Record<Role, string>;
export const errors: Record<string, string> = data.errors;
export const actionPrefixes: Record<string, string[]> = data.actionPrefixes;
export const detailKinds: Record<string, string> = data.detailKinds;
export const statusLabels: Record<
  string,
  Record<string, string>
> = data.statusLabels;
export const tasks = data.tasks as Record<Role, [string, string, string][]>;
export const allowedActions = (role: Role) =>
  Object.keys(actions).filter((k) => actions[k][1].includes(role));
export const relatedActions = (kind: string, role: Role) =>
  allowedActions(role).filter((a) =>
    (actionPrefixes[kind] || []).some((p) => a.startsWith(p)),
  );
