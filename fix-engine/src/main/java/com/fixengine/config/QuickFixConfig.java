package com.fixengine.config;

import com.fixengine.core.FixApplicationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.*;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class QuickFixConfig {

    private final FixEngineProperties properties;
    private final FixApplicationAdapter applicationAdapter;

    @Bean
    public SessionSettings sessionSettings() throws ConfigError {
        SessionSettings settings = new SessionSettings();

        // Default settings
        Dictionary defaults = new Dictionary();
        defaults.setString("FileStorePath", "data/fix-store");
        defaults.setString("FileLogPath", "logs/fix-messages");
        defaults.setString("ConnectionType", "initiator"); // per-session override
        defaults.setString("UseDataDictionary", "Y");
        defaults.setString("ValidateIncomingMessage", "Y");
        defaults.setString("ValidateFieldsOutOfOrder", "Y");
        defaults.setString("ValidateFieldsHaveValues", "Y");
        settings.set(defaults);

        // Per-session configuration
        for (FixEngineProperties.SessionProperties sp : properties.getSessions()) {
            if (!sp.isEnabled()) continue;

            SessionID sessionID = new SessionID(sp.getBeginString(), sp.getSenderCompId(), sp.getTargetCompId());
            Dictionary sessionDict = new Dictionary();

            sessionDict.setString("ConnectionType", sp.getMode().equalsIgnoreCase("INITIATOR") ? "initiator" : "acceptor");
            sessionDict.setString("BeginString", sp.getBeginString());
            sessionDict.setString("SenderCompID", sp.getSenderCompId());
            sessionDict.setString("TargetCompID", sp.getTargetCompId());
            sessionDict.setLong("HeartBtInt", sp.getHeartbeatInterval());
            sessionDict.setLong("ReconnectInterval", sp.getReconnectInterval());
            sessionDict.setString("ResetOnLogon", sp.isResetOnLogon() ? "Y" : "N");
            sessionDict.setString("ResetOnLogout", sp.isResetOnLogout() ? "Y" : "N");
            sessionDict.setString("ResetOnDisconnect", sp.isResetOnDisconnect() ? "Y" : "N");
            sessionDict.setString("StartTime", "00:00:00");
            sessionDict.setString("EndTime", "00:00:00");
            sessionDict.setString("UseDataDictionary", "Y");
            sessionDict.setString("DataDictionary", dataDictionaryPath(sp.getBeginString()));

            if ("INITIATOR".equalsIgnoreCase(sp.getMode())) {
                sessionDict.setString("SocketConnectHost", sp.getHost());
                sessionDict.setLong("SocketConnectPort", sp.getPort());
            } else {
                sessionDict.setLong("SocketAcceptPort", sp.getPort());
            }

            settings.set(sessionID, sessionDict);
            log.info("Registered FIX session: {} [{}] mode={} port={}",
                    sessionID, sp.getBeginString(), sp.getMode(), sp.getPort());
        }

        return settings;
    }

    @Bean
    public MessageStoreFactory messageStoreFactory(SessionSettings sessionSettings) {
        return new FileStoreFactory(sessionSettings);
    }

    @Bean
    public LogFactory logFactory(SessionSettings sessionSettings) {
        return new FileLogFactory(sessionSettings);
    }

    @Bean
    public MessageFactory messageFactory() {
        return new DefaultMessageFactory();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Connector fixConnector(SessionSettings sessionSettings,
                                  MessageStoreFactory messageStoreFactory,
                                  LogFactory logFactory,
                                  MessageFactory messageFactory) throws ConfigError {

        // Determine if we have any initiator sessions
        boolean hasInitiator = properties.getSessions().stream()
                .anyMatch(s -> s.isEnabled() && "INITIATOR".equalsIgnoreCase(s.getMode()));
        boolean hasAcceptor = properties.getSessions().stream()
                .anyMatch(s -> s.isEnabled() && "ACCEPTOR".equalsIgnoreCase(s.getMode()));

        if (hasAcceptor) {
            // Use ThreadedSocketAcceptor to handle both acceptor + initiator modes
            return new ThreadedSocketAcceptor(applicationAdapter, messageStoreFactory,
                    sessionSettings, logFactory, messageFactory);
        } else {
            return new SocketInitiator(applicationAdapter, messageStoreFactory,
                    sessionSettings, logFactory, messageFactory);
        }
    }

    private String dataDictionaryPath(String beginString) {
        return switch (beginString) {
            case "FIX.4.0" -> "quickfix/FIX40.xml";
            case "FIX.4.1" -> "quickfix/FIX41.xml";
            case "FIX.4.2" -> "quickfix/FIX42.xml";
            case "FIX.4.3" -> "quickfix/FIX43.xml";
            case "FIX.4.4" -> "quickfix/FIX44.xml";
            case "FIX.5.0" -> "quickfix/FIX50.xml";
            case "FIX.5.0SP1" -> "quickfix/FIX50SP1.xml";
            case "FIX.5.0SP2" -> "quickfix/FIX50SP2.xml";
            default -> "quickfix/FIX44.xml";
        };
    }
}
