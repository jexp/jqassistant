package com.buschmais.jqassistant.core.scanner.test;

import com.buschmais.jqassistant.core.model.api.descriptor.ArtifactDescriptor;
import com.buschmais.jqassistant.core.model.api.descriptor.Descriptor;
import com.buschmais.jqassistant.core.model.api.descriptor.PackageDescriptor;
import com.buschmais.jqassistant.core.scanner.test.set.pojo.Pojo;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.buschmais.jqassistant.core.model.test.matcher.descriptor.PackageDescriptorMatcher.packageDescriptor;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertThat;

/**
 * Contains tests regarding packages.
 */
public class PackageIT extends AbstractScannerIT {

    /**
     * Verifies that all packages are added to the artifact while scanning a directory.
     *
     * @throws IOException If the test fails.
     */
    @Test
    public void artifactContainsPackages() throws IOException {
        scanTestClassesDirectory();
        // Assert that all packages of Pojo.class are contained in the artifact
        List<Matcher<? super Iterable<? super PackageDescriptor>>> packageMatchers = new ArrayList<>();
        String currentPackage = Pojo.class.getPackage().getName();
        do {
            packageMatchers.add(hasItem(packageDescriptor(currentPackage)));
            int separatorIndex = currentPackage.lastIndexOf('.');
            if (separatorIndex != -1) {
                currentPackage = currentPackage.substring(0, separatorIndex);
            } else {
                currentPackage = null;
            }
        } while (currentPackage != null);
        assertThat(query("MATCH (a:ARTIFACT)-[:CONTAINS]->p:PACKAGE WHERE a.FQN = 'artifact' RETURN p").getColumn("p"), allOf(packageMatchers));
        assertThat(query("MATCH (a:ARTIFACT)-[:CONTAINS]->p:PACKAGE WHERE a.FQN ='artifact' AND NOT p-[:CONTAINS]->(:TYPE) RETURN p").getColumn("p"), hasItem(packageDescriptor(PackageIT.class.getPackage().getName() + ".set.empty")));
    }


    /**
     * Verifies that all packages containing elements have contains relations to their children.
     *
     * @throws IOException If the test fails.
     */
    @Test
    public void nonEmptyPackages() throws IOException {
        scanTestClassesDirectory();
        TestResult query = query("MATCH (a:ARTIFACT)-[:CONTAINS]->p:PACKAGE WHERE a.FQN ='artifact' AND NOT p-[:CONTAINS]->() RETURN p");
        assertThat(query.getRows().size(), equalTo(1));
        assertThat(query.getColumn("p"), hasItem(packageDescriptor(PackageIT.class.getPackage().getName() + ".set.empty")));
    }

    private void scanTestClassesDirectory() throws IOException {
        // Determine test classes directory.
        URL resource = Pojo.class.getResource("/");
        String file = resource.getFile();
        File directory = new File(file);
        Assert.assertTrue("Expected a directory.", directory.isDirectory());
        // Scan.
        store.beginTransaction();
        ArtifactDescriptor artifact = store.create(ArtifactDescriptor.class, "artifact");
        for (Descriptor descriptor : getArtifactScanner().scanDirectory(directory)) {
            artifact.getContains().add(descriptor);
        }
        store.commitTransaction();
    }

}