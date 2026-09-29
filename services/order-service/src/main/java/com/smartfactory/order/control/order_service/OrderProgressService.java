package com.smartfactory.order.control.order_service;

import static com.smartfactory.common.exception.AssemblyServiceExceptions.VEHICLE_PRODUCTION_NOT_FOUND_ERROR_CODE;
import static com.smartfactory.common.exception.OrderErrorCodes.ORDER_NOT_FOUND;

import java.time.LocalDateTime;

import com.smartfactory.common.dto.assembly.VehicleProductionView;
import com.smartfactory.common.dto.order.OrderProgressView;
import com.smartfactory.common.enums.OrderStatus;
import com.smartfactory.common.enums.ProductionStage;
import com.smartfactory.common.exception.BusinessException;
import com.smartfactory.common.payloads.quality_service.QualityApprovedPayload;
import com.smartfactory.common.payloads.quality_service.QualityReworkRequiredPayload;
import com.smartfactory.order.control.mapper.OrderCompletedPayloadMapper;
import com.smartfactory.order.control.mapper.OrderStatusUpdatedPayloadMapper;
import com.smartfactory.order.control.publisher.OrderEventPublisher;
import com.smartfactory.order.control.repository.OrderRepository;
import com.smartfactory.order.entity.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class OrderProgressService {
    private static final Logger log = LoggerFactory.getLogger(OrderProgressService.class);
    @Inject
    OrderRepository repo;
    @Inject
    OrderEventPublisher publisher;
    @Inject
    OrderCompletedPayloadMapper payloadMapper;
    @Inject
    @RestClient
    AssemblyClient assemblyClient;

    public OrderProgressView getProgressView(final String orderId) {

        final Order order = repo.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException("Order not found: " + orderId, ORDER_NOT_FOUND, 404));

        return new OrderProgressView(order.getOrderId(), order.getVehicleId(), order.getStatus(),
                resolveCurrentFactoryStage(order), order.getCompletionDate(), order.getLastModifiedDate());
    }

    private ProductionStage resolveCurrentFactoryStage(final Order order) {

        try {
            final VehicleProductionView vehicleProduction = assemblyClient.findByVehicleId(order.getVehicleId());

            return vehicleProduction.currentStage();
        } catch (final WebApplicationException e) {

            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                throw new BusinessException("Vehicle production not found for vehicle: " + order.getVehicleId(),
                        VEHICLE_PRODUCTION_NOT_FOUND_ERROR_CODE, 404);
            }

            throw e;
        }
    }

    @Transactional
    public void completeOrderFromQualityApproval(final String eventId, final QualityApprovedPayload payload) {
        try {

            log.info("Processing quality-approved event for vehicle {}", payload.vehicleId());

            // Idempotency check
            if (repo.isEventProcessed(eventId)) {
                log.debug("Event {} already processed", eventId);
                return;
            }

            final var order = repo.findByVehicleId(payload.vehicleId()).orElseThrow(
                    () -> new BusinessException("Order not found for vehicle " + payload.vehicleId(), "ORDER_NOT_FOUND",
                            404));

            // Validate status transition
            OrderStatusTransitionHelper.checkTransition(order.getStatus(), OrderStatus.COMPLETED);

            order.setStatus(OrderStatus.COMPLETED);
            order.setLastModifiedDate(LocalDateTime.now());
            order.setCompletionDate(LocalDateTime.now());
            repo.update(order);

            // Mark event as processed
            repo.markEventProcessed(eventId);

            // Emit order-completed event
            publisher.publishOrderCompleted(payloadMapper.toPayload(order));
            log.info("Order {} completed for vehicle {}", order.getOrderId(), payload.vehicleId());
        } catch (final BusinessException e) {
            log.error("Business error: {}", e.getErrorCode(), e);
            throw e;
        } catch (final Exception e) {
            log.error("Unexpected error processing quality-approved", e);
            throw new BusinessException("Failed to complete order from quality approval", "ORDER_COMPLETION_ERROR", e);
        }
    }

    @Transactional
    public void markOrderForRework(final @NotBlank String eventId, final QualityReworkRequiredPayload payload) {
        if (repo.isEventProcessed(eventId)) {
            return;
        }

        final var order = repo.findByVehicleId(payload.vehicleId()).orElseThrow(
                () -> new BusinessException("Order not found for vehicle " + payload.vehicleId(), "ORDER_NOT_FOUND",
                        404));

        OrderStatusTransitionHelper.checkTransition(order.getStatus(), OrderStatus.REWORK_REQUIRED);

        final var oldStatus = order.getStatus();
        order.setLastModifiedDate(LocalDateTime.now());
        order.setStatus(OrderStatus.REWORK_REQUIRED);
        repo.update(order);
        repo.markEventProcessed(eventId);

        publisher.publishOrderStatusUpdated(OrderStatusUpdatedPayloadMapper.build(order, oldStatus));

    }

}



