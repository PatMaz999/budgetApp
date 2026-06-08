package pk.ni.pasir_mazurek_patryk.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import pk.ni.pasir_mazurek_patryk.model.TransactionType;

import java.time.LocalDateTime;

@Getter
@Setter
public class TransactionDTO {

    @NotNull
    @Min(value = 1, message = "Kwota nie moze byc pusta")
    private Double amount;

    @NotNull
    private TransactionType type;

    @Size(max = 50)
    private String tags;

    @Size(max = 255)
    private String notes;

    private LocalDateTime timestamp;

}
