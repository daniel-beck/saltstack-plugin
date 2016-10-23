package com.waytta.clientinterface;

public class LocalClient extends BasicClient {

    public LocalClient(String clientInterface, String credentialsId, String target, String targettype, String function, Boolean blockbuild, Integer jobPollTime) {
        super(clientInterface, credentialsId, target, targettype, function);

        setBlockBuild(blockbuild);
        setJobPollTime(jobPollTime);
        setTarget(target);
        setTargetType(targettype);
    }
}
