package pk.ni.pasir_mazurek_patryk.service;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pk.ni.pasir_mazurek_patryk.dto.GroupDTO;
import pk.ni.pasir_mazurek_patryk.dto.GroupResponseDTO;
import pk.ni.pasir_mazurek_patryk.dto.UserDto;
import pk.ni.pasir_mazurek_patryk.model.User;
import pk.ni.pasir_mazurek_patryk.repository.DebtRepository;
import pk.ni.pasir_mazurek_patryk.repository.MembershipRepository;
import pk.ni.pasir_mazurek_patryk.repository.UserRepository;
import pk.ni.pasir_mazurek_patryk.security.JwtUtil;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupServiceIntegrationTest {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebApplicationContext context;

    private HttpGraphQlTester graphQlTester;

    private static final String TEST_EMAIL = "test@test";

    @BeforeEach
    void setUp() {
        debtRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();

        String authToken = getMockAuthToken(TEST_EMAIL);

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        WebTestClient client = MockMvcWebTestClient.bindTo(mockMvc)
                .baseUrl("/graphql")
                .defaultHeader("Authorization", "Bearer " + authToken)
                .build();

        graphQlTester = HttpGraphQlTester.create(client);
    }

    private String getMockAuthToken(String email) {
        UserDto userDto = new UserDto();
        userDto.setEmail(email);
        userDto.setPassword("password");
        userDto.setUsername("testuser");
        User user = userService.register(userDto);

        return jwtUtil.generateToken(user);
    }

    @Test
    @DisplayName("Utworzenie grupy dodaje właściciela jako członka i zwraca ją w myGroups")
    void currentUserShouldBecomeOwner() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group");

        Long userId = userRepository.findByEmail(TEST_EMAIL).orElseThrow(AssertionError::new).getId();

        graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute()
                .path("createGroup.ownerId")
                .entity(Long.class)
                .isEqualTo(userId);

        graphQlTester.documentName("test1_testCreateGroup")
                .operationName("getCurrentUserGroups")
                .execute()
                .path("myGroups")
                .entityList(GroupResponseDTO.class)
                .hasSize(1)
                .satisfies(groups -> {
                    var firstGroup = groups.getFirst();
                    assertEquals("Test Group", firstGroup.getName());
                    assertNotNull(firstGroup.getId());
                });

    }

    @Test
    @DisplayName("Tylko właściciel grupy może dodawać członków")
    void onlyOwnerCanAddMembers() {
        // 1. Właściciel (TEST_EMAIL) tworzy grupę
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group");

        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute()
                .path("createGroup.id")
                .entity(Long.class)
                .get();

        // 2. Tworzymy drugiego użytkownika, który nie jest właścicielem ani nawet w
        // grupie
        String nonOwnerEmail = "nonowner@test.com";
        String nonOwnerToken = getMockAuthToken(nonOwnerEmail);

        // 3. Tworzymy trzeciego użytkownika, którego chcemy dodać
        String memberEmail = "member@test.com";
        getMockAuthToken(memberEmail); // Rejestruje użytkownika

        // 4. Próba dodania członka przez nie-właściciela
        Map<String, Object> membershipData = Map.of(
                "userEmail", memberEmail,
                "groupId", groupId);

        graphQlTester.mutate()
                .headers(headers -> headers.set("Authorization", "Bearer " + nonOwnerToken))
                .build()
                .documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData", membershipData)
                .execute()
                .errors()
                .expect(error -> error.getMessage()
                        .contains("Tylko właściciel grupy może wykonać tę operację."));
    }

    @Test
    @DisplayName("groupMembers zwraca członków grupy tylko członkowi tej grupy")
    void groupMembersReturnsMembersOnlyToGroupMembers() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 3");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        graphQlTester.documentName("test3_groupMembersOnlyForMembers")
                .operationName("getGroupMembers")
                .variable("groupId", groupId)
                .execute()
                .path("groupMembers")
                .entityList(Object.class)
                .hasSize(1);

        String outsiderEmail = "outsider3@test.com";
        String outsiderToken = getMockAuthToken(outsiderEmail);
        graphQlTester.mutate().headers(h -> h.set("Authorization", "Bearer " + outsiderToken)).build()
                .documentName("test3_groupMembersOnlyForMembers")
                .operationName("getGroupMembers")
                .variable("groupId", groupId)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("Użytkownik nie jest członkiem tej grupy."));
    }

    @Test
    @DisplayName("groupDebts zwraca długi grupy tylko członkowi tej grupy")
    void groupDebtsReturnsDebtsOnlyToGroupMembers() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 4");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        graphQlTester.documentName("test4_groupDebtsOnlyForMembers")
                .operationName("getGroupDebts")
                .variable("groupId", groupId)
                .execute()
                .path("groupDebts")
                .entityList(Object.class)
                .hasSize(0);

        String outsiderEmail = "outsider4@test.com";
        String outsiderToken = getMockAuthToken(outsiderEmail);
        graphQlTester.mutate().headers(h -> h.set("Authorization", "Bearer " + outsiderToken)).build()
                .documentName("test4_groupDebtsOnlyForMembers")
                .operationName("getGroupDebts")
                .variable("groupId", groupId)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("Użytkownik nie jest członkiem tej grupy."));
    }

    @Test
    @DisplayName("Nowy członek dostaje tylko długi z transakcji dodanych po dołączeniu")
    void newMemberGetsDebtsOnlyFromTransactionsAddedAfterJoining() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 5");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String member1Email = "member1_group5@test.com";
        getMockAuthToken(member1Email);
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member1Email, "groupId", groupId))
                .execute().errors().verify();

        Map<String, Object> transactionData1 = Map.of(
                "groupId", groupId,
                "amount", 100.0,
                "type", "EXPENSE",
                "title", "Old Transaction");
        graphQlTester.documentName("test5_newMemberDebts")
                .operationName("addGroupTransaction")
                .variable("data", transactionData1)
                .execute().errors().verify();

        String newMemberEmail = "newmember_group5@test.com";
        String newMemberToken = getMockAuthToken(newMemberEmail);
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", newMemberEmail, "groupId", groupId))
                .execute().errors().verify();

        HttpGraphQlTester newMemberTester = graphQlTester.mutate()
                .headers(h -> h.set("Authorization", "Bearer " + newMemberToken)).build();
        newMemberTester.documentName("test4_groupDebtsOnlyForMembers")
                .operationName("getGroupDebts")
                .variable("groupId", groupId)
                .execute()
                .path("groupDebts")
                .entityList(Map.class)
                .satisfies(debts -> {
                    boolean isParticipant = debts.stream().anyMatch(d -> {
                        Map<?, ?> debtor = (Map<?, ?>) d.get("debtor");
                        Map<?, ?> creditor = (Map<?, ?>) d.get("creditor");
                        return newMemberEmail.equals(debtor.get("email")) || newMemberEmail.equals(creditor.get("email"));
                    });
                    org.junit.jupiter.api.Assertions.assertFalse(isParticipant);
                });

        Map<String, Object> transactionData2 = Map.of(
                "groupId", groupId,
                "amount", 90.0,
                "type", "EXPENSE",
                "title", "New Transaction");
        graphQlTester.documentName("test5_newMemberDebts")
                .operationName("addGroupTransaction")
                .variable("data", transactionData2)
                .execute().errors().verify();

        newMemberTester.documentName("test4_groupDebtsOnlyForMembers")
                .operationName("getGroupDebts")
                .variable("groupId", groupId)
                .execute()
                .path("groupDebts")
                .entityList(Map.class)
                .satisfies(debts -> {
                    boolean hasNewDebt = debts.stream().anyMatch(d -> {
                        Map<?, ?> debtor = (Map<?, ?>) d.get("debtor");
                        Map<?, ?> creditor = (Map<?, ?>) d.get("creditor");
                        return "New Transaction".equals(d.get("title")) &&
                                (newMemberEmail.equals(debtor.get("email")) || newMemberEmail.equals(creditor.get("email")));
                    });
                    org.junit.jupiter.api.Assertions.assertTrue(hasNewDebt);
                });
    }

    @Test
    @DisplayName("Transakcja grupowa typu INCOME tworzy długi od aktualnego użytkownika do pozostałych członków")
    void incomeGroupTransactionCreatesDebtsFromCurrentUserToOthers() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 6");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String memberEmail = "member6@test.com";
        String memberToken = getMockAuthToken(memberEmail);
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().errors().verify();

        Map<String, Object> transactionData = Map.of(
                "groupId", groupId,
                "amount", 100.0,
                "type", "INCOME",
                "title", "Income Transaction");
        graphQlTester.documentName("test5_newMemberDebts")
                .operationName("addGroupTransaction")
                .variable("data", transactionData)
                .execute().errors().verify();

        HttpGraphQlTester memberTester = graphQlTester.mutate()
                .headers(h -> h.set("Authorization", "Bearer " + memberToken))
                .build();
        memberTester.documentName("test4_groupDebtsOnlyForMembers")
                .operationName("getGroupDebts")
                .variable("groupId", groupId)
                .execute()
                .path("groupDebts[0].amount").entity(Double.class).isEqualTo(50.0)
                .path("groupDebts[0].title").entity(String.class).isEqualTo("Income Transaction")
                .path("groupDebts[0].creditor.email").entity(String.class).isEqualTo(memberEmail)
                .path("groupDebts[0].debtor.email").entity(String.class).isEqualTo(TEST_EMAIL);
    }

    @Test
    @DisplayName("Usunięcie członka nie usuwa jego historycznych długów")
    void removingMemberDoesNotDeleteHistoricalDebts() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 7");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String memberEmail = "member7@test.com";
        getMockAuthToken(memberEmail);
        Long membershipId = graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().path("addMember.id").entity(Long.class).get();

        graphQlTester.documentName("test5_newMemberDebts")
                .operationName("addGroupTransaction")
                .variable("data",
                        Map.of("groupId", groupId, "amount", 100.0, "type", "EXPENSE",
                                "title", "T"))
                .execute().errors().verify();

        graphQlTester.documentName("test7_removeMemberKeepDebts")
                .operationName("removeMemberFromGroup")
                .variable("id", membershipId)
                .execute().errors().verify();

        graphQlTester.documentName("test4_groupDebtsOnlyForMembers")
                .operationName("getGroupDebts")
                .variable("groupId", groupId)
                .execute()
                .path("groupDebts")
                .entityList(Object.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("Nie można usunąć właściciela z jego grupy przez removeMember")
    void cannotRemoveGroupOwner() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 8");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        Long ownerMembershipId = graphQlTester.documentName("test3_groupMembersOnlyForMembers")
                .operationName("getGroupMembers")
                .variable("groupId", groupId)
                .execute()
                .path("groupMembers[0].id")
                .entity(Long.class)
                .get();

        graphQlTester.documentName("test7_removeMemberKeepDebts")
                .operationName("removeMemberFromGroup")
                .variable("id", ownerMembershipId)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("Nie można usunąć właściciela z jego grupy."));
    }

    @Test
    @DisplayName("Członek grupy niebędący właścicielem nie może usunąć grupy")
    void nonOwnerCannotDeleteGroup() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 9");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String memberEmail = "member9@test.com";
        String token = getMockAuthToken(memberEmail);
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().errors().verify();

        graphQlTester.mutate().headers(h -> h.set("Authorization", "Bearer " + token)).build()
                .documentName("test9_nonOwnerCannotDeleteGroup")
                .operationName("deleteGroupMutation")
                .variable("id", groupId)
                .execute()
                .errors()
                .expect(e -> e.getMessage()
                        .contains("Tylko wlasciciel grupy moze ja usunac."));
    }

    @Test
    @DisplayName("createDebt tworzy ręczny dług tylko między członkami tej samej grupy")
    void createDebtOnlyBetweenSameGroupMembers() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 10");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();

        String memberEmail = "member10@test.com";
        getMockAuthToken(memberEmail);
        Long memberId = userRepository.findByEmail(memberEmail).orElseThrow().getId();

        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().errors().verify();

        Map<String, Object> debtData = Map.of(
                "debtorId", memberId,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Manual Debt");

        graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData)
                .execute()
                .path("createDebt.id").entity(Long.class).get();
    }

    @Test
    @DisplayName("createDebt odrzuca użytkownika spoza grupy i dług do samego siebie")
    void createDebtRejectsNonGroupMembersAndSelfDebt() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 11");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();
        String outsiderEmail = "outsider11@test.com";
        getMockAuthToken(outsiderEmail);
        Long outsiderId = userRepository.findByEmail(outsiderEmail).orElseThrow().getId();

        // 1. Odrzuca outsidera
        Map<String, Object> debtData1 = Map.of(
                "debtorId", outsiderId,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Manual Debt");
        graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData1)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("Użytkownik nie jest członkiem tej grupy."));

        // 2. Odrzuca dług do samego siebie
        Map<String, Object> debtData2 = Map.of(
                "debtorId", ownerId,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Manual Debt");
        graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData2)
                .execute()
                .errors()
                .expect(e -> e.getMessage()
                        .contains("Dłużnik i wierzyciel muszą być różnymi użytkownikami."));
    }

    @Test
    @DisplayName("Właściciel grupy może utworzyć dług między innymi członkami grupy")
    void groupOwnerCanCreateDebtBetweenOtherMembers() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 12");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String member1Email = "member12_1@test.com";
        getMockAuthToken(member1Email);
        Long member1Id = userRepository.findByEmail(member1Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member1Email, "groupId", groupId))
                .execute().errors().verify();

        String member2Email = "member12_2@test.com";
        getMockAuthToken(member2Email);
        Long member2Id = userRepository.findByEmail(member2Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member2Email, "groupId", groupId))
                .execute().errors().verify();

        Map<String, Object> debtData = Map.of(
                "debtorId", member1Id,
                "creditorId", member2Id,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Owner created debt");

        graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData)
                .execute()
                .path("createDebt.id").entity(Long.class).get();
    }

    @Test
    @DisplayName("Członek grupy może utworzyć dług tylko gdy jest jego uczestnikiem")
    void regularMemberCanOnlyCreateDebtIfParticipant() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 13");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String member1Email = "member13_1@test.com";
        String token1 = getMockAuthToken(member1Email);
        Long member1Id = userRepository.findByEmail(member1Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member1Email, "groupId", groupId))
                .execute().errors().verify();

        String member2Email = "member13_2@test.com";
        getMockAuthToken(member2Email);
        Long member2Id = userRepository.findByEmail(member2Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member2Email, "groupId", groupId))
                .execute().errors().verify();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();

        HttpGraphQlTester member1Tester = graphQlTester.mutate()
                .headers(h -> h.set("Authorization", "Bearer " + token1)).build();

        // 1. Sukces - tworzy dług gdzie sam jest dłużnikiem
        Map<String, Object> debtDataSuccess = Map.of(
                "debtorId", member1Id,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 10.0,
                "title", "My Debt");
        member1Tester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtDataSuccess)
                .execute().errors().verify();

        // 2. Porażka - próbuje utworzyć dług między owner a member2 (nie jest
        // uczestnikiem)
        Map<String, Object> debtDataFail = Map.of(
                "debtorId", member2Id,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 20.0,
                "title", "Not my Debt");
        member1Tester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtDataFail)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains(
                        "Tylko właściciel grupy albo uczestnik długu może wykonać te operacje."));
    }

    @Test
    @DisplayName("deleteDebt usuwa dług dostępny dla uczestnika długu")
    void deleteDebtDeletesDebtForParticipant() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 14");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String memberEmail = "member14@test.com";
        String token = getMockAuthToken(memberEmail);
        Long memberId = userRepository.findByEmail(memberEmail).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().errors().verify();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();

        Map<String, Object> debtData = Map.of(
                "debtorId", memberId,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Manual Debt");
        Long debtId = graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData)
                .execute().path("createDebt.id").entity(Long.class).get();

        // Uczestnik (członek) usuwa dług
        HttpGraphQlTester memberTester = graphQlTester.mutate()
                .headers(h -> h.set("Authorization", "Bearer " + token)).build();
        memberTester.documentName("test14_deleteDebtByParticipant")
                .operationName("deleteManualDebt")
                .variable("id", debtId)
                .execute().errors().verify();
    }

    @Test
    @DisplayName("deleteDebt odrzuca członka grupy, który nie jest właścicielem ani uczestnikiem długu")
    void deleteDebtRejectsNonOwnerAndNonParticipant() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 15");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String member1Email = "member15_1@test.com";
        getMockAuthToken(member1Email);
        Long member1Id = userRepository.findByEmail(member1Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member1Email, "groupId", groupId))
                .execute().errors().verify();

        String member2Email = "member15_2@test.com";
        String token2 = getMockAuthToken(member2Email);
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member2Email, "groupId", groupId))
                .execute().errors().verify();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();

        Map<String, Object> debtData = Map.of(
                "debtorId", member1Id,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Manual Debt");
        Long debtId = graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData)
                .execute().path("createDebt.id").entity(Long.class).get();

        // Member2 próbuje usunąć dług
        HttpGraphQlTester member2Tester = graphQlTester.mutate()
                .headers(h -> h.set("Authorization", "Bearer " + token2)).build();
        member2Tester.documentName("test14_deleteDebtByParticipant")
                .operationName("deleteManualDebt")
                .variable("id", debtId)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains(
                        "Tylko właściciel grupy albo uczestnik długu może wykonać te operacje."));
    }

    @Test
    @DisplayName("Właściciel grupy może usunąć dług, którego nie jest uczestnikiem")
    void ownerCanDeleteDebtBetweenOthers() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 16");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String member1Email = "member16_1@test.com";
        getMockAuthToken(member1Email);
        Long member1Id = userRepository.findByEmail(member1Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member1Email, "groupId", groupId))
                .execute().errors().verify();

        String member2Email = "member16_2@test.com";
        getMockAuthToken(member2Email);
        Long member2Id = userRepository.findByEmail(member2Email).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", member2Email, "groupId", groupId))
                .execute().errors().verify();

        Map<String, Object> debtData = Map.of(
                "debtorId", member1Id,
                "creditorId", member2Id,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Debt between others");
        Long debtId = graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData)
                .execute().path("createDebt.id").entity(Long.class).get();

        // Właściciel usuwa dług
        graphQlTester.documentName("test14_deleteDebtByParticipant")
                .operationName("deleteManualDebt")
                .variable("id", debtId)
                .execute().errors().verify();
    }

    @Test
    @DisplayName("Walidacje danych wejściowych GraphQL odrzucają puste lub niepoprawne wartości")
    void graphqlInputValidationRejectsInvalidValues() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 17");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();
        String memberEmail = "member17@test.com";
        getMockAuthToken(memberEmail);
        Long memberId = userRepository.findByEmail(memberEmail).orElseThrow().getId();

        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().errors().verify();

        // Negatywna kwota długu (walidacja @Positive)
        Map<String, Object> invalidDebtData = Map.of(
                "debtorId", memberId,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", -50.0,
                "title", "Invalid Manual Debt");

        graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", invalidDebtData)
                .execute()
                .errors()
                .expect(e -> e.getMessage().contains("Kwota musi byc wieksza od zera"));
    }

    @Test
    @DisplayName("Usunięcie grupy przez właściciela usuwa powiązane długi i grupę")
    void deletingGroupByOwnerDeletesRelatedDebtsAndGroup() {
        GroupDTO groupDTO = new GroupDTO();
        groupDTO.setName("Test Group 18");
        Long groupId = graphQlTester.documentName("test1_testCreateGroup")
                .operationName("createNewGroup")
                .variable("groupData", groupDTO)
                .execute().path("createGroup.id").entity(Long.class).get();

        String memberEmail = "member18@test.com";
        getMockAuthToken(memberEmail);
        Long memberId = userRepository.findByEmail(memberEmail).orElseThrow().getId();
        graphQlTester.documentName("test2_onlyOwnerCanAddMembers")
                .operationName("addMemberToGroup")
                .variable("membershipData",
                        Map.of("userEmail", memberEmail, "groupId", groupId))
                .execute().errors().verify();

        Long ownerId = userRepository.findByEmail(TEST_EMAIL).orElseThrow().getId();

        Map<String, Object> debtData = Map.of(
                "debtorId", memberId,
                "creditorId", ownerId,
                "groupId", groupId,
                "amount", 50.0,
                "title", "Manual Debt");
        graphQlTester.documentName("test10_createDebtSameGroup")
                .operationName("createManualDebt")
                .variable("data", debtData)
                .execute().errors().verify();

        // Usuwamy grupę
        graphQlTester.documentName("test9_nonOwnerCannotDeleteGroup")
                .operationName("deleteGroupMutation")
                .variable("id", groupId)
                .execute().errors().verify();

        // Sprawdzamy czy grupa istnieje
        graphQlTester.documentName("test1_testCreateGroup")
                .operationName("getCurrentUserGroups")
                .execute()
                .path("myGroups")
                .entityList(GroupResponseDTO.class)
                .satisfies(groups -> {
                    boolean exists = groups.stream().anyMatch(g -> g.getId().equals(groupId));
                    assertFalse(exists);
                });
    }
}