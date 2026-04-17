package io.jenkins.plugins.gsd;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;

/**
 * Placeholder global configuration; extend with AI/git defaults as the plugin grows.
 */
@Extension
public final class GsdGlobalConfiguration extends GlobalConfiguration {

    public GsdGlobalConfiguration() {
        load();
    }
}
