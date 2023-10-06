package de.intranda.goobi.plugins;

import lombok.Getter;

public class EntryToSaveTemplate {
    private static final String DEFAULT_CHOICE = "all";
    private static final boolean DEFAULT_OVERWRITE = false;

    @Getter
    private String type;
    @Getter
    private String name;
    @Getter
    private String value;
    @Getter
    private String choice;
    @Getter
    private boolean overwrite;

    public EntryToSaveTemplate(String type, String name, String value) {
        this(type, name, value, DEFAULT_CHOICE, DEFAULT_OVERWRITE);
    }

    public EntryToSaveTemplate(String type, String name, String value, String choice) {
        this(type, name, value, choice, DEFAULT_OVERWRITE);
    }

    public EntryToSaveTemplate(String type, String name, String value, boolean overwrite) {
        this(type, name, value, DEFAULT_CHOICE, overwrite);
    }

    public EntryToSaveTemplate(String type, String name, String value, String choice, boolean overwrite) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.choice = choice;
        this.overwrite = overwrite;
    }
}
