package fa.training.restapi.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.Nationalized;

import java.util.HashSet;
import java.util.Set;

import static jakarta.persistence.CascadeType.*;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "users")
@EqualsAndHashCode(exclude = "users")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long roleId;

    @Nationalized
    @Column(name = "role_name", nullable = false, unique = true)
    private String roleName;

    @Nationalized
    @Column(name = "description", nullable = false)
    private String description;

    @OneToMany(mappedBy = "role", cascade = { MERGE, PERSIST })
    @Builder.Default
    @JsonIgnore
    private Set<User> users = new HashSet();

    public void addUser(User user) {
        if (user != null) {
            this.users.add(user);
            user.setRole(this);
        }
    }

    public void deleteUser(User user) {
        if (user != null) {
            this.users.remove(user);
            user.setRole(null);
        }
    }
}
