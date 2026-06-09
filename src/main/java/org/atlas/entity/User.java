package org.atlas.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	public UUID id;

	@Column(nullable = false, unique = true)
	public String email;

	@Column(nullable = false)
	public String username;

	@Column(nullable = false)
	public String password;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	public Role role = Role.USER;

	@Column(nullable = false)
	public boolean active = true;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	public LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	public LocalDateTime updatedAt;

	public enum Role {
		USER,
		ADMIN,
		MODERATOR,
	}
}
