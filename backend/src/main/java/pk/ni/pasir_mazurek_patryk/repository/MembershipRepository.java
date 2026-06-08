package pk.ni.pasir_mazurek_patryk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.ni.pasir_mazurek_patryk.model.Membership;

import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    List<Membership> findByGroupId(Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    void deleteByGroupIdAndUserId(Long groupId, Long userId);
    void deleteByGroupId(Long groupId);
}
