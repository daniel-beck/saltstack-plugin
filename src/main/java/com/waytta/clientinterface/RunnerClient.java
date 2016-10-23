package com.waytta.clientinterface;

public class RunnerClient extends BasicClient {

    public RunnerClient(String clientInterface, String credentialsId, String function, String mods, String pillarValue){
        super(clientInterface, credentialsId, "", "", function);

        setMods(mods);
        setTarget("");
        setTargetType("");
        setPillarValue(pillarValue);
    }
}
