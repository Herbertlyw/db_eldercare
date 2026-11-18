import {
  IconUserGroup,
  IconCalendar,
  IconClockCircle,
  IconBook,
  IconFile,
  IconHeart,
  IconMessage,
  IconNotification,
  IconSafe,
  IconDashboard,
  IconCheckCircle,
} from "@arco-design/web-react/icon";

const icons: Record<string, typeof IconFile> = {
  elders: IconUserGroup,
  caregivers: IconUserGroup,
  recruitments: IconUserGroup,
  orders: IconCalendar,
  shifts: IconCalendar,
  assessments: IconCheckCircle,
  plans: IconBook,
  hours: IconClockCircle,
  hoursReview: IconClockCircle,
  credentials: IconSafe,
  trainingRecords: IconBook,
  trainings: IconBook,
  events: IconHeart,
  complaints: IconMessage,
  selfPay: IconFile,
  claims: IconSafe,
  claimProgress: IconSafe,
  inspections: IconCheckCircle,
  inspectionBatches: IconCheckCircle,
  organizationReports: IconDashboard,
  jurisdictionStats: IconDashboard,
  notifications: IconNotification,
  publicNotices: IconNotification,
  policies: IconBook,
};

export function NavigationIcon({ kind }: { kind: string }) {
  const Icon = icons[kind] || IconFile;
  return <Icon aria-hidden />;
}
