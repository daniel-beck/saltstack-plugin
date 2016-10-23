package com.waytta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.yaml.snakeyaml.Yaml;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.util.JSONUtils;

import javax.annotation.Nonnull;

public class SaltAPIBuilder extends Builder implements SimpleBuildStep {
    private static final Logger LOGGER = Logger.getLogger("com.waytta.saltstack");

    private final String servername;
    private final String authtype;
    private final String target;
    private final String targettype;
    private final String function;
    private String arguments;
    private String kwarguments;
    private final String clientInterfaceString;
    private ClientInterface clientInterface;
    private final Boolean blockbuild;
    private final Integer jobPollTime;
    private final String batchSize;
    private final String mods;
    private final Boolean usePillar;
    private final String pillarkey;
    private final String pillarvalue;

    private String credentialsId;
    private Boolean saveEnvVar;

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public SaltAPIBuilder(String servername, String authtype, String target, String targettype, String function,
            ClientInterface clientInterface, String credentialsId) {
        this.credentialsId = credentialsId;
        this.servername = servername;
        this.authtype = authtype;
        this.target = target;
        this.targettype = targettype;
        this.function = function;
        this.clientInterfaceString = clientInterface.getDescriptor().getDisplayName();
        this.clientInterface = clientInterface;
        if (clientInterface instanceof ClientInterface.Local) {
            this.blockbuild = ((ClientInterface.Local) clientInterface).isBlockBuild();
            this.jobPollTime = ((ClientInterface.Local) clientInterface).getJobPollTime();
            this.batchSize = "100%";
            this.mods = "";
            this.usePillar = false;
            this.pillarkey = "";
            this.pillarvalue = "";
        } else if (clientInterface instanceof ClientInterface.LocalBatch) {
            this.batchSize = ((ClientInterface.LocalBatch) clientInterface).getBatchSize();
            this.blockbuild = false;
            this.jobPollTime = 10;
            this.mods = "";
            this.usePillar = false;
            this.pillarkey = "";
            this.pillarvalue = "";
        } else if (clientInterface instanceof ClientInterface.Runner) {
            // TODO restore setting values
            this.mods = "";
            this.usePillar = true;
            this.pillarkey = "";
            this.pillarvalue = "";
            this.blockbuild = false;
            this.jobPollTime = 10;
            this.batchSize = "100%";
        } else {
            this.batchSize = "100%";
            this.blockbuild = false;
            this.jobPollTime = 10;
            this.mods = "";
            this.usePillar = false;
            this.pillarkey = "";
            this.pillarvalue = "";
        }
    }

