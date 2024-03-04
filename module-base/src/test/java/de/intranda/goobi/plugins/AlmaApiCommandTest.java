package de.intranda.goobi.plugins;

import static org.junit.Assert.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.junit.BeforeClass;
import org.junit.Test;

public class AlmaApiCommandTest {

    private static HierarchicalConfiguration conf;

    private static String resourcesFolder;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
        //        Path goobiFolder = Paths.get(resourcesFolder, "goobi_config.properties");
        //        String goobiMainFolder = goobiFolder.getParent().toString();
        //        ConfigurationHelper.CONFIG_FILE_NAME = goobiFolder.toString();
        //        ConfigurationHelper.resetConfigurationFile();
        //        ConfigurationHelper.getInstance().setParameter("goobiFolder", goobiMainFolder + "/");

        readConfig();
    }

    @Test
    public void testConstructor() {
        AlmaApiCommand fixture = new AlmaApiCommand(conf);
        assertNotNull(fixture);
    }

    private static void readConfig() throws Exception {

        String file = "plugin_intranda_step_alma_api.xml";
        XMLConfiguration xmlConfig = new XMLConfiguration();
        xmlConfig.setDelimiterParsingDisabled(true);

        xmlConfig.load(resourcesFolder + file);

        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        // find out the sub-configuration node for the right project and step
        SubnodeConfiguration sub = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");

        conf = sub.configurationsAt("/command").get(0);

    }
}
