package pk.ni.pasir_mazurek_patryk.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pk.ni.pasir_mazurek_patryk.dto.BalanceDTO;
import pk.ni.pasir_mazurek_patryk.dto.TransactionDTO;
import pk.ni.pasir_mazurek_patryk.model.Debt;
import pk.ni.pasir_mazurek_patryk.model.Transaction;
import pk.ni.pasir_mazurek_patryk.model.TransactionType;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.repository.DebtRepository;
import pk.ni.pasir_mazurek_patryk.repository.TransactionRepository;
import pk.ni.pasir_mazurek_patryk.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final DebtRepository debtRepository;

    public List<Transaction> getAllTransactions(){
        User user = getCurrentUser();
        return transactionRepository.findAllByUser(user);
    }

    public Transaction updateTransaction(Long id, TransactionDTO transactionDto){
        Transaction transaction = getTransaction(id);

        transaction.setAmount(transactionDto.getAmount());
        transaction.setType(transactionDto.getType());
        transaction.setTags(transactionDto.getTags());
        transaction.setNotes(transactionDto.getNotes());

        return transactionRepository.save(transaction);
    }

    public Transaction addTransaction(TransactionDTO transactionDetails) {
        Transaction transaction = new Transaction();

        transaction.setAmount(transactionDetails.getAmount());
        transaction.setType(transactionDetails.getType());
        transaction.setTags(transactionDetails.getTags());
        transaction.setNotes(transactionDetails.getNotes());
        transaction.setUser(getCurrentUser());
        transaction.setTimestamp(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    public void deleteTransaction(Long id) {

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));

        if(!transaction.getUser().getEmail().equals(getCurrentUser().getEmail())){
            throw new AccessDeniedException("Nie masz dostepu do tej transakcji");
        }

        transactionRepository.deleteById(id);
    }

    public Transaction getTransaction(Long id) {

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));

        if(!transaction.getUser().getEmail().equals(getCurrentUser().getEmail())){
            throw new AccessDeniedException("Nie masz dostepu do tej transakcji");
        }

        return transaction;
    }

    public BalanceDTO getUserBalance(User user, Float timestamp){

        List<Transaction> transactions;

        if(timestamp == null || timestamp <= 0)
            transactions = transactionRepository.findAllByUser(user);
        else{
            LocalDateTime days = LocalDateTime.now().minusDays(timestamp.longValue());
            transactions = transactionRepository.findAllByUserAndTimestampGreaterThanEqual(user, days);
        }

        double debt = debtRepository.findByCreditorId(user.getId()).stream()
                .filter(d -> !d.isConfirmedByCreditor())
                .mapToDouble(Debt::getAmount)
                .sum();

        double income = getTransactionSum(transactions, TransactionType.INCOME);
        double expense = getTransactionSum(transactions, TransactionType.EXPENSE) + debt;

        return new BalanceDTO(income, expense, income - expense);
    }

    private static double getTransactionSum(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType() == type)
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    public User getCurrentUser(){
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || authentication.getName() == null){
            throw new AccessDeniedException("Uzytkownik nie jest uwierzytelniony");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono zalogowanego uzytkownika " + email));
    }
}
