package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.MatchResult;

import org.easymock.EasyMock;
import org.goobi.beans.Process;
import org.goobi.beans.Project;
import org.goobi.beans.Ruleset;
import org.goobi.beans.Step;
import org.goobi.beans.User;
import org.goobi.production.enums.PluginReturnValue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import io.goobi.workflow.api.connection.HttpUtils;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetadatenHelper.class, VariableReplacer.class, ConfigurationHelper.class, ProcessManager.class,
        MetadataManager.class, Helper.class, HttpUtils.class })
@PowerMockIgnore({ "javax.management.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class AlmaApiPluginTest {

    private static String resourcesFolder;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File processDirectory;
    private File metadataDirectory;
    private Process process;
    private Step step;
    private Prefs prefs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Test
    public void testConstructor() throws Exception {
        AlmaApiStepPlugin plugin = new AlmaApiStepPlugin();
        assertNotNull(plugin);
    }

    @Test
    public void testInit() {
        AlmaApiStepPlugin plugin = new AlmaApiStepPlugin();
        plugin.initialize(step, "something");
        assertEquals(step.getTitel(), plugin.getStep().getTitel());
    }

    @Test
    @Ignore
    public void testRun() {
        AlmaApiStepPlugin plugin = new AlmaApiStepPlugin();
        plugin.initialize(step, "something");
        assertEquals(PluginReturnValue.FINISH, plugin.run());
    }

    @Before
    public void setUp() throws Exception {
        metadataDirectory = folder.newFolder("metadata");
        processDirectory = new File(metadataDirectory + File.separator + "1");
        processDirectory.mkdirs();
        String metadataDirectoryName = metadataDirectory.getAbsolutePath() + File.separator;
        Path metaSource = Paths.get(resourcesFolder, "meta.xml");
        Path metaTarget = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Files.copy(metaSource, metaTarget);

        Path anchorSource = Paths.get(resourcesFolder, "meta_anchor.xml");
        Path anchorTarget = Paths.get(processDirectory.getAbsolutePath(), "meta_anchor.xml");
        Files.copy(anchorSource, anchorTarget);

        PowerMock.mockStatic(ConfigurationHelper.class);
        ConfigurationHelper configurationHelper = EasyMock.createMock(ConfigurationHelper.class);
        EasyMock.expect(ConfigurationHelper.getInstance()).andReturn(configurationHelper).anyTimes();
        EasyMock.expect(configurationHelper.getMetsEditorLockingTime()).andReturn(1800000l).anyTimes();
        EasyMock.expect(configurationHelper.isAllowWhitespacesInFolder()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.useS3()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.isUseProxy()).andReturn(false).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiContentServerTimeOut()).andReturn(60000).anyTimes();
        EasyMock.expect(configurationHelper.getMetadataFolder()).andReturn(metadataDirectoryName).anyTimes();
        EasyMock.expect(configurationHelper.getRulesetFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getProcessImagesMainDirectoryName()).andReturn("00469418X_media").anyTimes();

        EasyMock.expect(configurationHelper.getConfigurationFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getGoobiFolder()).andReturn(resourcesFolder).anyTimes();
        EasyMock.expect(configurationHelper.getScriptsFolder()).andReturn(resourcesFolder).anyTimes();

        EasyMock.expect(configurationHelper.getNumberOfMetaBackups()).andReturn(0).anyTimes();
        EasyMock.replay(configurationHelper);

        PowerMock.mockStatic(HttpUtils.class);
        EasyMock.expect(HttpUtils.getStringFromUrl(EasyMock.anyString())).andReturn(getJsonResponse());

        PowerMock.mockStatic(Helper.class);
        Helper.addMessageToProcessJournal(EasyMock.anyInt(), EasyMock.anyObject(), EasyMock.anyString());
        Helper.addMessageToProcessJournal(EasyMock.anyInt(), EasyMock.anyObject(), EasyMock.anyString());
        Helper.addMessageToProcessJournal(EasyMock.anyInt(), EasyMock.anyObject(), EasyMock.anyString());

        PowerMock.mockStatic(VariableReplacer.class);
        EasyMock.expect(VariableReplacer.simpleReplace(EasyMock.anyString(), EasyMock.anyObject())).andReturn("00469418X_media").anyTimes();

        Iterable<MatchResult> results = EasyMock.createMock(Iterable.class);
        Iterator<MatchResult> iter = EasyMock.createMock(Iterator.class);
        EasyMock.expect(results.iterator()).andReturn(iter).anyTimes();
        EasyMock.expect(iter.hasNext()).andReturn(false).anyTimes();

        EasyMock.expect(VariableReplacer.findRegexMatches(EasyMock.anyString(), EasyMock.anyString())).andReturn(results).anyTimes();
        EasyMock.replay(results);
        EasyMock.replay(iter);

        PowerMock.replay(VariableReplacer.class);
        prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        Fileformat ff = new MetsMods(prefs);
        ff.read(metaTarget.toString());

        PowerMock.mockStatic(MetadatenHelper.class);
        EasyMock.expect(MetadatenHelper.getMetaFileType(EasyMock.anyString())).andReturn("mets").anyTimes();
        EasyMock.expect(MetadatenHelper.getFileformatByName(EasyMock.anyString(), EasyMock.anyObject())).andReturn(ff).anyTimes();
        EasyMock.expect(MetadatenHelper.getMetadataOfFileformat(EasyMock.anyObject(), EasyMock.anyBoolean()))
                .andReturn(Collections.emptyMap())
                .anyTimes();
        PowerMock.replay(MetadatenHelper.class);

        PowerMock.mockStatic(MetadataManager.class);
        MetadataManager.updateMetadata(1, Collections.emptyMap());
        MetadataManager.updateJSONMetadata(1, Collections.emptyMap());
        PowerMock.replay(MetadataManager.class);
        PowerMock.replay(ConfigurationHelper.class);
        PowerMock.replay(Helper.class);
        PowerMock.replay(HttpUtils.class);

        process = getProcess();

        Ruleset ruleset = PowerMock.createMock(Ruleset.class);
        ruleset.setTitel("ruleset");
        ruleset.setDatei("ruleset.xml");
        EasyMock.expect(ruleset.getDatei()).andReturn("ruleset.xml").anyTimes();
        process.setRegelsatz(ruleset);
        EasyMock.expect(ruleset.getPreferences()).andReturn(prefs).anyTimes();
        PowerMock.replay(ruleset);

    }

    private String getJsonResponse() {
        String s = "{\n"
                + "  \"status\": \"success\",\n"
                + "  \"message\": \"Found 1\",\n"
                + "  \"payload\": {\n"
                + "    \"thesis_id\": \"TID123\",\n"
                + "    \"final_draft_id\": \"FDI123\",\n"
                + "    \"similarity_submission_id\": \"SSI123\",\n"
                + "    \"draft\": {\n"
                + "      \"id\": \"FDI123\",\n"
                + "      \"filename\": \"<filename of submitted pdf>\",\n"
                + "      \"url\": \"<url of file if present>\",\n"
                + "      \"hash\": \"<sha256 has of submitted pdf>\"\n"
                + "    },\n"
                + "    \"attachments\": [\n"
                + "      {\n"
                + "        \"id\": \"<id>\",\n"
                + "        \"filename\": \"<filename>\",\n"
                + "        \"url\": \"<url of file if present>\",\n"
                + "        \"hash\": \"<sha256>\"\n"
                + "      }\n"
                + "    ],\n"
                + "    \"title_eng\": \"Main title\",\n"
                + "    \"title_orig\": \"Haupttitel\",\n"
                + "    \"language\": \"Deutsch\",\n"
                + "    \"abstract_eng\": \"Here is the English abstract\",\n"
                + "    \"abstract_orig\": \"Hier steht eine deutsche Zusammenfassung\",\n"
                + "    \"coauthors\": [\n"
                + "      {\n"
                + "        \"firstname\": \"Vorname Student\",\n"
                + "        \"surname\": \"Nachname Student\",\n"
                + "        \"stud_id\": \"12345\"\n"
                + "      }\n"
                + "    ],\n"
                + "    \"reviewers\": [\n"
                + "      {\n"
                + "        \"firstname\": \"Vorname Reviewer 1\",\n"
                + "        \"surname\": \"Nachname Reviewer 1\",\n"
                + "        \"email\": \"name@email.ac.at\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"firstname\": \"Vorname Reviewer 2\",\n"
                + "        \"surname\": \"Nachname Reviewer 2\",\n"
                + "        \"email\": \"name2@email.ac.at\"\n"
                + "      }\n"
                + "    ],\n"
                + "    \"submitted_at\": \"2023-06-02\",\n"
                + "    \"graded_at\": \"2023-07-20\",\n"
                + "    \"type\": \"Master\",\n"
                + "    \"program\": \"Masterstudium Steuern und Rechnungslegung\",\n"
                + "    \"achieving_title\": \"Master of Arts\",\n"
                + "    \"page_count\": \"98\",\n"
                + "    \"keywords\": [\n"
                + "      \"UGB\",\n"
                + "      \"IFRS\",\n"
                + "      \"Bilanzierung\",\n"
                + "      \"Cloud\"\n"
                + "    ],\n"
                + "    \"allow_publish\": {\n"
                + "      \"label\": \"Ich stimme der Veröffentlichung zu\",\n"
                + "      \"value\": true\n"
                + "    },\n"
                + "    \"own_work\": {\n"
                + "      \"label\": \"Das habe ich selbst verfasst\",\n"
                + "      \"value\": true\n"
                + "    },\n"
                + "    \"is_blocking\": false,\n"
                + "    \"blocking_length\": \"\",\n"
                + "    \"blocking_description\": \"\"\n"
                + "  }\n"
                + "}\n"
                + "";

        return s;
    }

    public Process getProcess() {
        Project project = new Project();
        project.setTitel("SampleProject");

        Process process = new Process();
        process.setTitel("00469418X");
        process.setProjekt(project);
        process.setId(1);
        List<Step> steps = new ArrayList<>();
        step = new Step();
        step.setReihenfolge(1);
        step.setProzess(process);
        step.setTitel("test step");
        step.setBearbeitungsstatusEnum(StepStatus.OPEN);
        User user = new User();
        user.setVorname("Firstname");
        user.setNachname("Lastname");
        step.setBearbeitungsbenutzer(user);
        steps.add(step);

        process.setSchritte(steps);

        try {
            createProcessDirectory(processDirectory);
        } catch (IOException e) {
        }

        return process;
    }

    private void createProcessDirectory(File processDirectory) throws IOException {

        // image folder
        File imageDirectory = new File(processDirectory.getAbsolutePath(), "images");
        imageDirectory.mkdir();
        // master folder
        File masterDirectory = new File(imageDirectory.getAbsolutePath(), "00469418X_master");
        masterDirectory.mkdir();

        // media folder
        File mediaDirectory = new File(imageDirectory.getAbsolutePath(), "00469418X_media");
        mediaDirectory.mkdir();

        // TODO add some file
    }
}
