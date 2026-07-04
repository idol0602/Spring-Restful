package fa.training.restapi.service.impl;

import fa.training.restapi.entity.User;
import fa.training.restapi.service.UserService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends GenericServiceImpl<User, Long> implements UserService {
    protected UserServiceImpl(JpaRepository<User, Long> repository) {
        super(repository);
    }
}
