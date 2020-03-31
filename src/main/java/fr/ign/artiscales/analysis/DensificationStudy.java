package fr.ign.artiscales.analysis;

import java.io.File;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.goal.Densification;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;
import fr.ign.cogit.geoToolsFunctions.Csv;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

public class DensificationStudy {

	/**
	 * This class allows to help densification studies that can be ask in French Schémas de Cohérence Territoriale (SCoT). It isolate empty parcels within urban zones (called
	 * <i>dent creuses</i> and simulates their densification. If they are too big, it simulates the creation of a whole neighborhood. Th output shapefile is called
	 * <i>parcelDentCreusesDensified.shp</i>
	 * 
	 * It also simulates the parcels that can be created with the flag parcels on already built parcels. The shapefile containing those parcels is called
	 * <i>parcelPossiblyDensified.shp</i>
	 *
	 */
	public static void main(String[] args) throws Exception {
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pmScen = new PMScenario(new File("/home/ubuntu/PMtest/Densification/SetM/jsonEx.json"), new File("/tmp"));
		pmScen.executeStep();
		
		//run a densification study on a single community
//		File rootFolder = new File("/home/ubuntu/PMtest/Densification/");
//		File parcelFile = new File(rootFolder, "torcy.shp");
//		// File parcelFile = new File(rootFolder, "marked.shp");
//		File zoningFile = new File(rootFolder, "zoning.shp");
//		File buildingFile = new File(rootFolder, "building.shp");
//		File roadFile = new File(rootFolder, "road.shp");
//		File isletFile = new File(rootFolder, "islet.shp");
//		File outFolder = new File(rootFolder, "/out/");
//		File tmpFolder = new File("/tmp/");
//		ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(rootFolder, "/profileBuildingType/smallHouse.json"));
//
//		ShapefileDataStore sdsParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
//		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource().getFeatures();
//
//		boolean isParcelWithoutStreetAllowed = false;
//		runDensificationStudy(parcels, isletFile, buildingFile, roadFile, zoningFile, tmpFolder, outFolder,
//				isParcelWithoutStreetAllowed, profile);
//		sdsParcel.dispose();		
	}

	public static void runDensificationStudy(SimpleFeatureCollection parcels, File isletFile, File buildingFile, File roadFile, File zoningFile,
			File tmpFolder, File outFolder, boolean isParcelWithoutStreetAllowed, ProfileUrbanFabric profile) throws Exception {

		ShapefileDataStore sdsIslet = new ShapefileDataStore(isletFile.toURI().toURL());
		SimpleFeatureCollection islet = sdsIslet.getFeatureSource().getFeatures();

		String splitField = MarkParcelAttributeFromPosition.getMarkFieldName();
		// get total unbuilt parcels from the urbanized zones
		SimpleFeatureCollection parcelsDentCreuseZone = MarkParcelAttributeFromPosition
				.markParcelIntersectConstructibleZoningType(MarkParcelAttributeFromPosition.markUnBuiltParcel(parcels, buildingFile), zoningFile);
		SimpleFeatureCollection parcelsCreated = Densification.densificationOrNeighborhood(parcelsDentCreuseZone, islet, tmpFolder, buildingFile,
				roadFile, profile, isParcelWithoutStreetAllowed);
		// simulate the densification of built parcels in the given zone
		SimpleFeatureCollection parcelsDensifZone = MarkParcelAttributeFromPosition
				.markParcelIntersectConstructibleZoningType(MarkParcelAttributeFromPosition.markBuiltParcel(parcels, buildingFile), zoningFile);
		SimpleFeatureCollection parcelsDensifCreated = Densification.densificationOrNeighborhood(parcelsDensifZone, islet, tmpFolder, buildingFile,
				roadFile, profile, isParcelWithoutStreetAllowed);

		// change split name to show if they can be built
		MarkParcelAttributeFromPosition.setMarkFieldName("BUILDABLE");
		// Mark the simulated parcels that doesn't contains buildings (and therefore can be build)
		parcelsCreated = MarkParcelAttributeFromPosition.markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsCreated),
				buildingFile);

		parcelsDensifCreated = MarkParcelAttributeFromPosition
				.markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsDensifCreated), buildingFile);
		// If the parcels have to be connected to the road, we mark them
		if (!isParcelWithoutStreetAllowed) {
			parcelsCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsCreated, islet, roadFile);
			parcelsDensifCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsDensifCreated, islet, roadFile);
		}
		// exporting output shapefiles and countings
		List<SimpleFeature> vacantParcelU = Arrays.stream(parcelsDensifCreated.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).collect(Collectors.toList());
		Collec.exportSFC(parcelsCreated, new File(outFolder, "parcelDentCreusesDensified.shp"), false);
		Collec.exportSFC(vacantParcelU, new File(outFolder, "parcelPossiblyDensified.shp"), false);
		
		long nbDentCreuse = Arrays.stream(parcelsDentCreuseZone.toArray(new SimpleFeature[0])).filter(feat -> feat.getAttribute(splitField).equals(1)).count();
		long nbdentCreusesParcels = Arrays.stream(parcelsCreated.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).count();
		System.out.println("number of dent creuses "+ nbDentCreuse);
		System.out.println("possible to have "+ nbdentCreusesParcels + " buildable parcels out of it");
		System.out.println();
		System.out.println("possible to have " + vacantParcelU.size() + " densifiable parcels");
		
		// saving the stats in a .csv file
		String[] firstline = {"DEPCOM","number of dent creuses","parcels simulated in dent creuses","parcels simulated by densification"};
		Object[] line = {nbDentCreuse, nbdentCreusesParcels,vacantParcelU.size()};
		Hashtable<String,Object[]> l = new Hashtable<String,Object[]>();
		l.put(ParcelAttribute.getCityCodeFromParcels(parcelsDensifCreated), line);
		Csv.generateCsvFile(l, outFolder, "densificationStudyResult", firstline, true);
		Csv.needFLine = false;
	}
}