    /*
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getServername() {
        return servername;
    }

    public String getAuthtype() {
        return authtype;
    }

    public String getTarget() {
        return target;
    }

    public String getTargettype() {
        return this.targettype;
    }

    public String getFunction() {
        return function;
    }

    public String getArguments() {
        return arguments;
    }

    @DataBoundSetter
    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getKwarguments() {
        return kwarguments;
    }

    @DataBoundSetter
    public void setKwarguments(String kwarguments) {
        this.kwarguments = kwarguments;
    }

    public ClientInterface getClientInterface() {
        return clientInterface;
    }

    public String getClientInterfaceString() { return clientInterfaceString; }

    public Boolean getBlockbuild() {
        return blockbuild;
    }

    public String getBatchSize() {
        return batchSize;
    }

    public Integer getJobPollTime() {
        return jobPollTime;
    }

    public String getMods() {
        return mods;
    }

    public Boolean getUsePillar() {
        return usePillar;
    }

    public String getPillarkey() {
        return pillarkey;
    }

    public String getPillarvalue() {
        return pillarvalue;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setSaveEnvVar(Boolean saveEnvVar) {
        this.saveEnvVar = saveEnvVar;
    }

    public Boolean getSaveEnvVar() {
        return saveEnvVar;
    }


    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {

        taskListener.getLogger().println(this.getJobPollTime());
    }

    private JSONArray createAuthArray(StandardUsernamePasswordCredentials credential) {
        JSONArray authArray = new JSONArray();
        JSONObject auth = new JSONObject();
        auth.put("username", credential.getUsername());
        auth.put("password", credential.getPassword().getPlainText());
        auth.put("eauth", authtype);
        authArray.add(auth);

        return authArray;
    }

    private JSONObject prepareSaltFunction(AbstractBuild build, BuildListener listener, String myClientInterface,
            String mytarget, String myfunction, String myarguments, String mykwarguments) {
        JSONObject saltFunc = new JSONObject();
        saltFunc.put("client", myClientInterface);
        if (myClientInterface.equals("local_batch")) {
            saltFunc.put("batch", batchSize);
            listener.getLogger().println("Running in batch mode. Batch size: " + batchSize);
        }
        if (myClientInterface.equals("runner")) {
            saltFunc.put("mods", mods);

            if (usePillar) {
                String myPillarkey = Utils.paramorize(build, listener, pillarkey);
                String myPillarvalue = Utils.paramorize(build, listener, pillarvalue);

                JSONObject jPillar = new JSONObject();
                try {
                    // If value was already a jsonobject, treat it as such
                    JSON runPillarValue = JSONSerializer.toJSON(myPillarvalue);
                    jPillar.put(myPillarkey, runPillarValue);
                } catch (JSONException e) {
                    // Otherwise it must have been a string
                    jPillar.put(JSONUtils.stripQuotes(myPillarkey), JSONUtils.stripQuotes(myPillarvalue));
                }

                saltFunc.put("pillar", jPillar);
            }
        }
        saltFunc.put("tgt", mytarget);
        saltFunc.put("expr_form", targettype);
        saltFunc.put("fun", myfunction);
        addArgumentsToSaltFunction(myarguments, saltFunc);
        addKwArgumentsToSaltFunction(mykwarguments, saltFunc);

        return saltFunc;
    }

    private void addKwArgumentsToSaltFunction(String mykwarguments, JSONObject saltFunc) {
        if (mykwarguments.length() > 0) {
            Map<String, String> kwArgs = new HashMap<String, String>();
            // spit on comma seperated not inside of single and double quotes
            String[] kwargItems = mykwarguments.split(",(?=(?:[^'\"]|'[^']*'|\"[^\"]*\")*$)");
            for (String kwarg : kwargItems) {
                // remove spaces at begining or end
                kwarg = kwarg.replaceAll("^\\s+|\\s+$", "");
                kwarg = kwarg.replaceAll("\"|\\\"", "");
                if (kwarg.contains("=")) {
                    String[] kwString = kwarg.split("=");
                    if (kwString.length > 2) {
                        // kwarg contained more than one =. Let's put the string
                        // back together
                        String kwFull = new String();
                        for (String kwItem : kwString) {
                            // Ignore the first item as it will remain the key
                            if (kwItem == kwString[0]) {
                                continue;
                            }
                            // add the second item
                            if (kwItem == kwString[1]) {
                                kwFull += kwItem;
                                continue;
                            }
                            // add all other items with an = to rejoin
                            kwFull += "=" + kwItem;
                        }
                        kwArgs.put(kwString[0], kwFull);
                    } else {
                        kwArgs.put(kwString[0], kwString[1]);
                    }
                }
            }
            // Add any kwargs to json message
            saltFunc.element("kwarg", kwArgs);
        }
    }

    StandardUsernamePasswordCredentials getCredentialById(String credentialId) {
        List<StandardUsernamePasswordCredentials> credentials = getCredentials(Jenkins.getInstance());
        for (StandardUsernamePasswordCredentials credential : credentials) {
            if (credential.getId().equals(credentialId)) {
                return credential;
            }
        }
        return null;
    }

    static List<StandardUsernamePasswordCredentials> getCredentials(Jenkins context) {
        List<DomainRequirement> requirements = URIRequirementBuilder.create().build();
        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider
                .lookupCredentials(StandardUsernamePasswordCredentials.class, context, ACL.SYSTEM, requirements);
        return credentials;
    }

    private void addArgumentsToSaltFunction(String myarguments, JSONObject saltFunc) {
        if (myarguments.length() > 0) {
            List<String> saltArguments = new ArrayList<String>();
            // spit on comma seperated not inside of single and double quotes
            String[] argItems = myarguments.split(",(?=(?:[^'\"]|'[^']*'|\"[^\"]*\")*$)");

            for (String arg : argItems) {
                // remove spaces at begining or end
                arg = arg.replaceAll("^\\s+|\\s+$", "");
                // if string wrapped in quotes, remove them since adding to list
                // re-quotes
                arg = arg.replaceAll("(^')|(^\")|('$)|(\"$)", "");
                saltArguments.add(arg);
            }

            // Add any args to json message
            saltFunc.element("arg", saltArguments);
        }
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension // This indicates to Jenkins that this is an implementation of an
               // extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private int pollTime = 10;
        private String outputFormat = "json";

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            try {
                // Test that value entered in config is an integer
                pollTime = formData.getInt("pollTime");
            } catch (Exception e) {
                // Fall back to default
                pollTime = 10;
            }
            outputFormat = formData.getString("outputFormat");
            save();
            return super.configure(req, formData);
        }

        public int getPollTime() {
            return pollTime;
        }

        public String getOutputFormat() {
            return outputFormat;
        }

        public FormValidation doTestConnection(@QueryParameter String servername, @QueryParameter String credentialsId,
                @QueryParameter String authtype) {
            StandardUsernamePasswordCredentials usedCredential = null;
            List<StandardUsernamePasswordCredentials> credentials = getCredentials(Jenkins.getInstance());
            for (StandardUsernamePasswordCredentials credential : credentials) {
                if (credential.getId().equals(credentialsId)) {
                    usedCredential = credential;
                    break;
                }
            }

            if (usedCredential == null) {
                return FormValidation.error("CredentialId error: no credential found with given ID.");
            }

            if (!servername.matches("\\{\\{\\w+\\}\\}")) {
                JSONArray authArray = new JSONArray();
                JSONObject auth = new JSONObject();
                auth.put("username", usedCredential.getUsername());
                auth.put("password", usedCredential.getPassword().getPlainText());
                auth.put("eauth", authtype);
                authArray.add(auth);
                String token = Utils.getToken(servername, authArray);
                if (token.contains("Error")) {
                    return FormValidation.error("Client error: " + token);
                }

                return FormValidation.ok("Success");
            }

            return FormValidation.warning("Cannot expand parametrized server name.");
        }

        public StandardListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context,
                @QueryParameter final String servername) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            result.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                    getCredentials(context));
            return result;
        }

        public FormValidation doCheckServername(@QueryParameter String value) {
            if (!value.matches("\\{\\{\\w+\\}\\}")) {
                if (value.length() == 0) {
                    return FormValidation.error("Please specify a name");
                }
                if (value.length() < 10) {
                    return FormValidation.warning("Isn't the name too short?");
                }
                if (!value.contains("https://") && !value.contains("http://")) {
                    return FormValidation
                            .warning("Missing protocol: Servername should be in the format https://host.domain:8000");
                }
                if (!value.substring(7).contains(":")) {
                    return FormValidation
                            .warning("Missing port: Servername should be in the format https://host.domain:8000");
                }
                return FormValidation.ok();
            }

            return FormValidation.warning("Cannot expand parametrized server name.");
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Item project, @QueryParameter String value) {
            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }

            if (value == null) {
                return FormValidation.ok();
            }

            StandardUsernamePasswordCredentials usedCredential = null;
            List<StandardUsernamePasswordCredentials> credentials = getCredentials(Jenkins.getInstance());
            for (StandardUsernamePasswordCredentials credential : credentials) {
                if (credential.getId().equals(value)) {
                    usedCredential = credential;
                }
            }

            if (usedCredential == null) {
                return FormValidation.error("Cannot find any credentials with id " + value);
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckTarget(@QueryParameter String value) {
            return validateFormStringField(value, "Please specify a salt target", "Isn't it too short?");
        }

        public FormValidation doCheckFunction(@QueryParameter String value) {
            return validateFormStringField(value, "Please specify a salt function", "Isn't it too short?");
        }

        private FormValidation validateFormStringField(String value, String lackOfFieldMessage,
                String fieldToShortMessage) {
            if (value.length() == 0) {
                return FormValidation.error(lackOfFieldMessage);
            }

            if (value.length() < 3) {
                return FormValidation.warning(fieldToShortMessage);
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckPollTime(@QueryParameter String value) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error("Specify a number larger than 3");
            }
            if (Integer.parseInt(value) < 3) {
                return FormValidation.warning("Specify a number larger than 3");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBatchSize(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("Please specify batch size");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Send a message to Salt API";
        }
    }
}
