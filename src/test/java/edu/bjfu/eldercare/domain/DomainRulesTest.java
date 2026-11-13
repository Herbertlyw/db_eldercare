package edu.bjfu.eldercare.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bjfu.eldercare.domain.care.CarePlan;
import edu.bjfu.eldercare.domain.feedback.Complaint;
import edu.bjfu.eldercare.domain.monitoring.EmergencyEvent;
import edu.bjfu.eldercare.domain.order.ServiceOrder;
import edu.bjfu.eldercare.domain.order.ServicePrice;
import edu.bjfu.eldercare.domain.shared.Position;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.domain.subsidy.SubsidyClaim;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DomainRulesTest {
    private void check(boolean allowed, Executable operation) {
        if (allowed) {
            assertDoesNotThrow(operation);
        } else {
            assertThrows(Rule.class, operation);
        }
    }

    @ParameterizedTest
    @EnumSource(ServiceOrder.Status.class)
    void ordersEnforceEachStage(ServiceOrder.Status status) {
        var order = new ServiceOrder(1, status);
        check(status == ServiceOrder.Status.ASSIGNED, order::requireAcceptable);
        check(status == ServiceOrder.Status.CONFIRM, order::requireConfirmable);
        check(
                Set.of(
                                ServiceOrder.Status.PENDING,
                                ServiceOrder.Status.ASSIGNED,
                                ServiceOrder.Status.ACCEPTED)
                        .contains(status),
                order::requireCancellable);
        check(
                Set.of(
                                ServiceOrder.Status.PENDING,
                                ServiceOrder.Status.ASSIGNED,
                                ServiceOrder.Status.ACCEPTED)
                        .contains(status),
                order::requireDispatchable);
        check(
                Set.of(ServiceOrder.Status.ASSIGNED, ServiceOrder.Status.ACCEPTED).contains(status),
                order::requireReleasable);
        check(status == ServiceOrder.Status.ACCEPTED, () -> order.requireServiceStage(false));
        check(status == ServiceOrder.Status.SERVING, () -> order.requireServiceStage(true));
        check(status == ServiceOrder.Status.COMPLETED, order::requireReviewable);
    }

    @ParameterizedTest
    @EnumSource(Complaint.Status.class)
    void complaintsCannotSkipOrRepeatStages(Complaint.Status status) {
        var complaint = new Complaint(1, status);
        check(status == Complaint.Status.SUBMITTED, complaint::requireAssignable);
        check(status == Complaint.Status.ASSIGNED, complaint::requireReplyable);
        check(status == Complaint.Status.REPLIED, complaint::requireClosable);
    }

    @ParameterizedTest
    @EnumSource(CarePlan.Status.class)
    void endedPlansCannotBeReactivated(CarePlan.Status status) {
        var plan = new CarePlan(1, status);
        check(
                status == CarePlan.Status.ACTIVE || status == CarePlan.Status.PAUSED,
                plan::requireStateChangeable);
    }

    @ParameterizedTest
    @EnumSource(SubsidyClaim.Status.class)
    void subsidyReviewCannotSkipLevelsOrPayTwice(SubsidyClaim.Status status) {
        var claim = new SubsidyClaim(1, status);
        check(
                status == SubsidyClaim.Status.SUBMITTED,
                () ->
                        assertEquals(
                                SubsidyClaim.Status.STREET_APPROVED,
                                claim.review(SubsidyClaim.ReviewStage.STREET, true)));
        check(
                status == SubsidyClaim.Status.STREET_APPROVED,
                () ->
                        assertEquals(
                                SubsidyClaim.Status.APPROVED,
                                claim.review(SubsidyClaim.ReviewStage.REGULATOR, true)));
        check(status == SubsidyClaim.Status.APPROVED, claim::requirePayable);
        check(status == SubsidyClaim.Status.REJECTED, claim::requireResubmittable);
    }

    @Test
    void rejectionAtEitherLevelReturnsToEvidenceCorrection() {
        assertEquals(
                SubsidyClaim.Status.REJECTED,
                new SubsidyClaim(1, SubsidyClaim.Status.SUBMITTED)
                        .review(SubsidyClaim.ReviewStage.STREET, false));
        assertEquals(
                SubsidyClaim.Status.REJECTED,
                new SubsidyClaim(1, SubsidyClaim.Status.STREET_APPROVED)
                        .review(SubsidyClaim.ReviewStage.REGULATOR, false));
    }

    @ParameterizedTest
    @EnumSource(EmergencyEvent.Status.class)
    void emergencyNeedsResponseAndArrivalBeforeClosure(EmergencyEvent.Status status) {
        var waiting = new EmergencyEvent(1, status, false);
        var arrived = new EmergencyEvent(1, status, true);
        boolean open =
                status == EmergencyEvent.Status.UNCONFIRMED || status == EmergencyEvent.Status.OPEN;
        check(open, waiting::requireRespondable);
        check(open, waiting::requireDismissible);
        check(status == EmergencyEvent.Status.UNCONFIRMED, waiting::requireConfirmable);
        check(status == EmergencyEvent.Status.RESPONDED, waiting::requireArrivalRecordable);
        check(false, arrived::requireArrivalRecordable);
        check(false, waiting::requireClosable);
        check(status == EmergencyEvent.Status.RESPONDED, arrived::requireClosable);
    }

    @Test
    void hourlyPriceRoundsHalfUpToCents() {
        assertEquals(
                new BigDecimal("12.38"),
                new ServicePrice(new BigDecimal("16.50"), ServicePrice.Unit.HOUR).forMinutes(45));
    }

    @Test
    void visitPriceDoesNotDependOnDuration() {
        var price = new ServicePrice(new BigDecimal("30.00"), ServicePrice.Unit.VISIT);
        assertEquals(new BigDecimal("30.00"), price.forMinutes(20));
        assertEquals(new BigDecimal("30.00"), price.forMinutes(90));
    }

    @Test
    void fenceIncludesBoundaryButRejectsOutside() {
        var center = new Position(10, 20);
        assertTrue(new Position(13, 24).within(center, 5));
        assertTrue(center.within(center, 0));
        assertFalse(new Position(13, 24.001).within(center, 5));
        assertFalse(new Position(Double.NaN, 20).within(center, 5));
    }
}
