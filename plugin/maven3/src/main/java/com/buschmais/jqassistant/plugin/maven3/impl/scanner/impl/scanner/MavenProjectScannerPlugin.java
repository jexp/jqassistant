package com.buschmais.jqassistant.plugin.maven3.impl.scanner.impl.scanner;

import static com.buschmais.jqassistant.plugin.java.api.scanner.JavaScope.CLASSPATH;
import static com.buschmais.jqassistant.plugin.junit4.api.scanner.JunitScope.TESTREPORTS;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.ScannerContext;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.ArtifactDescriptor;
import com.buschmais.jqassistant.plugin.common.api.model.DependsOnDescriptor;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenProjectDescriptor;
import com.buschmais.jqassistant.plugin.maven3.api.model.MavenProjectDirectoryDescriptor;
import com.buschmais.jqassistant.plugin.maven3.api.scanner.AbstractMavenProjectScannerPlugin;

/**
 * A project scanner plugin for maven projects.
 */
public class MavenProjectScannerPlugin extends AbstractMavenProjectScannerPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenProjectScannerPlugin.class);

    @Override
    public boolean accepts(MavenProject item, String path, Scope scope) throws IOException {
        return true;
    }

    @Override
    public FileDescriptor scan(MavenProject project, String path, Scope scope, Scanner scanner) throws IOException {
        ScannerContext context = scanner.getContext();
        MavenProjectDirectoryDescriptor projectDescriptor = resolveProject(project, MavenProjectDirectoryDescriptor.class, context);
        projectDescriptor.setPackaging(project.getPackaging());
        Artifact artifact = project.getArtifact();
        ArtifactDescriptor mainArtifactDescriptor = scanClassesDirectory(projectDescriptor, artifact, false, project.getBuild().getOutputDirectory(), scanner);
        ArtifactDescriptor testArtifactDescriptor = scanClassesDirectory(projectDescriptor, artifact, true, project.getBuild().getTestOutputDirectory(),
                scanner);
        addProjectDetails(project, projectDescriptor, mainArtifactDescriptor, testArtifactDescriptor, context);
        scanTestReports(scanner, project.getBuild().getDirectory() + "/surefire-reports");
        scanTestReports(scanner, project.getBuild().getDirectory() + "/failsafe-reports");
        return projectDescriptor;
    }

    /**
     * Add project specific information.
     * 
     * @param project
     *            The project.
     * @param projectDescriptor
     *            The project descriptor.
     * @param mainArtifactDescriptor
     *            The artifact descriptor representing the main artifact.
     * @param testArtifactDescriptor
     *            The artifact descriptor representing the test artifact.
     */
    private void addProjectDetails(MavenProject project, MavenProjectDirectoryDescriptor projectDescriptor, ArtifactDescriptor mainArtifactDescriptor,
            ArtifactDescriptor testArtifactDescriptor, ScannerContext scannerContext) {
        addParent(project, projectDescriptor, scannerContext);
        addModules(project, projectDescriptor, scannerContext);
        addDependencies(project, mainArtifactDescriptor, testArtifactDescriptor, scannerContext);
    }

    /**
     * Add the relation to the parent project.
     * 
     * @param project
     *            The project.
     * @param projectDescriptor
     *            The project descriptor.
     */
    private void addParent(MavenProject project, MavenProjectDirectoryDescriptor projectDescriptor, ScannerContext scannerContext) {
        MavenProject parent = project.getParent();
        if (parent != null) {
            MavenProjectDescriptor parentDescriptor = resolveProject(parent, MavenProjectDescriptor.class, scannerContext);
            projectDescriptor.setParent(parentDescriptor);
        }
    }

    /**
     * Add relations to the modules.
     * 
     * @param project
     *            The project.
     * @param projectDescriptor
     *            The project descriptor.
     */
    private void addModules(MavenProject project, MavenProjectDirectoryDescriptor projectDescriptor, ScannerContext scannerContext) {
        File projectDirectory = project.getBasedir();
        Set<File> modules = new HashSet<>();
        for (String moduleName : (List<String>) project.getModules()) {
            File module = new File(projectDirectory, moduleName);
            modules.add(module);
        }
        for (MavenProject module : (List<MavenProject>) project.getCollectedProjects()) {
            if (modules.contains(module.getBasedir())) {
                MavenProjectDescriptor moduleDescriptor = resolveProject(module, MavenProjectDescriptor.class, scannerContext);
                projectDescriptor.getModules().add(moduleDescriptor);
            }
        }
    }

    /**
     * Add dependency relations to the artifacts.
     * 
     * @param project
     *            The project.
     * @param mainArtifactDescriptor
     *            The artifact descriptor representing the main artifact.
     * @param testArtifactDescriptor
     *            The artifact descriptor representing the test artifact.
     * @param scannerContext
     *            The scanner context.
     */
    private void addDependencies(MavenProject project, ArtifactDescriptor mainArtifactDescriptor, ArtifactDescriptor testArtifactDescriptor,
            ScannerContext scannerContext) {
        if (mainArtifactDescriptor != null && testArtifactDescriptor != null) {
            DependsOnDescriptor dependsOnDescriptor = scannerContext.getStore().create(testArtifactDescriptor, DependsOnDescriptor.class,
                    mainArtifactDescriptor);
            dependsOnDescriptor.setScope(Artifact.SCOPE_TEST);
        }
        for (Artifact dependency : (Set<Artifact>) project.getDependencyArtifacts()) {
            ArtifactDescriptor dependencyDescriptor = resolveArtifact(dependency, scannerContext);
            DependsOnDescriptor dependsOnDescriptor;
            ArtifactDescriptor dependentDescriptor;
            String scope = dependency.getScope();
            if (Artifact.SCOPE_TEST.equals(scope)) {
                dependentDescriptor = testArtifactDescriptor;
            } else {
                dependentDescriptor = mainArtifactDescriptor;
            }
            if (dependentDescriptor != null) {
                dependsOnDescriptor = scannerContext.getStore().create(dependentDescriptor, DependsOnDescriptor.class, dependencyDescriptor);
                dependsOnDescriptor.setScope(scope);
                dependsOnDescriptor.setOptional(dependency.isOptional());
            }
        }
    }

    /**
     * Scan the given directory for classes.
     * 
     * @param directoryName
     *            The directory.
     * @throws java.io.IOException
     *             If scanning fails.
     */
    private ArtifactDescriptor scanClassesDirectory(MavenProjectDirectoryDescriptor projectDescriptor, Artifact artifact, boolean testJar,
            final String directoryName, Scanner scanner) throws IOException {
        final File directory = new File(directoryName);
        if (!directory.exists()) {
            LOGGER.info("Directory '" + directory.getAbsolutePath() + "' does not exist, skipping scan.");
        } else {
            ArtifactDescriptor artifactDescriptor = resolveArtifact(artifact, testJar, scanner.getContext());
            scanner.getContext().push(ArtifactDescriptor.class, artifactDescriptor);
            try {
                scanner.scan(directory, directoryName, CLASSPATH);
            } finally {
                scanner.getContext().pop(ArtifactDescriptor.class);
            }
            projectDescriptor.getCreatesArtifacts().add(artifactDescriptor);
            return artifactDescriptor;
        }
        return null;
    }

    /**
     * Scans a directory for test reports.
     * 
     * @param directoryName
     *            The directory name.
     * @throws java.io.IOException
     *             If scanning fails.
     */
    private void scanTestReports(Scanner scanner, String directoryName) throws IOException {
        final File directory = new File(directoryName);
        if (directory.exists()) {
            scanner.scan(directory, TESTREPORTS);
        }
    }
}
