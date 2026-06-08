package pk.ni.pasir_mazurek_patryk.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pk.ni.pasir_mazurek_patryk.dto.BalanceDTO;
import pk.ni.pasir_mazurek_patryk.dto.TransactionDTO;
import pk.ni.pasir_mazurek_patryk.model.Transaction;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.service.TransactionService;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class TransactionGraphQLController {

    private final TransactionService transactionService;

    @QueryMapping
    public List<Transaction> transactions() {
        return transactionService.getAllTransactions();
    }

    @MutationMapping
    public Transaction addTransaction(@Valid @Argument TransactionDTO transactionDTO) {
        return transactionService.addTransaction(transactionDTO);
    }

    @MutationMapping
    public Transaction updateTransaction(
            @Argument Long id,
            @Valid @Argument TransactionDTO transactionDTO
            ) {
        return transactionService.updateTransaction(id, transactionDTO);
    }

    @MutationMapping
    public void deleteTransaction(@Argument Long id) {
        transactionService.deleteTransaction(id);
    }

    @QueryMapping
    public BalanceDTO userBalance(@Argument Float days) {
        User user = transactionService.getCurrentUser();
        return transactionService.getUserBalance(user, days);
    }

}
