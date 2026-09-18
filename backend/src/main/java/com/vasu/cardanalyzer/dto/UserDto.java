package com.vasu.cardanalyzer.dto;

import com.vasu.cardanalyzer.model.AuthProvider;
import com.vasu.cardanalyzer.model.Role;
import com.vasu.cardanalyzer.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private String id;
    private String name;
    private String email;
    private AuthProvider provider;
    private Role role;

    public static UserDto fromEntity(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProvider(),
                user.getRole()
        );
    }
}
