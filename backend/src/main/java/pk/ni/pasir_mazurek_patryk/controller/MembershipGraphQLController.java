package pk.ni.pasir_mazurek_patryk.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pk.ni.pasir_mazurek_patryk.dto.GroupResponseDTO;
import pk.ni.pasir_mazurek_patryk.dto.MembershipDTO;
import pk.ni.pasir_mazurek_patryk.dto.MembershipResponseDTO;
import pk.ni.pasir_mazurek_patryk.model.Membership;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.repository.GroupRepository;
import pk.ni.pasir_mazurek_patryk.service.CurrentUserService;
import pk.ni.pasir_mazurek_patryk.service.MembershipService;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MembershipGraphQLController {

    private final MembershipService membershipService;
    private final GroupRepository groupRepository;
    private final CurrentUserService currentUserService;

    @QueryMapping
    public List<MembershipResponseDTO> groupMembers(@Argument Long groupId) {
        return membershipService.getGroupMembers(groupId).stream()
                .map(membership -> new MembershipResponseDTO(
                        membership.getId(),
                        membership.getUser().getId(),
                        membership.getGroup().getId(),
                        membership.getUser().getEmail()
                ))
                .toList();
    }

    @MutationMapping
    public MembershipResponseDTO addMember(@Valid @Argument MembershipDTO membershipDTO) {
        Membership membership = membershipService.addMember(membershipDTO);
        return new MembershipResponseDTO(
                membership.getId(),
                membership.getUser().getId(),
                membership.getGroup().getId(),
                membership.getUser().getEmail()
        );
    }

    @QueryMapping
    public List<GroupResponseDTO> myGroups(){
        User currentUser = currentUserService.getCurrentUser();
        return groupRepository.findByMemberships_User(currentUser).stream()
                .map(group -> new GroupResponseDTO(
                        group.getId(),
                        group.getName(),
                        group.getOwner().getId()
                ))
                .toList();
    }

    @MutationMapping
    public Boolean removeMember(@Argument Long membershipId) {
        membershipService.removeMember(membershipId);
        return true;
    }
}
