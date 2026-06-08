package pk.ni.pasir_mazurek_patryk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupDTO {
    @NotBlank(message = "Nazwa grupy nie moze byc pusta")
    @Size(max = 100, message = "Nazwa grupy nie moze przekraczac 100 znakow")
    private String name;
}
