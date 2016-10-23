package com.waytta.clientinterface;

import hudson.Extension;
import hudson.model.Descriptor;
import com.waytta.SaltAPIBuilder;


public class LocalBatchClient extends BasicClient {
	//AbstractDescribableImpl<LocalBatchClient>

    public LocalBatchClient(String clientInterface, String credentialsId, String target, String targettype, String function, String batchSize) {
        super(clientInterface, credentialsId, target, targettype, function);

        setBatchSize(batchSize);
        setTarget(target);
        setTargetType(targettype);
    }
    
    
    	  @Extension
    	  public static class DescriptorImpl extends Descriptor<SaltAPIBuilder> {
    	    public String getDisplayName() { return "LocalBatchClient"; }
    	  }
    //@Extension public static final ClassDescriptor D = new DescriptorImpl();
}
