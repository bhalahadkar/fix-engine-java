package com.fixengine.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.MsgType;

/**
 * Routes inbound FIX messages to the appropriate handler based on message type.
 */
@Slf4j
@Component
public class FixMessageRouter {

    public void route(Message message, SessionID sessionId) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            switch (msgType) {
                case MsgType.ORDER_SINGLE             -> handleNewOrderSingle(message, sessionId);
                case MsgType.EXECUTION_REPORT         -> handleExecutionReport(message, sessionId);
                case MsgType.ORDER_CANCEL_REPLACE_REQUEST -> handleOrderCancelReplace(message, sessionId);
                case MsgType.ORDER_CANCEL_REQUEST     -> handleOrderCancelRequest(message, sessionId);
                case MsgType.MARKET_DATA_REQUEST      -> handleMarketDataRequest(message, sessionId);
                case MsgType.MARKET_DATA_SNAPSHOT_FULL_REFRESH -> handleMarketDataSnapshot(message, sessionId);
                default -> log.debug("Unhandled message type '{}' from session {}", msgType, sessionId);
            }
        } catch (FieldNotFound e) {
            log.error("Missing MsgType field in message from session {}", sessionId);
        }
    }

    private void handleNewOrderSingle(Message message, SessionID sessionId) {
        log.debug("NewOrderSingle received from {}", sessionId);
        // Implement order handling logic here
    }

    private void handleExecutionReport(Message message, SessionID sessionId) {
        log.debug("ExecutionReport received from {}", sessionId);
        // Implement execution report handling logic here
    }

    private void handleOrderCancelReplace(Message message, SessionID sessionId) {
        log.debug("OrderCancelReplaceRequest received from {}", sessionId);
    }

    private void handleOrderCancelRequest(Message message, SessionID sessionId) {
        log.debug("OrderCancelRequest received from {}", sessionId);
    }

    private void handleMarketDataRequest(Message message, SessionID sessionId) {
        log.debug("MarketDataRequest received from {}", sessionId);
    }

    private void handleMarketDataSnapshot(Message message, SessionID sessionId) {
        log.debug("MarketDataSnapshot received from {}", sessionId);
    }
}
