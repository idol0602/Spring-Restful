package fa.training.restapi.repository;

import fa.training.restapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = { "role" })
    Optional<User> findByUserName(String username);

    Boolean existsByUserName(String username);
}
