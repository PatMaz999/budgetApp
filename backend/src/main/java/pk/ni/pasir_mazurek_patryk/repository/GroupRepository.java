package pk.ni.pasir_mazurek_patryk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pk.ni.pasir_mazurek_patryk.model.Group;
import pk.ni.pasir_mazurek_patryk.model.User;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByMemberships_User(User  user);

}
