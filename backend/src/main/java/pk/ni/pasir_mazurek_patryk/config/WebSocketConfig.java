package pk.ni.pasir_mazurek_patryk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pk.ni.pasir_mazurek_patryk.security.WebSocketAuthInterceptor;
import pk.ni.pasir_mazurek_patryk.webSocket.TransactionHandler;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {


    private final TransactionHandler transactionHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(transactionHandler, "/ws/group-notifications")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}