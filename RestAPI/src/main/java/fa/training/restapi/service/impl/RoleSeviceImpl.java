package fa.training.restapi.service.impl;

import fa.training.restapi.entity.Role;
import fa.training.restapi.repository.RoleRepository;
import fa.training.restapi.service.RoleSevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RoleSeviceImpl extends GenericServiceImpl<Role, Long> implements RoleSevice {

    protected RoleSeviceImpl(JpaRepository<Role, Long> repository) {
        super(repository);
    }

    @Override
    public Boolean existsByRoleName(String roleName) {
        return ((RoleRepository) repository).existsByRoleName(roleName);
    }

    @Override
    public Optional<Role> findByRoleName(String roleName) {
        return ((RoleRepository) repository).findByRoleName(roleName);
    }
}
