package fa.training.restapi.service;

import fa.training.restapi.entity.Role;

import java.util.Optional;

public interface RoleSevice extends GenericService<Role, Long>{
    Boolean existsByRoleName(String roleName);
    Optional<Role> findByRoleName(String roleName);
}
