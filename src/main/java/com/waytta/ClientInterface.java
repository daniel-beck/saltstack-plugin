package com.waytta;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public abstract class ClientInterface implements ExtensionPoint, Describable<ClientInterface> {

    protected ClientInterface() {

    }

    public Descriptor<ClientInterface> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public static class ClientInterfaceDescriptor extends Descriptor<ClientInterface> {

        public ClientInterfaceDescriptor(Class<? extends ClientInterface> clazz) {
            super(clazz);
        }

        @Override
        public String getDisplayName() {
            if (clazz == Local.class)
                return "local";
            if (clazz == LocalBatch.class)
                return "local_batch";
            if (clazz == Runner.class)
                return "runner";
            return "";
        }
    }

    public static class Local extends ClientInterface {

        private boolean blockBuild;
        private int jobPollTime;

        @DataBoundConstructor
        public Local(boolean blockBuild, int jobPollTime) {
            this.blockBuild = blockBuild;
            this.jobPollTime = jobPollTime;
        }

        public boolean isBlockBuild() {
            return blockBuild;
        }

        public int getJobPollTime() {
            return jobPollTime;
        }

        @Extension
        public static final ClientInterfaceDescriptor DESCRIPTOR = new ClientInterfaceDescriptor(Local.class);
    }

    public static class LocalBatch extends ClientInterface {

        private String batchSize;

        @DataBoundConstructor
        public LocalBatch(String batchSize) {
            this.batchSize = batchSize;
        }

        public String getBatchSize() {
            return batchSize;
        }

        @Extension
        public static final ClientInterfaceDescriptor DESCRIPTOR = new ClientInterfaceDescriptor(LocalBatch.class);
    }

    public static class Runner extends ClientInterface {
        @DataBoundConstructor
        public Runner(String mods, boolean usePillar, String pillarKey, String pillarValue) {

        }

        @Extension
        public static final ClientInterfaceDescriptor DESCRIPTOR = new ClientInterfaceDescriptor(Runner.class);
    }
}
