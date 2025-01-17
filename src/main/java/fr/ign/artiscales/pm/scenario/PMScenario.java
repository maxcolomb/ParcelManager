package fr.ign.artiscales.pm.scenario;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import fr.ign.artiscales.pm.workflow.Workflow;
import org.apache.commons.math3.random.MersenneTwister;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Object representing a Parcel Manager scenario. Will set files and launch a list of predefined {@link fr.ign.artiscales.pm.scenario.PMStep}.
 *
 * @author Maxime Colomb
 * @see <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">scenarioCreation.md</a>
 */
public class PMScenario {
    /**
     * If true, the parcels simulated for each steps will be the input of the next step. If false, the simulation will operate on the input parcel for each steps
     */
    private static boolean REUSESIMULATEDPARCELS = true;
    /**
     * If true, save a geopackage containing only the simulated parcels in the temporary folder for every workflow simulated.
     */
    private static boolean SAVEINTERMEDIATERESULT = false;
    /**
     * If true, will save all the intermediate results in the temporary folder
     */
    private static boolean DEBUG = false;
    private static MersenneTwister random = new MersenneTwister();
    boolean keepExistingRoad = true, adaptAreaOfUrbanFabric = false, generatePeripheralRoad = false;
    private File zoningFile, buildingFile, roadFile, polygonIntersection, zone, predicateFile, parcelFile, profileFolder, outFolder;
    private List<PMStep> stepList = new ArrayList<>();
    private boolean fileSet = false;

    /**
     * Create new Scenario
     *
     * @param jSON json file containing every scenario's parameter and list of steps
     * @throws IOException tons of geo files reading.
     */
    public PMScenario(File jSON) throws IOException {
        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(jSON);
        JsonToken token = parser.nextToken();
        while (!parser.isClosed()) {
            token = parser.nextToken();
//			shortcut if every data is in the same folder
            if (token == JsonToken.FIELD_NAME && "rootfile".equals(parser.getCurrentName()) && !fileSet) {
                fileSet = true;
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    String rootFolder = parser.getText();
                    zoningFile = new File(rootFolder, "zoning.gpkg");
                    buildingFile = new File(rootFolder, "building.gpkg");
                    roadFile = new File(rootFolder, "road.gpkg");
                    polygonIntersection = new File(rootFolder, "polygonIntersection.gpkg");
                    zone = new File(rootFolder, "zone.gpkg");
                    predicateFile = new File(rootFolder, "predicate.csv");
                    parcelFile = new File(rootFolder, "parcel.gpkg");
                    profileFolder = new File(rootFolder, "profileUrbanFabric");
                }
            }
            //if the line is an array, it describes a PMStep
            if (token == JsonToken.FIELD_NAME && "steps".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                String workflow = "";
                String parcelProcess = "";
                String genericZone = "";
                String preciseZone = "";
                String communityNumber = "";
                String communityType = "";
                String urbanFabric = "";
                String selection = "";
                while (token != JsonToken.END_ARRAY) {
                    token = parser.nextToken();
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("workflow")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            workflow = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("parcelProcess")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            parcelProcess = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("genericZone")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            genericZone = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("preciseZone")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            preciseZone = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("communityNumber")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            communityNumber = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("communityType")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            communityType = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("urbanFabricType")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            urbanFabric = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("selection")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            selection = parser.getText();
                    }
                    //specific options concerning workflows can be parsed here
                    if (token == JsonToken.FIELD_NAME && "optional".equals(parser.getCurrentName())) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING) {
                            switch (parser.getText()) {
                                case "keepExistingRoad:true":
                                case "keepExistingRoad":
                                    keepExistingRoad = true;
                                    break;
                                case "keepExistingRoad:false":
                                    keepExistingRoad = false;
                                    break;
                                case "adaptAreaOfUrbanFabric:true":
                                case "adaptAreaOfUrbanFabric":
                                    adaptAreaOfUrbanFabric = true;
                                    break;
                                case "peripheralRoad:true":
                                    generatePeripheralRoad = true;
                                    break;
                                case "peripheralRoad:false":
                                    generatePeripheralRoad = false;
                                    break;
                            }
                        }
                    }
                    if (token == JsonToken.END_OBJECT) {
                        List<PMStep> list = getStepList();
                        PMStep step = new PMStep(workflow, parcelProcess, genericZone, preciseZone, communityNumber, communityType, urbanFabric, selection, generatePeripheralRoad, keepExistingRoad, adaptAreaOfUrbanFabric);
                        list.add(step);
                        setStepList(list);
                        workflow = parcelProcess = genericZone = preciseZone = communityNumber = communityType = urbanFabric = selection = "";
                    }
                }
            }

            if (token == JsonToken.FIELD_NAME && "zoningFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    zoningFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "roadFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    roadFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "buildingFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    buildingFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "polygonIntersectionFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    polygonIntersection = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "zoneFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    zone = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "predicateFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    predicateFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "parcelFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    parcelFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "profileFolder".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    profileFolder = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "outFolder".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    outFolder = new File(parser.getText());
                    fileSet = true;
                }
            }
        }
        parser.close();
        PMStep.setFiles(parcelFile, zoningFile, buildingFile, roadFile, predicateFile, polygonIntersection, zone, outFolder,
                profileFolder);
    }

    public static MersenneTwister getRandom() {
        return random;
    }

