package org.jahia.community.jcraccountcreationnotification;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resource/metadata consistency guards over shipped module files.
 *
 * <p>Surefire runs with the module base directory as the working directory, so the shipped
 * source files are read via paths relative to {@code ${basedir}}.
 *
 * <p><b>Two of these tests are CHARACTERIZATION tests</b>: they assert the module's <i>current</i>
 * (defective) state so the suite stays green while documenting a known defect. Each is explicitly
 * labelled for Stage 7 to <b>invert</b> once the underlying fix lands.
 */
public class ModuleResourceConsistencyTest {

    private static final String CONFIG_DIR = "src/main/resources/META-INF/configurations";

    private static String readFile(String relativePath) throws IOException {
        final File file = new File(relativePath);
        assertThat(file)
                .as("expected shipped file to exist: %s", relativePath)
                .exists();
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // D2 ⚠ CHARACTERIZATION — shipped .cfg filename PID vs consumed PID.
    //
    // Felix FileInstall derives the config PID from the .cfg filename. The module ships
    // "org.jahia.modules.jcraccountcreationnotification.cfg" (PID org.jahia.MODULES...),
    // but every consumer (ManagedService registration + the saveSettings mutation) uses
    // JcrAccountCreationNotificationConfig.PID = "org.jahia.COMMUNITY...". They differ, so
    // the shipped default file is delivered to a PID nothing reads and operator edits to it
    // are silently ignored.
    //
    // This test documents the CURRENT (mismatched) state so it passes today. STAGE 7 fix:
    // rename the shipped .cfg to <consumer PID>.cfg (keeping the "# default configuration"
    // first line) and INVERT this test to assert the shipped PID EQUALS the consumer PID.
    // -----------------------------------------------------------------------

    @Test
    public void d2_shippedCfgPid_currentlyMismatchesConsumerPid_characterization() {
        final File configDir = new File(CONFIG_DIR);
        assertThat(configDir)
                .as("configurations directory should exist: %s", CONFIG_DIR)
                .isDirectory();

        final File[] cfgFiles = configDir.listFiles((dir, name) -> name.endsWith(".cfg"));
        assertThat(cfgFiles).as("at least one shipped .cfg").isNotNull().isNotEmpty();

        final List<String> shippedPids = Arrays.stream(cfgFiles)
                .map(File::getName)
                .map(name -> name.substring(0, name.length() - ".cfg".length()))
                .collect(Collectors.toList());

        // Exactly one default config is shipped today.
        assertThat(shippedPids).hasSize(1);
        final String shippedPid = shippedPids.get(0);

        // CURRENT (defective) state: the shipped filename encodes the wrong PID namespace.
        assertThat(shippedPid).isEqualTo("org.jahia.modules.jcraccountcreationnotification");
        assertThat(shippedPid)
                .as("STAGE-7: after the rename this must become isEqualTo(...) — see class doc")
                .isNotEqualTo(JcrAccountCreationNotificationConfig.PID);
    }

    // -----------------------------------------------------------------------
    // U10 — definitions.cnd is a stub: only namespace declarations, no node types.
    // Change-detector: adding a real [nodeType] block should fail this and prompt review.
    // -----------------------------------------------------------------------

    @Test
    public void u10_definitionsCnd_declaresNamespacesOnly_noNodeTypes() throws IOException {
        final String cnd = readFile("src/main/resources/META-INF/definitions.cnd");

        final List<String> meaningfulLines = Arrays.stream(cnd.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                .collect(Collectors.toList());

        assertThat(meaningfulLines)
                .as("every non-empty line must be a namespace declaration")
                .allMatch(line -> line.startsWith("<") && line.endsWith(">"));
        assertThat(cnd)
                .as("no node-type definition block ('[' ... ']') is present")
                .doesNotContain("[");
    }

    // -----------------------------------------------------------------------
    // D1 CHARACTERIZATION — version drift across build metadata.
    //
    // pom.xml is authoritative for the Maven build; package.json and AGENTS.md have drifted.
    // Documents the CURRENT distinct values so it passes today. STAGE 7 fix: reconcile all
    // three to a single version and INVERT this to assert pom == package.json == AGENTS.md.
    // -----------------------------------------------------------------------

    @Test
    public void d1_versionMetadata_currentlyDrifts_characterization() throws IOException {
        final String pomVersion = moduleVersionFromPom(readFile("pom.xml"));
        final String pkgVersion = firstMatch(readFile("package.json"),
                "\"version\"\\s*:\\s*\"([^\"]+)\"");
        final String agentsVersion = firstMatch(readFile("AGENTS.md"),
                "\\*\\*version\\*\\*.*?`([0-9]+\\.[0-9]+\\.[0-9]+[^`]*)`");

        // CURRENT (drifted) values.
        assertThat(pomVersion).isEqualTo("2.0.3-SNAPSHOT");
        assertThat(pkgVersion).isEqualTo("2.0.0-SNAPSHOT");
        assertThat(agentsVersion).isEqualTo("2.0.1-SNAPSHOT");

        assertThat(List.of(pomVersion, pkgVersion, agentsVersion).stream().distinct().count())
                .as("STAGE-7: reconcile to one version, then assert all three are equal")
                .isGreaterThan(1L);
    }

    /** Module version = the {@code <version>} immediately after the closing {@code </parent>}. */
    private static String moduleVersionFromPom(String pom) {
        final int afterParent = pom.indexOf("</parent>");
        final String scope = afterParent >= 0 ? pom.substring(afterParent) : pom;
        return firstMatch(scope, "<version>([^<]+)</version>");
    }

    private static String firstMatch(String text, String regex) {
        final Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        assertThat(m.find()).as("pattern %s must match", regex).isTrue();
        return m.group(1).trim();
    }
}
