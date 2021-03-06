package com.buschmais.jqassistant.scm.cli;

import static com.buschmais.jqassistant.plugin.java.api.scanner.JavaScope.CLASSPATH;
import static com.buschmais.jqassistant.scm.cli.Log.getLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;

import com.buschmais.jqassistant.core.plugin.api.PluginRepositoryException;
import com.buschmais.jqassistant.core.plugin.api.ScannerPluginRepository;
import com.buschmais.jqassistant.core.plugin.impl.ScannerPluginRepositoryImpl;
import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.ScannerPlugin;
import com.buschmais.jqassistant.core.scanner.impl.ScannerImpl;
import com.buschmais.jqassistant.core.store.api.Store;

/**
 * @author jn4, Kontext E GmbH, 23.01.14
 */
public class ScanTask extends AbstractJQATask implements OptionsConsumer {
    private final List<String> directoryNames = new ArrayList<>();

    public ScanTask() {
        super("scan");
    }

    protected void executeTask(final Store store) {
        store.reset();
        try {
            for (String directoryName : directoryNames) {
                properties = new HashMap<>();
                scanDirectory(store, directoryName, getScannerPluginRepository(properties).getScannerPlugins());
            }
        } catch (PluginRepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    protected ScannerPluginRepository getScannerPluginRepository(Map<String, Object> properties) {
        try {
            return new ScannerPluginRepositoryImpl(pluginConfigurationReader, properties);
        } catch (PluginRepositoryException e) {
            throw new RuntimeException("Cannot create scanner plugin repository.", e);
        }
    }

    private void scanDirectory(Store store, final String directoryName, final List<ScannerPlugin<?>> scannerPlugins) {
        final File directory = new File(directoryName);
        String absolutePath = directory.getAbsolutePath();
        if (!directory.exists()) {
            getLog().info("Directory '" + absolutePath + "' does not exist, skipping scan.");
        } else {
            store.beginTransaction();
            try {
                final Scanner scanner = new ScannerImpl(store, scannerPlugins);
                scanner.scan(directory, CLASSPATH);
            } finally {
                store.commitTransaction();
            }
        }
    }

    @Override
    public void withOptions(final CommandLine options) {
        if (options.hasOption("d")) {
            for (String dir : options.getOptionValues("d")) {
                if (dir.trim().length() > 0)
                    directoryNames.add(dir);
            }
        }

        if (directoryNames.isEmpty()) {
            throw new MissingConfigurationParameterException("No directories to be scanned given, use 'dirs' argument to specify some");
        }
    }

    @SuppressWarnings("static-access")
    @Override
    protected void addTaskOptions(final List<Option> options) {
        options.add(OptionBuilder.withArgName("d").withLongOpt("dirs").withDescription("directories to be scanned, comma separated").withValueSeparator(',')
                .hasArgs().create("d"));
    }
}
