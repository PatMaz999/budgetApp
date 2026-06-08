package pk.ni.pasir_mazurek_patryk.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pk.ni.pasir_mazurek_patryk.dto.DebtDTO;
import pk.ni.pasir_mazurek_patryk.dto.TransactionDTO;
import pk.ni.pasir_mazurek_patryk.model.Debt;
import pk.ni.pasir_mazurek_patryk.model.Group;
import pk.ni.pasir_mazurek_patryk.model.TransactionType;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.repository.DebtRepository;
import pk.ni.pasir_mazurek_patryk.repository.GroupRepository;
import pk.ni.pasir_mazurek_patryk.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final CurrentUserService currentUserService;
    private final TransactionService transactionService;

    public List<Debt> getGroupDebts(Long groupId) {
        membershipService.assertCurrentUserIsGroupMember(groupId);
        return debtRepository.findByGroupId(groupId);
    }

    public Debt createDebt(DebtDTO debtDTO) {
        Group group = groupRepository.findById(debtDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie można utworzyć długu. Grupa o ID " + debtDTO.getGroupId() + " nie istnieje."));
        User debtor = userRepository.findById(debtDTO.getDebtorId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie można utworzyć długu. Dłużnik o ID " + debtDTO.getDebtorId() + " nie istnieje."));
        User creditor = userRepository.findById(debtDTO.getCreditorId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie można utworzyć długu. Wierzyciel o ID " + debtDTO.getCreditorId() + " nie istnieje."));

        membershipService.assertCurrentUserIsGroupMember(group.getId());
        membershipService.assertUserIsGroupMember(group.getId(), debtor.getId());
        membershipService.assertUserIsGroupMember(group.getId(), creditor.getId());

        if (debtor.getId().equals(creditor.getId())) {
            throw new IllegalStateException("Dłużnik i wierzyciel muszą być różnymi użytkownikami.");
        }

        User currentUser = currentUserService.getCurrentUser();
        assertCurrentUserCanManageDebt(group, debtor, creditor, currentUser);

        Debt debt = new Debt();
        debt.setGroup(group);
        debt.setDebtor(debtor);
        debt.setCreditor(creditor);
        debt.setAmount(debtDTO.getAmount());
        debt.setTitle(debtDTO.getTitle());

        return debtRepository.save(debt);
    }

    public void deleteDebt(Long debtId) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie można usunąć długu. Dług o ID " + debtId + " nie istnieje."));

        membershipService.assertCurrentUserIsGroupMember(debt.getGroup().getId());
        User currentUser = currentUserService.getCurrentUser();
        assertCurrentUserCanManageDebt(debt.getGroup(), debt.getDebtor(), debt.getCreditor(), currentUser);
        debtRepository.delete(debt);
    }

    private void assertCurrentUserCanManageDebt(Group group, User debtor, User creditor, User currentUser) {
        boolean isGroupOwner = group.getOwner().getId().equals(currentUser.getId());
        boolean isDebtParticipant = debtor.getId().equals(currentUser.getId())
                || creditor.getId().equals(currentUser.getId());

        if (!isGroupOwner && !isDebtParticipant) {
            throw new AccessDeniedException(
                    "Tylko właściciel grupy albo uczestnik długu może wykonać te operacje.");
        }
    }

    public Debt markDebtAsPaid(Long debtId) {
        Debt debt = getDebtForCurrentGroupMember(debtId);
        User currentUser = currentUserService.getCurrentUser();
        if (!debt.getDebtor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko dluznik moze oznaczyc dlug jako oplacony.");
        }

        TransactionDTO transaction = new  TransactionDTO();
        transaction.setAmount(debt.getAmount());
        transaction.setType(TransactionType.EXPENSE);
        transaction.setNotes("Splat dlugu: " + debt.getTitle());
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setTags("debt, payment");
        transactionService.addTransaction(transaction);

        debt.setPaidByDebtor(true);
        debt.setConfirmedByCreditor(false);
        return debtRepository.save(debt);
    }

    public Debt confirmDebtPayment(Long debtId) {
        Debt debt = getDebtForCurrentGroupMember(debtId);
        User currentUser = currentUserService.getCurrentUser();
        if (!debt.getCreditor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko wierzyciel moze potwierdzic splate dlugu.");
        }
        if (!debt.isPaidByDebtor()) {
            throw new IllegalStateException(
                    "Dlug musi zostac najpierw oznaczony jako oplacony przez dluznika.");
        }
        debt.setConfirmedByCreditor(true);
        return debtRepository.save(debt);
    }

    private Debt getDebtForCurrentGroupMember(Long debtId) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie znaleziono dlugu o ID " + debtId + "."));
        membershipService.assertCurrentUserIsGroupMember(debt.getGroup().getId());
        return debt;
    }



}
