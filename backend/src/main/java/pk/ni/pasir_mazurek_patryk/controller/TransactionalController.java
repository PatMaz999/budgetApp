package pk.ni.pasir_mazurek_patryk.controller;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pk.ni.pasir_mazurek_patryk.dto.TransactionDTO;
import pk.ni.pasir_mazurek_patryk.model.Transaction;
import pk.ni.pasir_mazurek_patryk.service.TransactionService;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
public class TransactionalController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransaction(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(
            @PathVariable Long id,
            @Validated @RequestBody TransactionDTO transactionDetails
    ){
        return ResponseEntity.ok(transactionService.updateTransaction(id, transactionDetails));
    }

    @PostMapping
    public ResponseEntity<Transaction> addTransaction(@Validated @RequestBody TransactionDTO transactionDetails){
        Transaction transaction = transactionService.addTransaction(transactionDetails);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transaction.getId())
                .toUri();

        return ResponseEntity.created(location).body(transaction);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id){
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

}
