import { HomeContext, type HomeProps } from "./shared";
import { FamilyHome } from "./FamilyHome";
import { CaregiverHome } from "./CaregiverHome";
import { AdminHome } from "./AdminHome";
import { ManagerHome } from "./ManagerHome";
import { StreetHome } from "./StreetHome";
import { RegulatorHome } from "./RegulatorHome";
import "../../styles/workspaces.css";
const pages = {
  FAMILY: FamilyHome,
  CAREGIVER: CaregiverHome,
  ADMIN: AdminHome,
  MANAGER: ManagerHome,
  STREET: StreetHome,
  REGULATOR: RegulatorHome,
};
export function RoleHome(props: HomeProps) {
  const Page = pages[props.user.role];
  return (
    <HomeContext.Provider value={props}>
      <section
        className={"role-home role-" + props.user.role.toLowerCase()}
        data-role={props.user.role}
      >
        <Page />
      </section>
    </HomeContext.Provider>
  );
}
