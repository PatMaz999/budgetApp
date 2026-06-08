package pk.ni.pasir_mazurek_patryk.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pk.ni.pasir_mazurek_patryk.dto.MembershipDTO;
import pk.ni.pasir_mazurek_patryk.model.Group;
import pk.ni.pasir_mazurek_patryk.model.Membership;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.repository.GroupRepository;
import pk.ni.pasir_mazurek_patryk.repository.MembershipRepository;
import pk.ni.pasir_mazurek_patryk.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public List<Membership> getGroupMembers(Long groupId) {
        assertCurrentUserIsGroupMember(groupId);
        return membershipRepository.findByGroupId(groupId);
    }

    public Membership addMember(MembershipDTO membershipDTO) {
        assertCurrentUserIsGroupOwner(membershipDTO.getGroupId());

        User user = userRepository.findByEmail(membershipDTO.getUserEmail())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie znaleziono użytkownika o emailu: " + membershipDTO.getUserEmail()));
        Group group = groupRepository.findById(membershipDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie znaleziono grupy o ID: " + membershipDTO.getGroupId()));

        // Validation: check if user is already member of group
        boolean alreadyMember = membershipRepository.findByGroupId(group.getId()).stream()
                .anyMatch(membership -> membership.getUser().getId().equals(user.getId()));

        if (alreadyMember) {
            throw new IllegalStateException("Użytkownik jest już członkiem tej grupy.");
        }

        Membership membership = new Membership();
        membership.setUser(user);
        membership.setGroup(group);

        return membershipRepository.save(membership);
    }

    public void removeMember(Long membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Członkostwo nie istnieje"));

        User currentUser = currentUserService.getCurrentUser(); // who is trying to remove member
        User groupOwner = membership.getGroup().getOwner(); // who is the owner of the group

        if (!currentUser.getId().equals(groupOwner.getId())) {
            throw new AccessDeniedException("Tylko właściciel grupy może usuwać członków.");
        }

        if (membership.getUser().getId().equals(groupOwner.getId())) {
            throw new IllegalStateException("Nie można usunąć właściciela z jego grupy.");
        }

        membershipRepository.delete(membership);
    }

    public void assertCurrentUserIsGroupMember(Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie znaleziono grupy o ID: " + groupId));

        User currentUser = currentUserService.getCurrentUser();
        assertUserIsGroupMember(groupId, currentUser.getId());
    }

    public void assertCurrentUserIsGroupOwner(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nie znaleziono grupy o ID: " + groupId));

        User currentUser = currentUserService.getCurrentUser();
        if (!group.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko właściciel grupy może wykonać tę operację.");
        }
    }

    public void assertUserIsGroupMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new AccessDeniedException("Użytkownik nie jest członkiem tej grupy.");
        }
    }
}
