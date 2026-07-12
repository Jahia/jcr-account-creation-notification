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
 * <p>These tests act as regression guards over shipped module files, including that the shipped
 * OSGi config filename encodes the PID the module actually consumes, and that the build-metadata
 * version is consistent across pom.xml, package.json and AGENTS.md.
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
    // D2 — shipped .cfg filename PID must equal the consumed PID.
    //
    // Felix FileInstall derives the config PID from the .cfg filename. Every consumer
    // (ManagedService registration + the saveSettings mutation) reads
    // JcrAccountCreationNotificationConfig.PID = "org.jahia.community.jcraccountcreationnotification".
    // The shipped default file MUST therefore be named "<that PID>.cfg", otherwise the default
    // config is delivered to a PID nothing reads and operator edits to it are silently ignored.
    //
    // Regression guard: keeps the shipped filename and the consumed PID from diverging again.
    // -----------------------------------------------------------------------

    @Test
    public void d2_shippedCfgFilename_matchesConsumedPid() {
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

        // Exactly one default config is shipped.
        assertThat(shippedPids).hasSize(1);
        final String shippedPid = shippedPids.get(0);

        // The shipped filename must encode exactly the PID the module consumes.
        assertThat(shippedPid)
                .as("shipped .cfg filename PID must equal the consumed config PID so "
                        + "Felix FileInstall delivers operator edits to the PID the module reads")
                .isEqualTo(JcrAccountCreationNotificationConfig.PID);
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
    // D1 — build-metadata version must be consistent across all sources.
    //
    // pom.xml is authoritative for the Maven build; package.json and AGENTS.md must agree with it.
    // Regression guard: keeps the three version declarations from drifting apart again.
    // -----------------------------------------------------------------------

    @Test
    public void d1_versionMetadata_isConsistentAcrossSources() throws IOException {
        final String pomVersion = moduleVersionFromPom(readFile("pom.xml"));
        final String pkgVersion = firstMatch(readFile("package.json"),
                "\"version\"\\s*:\\s*\"([^\"]+)\"");
        final String agentsVersion = firstMatch(readFile("AGENTS.md"),
                "\\*\\*version\\*\\*.*?`([0-9]+\\.[0-9]+\\.[0-9]+[^`]*)`");

        // pom.xml is authoritative; the other two must match it.
        assertThat(pkgVersion)
                .as("package.json version must match the authoritative pom.xml version")
                .isEqualTo(pomVersion);
        assertThat(agentsVersion)
                .as("AGENTS.md version must match the authoritative pom.xml version")
                .isEqualTo(pomVersion);
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
