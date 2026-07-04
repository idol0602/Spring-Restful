package fa.training.restapi.service.impl;

import fa.training.restapi.entity.Course;
import fa.training.restapi.service.CourseService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl extends GenericServiceImpl<Course, Long> implements CourseService {
    protected CourseServiceImpl(JpaRepository<Course, Long> repository) {
        super(repository);
    }
}
