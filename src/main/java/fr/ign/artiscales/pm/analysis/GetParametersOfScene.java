package fr.ign.artiscales.pm.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.parcelFunction.ParcelGetter;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;

/**
 * This class generates values for the description of an urban fabric. It is aimed to help the setting of {@link fr.ign.artiscales.tools.parameter.ProfileUrbanFabric} parameters.
 * It can work on different scales, from the ilot or the {@link GeneralFields#zonePreciseNameField} to a whole community.
 * 
 * @author Maxime Colomb
 *
 */
public class GetParametersOfScene {

	// static String scaleZone = "community";
	// TODO mettre la possibilité de mélanger ces échelles List<String> scaleZone = Arrays.asList("community");
	static File parcelFile, buildingFile, zoningFile, roadFile, outFolder;

	/**
	 * Proceed to the analysis of parameters in every defined zones of the geographic files.
	 */
	public static void main(String[] args) throws IOException {
		outFolder = new File("/tmp/ParametersOfScene");
		setFiles(new File("src/main/resources/GeneralTest/"));
		String scaleZone = "genericZone";
		generateAnalysisOfScene(scaleZone);
		scaleZone = "preciseZone";
		generateAnalysisOfScene(scaleZone);
		scaleZone = "community";
		generateAnalysisOfScene(scaleZone);
		scaleZone = "islet";
		generateAnalysisOfScene(scaleZone);
	}

	/**
	 * Set automaticlally the file names with their basic names from a root folder. Possible to change them with dedicaded setters
	 * 
<<<<<<< HEAD
	 * @param mainFolder
	 *            root folder containing the geographic layers.
=======
	 * @param mainFolder root folder containing the geographic layers.
>>>>>>> 65ef530bc51b90621d238bd2e7e34d57ebfffdfa
	 */
	public static void setFiles(File mainFolder) {
		parcelFile = new File(mainFolder, "parcel" + Collec.getDefaultGISFileType());
		if (!parcelFile.exists())
			System.out.println(parcelFile + " doesn't exist");
		buildingFile = new File(mainFolder, "building" + Collec.getDefaultGISFileType());
		if (!buildingFile.exists())
			System.out.println(buildingFile + " doesn't exist");
		zoningFile = new File(mainFolder, "zoning" + Collec.getDefaultGISFileType());
		if (!zoningFile.exists())
			System.out.println(zoningFile + " doesn't exist");
		roadFile = new File(mainFolder, "road" + Collec.getDefaultGISFileType());
		if (!roadFile.exists())
			System.out.println(roadFile + " doesn't exist");
		outFolder.mkdirs();
	}
	/// **
	// * Allow the
	// * @param scalesZone
	// * @throws IOException
	// */
	// public static void generateAnalysisOfScene(List<String> scalesZone) throws IOException {
	// for (String scaleZone : scalesZone)
	// generateAnalysisOfScene(scaleZone);
	// //TODO finish that
	// }

