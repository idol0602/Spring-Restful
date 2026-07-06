package fa.training.restapi.controller;

import fa.training.restapi.dto.request.CourseRequest;
import fa.training.restapi.dto.response.ApiResponse;
import fa.training.restapi.entity.Course;
import fa.training.restapi.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class CourseController {
        private final CourseService courseService;

        // Create a new course
        @PostMapping
        public ResponseEntity<ApiResponse<Course>> createCourse(@Valid @RequestBody CourseRequest request) {
                Course course = Course.builder()
                                .courseName(request.getCourseName())
                                .duration(request.getDuration())
                                .description(request.getDescription())
                                .build();

                Course savedCourse = courseService.save(course);
                ApiResponse<Course> responseBody;
                if (savedCourse == null) {
                        responseBody = ApiResponse.<Course>builder()
                                        .success(false)
                                        .message("Failed to create course")
                                        .build();
                        return new ResponseEntity<>(responseBody, HttpStatus.INTERNAL_SERVER_ERROR);
                }
                responseBody = ApiResponse.<Course>builder()
                                .success(true)
                                .message("Course created successfully")
                                .result(savedCourse)
                                .build();
                return new ResponseEntity<>(responseBody, HttpStatus.CREATED);
        }

        // Get all courses
        @GetMapping
        @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
        public ResponseEntity<ApiResponse<List<Course>>> getAllCourses() {
                List<Course> courses = courseService.findAll();
                ApiResponse<List<Course>> responseBody = ApiResponse.<List<Course>>builder()
                                .success(true)
                                .message("Courses retrieved successfully")
                                .result(courses)
                                .build();
                return ResponseEntity.ok(responseBody);
        }

        // Get a course by ID
        @GetMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
        public ResponseEntity<ApiResponse<Course>> getCourseById(@PathVariable Long id) {
                Optional<Course> course = courseService.findById(id);
                ApiResponse<Course> responseBody;
                if (!course.isPresent()) {
                        responseBody = ApiResponse.<Course>builder()
                                        .success(false)
                                        .message("Course not found")
                                        .build();
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseBody);
                }
                responseBody = ApiResponse.<Course>builder()
                                .success(true)
                                .message("Course retrieved successfully")
                                .result(course.get())
                                .build();
                return ResponseEntity.ok(responseBody);
        }

        // Get courses with pagination
        @GetMapping("/page")
        @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
        public ResponseEntity<ApiResponse<Page<Course>>> getCoursesPage(@ParameterObject Pageable pageable) {
                Page<Course> page = courseService.findAll(pageable);
                ApiResponse<Page<Course>> responseBody = ApiResponse.<Page<Course>>builder()
                                .success(true)
                                .message("Courses page retrieved successfully")
                                .result(page)
                                .build();
                return ResponseEntity.ok(responseBody);
        }

        // Update a course by ID
        @PutMapping("/{id}")
        public ResponseEntity<ApiResponse<Course>> updateCourse(@PathVariable Long id,
                        @Valid @RequestBody CourseRequest request) {
                Course course = Course.builder()
                                .courseId(id)
                                .courseName(request.getCourseName())
                                .duration(request.getDuration())
                                .description(request.getDescription())
                                .build();

                Course updatedCourse = courseService.save(course);
                ApiResponse<Course> responseBody = ApiResponse.<Course>builder()
                                .success(true)
                                .message("Course updated successfully")
                                .result(updatedCourse)
                                .build();
                return ResponseEntity.ok(responseBody);
        }

        // Delete a course by ID
        @DeleteMapping("/{id}")
        public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long id) {
                courseService.deleteById(id);
                ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Course deleted successfully")
                                .build();
                return ResponseEntity.ok(responseBody);
        }

        // Import from CSV file
        @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<List<Course>>> importCourses(
                        @RequestParam("file") MultipartFile file) {
                try {
                        courseService.importFromCsv(file);
                        ApiResponse<List<Course>> responseBody = ApiResponse.<List<Course>>builder()
                                        .success(true)
                                        .message("Courses imported successfully")
                                        .build();
                        return new ResponseEntity<>(responseBody, HttpStatus.CREATED);
                } catch (Exception e) {
                        ApiResponse<List<Course>> responseBody = ApiResponse.<List<Course>>builder()
                                        .success(false)
                                        .message("Failed to import courses: " + e.getMessage())
                                        .build();
                        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
                }
        }

        // Export to CSV
        @GetMapping("/export")
        @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
        public ResponseEntity<StreamingResponseBody> exportCourses() {
                StreamingResponseBody responseBody = outputStream -> courseService.exportToCsv(outputStream);

                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=courses.csv")
                                .contentType(MediaType.parseMediaType("text/csv"))
                                .body(responseBody);
        }

        // Delete many
        @DeleteMapping("/batch")
        public ResponseEntity<ApiResponse<Void>> deleteManyCourses(@RequestBody List<Long> ids) {
                courseService.deleteAll(ids);
                ApiResponse<Void> responseBody = ApiResponse.<Void>builder()
                                .success(true)
                                .message("Batch deletion completed successfully")
                                .build();
                return ResponseEntity.ok(responseBody);
        }
}
