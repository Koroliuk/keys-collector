package com.invictoprojects.keyscollector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Repository {

    @JsonProperty("name")
    private String name;

}