	/**
	 * Generate a graph with the area of parcels.
	 * 
	 * @param scaleZone
	 *            The scale of the studied zone. Can either be:
	 *            <ul>
	 *            <li>community</li>
	 *            <li>genericZone</li>
	 *            <li>preciseZone</li>
	 *            <li>islet</li>
	 *            </ul>
	 * 
	 * @throws IOException
	 */
	public static void generateAnalysisOfScene(String scaleZone) throws IOException {
		DataStore ds = Geopackages.getDataStore(parcelFile);
		// sdsRoad.setCharset(Charset.forName("UTF-8"));
		SimpleFeatureCollection parcels = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
		// collection of every input with its set of parcels
		HashMap<String, SimpleFeatureCollection> listSFC = new HashMap<String, SimpleFeatureCollection>();
		DataStore sdsZone = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zonings = DataUtilities
				.collection(Collec.selectIntersection(sdsZone.getFeatureSource(sdsZone.getTypeNames()[0]).getFeatures(), parcels));
		sdsZone.dispose();
		switch (scaleZone) {
		case "community":
			for (String cityCodes : ParcelAttribute.getCityCodesOfParcels(parcels))
				listSFC.put(cityCodes, ParcelGetter.getFrenchParcelByZip(parcels, cityCodes));
			break;
		case "genericZone":
			for (String genericZone : GeneralFields.getGenericZoningTypes(zonings))
				listSFC.put(genericZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
						MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcels, genericZone, zoningFile)));
			break;
		case "preciseZone":
			for (String preciseZone : GeneralFields.getPreciseZoningTypes(zonings))
				listSFC.put(preciseZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
						MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcels, "", preciseZone, zoningFile)));
			break;
		case "islet":
			SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
			int i = 0;
			try (SimpleFeatureIterator it = islet.features()) {
				while (it.hasNext())
					listSFC.put(String.valueOf(i++), MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition
							.markParcelIntersectPolygonIntersection(parcels, Arrays.asList((Geometry) it.next().getDefaultGeometry()))));
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		}
		DataStore dsRoad = Geopackages.getDataStore(roadFile);
		for (String zone : listSFC.keySet()) {
			// Parcel's area
			areaBuiltAndTotal(listSFC.get(zone), scaleZone, zone);
			// Road Information
			publicSpaceRatio(listSFC.get(zone), DataUtilities.collection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures()), scaleZone,
					zone);
		}
		// Public Space Ratio
		dsRoad.dispose();
		ds.dispose();
		System.out.println("##### " + scaleZone + " done");
	}

	/**
	 * Generate the road information in the give zones
	 * 
	 * @param collection
	 * @param road
	 * @param scaleZone
	 * @param zone
	 * @throws IOException
	 */
	public static void publicSpaceRatio(SimpleFeatureCollection collection, SimpleFeatureCollection road, String scaleZone, String zone)
			throws IOException {
		// Road informations is harder to produce. We are based on the road Geopackage and on the ratio of road/area calculation to produce estimations
		// we create a buffer around the zone to get corresponding road segments. The buffer length depends on the type of scale
		double buffer = 42;
		switch (scaleZone) {
		case "genericZone":
			buffer = 20;
			break;
		case "preciseZone":
		case "islet":
			buffer = 10;
			break;
		}
		Geometry zoneGeom = Geom.unionSFC(collection).buffer(buffer).buffer(-buffer);
		SimpleFeatureCollection roadsSelected = Collec.selectIntersection(road, zoneGeom);
		if (roadsSelected.size() > 1)
			MakeStatisticGraphs.roadGraph(roadsSelected, "length of the " + scaleZone + " " + zone + " roads ", "width of the road",
					"lenght of the type of road", outFolder);
		RoadRatioParcels.roadRatioZone(Geom.geomsToCollec(Arrays.asList(zoneGeom), GeneralFields.getSFBZoning()), collection, zone, outFolder,
				roadFile);
	}

	/**
	 * Calculate the area bound of every parcels then only the build parcels.
	 * 
	 * @param collection
	 * @param scaleZone
	 * @param zone
	 * @throws IOException
	 */
	public static void areaBuiltAndTotal(SimpleFeatureCollection collection, String scaleZone, String zone) throws IOException {
		if (collection.isEmpty())
			return;
		HashMap<String, SimpleFeatureCollection> lSFC = new HashMap<String, SimpleFeatureCollection>();
		lSFC.put("total parcels", MarkParcelAttributeFromPosition.getOnlyMarkedParcels(collection));
		lSFC.put("built parcels",
				MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markBuiltParcel(collection, buildingFile)));
		for (String nameSfc : lSFC.keySet()) {
			SimpleFeatureCollection sfc = lSFC.get(nameSfc);
			if (sfc != null && sfc.size() > 2) {
				AreaGraph vals = MakeStatisticGraphs.sortValuesAndCategorize(
						Arrays.stream(sfc.toArray(new SimpleFeature[0])).collect(Collectors.toList()), scaleZone + zone, true);
				vals.toCSV(outFolder);
				MakeStatisticGraphs.makeGraphHisto(vals, outFolder, "area of the" + nameSfc + "of the " + scaleZone + " " + zone + " without crests",
						"parcel area", "nb parcels", 15);
			}
		}
	}

	public static File getParcelFile() {
		return parcelFile;
	}

	public static void setParcelFile(File parcelFile) {
		GetParametersOfScene.parcelFile = parcelFile;
	}

	public static File getBuildingFile() {
		return buildingFile;
	}

	public static void setBuildingFile(File buildingFile) {
		GetParametersOfScene.buildingFile = buildingFile;
	}

	public static File getZoningFile() {
		return zoningFile;
	}

	public static void setZoningFile(File zoningFile) {
		GetParametersOfScene.zoningFile = zoningFile;
	}

	public static File getRoadFile() {
		return roadFile;
	}

	public static void setRoadFile(File roadFile) {
		GetParametersOfScene.roadFile = roadFile;
	}

	public static File getOutFolder() {
		return outFolder;
	}

	public static void setOutFolder(File outFolder) {
		GetParametersOfScene.outFolder = outFolder;
	}
}
