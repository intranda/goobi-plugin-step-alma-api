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
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.Before;
import org.junit.BeforeClass;
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
import de.sub.goobi.helper.XmlTools;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import io.goobi.workflow.api.connection.HttpUtils;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MetadatenHelper.class, VariableReplacer.class, ConfigurationHelper.class, ProcessManager.class, MetadataManager.class, Helper.class,
    HttpUtils.class, PropertyManager.class })
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
    public void testRun() {
        AlmaApiStepPlugin plugin = new AlmaApiStepPlugin();
        plugin.initialize(step, "something");
        assertEquals(PluginReturnValue.FINISH, plugin.run());

        // open mets file
        Namespace mets = Namespace.getNamespace("mets", "http://www.loc.gov/METS/");
        Namespace mods = Namespace.getNamespace("mods", "http://www.loc.gov/mods/v3");
        Namespace goobi = Namespace.getNamespace("goobi", "http://meta.goobi.org/v1.5.1/");
        Path metaFile = Paths.get(processDirectory.getAbsolutePath(), "meta.xml");
        Document doc = XmlTools.readDocumentFromFile(metaFile);
        Element goobiElement = doc.getRootElement()
                .getChild("dmdSec", mets)
                .getChild("mdWrap", mets)
                .getChild("xmlData", mets)
                .getChild("mods", mods)
                .getChild("extension", mods)
                .getChild("goobi", goobi);
        List<Element> children = goobiElement.getChildren();
        assertEquals(13, children.size());

        Element student = children.get(11);
        assertEquals("Name", student.getChildren().get(0).getAttributeValue("name"));
        assertEquals("fullname", student.getChildren().get(0).getValue());
        assertEquals("StudentId", student.getChildren().get(1).getAttributeValue("name"));
        assertEquals("matricle", student.getChildren().get(1).getValue());
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
        MetadataManager.updateMetadata(1, Collections.emptyMap());
        MetadataManager.updateJSONMetadata(1, Collections.emptyMap());
        MetadataManager.updateJSONMetadata(1, Collections.emptyMap());

        PowerMock.mockStatic(PropertyManager.class);
        EasyMock.expect(PropertyManager.getProcessPropertiesForProcess(EasyMock.anyInt())).andReturn(Collections.emptyList()).anyTimes();
        PropertyManager.saveProcessProperty(EasyMock.anyObject());

        PowerMock.replay(PropertyManager.class);
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
        String s = "{" + "   \"status\":\"success\"," + "   \"message\":\"Found 1 thesis\"," + "   \"total\":null," + "   \"thesis\":{"
                + "      \"student\":{" + "         \"fullname\":\"NACHNAME Vorname\"," + "         \"firstname\":\"Vorname\","
                + "         \"surname\":\"NACHNAME\"," + "         \"email\":\"name@mail.at\"," + "         \"role\":\"Student\","
                + "         \"reviewer_role\":null," + "         \"student_fk\":123," + "         \"matricle\":1234567890,"
                + "         \"employee_fk\":null" + "      }," + "      \"tid\":106," + "      \"grade_protocol_at\":\"2023-12-14T10:28:11\","
                + "      \"degree_program\":\"Bachelorstudium Wirtschafts- und Sozialwissenschaften\"," + "      \"abstract_english\":null,"
                + "      \"abstract_original\":null," + "      \"keywords\":[" + "         \"test 1\"," + "         \"test2\","
                + "         \"test keyword 3\"" + "      ]," + "      \"blocking_months\":null," + "      \"blocking_description\":null,"
                + "      \"language\":\"German\"," + "      \"type\":\"Bachelorarbeit\"," + "      \"blocking_state\":{" + "         \"tid\":1,"
                + "         \"description\":\"no block is requested\"," + "         \"key\":\"NOT_REQUESTED\"," + "         \"state_order\":1"
                + "      }," + "      \"is_blocking\":false," + "      \"is_cumulative\":false," + "      \"cumulative_titles\":null,"
                + "      \"state\":{" + "         \"tid\":7," + "         \"description\":\"grade has been published, attendee certificate\","
                + "         \"key\":\"GRADE_PUBLISHED\"," + "         \"state_order\":7" + "      }," + "      \"final_draft\":{"
                + "         \"tid\":106," + "         \"thesis_tid\":106," + "         \"version\":1," + "         \"title_original\":\"Haupttitel\","
                + "         \"title_english\":\"Main title\"," + "         \"legal_agreement\":[" + "            " + "         ],"
                + "         \"is_similarity_report\":true," + "         \"created_at\":\"2023-12-14T10:28:11\"," + "         \"archived_at\":null,"
                + "         \"archived_reason\":null," + "         \"draft_pdf\":{" + "            \"tid\":106,"
                + "            \"filepath\":\"<path to pdf file>\"," + "            \"filename\":\"filename.pdf\","
                + "            \"filename_orig\":\"filename.pdf\"," + "            \"filetype\":\".pdf\"," + "            \"filehash\":\"a hash\","
                + "            \"downloaded_at\":null," + "            \"downloaded_by\":null" + "         }," + "         \"page_count\":null,"
                + "         \"submitted_at\":\"2023-12-14T10:28:11\"," + "         \"similarity_scoring\":34.0,"
                + "         \"similarity_submission_id\":\"38d06a03-96d5-4f25-8fc5-199af29d2bc8\","
                + "         \"similarity_report_pdf_id\":\"9586b810-c30c-4b9a-bdfe-8ff5108d44f0\"," + "         \"similarity_report_pdf_file\":null,"
                + "         \"similarity_state\":{" + "            \"tid\":8,"
                + "            \"description\":\"similarity report has been completed by turnitin\","
                + "            \"key\":\"SIMILARITY_REPORT_PDF_COMPLETE\"," + "            \"state_order\":8" + "         }" + "      },"
                + "      \"reviewers\":[" + "         {" + "            \"fullname\":\"Reviewer complete name\","
                + "            \"firstname\":\"Reviewer Firstname 1\"," + "            \"surname\":\"Reviewer Lastname 1\","
                + "            \"email\":\"reviewer1@mail.at\"," + "            \"role\":\"BeurteilerIn\"," + "            \"reviewer_role\":{"
                + "               \"role_id\":1," + "               \"name\":\"BeurteilerIn\"," + "               \"order\":1" + "            },"
                + "            \"student_fk\":null," + "            \"matricle\":null," + "            \"employee_fk\":1234" + "         },"
                + "         {" + "            \"fullname\":\"Reviewer 2 complete name\"," + "            \"firstname\":\"Reviewer Firstname 2\","
                + "            \"surname\":\"Reviewer Lastname 2\"," + "            \"email\":\"reviewer2@mail.at\","
                + "            \"role\":\"BeurteilerIn\"," + "            \"reviewer_role\":{" + "               \"role_id\":1,"
                + "               \"name\":\"BeurteilerIn\"," + "               \"order\":1" + "            }," + "            \"student_fk\":null,"
                + "            \"matricle\":null," + "            \"employee_fk\":5678" + "         }" + "      ],"
                + "      \"shared_thesis_identifier\":null," + "      \"attachments\":[" + "         {" + "            \"tid\":107,"
                + "            \"filepath\":\"<atached pdf file>\"," + "            \"filename\":\"otherfile.pdf\","
                + "            \"filename_orig\":\"otherfile.pdf\"," + "            \"filetype\":\".pdf\"," + "            \"filehash\":\"a hash\","
                + "            \"downloaded_at\":null," + "            \"downloaded_by\":null" + "         }" + "      ],"
                + "      \"coauthors\":null," + "      \"is_download_complete\":false" + "   }" + "}";

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
