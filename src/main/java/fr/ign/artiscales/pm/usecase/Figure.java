package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;

import java.io.File;

/**
 * Method generating figures in Colomb 2021 et al.
 */
public class Figure {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        // Generate parcel reshaping exemplifying the road network generation in Section 3 of Colomb et al. 2021.
        PMScenario.setReuseSimulatedParcels(false);
        PMScenario pm = new PMScenario(new File("src/main/resources/Figure/scenarioRoadCreation.json"));
        pm.executeStep();

        // Generate parcel reshaping for the Straight Skeleton example in Section 3 of Colomb et al. 2021.
        PMScenario pmSSfig = new PMScenario(new File("src/main/resources/Figure/scenarioFigureSS.json"));
        UseCase.setSAVEINTERMEDIATERESULT(true);
        UseCase.setDEBUG(true);
        File f = PMStep.getOUTFOLDER();
        for (PMStep pmstep : pmSSfig.getStepList()) {
            System.out.println("try " + pmstep);
            // set new out folder to avoid overwriting
            PMStep.setOUTFOLDER(new File(f, (pmstep.isPeripheralRoad() ? "peripheralRoad" : "noPeripheralRoad") + "_" + (ProfileUrbanFabric.convertJSONtoProfile(new File(PMStep.getPROFILEFOLDER() + "/" + pmstep.getUrbanFabricType() + ".json")).getMaxDepth() != 0 ? "offset" : "noOffset")));
            pmstep.execute();
        }
        System.out.println("Figure took " + (System.currentTimeMillis() - start) + " milliseconds");
    }
}
