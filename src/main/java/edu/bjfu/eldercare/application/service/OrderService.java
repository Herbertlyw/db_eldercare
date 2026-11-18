package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.decimal;
import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.rawNumber;
import static edu.bjfu.eldercare.application.support.Inputs.rawText;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.support.Inputs;
import edu.bjfu.eldercare.domain.order.ServiceOrder;
import edu.bjfu.eldercare.domain.order.ServicePrice;
import edu.bjfu.eldercare.domain.repository.OrderRepository;
import edu.bjfu.eldercare.domain.shared.Position;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 预约、派单和服务履约。 */
@Component
public class OrderService implements ActionModule {

    private final OrderRepository repository;

    private final Scope scope;

    private final RecordAccess access;

    public OrderService(OrderRepository repository, Scope scope, RecordAccess access) {
        this.repository = repository;
        this.scope = scope;
        this.access = access;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("order.create", this::createOrder),
                Map.entry("order.reschedule", this::reschedule),
                Map.entry("order.recommend", this::recommendOrder),
                Map.entry("order.assign", this::assignOrder),
                Map.entry("order.accept", this::acceptOrder),
                Map.entry("order.checkin", this::checkinOrder),
                Map.entry("order.finish", this::finishOrder),
                Map.entry("order.confirm", this::confirmOrder),
                Map.entry("order.cancel", this::cancelOrder),
                Map.entry("order.release", this::releaseOrder),
                Map.entry("order.reject", this::releaseOrder));
    }

    private Object createOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "FAMILY");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        var e = repository.findElder(recordId);
        var item = repository.findServiceItem(rawNumber(input, "itemId"));
        var start = parseTime(rawText(input, "startsAt"));
        var end = start.plusMinutes(id(item, "duration_minutes"));
        if (start.isBefore(LocalDateTime.now())) {
            throw new Rule("预约时间必须在未来");
        }
        return repository.insertOrder(
                recordId,
                (Number) item.get("id"),
                (Number) e.get("community_id"),
                start,
                end,
                (String) e.get("address"),
                user.role().equals("FAMILY")
                        ? "FAMILY"
                        : Inputs.choice(
                                Map.of("channel", Inputs.optional(input, "channel", "COMMUNITY")),
                                "channel",
                                "PHONE",
                                "COMMUNITY"),
                price(item),
                user.id(),
                (Number) e.get("position_x"),
                (Number) e.get("position_y"));
    }

    private Object reschedule(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long orderId = number(input, "orderId");
        var order = access.activeOrder(user, orderId);
        if (!"PENDING".equals(order.get("status")) || repository.windowOpen(orderId)) {
            throw new Rule("只能改约已错过时段的待派订单");
        }
        var start = parseTime(text(input, "startsAt"));
        var duration =
                Duration.between(
                        (LocalDateTime) order.get("starts_at"),
                        (LocalDateTime) order.get("ends_at"));
        var end = start.plus(duration);
        repository.assertOrderEligible(
                id(order, "elder_id"), id(order, "item_id"), id(order, "community_id"), start, end);
        if (repository.reschedule(orderId, start, end) != 1) throw new Rule("请选择将来的预约时间");
        return "已更新预约时间";
    }

    private Object recommendOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        return recommend(user, rawNumber(input, "orderId"));
    }

    private Object assignOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = rawNumber(input, "orderId");
        access.activeOrder(user, recordId);
        requireOpenWindow(recordId);
        repository.assignOrder(recordId, rawNumber(input, "shiftId"), rawText(input, "reason"));
        return "派单成功";
    }

    private Object acceptOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "CAREGIVER");
        long recordId = rawNumber(input, "orderId");
        var order = access.activeOrder(user, recordId);
        orderState(order).requireAcceptable();
        var dispatch = repository.findWaitingDispatch(recordId);
        repository.assertWorkerEligible(recordId, (Number) dispatch.get("shift_id"));
        repository.acceptDispatch((Number) dispatch.get("id"));
        return repository.markAccepted(recordId);
    }

    private Object checkinOrder(Auth.User user, Map<String, Object> input) {
        return attendance(user, input, false);
    }

    private Object finishOrder(Auth.User user, Map<String, Object> input) {
        return attendance(user, input, true);
    }

    private Object confirmOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "FAMILY");
        long recordId = rawNumber(input, "orderId");
        var order = access.activeOrder(user, recordId);
        orderState(order).requireConfirmable();
        repository.recordConfirmation(user.id(), rawText(input, "confirmation"), recordId);
        return repository.markCompleted(recordId);
    }

    private Object cancelOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "FAMILY");
        long recordId = rawNumber(input, "orderId");
        var order = access.activeOrder(user, recordId);
        orderState(order).requireCancellable();
        var ds = repository.findActiveDispatches(recordId);
        for (var z : ds) {
            repository.rejectDispatch(rawText(input, "reason"), (Number) z.get("id"));
            repository.releaseShiftCapacity((Number) z.get("shift_id"));
        }
        return repository.markCancelled(recordId);
    }

    private Object releaseOrder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER", "CAREGIVER");
        long recordId = number(input, "orderId");
        repository.lockOrder(recordId);
        var record = scope.order(user, recordId);
        orderState(record).requireReleasable();
        var dispatch = repository.findActiveDispatch(recordId);
        scope.worker(user, id(dispatch, "caregiver_id"));
        repository.rejectDispatch(text(input, "reason"), (Number) dispatch.get("id"));
        repository.releaseShiftCapacity((Number) dispatch.get("shift_id"));
        return repository.markPending(recordId);
    }

    private boolean fence(Map<String, Object> order, BigDecimal x, BigDecimal y) {
        long radius = repository.findFenceRadius((Number) order.get("community_id"));
        return new Position(x.doubleValue(), y.doubleValue())
                .within(
                        new Position(
                                ((Number) order.get("position_x")).doubleValue(),
                                ((Number) order.get("position_y")).doubleValue()),
                        radius);
    }

    private Object attendance(Auth.User user, Map<String, Object> input, boolean finish) {
        Auth.role(user, "CAREGIVER");
        long orderId = number(input, "orderId");
        repository.lockOrder(orderId);
        var order = scope.order(user, orderId);
        orderState(order).requireServiceStage(finish);
        var dispatch = repository.findAcceptedDispatch(orderId);
        scope.worker(user, id(dispatch, "caregiver_id"));
        BigDecimal x = decimal(input, "x"), y = decimal(input, "y");
        boolean inside = fence(order, x, y);
        if (!finish) {
            repository.assertWorkerEligible(orderId, (Number) dispatch.get("shift_id"));
            repository.insertCheckin(
                    (Number) dispatch.get("id"), text(input, "location"), inside, x, y);
            return repository.markServing(orderId);
        }
        repository.recordCheckout(x, y, inside, (Number) dispatch.get("id"));
        repository.verifyHours((Number) dispatch.get("id"));
        repository.insertServiceRecord((Number) dispatch.get("id"), text(input, "notes"));
        repository.finishDispatch((Number) dispatch.get("id"));
        return repository.markAwaitingConfirmation(orderId);
    }

    private static BigDecimal price(Map<String, Object> item) {
        return new ServicePrice(
                        (BigDecimal) item.get("price"),
                        ServicePrice.Unit.valueOf((String) item.get("unit")))
                .forMinutes(id(item, "duration_minutes"));
    }

    private static LocalDateTime parseTime(String text) {
        try {
            return LocalDateTime.parse(text);
        } catch (java.time.format.DateTimeParseException e) {
            throw new Rule("时间格式应为 yyyy-MM-ddTHH:mm");
        }
    }

    private List<Map<String, Object>> recommend(Auth.User user, long orderId) {
        var order = access.activeOrder(user, orderId);
        orderState(order).requireDispatchable();
        requireOpenWindow(orderId);
        repository.assertOrderEligible(
                (Number) order.get("elder_id"),
                (Number) order.get("item_id"),
                (Number) order.get("community_id"),
                (LocalDateTime) order.get("starts_at"),
                (LocalDateTime) order.get("ends_at"));
        // Suggestions are advisory; assign_order rechecks under locks before reserving capacity.
        return repository.findRecommendations(
                (LocalDateTime) order.get("starts_at"),
                (LocalDateTime) order.get("ends_at"),
                orderId,
                (Number) order.get("community_id"),
                (Number) order.get("item_id"),
                (LocalDateTime) order.get("starts_at"),
                (LocalDateTime) order.get("ends_at"),
                orderId,
                (LocalDateTime) order.get("ends_at"),
                (LocalDateTime) order.get("starts_at"));
    }

    private void requireOpenWindow(long orderId) {
        if (!repository.windowOpen(orderId)) throw new Rule("预约时段已结束，请联系改约或取消");
    }

    private ServiceOrder orderState(Map<String, Object> row) {
        return new ServiceOrder(
                id(row, "id"), ServiceOrder.Status.valueOf((String) row.get("status")));
    }
}
