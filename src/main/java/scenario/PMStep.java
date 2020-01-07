package scenario;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import fields.AttributeFromPosition;
import fields.FrenchParcelFields;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parameter.ProfileBuilding;
import fr.ign.cogit.parcelFunction.ParcelGetter;
import fr.ign.cogit.parcelFunction.ParcelState;
import goal.ParcelConsolidRecomp;
import goal.ParcelDensification;
import goal.ParcelTotRecomp;

public class PMStep {
	public PMStep(String goal, String parcelProcess, String zone, String communityNumber, String communityType, String buildingType) {
		super();
		this.goal = goal;
		this.parcelProcess = parcelProcess;
		this.zone = zone;
		this.communityNumber = communityNumber;
		this.communityType = communityType;
		this.buildingType = buildingType;
	}

	public static void setFiles(File parcelFile, File ilotFile, File zoningFile, File tmpFolder, File buildingFile, File predicateFile,
			File communityFile, File polygonIntersection, File outFolder, File profileFolder) {
		PARCELFILE = parcelFile;
		ILOTFILE = ilotFile;
		ZONINGFILE = zoningFile;
		TMPFOLDER = tmpFolder;
		BUILDINGFILE = buildingFile;
		POLYGONINTERSECTION = polygonIntersection;
		PREDICATEFILE = predicateFile;
		COMMUNITYFILE = communityFile;
		OUTFOLDER = outFolder;
		PROFILEFOLDER = profileFolder;
	}

	String goal;
	String parcelProcess;
	String zone;
	String communityNumber;
	String communityType;
	String buildingType;

	static File PARCELFILE;
	static File ILOTFILE;
	static File ZONINGFILE;
	static File TMPFOLDER;
	static File BUILDINGFILE;
	static File PREDICATEFILE;
	static File COMMUNITYFILE;
	static File POLYGONINTERSECTION;
	static File OUTFOLDER;
	static File PROFILEFOLDER;

	public PMStep() {

	}

	public File execute() throws Exception {
		ProfileBuilding.setProfileFolder(PROFILEFOLDER.toString());
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(PARCELFILE.toURI().toURL());
		SimpleFeatureCollection parcel = new DefaultFeatureCollection();
		if (communityNumber != null) {
			parcel = ParcelGetter.getParcelByZip(shpDSParcel.getFeatureSource().getFeatures(), communityNumber);
		} else if (communityType != null) {
			// TODO per each type
		} else {
			parcel = shpDSParcel.getFeatureSource().getFeatures();
		}

		ShapefileDataStore shpDSIlot = new ShapefileDataStore(ILOTFILE.toURI().toURL());
		SimpleFeatureCollection ilot = shpDSIlot.getFeatureSource().getFeatures();

		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		InputStream fileInputStream = new FileInputStream(ProfileBuilding.getProfileFolder() + "/" + buildingType + ".json");
		ProfileBuilding profile = mapper.readValue(fileInputStream, ProfileBuilding.class);
		SimpleFeatureCollection parcelCut = new DefaultFeatureCollection();
		switch (goal) {
		case "totalZone":
			ParcelTotRecomp.PROCESS = parcelProcess;
			ShapefileDataStore shpDSZone = new ShapefileDataStore(ZONINGFILE.toURI().toURL());
			SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();
			SimpleFeatureCollection zoneCollection = ParcelTotRecomp.createZoneToCut(zone, featuresZones, parcel);
			parcelCut = ParcelTotRecomp.parcelTotRecomp(zoneCollection, parcel, TMPFOLDER, ZONINGFILE, profile.getMaximalArea(),
					profile.getMinimalArea(), profile.getMaximalWidth(), profile.getStreetWidth(), profile.getDecompositionLevelWithoutStreet());
			shpDSZone.dispose();
			break;
		case "dens":
			SimpleFeatureCollection parcelMarked = AttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, POLYGONINTERSECTION);
			SimpleFeatureCollection toDensify = AttributeFromPosition.markParcelIntersectZoningType(parcelMarked, zone, ZONINGFILE);
			parcelCut = ParcelDensification.parcelDensification(toDensify, ilot, TMPFOLDER, BUILDINGFILE, profile.getMaximalArea(),
					profile.getMinimalArea(), profile.getMaximalWidth(), profile.getLenDriveway(),
					ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE));
			break;
		case "consolid":
			SimpleFeatureCollection parcelMarked2 = AttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, POLYGONINTERSECTION);
			SimpleFeatureCollection toDensify2 = AttributeFromPosition.markParcelIntersectZoningType(parcelMarked2, zone, ZONINGFILE);
			ParcelConsolidRecomp.PROCESS = parcelProcess;
			parcelCut = ParcelConsolidRecomp.parcelConsolidRecomp(toDensify2, TMPFOLDER, profile.getMaximalArea(), profile.getMinimalArea(),
					profile.getMaximalWidth(), profile.getStreetWidth(), profile.getDecompositionLevelWithoutStreet());
			break;
		}
		File output = new File(OUTFOLDER, "parcelCuted-" + goal + ".shp");
		parcelCut = FrenchParcelFields.fixParcelAttributes(parcelCut, TMPFOLDER, COMMUNITYFILE);
		Vectors.exportSFC(parcelCut, output);
		shpDSIlot.dispose();
		shpDSParcel.dispose();
		return output;

	}

	public static void setParcel(File parcelFile) {
		PARCELFILE = parcelFile;
	}

	@Override
	public String toString() {
		return "PMStep [goal=" + goal + ", parcelProcess=" + parcelProcess + ", zone=" + zone + ", communityNumber=" + communityNumber
				+ ", communityType=" + communityType + ", buildingType=" + buildingType + "]";
	}
}