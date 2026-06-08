package pk.ni.pasir_mazurek_patryk.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import pk.ni.pasir_mazurek_patryk.dto.GroupTransactionDTO;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.service.CurrentUserService;
import pk.ni.pasir_mazurek_patryk.service.GroupTransactionService;

@Controller
@RequiredArgsConstructor
public class GroupTransactionGraphQLController {

    private final GroupTransactionService groupTransactionService;
    private final CurrentUserService currentUserService;

    @MutationMapping
    public Boolean addGroupTransaction(@Valid @Argument GroupTransactionDTO groupTransactionDTO){
        User user = currentUserService.getCurrentUser();
        groupTransactionService.addGroupTransaction(groupTransactionDTO, user);
        return true;
    }
}
