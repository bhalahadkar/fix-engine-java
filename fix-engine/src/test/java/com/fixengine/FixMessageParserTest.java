package com.fixengine;

import com.fixengine.config.FixEngineProperties;
import com.fixengine.core.FixMessageParser;
import com.fixengine.entity.FixMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.*;
import quickfix.field.*;

import static org.assertj.core.api.Assertions.assertThat;

class FixMessageParserTest {

    private FixMessageParser parser;

    @BeforeEach
    void setUp() {
        FixEngineProperties props = new FixEngineProperties();
        FixEngineProperties.NodeProperties node = new FixEngineProperties.NodeProperties();
        node.setId("test-node");
        props.setNode(node);
        parser = new FixMessageParser(props);
    }

    @Test
    void shouldParseNewOrderSingle() throws Exception {
        Message msg = new Message();
        msg.getHeader().setString(BeginString.FIELD, "FIX.4.4");
        msg.getHeader().setString(MsgType.FIELD, MsgType.ORDER_SINGLE);
        msg.getHeader().setInt(MsgSeqNum.FIELD, 42);
        msg.setString(ClOrdID.FIELD, "ORDER-001");
        msg.setString(Symbol.FIELD, "AAPL");
        msg.setChar(Side.FIELD, Side.BUY);
        msg.setDouble(OrderQty.FIELD, 1000.0);

        SessionID sessionId = new SessionID("FIX.4.4", "MY_FIRM", "BROKER_A");
        FixMessage entity = parser.toEntity(msg, sessionId, "O");

        assertThat(entity.getMsgType()).isEqualTo("D");
        assertThat(entity.getSessionId()).isEqualTo(sessionId.toString());
        assertThat(entity.getSenderCompId()).isEqualTo("MY_FIRM");
        assertThat(entity.getTargetCompId()).isEqualTo("BROKER_A");
        assertThat(entity.getDirection()).isEqualTo("O");
        assertThat(entity.getMsgSeqNum()).isEqualTo(42L);
        assertThat(entity.getNodeId()).isEqualTo("test-node");
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    void shouldHandleMissingOptionalFields() throws Exception {
        Message msg = new Message();
        msg.getHeader().setString(MsgType.FIELD, MsgType.HEARTBEAT);

        SessionID sessionId = new SessionID("FIX.4.4", "A", "B");
        FixMessage entity = parser.toEntity(msg, sessionId, "I");

        assertThat(entity.getMsgType()).isEqualTo("0");
        assertThat(entity.getMsgSeqNum()).isEqualTo(0L);
        assertThat(entity.getReceivedAt()).isNotNull();
    }
}
