export type Role =
  "ADMIN" | "FAMILY" | "CAREGIVER" | "MANAGER" | "STREET" | "REGULATOR";
export interface User {
  id: number;
  name: string;
  role: Role;
  community: number;
  organization?: number;
  mustChangePassword?: boolean;
}
// SQL projections vary by query; scalar values and nested details are interpreted at their UI boundary.
export type Row = Record<string, any>;
export type Values = Record<string, any>;
export type ActionDefinition = [string, Role[], string[]];
export interface ListResult {
  rows: Row[];
  total: number;
  states?: string[];
  dateField?: string;
}
export type Criteria = {
  search?: string;
  status?: string;
  from?: string;
  to?: string;
  field?: string;
  value?: string | number;
};
export interface Choice {
  action: string;
  label: string;
  preset: Values;
}
export interface Operation {
  action: string;
  preset: Values;
  context?: { kind: string; row: Row };
  serial: number;
}
