package pk.ni.pasir_mazurek_patryk.webSocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import pk.ni.pasir_mazurek_patryk.dto.WebSocketResponseDTO;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
public class TransactionHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<Long, List<WebSocketSession>> groupSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String groupIdStr = UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst("groupId");

        if (groupIdStr == null) {
            System.out.println("WebSocket connection established without groupId for user: " + session.getAttributes().get("userEmail"));
            return;
        }

        try {
            Long groupId = Long.parseLong(groupIdStr);
            groupSessions.computeIfAbsent(groupId, k -> new CopyOnWriteArrayList<>()).add(session);
            session.getAttributes().put("groupId", groupId);
            System.out.println("WebSocket connection established for groupId: " + groupId);
        } catch (NumberFormatException _) {
            System.err.println("Invalid groupId format: " + groupIdStr);
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long groupId = (Long) session.getAttributes().get("groupId");
        if (groupId != null) {
            List<WebSocketSession> sessions = groupSessions.get(groupId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    groupSessions.remove(groupId);
                }
            }
        }
    }

    public void broadcastExpenseAdded(WebSocketResponseDTO notification) {
        Long groupId = notification.getGroupId();
        List<WebSocketSession> sessions = groupSessions.get(groupId);

        if (sessions != null) {
            try {
                String jsonPayload = objectMapper.writeValueAsString(notification);
                TextMessage message = new TextMessage(jsonPayload);

                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
