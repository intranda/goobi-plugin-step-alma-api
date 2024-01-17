package de.intranda.goobi.plugins;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Target {

    private String variableName;

    private String path;

    // can be string or object
    private String type;

}
