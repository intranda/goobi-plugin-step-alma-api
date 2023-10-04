/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

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
