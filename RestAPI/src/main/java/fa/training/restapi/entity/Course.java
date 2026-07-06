package fa.training.restapi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "course_seq_gen")
    @SequenceGenerator(
            name = "course_seq_gen",
            sequenceName = "course_seq_id",
            initialValue = 1,
            allocationSize = 1
    )
    private Long courseId;

    @Nationalized
    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Nationalized
    @Column(name = "description", nullable = false)
    private String description;
}