//    public static void main(String[] args) throws Exception {
//        PMScenario.setDEBUG(false);
//        PMScenario.setSaveIntermediateResult(false);
//        PMScenario pm = new PMScenario(new File("src/main/resources/TestScenario/scenarioOBB.json"));
//        pm.executeStep();
//        PMScenario pm2 = new PMScenario(new File("src/main/resources/TestScenario/scenarioStraightSkeleton.json"));
//        pm2.executeStep();
//        PMScenario pm3 = new PMScenario(new File("src/main/resources/TestScenario/scenarioStraightSkeletonPeripheralRoad.json"));
//        pm3.executeStep();
//        PMScenario pm4 = new PMScenario(new File("src/main/resources/TestScenario/scenarioOffset.json"));
//        pm4.executeStep();
//    }

    /**
     * Are we exporting the intermediate results ? Will also set this statut to {@link Workflow} and {@link fr.ign.artiscales.pm.division.Division}.
     *
     * @return are we exporting the intermediate results ?
     */
    public static boolean isSaveIntermediateResult() {
        return SAVEINTERMEDIATERESULT;
    }

    /**
     * Set if we save every intermediate results of scenarios, workflows and division processes. Will also set this status to {@link Workflow} and {@link fr.ign.artiscales.pm.division.Division}.
     *
     * @param saveIntermediateResult if true, export every intermediate results into a temporary folder.
     */
    public static void setSaveIntermediateResult(boolean saveIntermediateResult) {
        Workflow.setSAVEINTERMEDIATERESULT(saveIntermediateResult);
        SAVEINTERMEDIATERESULT = saveIntermediateResult;
    }

    /**
     * If true, will save all the intermediate results in the temporary folder
     *
     * @return DEBUG
     */
    public static boolean isDEBUG() {
        return DEBUG;
    }

    /**
     * If true, will save all the intermediate results in the temporary folder
     *
     * @param dEBUG true for debug mode
     */
    public static void setDEBUG(boolean dEBUG) {
        Workflow.setDEBUG(dEBUG);
        DEBUG = dEBUG;
    }

    /**
     * Sometimes, we want to apply multiple processes on the same input parcels, i.e. in order to check which division process is the best or which urbanFabric parameters.
     *
     * @param reuseSimulatedParcel If true, the parcels simulated for each steps will be the input of the next step. If false, the simulation will operate on the input parcel for each steps
     */
    public static void setReuseSimulatedParcels(boolean reuseSimulatedParcel) {
        REUSESIMULATEDPARCELS = reuseSimulatedParcel;
    }

    public static void setSeed(long seed) {
        random = new MersenneTwister(seed);
        Workflow.setSeed(seed);
    }

    /**
     * Run every step that are present in the stepList
     *
     * @throws IOException tons of reading and writing
     */
    public void executeStep() throws IOException {
        for (PMStep pmstep : getStepList()) {
            System.out.println("try " + pmstep);
            if (isDEBUG())
                System.out.println(this);
            if (REUSESIMULATEDPARCELS)
                PMStep.setParcel(pmstep.execute());
            else
                pmstep.execute();
        }
    }

    /**
     * Get the scenario's list of steps
     *
     * @return current ordered list of step
     */
    public List<PMStep> getStepList() {
        return stepList;
    }

    /**
     * Define a new list of steps for the scenario
     *
     * @param stepList new ordered list of steps
     */
    public void setStepList(List<PMStep> stepList) {
        this.stepList = stepList;
    }

    @Override
    public String toString() {
        return "PMScenario [zoningFile=" + zoningFile + ", buildingFile=" + buildingFile + ", roadFile=" + roadFile + ", polygonIntersection="
                + polygonIntersection + ", zone=" + zone + ", predicateFile=" + predicateFile + ", parcelFile=" + parcelFile + ", outFolder=" + outFolder + ", stepList=" + stepList + ", fileSet=" + fileSet + ", profileFolder=" + profileFolder + "]";
    }
}
