package pk.ni.pasir_mazurek_patryk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MembershipDTO {
    @NotBlank(message = "email nie moze byc pusty")
    @Email(message = "Email uzytkownika musi byc poprawnym adresem email")
    private String userEmail;

    @NotNull
    private Long groupId;
}
