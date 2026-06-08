package pk.ni.pasir_mazurek_patryk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pk.ni.pasir_mazurek_patryk.model.Debt;

import java.util.List;

@Repository
public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);

    List<Debt> findByCreditorId(Long creditorId);
}
