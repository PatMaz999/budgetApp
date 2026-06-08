package pk.ni.pasir_mazurek_patryk.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class WebSocketResponseDTO {
    private String type;
    private Long groupId;
    private String groupName;
    private String title;
    private Double amount;
    private Double userShare;
    private String createdByEmail;
    private String message;
}
