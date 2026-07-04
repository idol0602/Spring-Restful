package fa.training.restapi.config;

import fa.training.restapi.entity.Course;
import fa.training.restapi.entity.DefaultRoles;
import fa.training.restapi.entity.Role;
import fa.training.restapi.entity.User;
import fa.training.restapi.repository.RoleRepository;
import fa.training.restapi.repository.UserRepository;
import fa.training.restapi.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initRoles();
        initUsers();
        initCourses();
    }

    private void initRoles() {
        if (roleRepository.count() > 0) {
            log.info("Bảng roles đã có dữ liệu, bỏ qua khởi tạo.");
            return;
        }
        log.info("Bảng roles rỗng, đang tạo dữ liệu mẫu...");

        Role adminRole = Role.builder()
                .roleName(DefaultRoles.ADMIN.name())
                .description("Quản trị viên hệ thống")
                .build();

        Role teacherRole = Role.builder()
                .roleName(DefaultRoles.TEACHER.name())
                .description("Giảng viên")
                .build();

        Role studentRole = Role.builder()
                .roleName(DefaultRoles.STUDENT.name())
                .description("Học viên")
                .build();

        roleRepository.save(adminRole);
        roleRepository.save(teacherRole);
        roleRepository.save(studentRole);
        log.info("Đã tạo 3 role mặc định: ADMIN, TEACHER, STUDENT");
    }

    private void initUsers() {
        if (userRepository.count() > 0) {
            log.info("Bảng users đã có dữ liệu, bỏ qua khởi tạo.");
            return;
        }
        log.info("Bảng users rỗng, đang tạo tài khoản mặc định...");

        // Tìm các role đã tạo
        Role adminRole = roleRepository.findByRoleName(DefaultRoles.ADMIN.name())
                .orElseThrow(() -> new RuntimeException("Role ADMIN không tồn tại"));
        Role teacherRole = roleRepository.findByRoleName(DefaultRoles.TEACHER.name())
                .orElseThrow(() -> new RuntimeException("Role TEACHER không tồn tại"));
        Role studentRole = roleRepository.findByRoleName(DefaultRoles.STUDENT.name())
                .orElseThrow(() -> new RuntimeException("Role STUDENT không tồn tại"));

        // Tạo Admin mặc định
        User admin = User.builder()
                .userName("admin")
                .passWord(passwordEncoder.encode("admin123"))
                .fullName("Administrator")
                .email("admin@training.com")
                .status("Active")
                .role(adminRole)
                .build();
        adminRole.addUser(admin);

        // Tạo Teacher mặc định
        User teacher = User.builder()
                .userName("teacher")
                .passWord(passwordEncoder.encode("teacher123"))
                .fullName("Default Teacher")
                .email("teacher@training.com")
                .status("Active")
                .role(teacherRole)
                .build();
        teacherRole.addUser(teacher);

        // Tạo Student mặc định
        User student = User.builder()
                .userName("student")
                .passWord(passwordEncoder.encode("student123"))
                .fullName("Default Student")
                .email("student@training.com")
                .status("Active")
                .role(studentRole)
                .build();
        studentRole.addUser(student);

        userRepository.save(admin);
        userRepository.save(teacher);
        userRepository.save(student);
        log.info("Đã tạo 3 tài khoản mặc định: admin/admin123, teacher/teacher123, student/student123");
    }

    private void initCourses() {
        if (courseService.findAll().size() > 0) {
            log.info("Bảng courses đã có dữ liệu, bỏ qua khởi tạo.");
            return;
        }
        log.info("Bảng courses rỗng, đang tạo dữ liệu mẫu...");

        Course course1 = Course.builder()
                .courseName("Java Spring Boot")
                .duration(40)
                .description("Khóa học lập trình Backend với Spring Boot")
                .build();

        Course course2 = Course.builder()
                .courseName("Frontend React")
                .duration(30)
                .description("Khóa học lập trình Frontend với ReactJS")
                .build();

        Course course3 = Course.builder()
                .courseName("DevOps Basic")
                .duration(20)
                .description("Khóa học cơ bản về DevOps và CI/CD")
                .build();

        courseService.save(course1);
        courseService.save(course2);
        courseService.save(course3);
        log.info("Đã tạo 3 khóa học mẫu.");
    }
}
