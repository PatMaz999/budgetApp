package pk.ni.pasir_mazurek_patryk.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GroupTransactionDTO {

    @NotNull(message = "Id grupy nie moze byc puste")
    private Long groupId;

    @NotNull(message = "Kwota nie moze byc pusta")
    @Positive(message = "Kwota musi byc wiekszaa od zera")
    private Double amount;

    @NotBlank
    @Pattern(regexp = "INCOME|EXPENSE", message = "Typ transakcji musi miec wartosc INOCME albo EXPENSE")
    private String type;

    @NotBlank(message = "Tytul nie moze byc pusty")
    @Size(max = 100, message = "Tytul nie moze przekraczac 100 znakow")
    private String title;

    private List<Long> selectedUserIds;
}
