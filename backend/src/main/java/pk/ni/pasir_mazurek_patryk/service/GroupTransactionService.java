package pk.ni.pasir_mazurek_patryk.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pk.ni.pasir_mazurek_patryk.dto.GroupTransactionDTO;
import pk.ni.pasir_mazurek_patryk.dto.TransactionDTO;
import pk.ni.pasir_mazurek_patryk.dto.WebSocketResponseDTO;
import pk.ni.pasir_mazurek_patryk.model.*;
import pk.ni.pasir_mazurek_patryk.repository.DebtRepository;
import pk.ni.pasir_mazurek_patryk.repository.GroupRepository;
import pk.ni.pasir_mazurek_patryk.repository.MembershipRepository;
import pk.ni.pasir_mazurek_patryk.webSocket.TransactionHandler;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GroupTransactionService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final DebtRepository debtRepository;
    private final MembershipService membershipService;
    private final TransactionService transactionService;

    private final TransactionHandler transactionHandler;

    public void addGroupTransaction(GroupTransactionDTO transactionDTO, User currentUser) {
        Group group = groupRepository.findById(transactionDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono Grupy"));

        membershipService.assertCurrentUserIsGroupMember(group.getId());

        List<Membership> members = membershipRepository.findByGroupId(group.getId());
        List<Membership> selectedMembers = selectParticipants(transactionDTO, members, currentUser);
        if (selectedMembers.isEmpty()) {
            throw new IllegalStateException("Grupa nie ma czlonkow, nie mozna dodac transakcji.");
        }
        double amountPerUser = transactionDTO.getAmount() / selectedMembers.size();
        boolean expense = "EXPENSE".equals(transactionDTO.getType());


//         dodanie powiadomienia WebSocket
        var message = WebSocketResponseDTO.builder()
                .type("GROUP_EXPENSE_ADDED")
                .groupId(group.getId())
                .groupName(group.getName())
                .title(transactionDTO.getTitle())
                .amount(transactionDTO.getAmount())
                .userShare(amountPerUser)
                .createdByEmail(currentUser.getEmail())
                .message(String.format("%s dodał wydatek \"%s\" w grupie %s. Twoja część: %.2f zł.",
                        currentUser.getEmail(), transactionDTO.getTitle(), group.getName(), amountPerUser))
                .build();

        transactionHandler.broadcastExpenseAdded(message);


        for (Membership member : selectedMembers) {
            User otherUser = member.getUser();
            if (!otherUser.getId().equals(currentUser.getId())) {
                Debt debt = new Debt();
                debt.setDebtor(expense ? otherUser : currentUser);
                debt.setCreditor(expense ? currentUser : otherUser);
                debt.setGroup(group);
                debt.setAmount(amountPerUser);
                debt.setTitle(transactionDTO.getTitle());
                debtRepository.save(debt);
            }
            else{
                TransactionDTO transaction = new TransactionDTO();
                transaction.setAmount(amountPerUser);
                transaction.setType(TransactionType.EXPENSE);
                transaction.setTags("Wydatek group " + group.getName());
                transaction.setNotes("Transakcja grupowa: " + transactionDTO.getTitle());
                transaction.setTimestamp(LocalDateTime.now());

                transactionService.addTransaction(transaction);
            }
        }
    }

    private List<Membership> selectParticipants(
            GroupTransactionDTO transactionDTO,
            List<Membership> members,
            User currentUser) {
        List<Long> selectedUserIds = transactionDTO.getSelectedUserIds();
        if (selectedUserIds == null || selectedUserIds.isEmpty()) {
            return members;
        }
        Set<Long> uniqueSelectedUserIds = new HashSet<>(selectedUserIds);
        List<Membership> selectedMembers = members.stream()
                .filter(membership -> uniqueSelectedUserIds.contains(membership.getUser().getId()))
                .toList();
        if (selectedMembers.size() != uniqueSelectedUserIds.size()) {
            throw new IllegalStateException(
                    "Wszyscy wybrani uzytkownicy musza byc czlonkami grupy.");
        }
        boolean currentUserSelected = selectedMembers.stream()
                .anyMatch(membership -> membership.getUser().getId().equals(currentUser.getId()));
        if (!currentUserSelected) {
            throw new IllegalStateException(
                    "Aktualny uzytkownik musi byc uczestnikiem transakcji grupowej.");
        }
        if (selectedMembers.size() < 2) {
            throw new IllegalStateException("Transakcja grupowa wymaga co najmniej dwoch uczestnikow.");
        }
        return selectedMembers;
    }
}
