package com.yevgeni.demo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.*;
import lombok.ToString;

@ToString
@AllArgsConstructor
@EqualsAndHashCode(of = {"userId","name","password"})
@NoArgsConstructor
@Data
@Document(value = "users")
public class User {

    @Id
    private String userId;
    private String name;
    private String password;
    
}