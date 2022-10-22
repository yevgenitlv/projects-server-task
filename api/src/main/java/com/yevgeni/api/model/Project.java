package com.yevgeni.api.model;

import java.util.List;



import lombok.*;
import lombok.ToString;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Project {

    private String id;
    private String name;
    private boolean status;
    private List <String> userIds;
}