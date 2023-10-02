package de.intranda.goobi.plugins;

import lombok.Data;

@Data
public class ProcessPropertyTemplate {
    private String name;
    private String value;
    private String choice;
    private boolean overwrite;

    public ProcessPropertyTemplate(String name, String value) {
        this(name, value, "all", false);
    }

    public ProcessPropertyTemplate(String name, String value, String choice) {
        this(name, value, choice, false);
    }

    public ProcessPropertyTemplate(String name, String value, boolean overwrite) {
        this(name, value, "all", overwrite);
    }

    public ProcessPropertyTemplate(String name, String value, String choice, boolean overwrite) {
        this.name = name;
        this.value = value;
        this.choice = choice;
        this.overwrite = overwrite;
    }

}
