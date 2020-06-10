package fr.ign.artiscales.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import com.opencsv.CSVWriter;

import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;

/**
 * This class calculates basic statistics for every marked parcels. If no mark parcels are found, stats are calucated for every parcels.
 * 
 * @author Maxime Colomb
 *
 */
public class ParcelStat {

//	public static void main(String[] args) throws Exception {
//		long strat = System.currentTimeMillis();
//		// ShapefileDataStore sdsParcel = new ShapefileDataStore(
//		// new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/GeneralTest/parcel.shp").toURI().toURL());
//		ShapefileDataStore sdsParcelEv = new ShapefileDataStore(
//				new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out2/evolvedParcel.shp").toURI().toURL());
//		ShapefileDataStore sdsSimu = new ShapefileDataStore(
//				new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out2/simulatedParcels.shp").toURI().toURL());
//		SimpleFeatureCollection parcelEv = sdsParcelEv.getFeatureSource().getFeatures();
//		SimpleFeatureCollection parcelSimu = sdsSimu.getFeatureSource().getFeatures();
//		Collec.exportSFC(makeHausdorfDistanceMaps(parcelEv, parcelSimu), new File("/tmp/haus"));
//		sdsParcelEv.dispose();
//		sdsSimu.dispose();
//		System.out.println("time : " + (System.currentTimeMillis() - strat));
//	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, File roadFile, File parcelStatCsv)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		ShapefileDataStore sds = new ShapefileDataStore(roadFile.toURI().toURL());
		writeStatSingleParcel(parcels, sds.getFeatureSource().getFeatures(), parcelStatCsv);
		sds.dispose();
	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File parcelStatCsv)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		// look if there's mark field. If not, every parcels are marked
		if (!Collec.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
			System.out.println(
					"+++ writeStatSingleParcel: unmarked parcels. Try to mark them with the MarkParcelAttributeFromPosition.markAllParcel() method. Return null ");
			return;
		}
		CSVWriter csv = new CSVWriter(new FileWriter(parcelStatCsv, false));
		String[] firstLine = { "code", "area", "perimeter", "contactWithRoad", "widthContactWithRoad", "numberOfNeighborhood", "maxBoundingCircle"};
		csv.writeNext(firstLine);
		SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
		Arrays.stream(parcels.toArray(new SimpleFeature[0]))
				.filter(p -> (int) p.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1).forEach(parcel -> {
					// if parcel is marked to be analyzed
					Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
					double widthRoadContact = ParcelState.getParcelFrontSideWidth((Polygon) Geom.getPolygon(parcelGeom),
							Collec.snapDatas(roads, parcelGeom.buffer(7)),
							Geom.fromMultiToLineString(Collec.fromPolygonSFCtoRingMultiLines(Collec.snapDatas(islet, parcelGeom))));
					boolean contactWithRoad = false;
					if (widthRoadContact != 0)
						contactWithRoad = true;
					// float compactness =
					String[] line = {
							parcel.getAttribute(ParcelSchema.getMinParcelCommunityField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelNumberField()),
							String.valueOf(parcelGeom.getArea()), String.valueOf(parcelGeom.getLength()), String.valueOf(contactWithRoad),
							String.valueOf(widthRoadContact),
							String.valueOf(ParcelState.countParcelNeighborhood(parcelGeom, Collec.snapDatas(parcels, parcelGeom.buffer(2)))),
							String.valueOf(egress(parcelGeom)) };
					csv.writeNext(line);
				});
		csv.close();
	}

	public static double egress(Geometry geom) {
		MinimumBoundingCircle mbc = new MinimumBoundingCircle(geom);
		// only on jts 1.17
		// System.out.println(mbc.getCircle());
		// MaximumInscribedCircle mic = new MaximumInscribedCircle(geom.buffer(0), 1);
		// System.out.println(mic.getCenter().buffer(mic.getRadiusLine().getLength()));
		// return mic.getRadiusLine().getLength() / mbc.getDiameter().getLength() ;
		return mbc.getDiameter().getLength();
	}

	public static SimpleFeatureCollection makeHausdorfDistanceMaps(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare)
			throws NoSuchAuthorityCodeException, FactoryException, IOException {
		if (!Collec.isCollecContainsAttribute(parcelIn, "CODE")) 
			GeneralFields.addParcelCode(parcelIn);
		if (!Collec.isCollecContainsAttribute(parcelToCompare, "CODE")) 
			GeneralFields.addParcelCode(parcelToCompare);
		SimpleFeatureType schema = parcelIn.getSchema();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("minParcel");
		sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
		sfTypeBuilder.add(schema.getGeometryDescriptor().getLocalName(), Polygon.class);
		sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
		sfTypeBuilder.add("DisHausDst", Double.class);
		sfTypeBuilder.add("HausDist", Double.class);
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add("CodeAppar", String.class);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
		HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
		try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
				SimpleFeature parcelCompare = Collec.getSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
				if (parcelCompare != null) {
					Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
					DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
					builder.set("DisHausDst", dhd.distance());
					builder.set("HausDist", hausDis.measure(parcelGeom, parcelCompareGeom));
					builder.set("CODE", parcel.getAttribute("CODE"));
					builder.set("CodeAppar", parcelCompare.getAttribute("CODE"));
					builder.set(schema.getGeometryDescriptor().getLocalName(), parcelGeom);
					result.add(builder.buildFeature(Attribute.makeUniqueId()));
				} else {
					builder.set("CODE", parcel.getAttribute("CODE"));
					builder.set(schema.getGeometryDescriptor().getLocalName(), parcelGeom);
					result.add(builder.buildFeature(Attribute.makeUniqueId()));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result.collection();
	}
}