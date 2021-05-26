package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;

import java.io.File;

/**
 * Method generating figures in Colomb 2021 et al.
 */
public class Figure {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        PMStep.setGENERATEATTRIBUTES(false);
        PMScenario.setReuseSimulatedParcels(false);
        PMScenario pm = new PMScenario(new File("src/main/resources/Figure/scenario.json"));
        pm.executeStep();
        PMScenario pmSS = new PMScenario(new File("src/main/resources/Figure/scenarioSS.json"));
        pmSS.executeStep();
        System.out.println(System.currentTimeMillis() - start);
    }
}