package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.division.FlagDivision;
import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Simulation following that workflow divides parcels to ensure that they could be densified. The
 * {@link FlagDivision#doFlagDivision(SimpleFeature, SimpleFeatureCollection, SimpleFeatureCollection, double, double, double, double, double, List, Geometry)} method is applied on the selected
 * parcels. If the creation of a flag parcel is impossible and the local rules allows parcel to be disconnected from the road network, the
 * {@link OBBDivision#splitParcels(SimpleFeature, double, double, double, double, List, double, boolean, int)} is applied. Other behavior can be set relatively to the
 * parcel's sizes.
 *
 * @author Maxime Colomb
 */
public class Densification extends Workflow {

    static double uncountedBuildingArea = 20;

    public Densification() {
    }
//
//    public static void main(String[] args) throws Exception {
//        long start = System.currentTimeMillis();
//        File rootFolder = new File("src/main/resources/TestScenario/");
//        File parcelFile = new File("/tmp/parcels.gpkg");
//        File buildingFile = new File(rootFolder, "InputData/building.gpkg");
//        File roadFile = new File(rootFolder, "InputData/road.gpkg");
//        File outFolder = new File("/tmp/densification");
//        outFolder.mkdirs();
//        ParcelSchema.setParcelCommunityField("CODE_COM");
//        DataStore pDS = CollecMgmt.getDataStore(parcelFile);
//        SimpleFeatureCollection parcels = MarkParcelAttributeFromPosition.markParcelsSup(pDS.getFeatureSource(pDS.getTypeNames()[0]).getFeatures(), 3000);
//        CollecMgmt.exportSFC((new Densification()).densification(parcels, CityGeneration.createUrbanBlock(parcels), outFolder, buildingFile, roadFile,
//                        ProfileUrbanFabric.convertJSONtoProfile(new File("src/main/resources/TestScenario/profileUrbanFabric/smallHouse.json")), false),
//                new File(outFolder, "result"));
//        System.out.println(System.currentTimeMillis() - start);
//    }

//    public static void main(String[] args) throws IOException {
//        File root = new File("/home/mc/workspace/parcelmanager/src/main/resources/TestScenario/");
//        File ini = new File(root, "InputData/parcel.gpkg");
//        DataStore ds = CollecMgmt.getDataStore(ini);
//        Workflow.setDEBUG(true);
//        SimpleFeatureCollection parcel = MarkParcelAttributeFromPosition.markRandomParcels(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), 50, false);
//        CollecMgmt.exportSFC(parcel, new File("/tmp/ini.gpkg"));
//        new Densification().densificationOrNeighborhood(parcel, CityGeneration.createUrbanBlock(parcel),
//                new File("/tmp/"), new File(root, "InputData/building.gpkg"), new File(root, "InputData/road.gpkg"),
//                ProfileUrbanFabric.convertJSONtoProfile(new File(root, "profileUrbanFabric/smallHouse.json")), false, null, 3);
//
//    }

    //    public static void main(String[] args) throws IOException {
//        File ReMarked = new File("/home/mc/workspace/parcelmanager/src/main/resources/ParcelShapeComparison/OutputResults/densificationOrNeighborhood-ReMarked.gpkg");
//        DataStore ds = CollecMgmt.getDataStore(ReMarked);
//        File base = new File("/home/mc/workspace/parcelmanager/src/main/resources/ParcelShapeComparison/OutputResults/parcelCuted-consolidationDivision-smallHouse-NC_.gpkg");
//        DataStore dsBase = CollecMgmt.getDataStore(base);
//        DefaultFeatureCollection dfc = new DefaultFeatureCollection(dsBase.getFeatureSource(dsBase.getTypeNames()[0]).getFeatures());
//        dfc.addAll(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures());
//        System.out.println("wicked " + ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures().getSchema());
//        System.out.println("normal " + dsBase.getFeatureSource(dsBase.getTypeNames()[0]).getFeatures().getSchema());
//        CollecMgmt.exportSFC(dfc, new File("/tmp/fefe"));
//    }

    /**
     * Apply the densification workflow on a set of marked parcels.
     * TODO improvements: if a densification is impossible (mainly for building constructed on the both cut parcel reason), reiterate the flag cut division with irregularityCoeff. The cut may work better !
     *
     * @param parcelCollection    {@link SimpleFeatureCollection} of marked parcels.
     * @param blockCollection     {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                            {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder           Folder to store result files
     * @param buildingFile        Geopackage representing the buildings
     * @param roadFile            Geopackage representing the roads. If road not needed, use the overloaded method.
     * @param maximalArea         threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalArea         threshold under which the parcels is not kept. If parcel simulated is under this workflow will keep the unsimulated parcel.
     * @param minContactWithRoad  threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
     * @param lenDriveway         lenght of the driveway to connect a parcel through another parcel to the road
     * @param allowIsolatedParcel true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @param exclusionZone       Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @return The input {@link SimpleFeatureCollection} with each marked parcel replaced by simulated parcels.
     * @throws IOException Reading and writing geo files
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, double harmonyCoeff, double irregularityCoeff, double maximalArea, double minimalArea,
                                                 double minContactWithRoad, double lenDriveway, boolean allowIsolatedParcel, Geometry exclusionZone) throws IOException {
        // if parcels doesn't contains the markParcelAttribute field or have no marked parcels
        if (MarkParcelAttributeFromPosition.isNoParcelMarked(parcelCollection)) {
            System.out.println("Densification : unmarked parcels");
            return GeneralFields.transformSFCToMinParcel(parcelCollection);
        }
        checkFields(parcelCollection.getSchema());
        // preparation of the builder and empty collections
        final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        DefaultFeatureCollection onlyCutedParcels = new DefaultFeatureCollection();
        DefaultFeatureCollection resultParcels = new DefaultFeatureCollection();
        SimpleFeatureBuilder sFBParcel = ParcelSchema.getSFBWithoutSplit(parcelCollection.getSchema());
        DataStore roadDS = null;
        SimpleFeatureCollection road = null;
        if (roadFile != null) {
            roadDS = CollecMgmt.getDataStore(roadFile);
            road = roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures();
        }
        DataStore buildingDS = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection building = buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures();
        try (SimpleFeatureIterator iterator = parcelCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature initialParcel = iterator.next();
                // if the parcel is selected for the simulation and bigger than the limit size
                if (initialParcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
                        && initialParcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)
                        && ((Geometry) initialParcel.getDefaultGeometry()).getArea() > maximalArea) {
                    List<LineString> lines = CollecTransform.fromPolygonSFCtoListRingLines(blockCollection.subCollection(ff.bbox(ff.property(initialParcel.getFeatureType().getGeometryDescriptor().getLocalName()), initialParcel.getBounds())));
                    // we get the needed block lines
                    // we flag cut the parcel (differently regarding whether they have optional data or not)
                    SimpleFeatureCollection unsortedFlagParcel = FlagDivision.doFlagDivision(initialParcel, road, building, harmonyCoeff, irregularityCoeff, maximalArea, minContactWithRoad, lenDriveway, lines, exclusionZone);
                    // we check if the cut parcels are meeting the expectations
                    boolean add = true;
                    // If it returned a collection of 1, it was impossible to flag split the parcel. If allowed, we cut the parcel with regular OBB
                    if (unsortedFlagParcel.size() == 1)
                        if (allowIsolatedParcel)
                            unsortedFlagParcel = OBBDivision.splitParcels(initialParcel, maximalArea, minContactWithRoad, 0.5, irregularityCoeff, lines, 0, true, 99);
                        else
                            add = false;
                    // If the flag cut parcel size is too small, we won't add anything
                    try (SimpleFeatureIterator parcelIt = unsortedFlagParcel.features()) {
                        while (parcelIt.hasNext())
                            if (((Geometry) parcelIt.next().getDefaultGeometry()).getArea() < minimalArea) {
                                add = false;
                                break;
                            }
                    } catch (Exception problem) {
                        System.out.println("problem" + problem + "for " + initialParcel + " feature densification");
                        problem.printStackTrace();
                    }
                    if (add) { // We check existing buildings are constructed across two cut parcels. If true, me merge those parcels together
                        DefaultFeatureCollection toMerge = new DefaultFeatureCollection();
                        try (SimpleFeatureIterator parcelIt = unsortedFlagParcel.features()) {
                            while (parcelIt.hasNext()) {
                                SimpleFeature parcel = parcelIt.next();
                                if (ParcelState.isAlreadyBuilt(CollecTransform.selectIntersection(building, parcel), parcel, -1, uncountedBuildingArea)) // parcel is built, we try to merge
                                    toMerge.add(parcel);
                            }
                        } catch (Exception problem) {
                            problem.printStackTrace();
                        }
                        // if buildings are present on every cut parts of the parcel, we cancel densification
                        if (toMerge.size() == unsortedFlagParcel.size())
                            add = false;
                        else if (toMerge.size() > 1) { // merge the parcel that are built upon a building (we assume that it must be the same building)
                            // tmp save the collection of output parcels
                            DefaultFeatureCollection tmpUnsortedFlagParcel = new DefaultFeatureCollection(unsortedFlagParcel);
                            unsortedFlagParcel = new DefaultFeatureCollection();
                            // we add the merged parcels
                            SimpleFeatureBuilder builder = Schemas.getSFBSchemaWithMultiPolygon(toMerge.getSchema());
                            builder.set(toMerge.getSchema().getGeometryDescriptor().getLocalName(), Geom.safeUnion(toMerge).buffer(0.1).buffer(-0.1));
                            ((DefaultFeatureCollection) unsortedFlagParcel).add(builder.buildFeature(Attribute.makeUniqueId()));
                            // we check if the flag cut parcels have been merged or if they need to be put on the new collection
                            try (SimpleFeatureIterator parcelIt = tmpUnsortedFlagParcel.features()) {
                                while (parcelIt.hasNext()) {
                                    SimpleFeature parcel = parcelIt.next();
                                    boolean complete = true;
                                    // we now iterate on the parcel that we have merged
                                    try (SimpleFeatureIterator parcelIt2 = toMerge.features()) {
                                        while (parcelIt2.hasNext())
                                            // if the initial parcel has been merged, we don't add it to the new collection
                                            if (parcel.getDefaultGeometry().equals(parcelIt2.next().getDefaultGeometry())) {
                                                complete = false;
                                                break;
                                            }
                                    } catch (Exception problem) {
                                        problem.printStackTrace();
                                    }
                                    // if at least one parcel is unbuilt, then the decomposition is not in vain
                                    if (complete) {
                                        Schemas.setFieldsToSFB(builder, parcel);
                                        ((DefaultFeatureCollection) unsortedFlagParcel).add(builder.buildFeature(Attribute.makeUniqueId()));
                                    }
                                }
                            } catch (Exception problem) {
                                problem.printStackTrace();
                            }
                        }
                    }
                    if (add) { // if we are okay to add parts : we construct the new parcels
                        int i = 1;
                        try (SimpleFeatureIterator parcelCutedIt = unsortedFlagParcel.features()) {
                            while (parcelCutedIt.hasNext()) {
                                Geometry pGeom = (Geometry) parcelCutedIt.next().getDefaultGeometry();
                                for (int ii = 0; ii < pGeom.getNumGeometries(); ii++) {
                                    Schemas.setFieldsToSFB(sFBParcel, initialParcel);
                                    sFBParcel.set(geomName, pGeom.getGeometryN(ii));
                                    sFBParcel.set(ParcelSchema.getParcelSectionField(), makeNewSection(initialParcel.getAttribute(ParcelSchema.getParcelSectionField()) + "-" + i++));
                                    sFBParcel.set(ParcelSchema.getParcelNumberField(), initialParcel.getAttribute(ParcelSchema.getParcelNumberField() + "-" + i));
                                    sFBParcel.set(ParcelSchema.getParcelCommunityField(), initialParcel.getAttribute(ParcelSchema.getParcelCommunityField()));
                                    SimpleFeature cutedParcel = sFBParcel.buildFeature(Attribute.makeUniqueId());
                                    resultParcels.add(cutedParcel);
                                    if (isSAVEINTERMEDIATERESULT())
                                        onlyCutedParcels.add(cutedParcel);
                                }
                            }
                        } catch (Exception problem) {
                            problem.printStackTrace();
                        }
                    } else {
                        Schemas.setFieldsToSFB(sFBParcel, initialParcel);
                        resultParcels.add(sFBParcel.buildFeature(Attribute.makeUniqueId()));
                    }
                } else {  // if no simulation needed, we add the normal parcel
                    Schemas.setFieldsToSFB(sFBParcel, initialParcel);
                    resultParcels.add(sFBParcel.buildFeature(Attribute.makeUniqueId()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (isSAVEINTERMEDIATERESULT()) {
            CollecMgmt.exportSFC(onlyCutedParcels, new File(outFolder, "parcelDensificationOnly"), OVERWRITEGEOPACKAGE);
            OVERWRITEGEOPACKAGE = false;
        }
        buildingDS.dispose();
        if (roadFile != null)
            roadDS.dispose();
        return resultParcels.collection();
    }

    /**
     * Apply the densification workflow on a set of marked parcels.
     * <p>
     * overload of the {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, double, double, boolean, Geometry)}
     * method if we choose to not use a geometry of exclusion
     *
     * @param parcelCollection        SimpleFeatureCollection of marked parcels.
     * @param blockCollection         SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                                {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder               folder to store created files
     * @param buildingFile            Geopackage representing the buildings
     * @param roadFile                Geopackage representing the roads
     * @param maximalAreaSplitParcel  threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalAreaSplitParcel  threshold under which the parcels is not kept. If parcel simulated is under this workflow will keep the unsimulated parcel.
     * @param minimalWidthContactRoad threshold of parcel contact to road under which the OBB algorithm stops to decompose parcels
     * @param lenDriveway             length of the driveway to connect a parcel through another parcel to the road
     * @param allowIsolatedParcel     true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @return The input {@link SimpleFeatureCollection} with each marked parcel replaced by simulated parcels.
     * @throws IOException Reading and writing geo files
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, double harmonyCoeff, double irregularityCoeff, double maximalAreaSplitParcel, double minimalAreaSplitParcel,
                                                 double minimalWidthContactRoad, double lenDriveway, boolean allowIsolatedParcel) throws IOException {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, roadFile, harmonyCoeff, irregularityCoeff, maximalAreaSplitParcel,
                minimalAreaSplitParcel, minimalWidthContactRoad, lenDriveway, allowIsolatedParcel, null);
    }

    /**
     * Apply the densification workflow on a set of marked parcels.
     * Overload of the {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, double, double, boolean, Geometry)}
     * method if we choose to not use roads
     *
     * @param parcelCollection        SimpleFeatureCollection of marked parcels.
     * @param blockCollection         SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                                {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder               folder to store created files
     * @param buildingFile            Geopackage representing the buildings
     * @param maximalAreaSplitParcel  threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalAreaSplitParcel  threshold under which the parcels is not kept. If parcel simulated is under this workflow will keep the unsimulated parcel.
     * @param minimalWidthContactRoad threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
     * @param lenDriveway             lenght of the driveway to connect a parcel through another parcel to the road
     * @param allowIsolatedParcel     true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @return The input {@link SimpleFeatureCollection} with each marked parcel replaced by simulated parcels.
     * @throws IOException Reading and writing geo files
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, double harmonyCoeff, double irregularityCoeff, double maximalAreaSplitParcel, double minimalAreaSplitParcel,
                                                 double minimalWidthContactRoad, double lenDriveway, boolean allowIsolatedParcel) throws IOException {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, null, harmonyCoeff, irregularityCoeff, maximalAreaSplitParcel,
                minimalAreaSplitParcel, minimalWidthContactRoad, lenDriveway, allowIsolatedParcel);
    }

    /**
     * Apply the densification workflow on a set of marked parcels. Overload
     * {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, ProfileUrbanFabric, boolean, Geometry)}
     * method with a profile urban fabric (which automatically report its parameters to the fields) and no exclusion geometry.
     *
     * @param parcelCollection    SimpleFeatureCollection of marked parcels.
     * @param blockCollection     SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                            {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder           folder to store result files.
     * @param buildingFile        Geopackage representing the buildings.
     * @param roadFile            Geopackage representing the roads (optional).
     * @param profile             Description of the urban fabric profile planed to be simulated on this zone.
     * @param allowIsolatedParcel true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @return The input {@link SimpleFeatureCollection} with each marked parcel replaced by simulated parcels.
     * @throws IOException Writing files in debug modes
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel) throws IOException {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, roadFile, profile, allowIsolatedParcel, null);
    }

    /**
     * Apply the densification workflow on a set of marked parcels. Overload
     * {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, double, double, boolean, Geometry)} method with a
     * profile urban fabric input (which automatically report its parameters to the fields)
     *
     * @param parcelCollection    SimpleFeatureCollection of marked parcels.
     * @param blockCollection     SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                            {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder           folder to store result files.
     * @param buildingFile        Geopackage representing the buildings.
     * @param roadFile            Geopackage representing the roads (optional).
     * @param profile             Description of the urban fabric profile planed to be simulated on this zone.
     * @param allowIsolatedParcel true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @param exclusionZone       Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @return The input {@link SimpleFeatureCollection} with each marked parcel replaced by simulated parcels.
     * @throws IOException Writing files in debug modes
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel, Geometry exclusionZone) throws IOException {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, roadFile, profile.getHarmonyCoeff(), profile.getIrregularityCoeff(),
                profile.getMaximalArea(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getDrivewayWidth(),
                allowIsolatedParcel, exclusionZone);
    }

    /**
     * Apply a hybrid densification process on the coming parcel collection. The parcels that size are inferior to 4x the maximal area of parcel type to create are runned with the
     * densication workflow. The parcels that size are superior to 4x the maximal area are considered as able to build neighborhood. They are divided with the
     * {@link fr.ign.artiscales.pm.workflow.ConsolidationDivision#consolidationDivision(SimpleFeatureCollection, File, File, ProfileUrbanFabric)} method.
     *
     * @param parcelCollection          SimpleFeatureCollection of marked parcels.
     * @param blockCollection           SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                                  {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder                 folder to store result files.
     * @param buildingFile              Geopackage representing the buildings.
     * @param roadFile                  Geopackage representing the roads (optional).
     * @param profile                   ProfileUrbanFabric of the simulated urban scene.
     * @param allowIsolatedParcel       true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @param exclusionZone             Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @param factorOflargeZoneCreation If the area of the parcel to be simulated is superior to the maximal size of parcels multiplied by this factor, the simulation will be done with the
     *                                  {@link fr.ign.artiscales.pm.workflow.ConsolidationDivision#consolidationDivision(SimpleFeatureCollection, File, File, ProfileUrbanFabric)} method.
     * @return The input {@link SimpleFeatureCollection} with each marked parcel replaced by simulated parcels.
     * @throws IOException Writing files in debug modes
     */
    public SimpleFeatureCollection densificationOrNeighborhood(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder, File buildingFile, File roadFile,
                                                               ProfileUrbanFabric profile, boolean allowIsolatedParcel, Geometry exclusionZone, int factorOflargeZoneCreation) throws IOException {
        // We flagcut the parcels which size is inferior to 4x the max parcel size
        parcelCollection = DataUtilities.collection(parcelCollection); //load into memory
        SimpleFeatureCollection infParcels = MarkParcelAttributeFromPosition.markParcelsInf(parcelCollection, profile.getMaximalArea() * factorOflargeZoneCreation);
        if (isDEBUG())
            CollecMgmt.exportSFC(infParcels, new File(outFolder, "densificationOrNeighborhood-Marked"));
        SimpleFeatureCollection parcelDensified = densification(infParcels,
                blockCollection, outFolder, buildingFile, roadFile, profile.getHarmonyCoeff(), profile.getIrregularityCoeff(), profile.getMaximalArea(),
                profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getDrivewayWidth(), allowIsolatedParcel, exclusionZone);
        if (isDEBUG())
            CollecMgmt.exportSFC(parcelDensified, new File(outFolder, "densificationOrNeighborhood-Dens"));

        // if parcels are too big, we try to create neighborhoods inside them with the consolidation algorithm
        // We first re-mark the parcels that were marked.
        SimpleFeatureCollection alreadyMarkedParcels = MarkParcelAttributeFromPosition.markAlreadyMarkedParcels(parcelDensified, parcelCollection);
        if (isDEBUG())
            CollecMgmt.exportSFC(alreadyMarkedParcels, new File(outFolder, "densificationOrNeighborhood-alreadyMarked"));

        SimpleFeatureCollection supParcels = MarkParcelAttributeFromPosition.markParcelsSup(alreadyMarkedParcels,
                profile.getMaximalArea() * factorOflargeZoneCreation);
        if (isDEBUG())
            CollecMgmt.exportSFC(supParcels, new File(outFolder, "densificationOrNeighborhood-ReMarked"));

//        if (!MarkParcelAttributeFromPosition.isNoParcelMarked(supParcels)) {
        profile.setStreetWidth(profile.getLaneWidth());
        parcelDensified = (new ConsolidationDivision()).consolidationDivision(supParcels, roadFile, outFolder, profile);
        if (isDEBUG())
            CollecMgmt.exportSFC(parcelDensified, new File(outFolder, "densificationOrNeighborhood-Neigh"));
//        }
        return parcelDensified;
    }


    /**
     * Create a new section name following a precise rule.
     *
     * @param section name of the former section
     * @return the new section's name
     */
    public String makeNewSection(String section) {
        return section + "-Densifyed";
    }

    /**
     * Check if the input {@link SimpleFeature} has a section field that has been simulated with this present workflow.
     *
     * @param feat {@link SimpleFeature} to test.
     * @return true if the section field is marked with the {@link #makeNewSection(String)} method.
     */
    public boolean isNewSection(SimpleFeature feat) {
        String field = (String) feat.getAttribute(ParcelSchema.getParcelSectionField());
        return field != null && field.endsWith("-Densifyed");
    }
}
