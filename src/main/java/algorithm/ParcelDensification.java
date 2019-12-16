package algorithm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import processus.ParcelSplitFlag;

public class ParcelDensification {

	/**
	 * Apply the densification process
	 * 
	 * @param splitZone
	 * @param parcelCollection
	 * @param tmpFolder
	 * @param rootFile
	 * @param p
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelDensification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection ilotCollection,
			File tmpFolder, File buildingFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean isArt3AllowsIsolatedParcel) throws Exception {

		final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();

		// the little islands (ilots)
		File ilotReduced = new File(tmpFolder, "ilotTmp.shp");
		Vectors.exportSFC(ilotCollection, ilotReduced);
		// IFeatureCollection<IFeature> featC = ShapefileReader.read(ilotReduced.getAbsolutePath());
		//
		// List<IOrientableCurve> lOC = featC.select(parcelCollec.envelope()).parallelStream().map(x -> FromGeomToLineString.convert(x.getGeom()))
		// .collect(ArrayList::new, List::addAll, List::addAll);
		// IMultiCurve<IOrientableCurve> iMultiCurve = new GM_MultiCurve<>(lOC);

		DefaultFeatureCollection cutedAll = new DefaultFeatureCollection();
		SimpleFeatureBuilder SFBFrenchParcel = ParcelSchema.getSFBFrenchParcel();
		SimpleFeatureIterator parcelCollectionIt = parcelCollection.features();

		try {
			while (parcelCollectionIt.hasNext()) {
				SimpleFeature parcel = parcelCollectionIt.next();

				// if the parcel is selected for the simulation and bigger than the limit size
				if (parcel.getAttribute("SPLIT").equals(1) && ((Geometry) parcel.getDefaultGeometry()).getArea() > maximalAreaSplitParcel) {
					// we falg cut the parcel
					SimpleFeatureCollection tmp = ParcelSplitFlag.generateFlagSplitedParcels(parcel, null, tmpFolder, buildingFile,
							maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, isArt3AllowsIsolatedParcel);
					// if the cut parcels are inferior to the minimal size, we cancel all and add the initial parcel
					boolean add = true;
					SimpleFeatureIterator parcelIt = tmp.features();
					try {
						while (parcelIt.hasNext()) {
							if (((Geometry) parcelIt.next().getDefaultGeometry()).getArea() < minimalAreaSplitParcel) {
								System.out.println("densifyed parcel is too small");
								add = false;
								break;
							}
						}
					} catch (Exception problem) {
						System.out.println("problem" + problem + "for " + parcel + " feature densification");
						problem.printStackTrace();
					} finally {
						parcelIt.close();
					}
					if (add) {
						// construct the new parcels
						// could have been cleaner with a stream but still don't know how to have an external counter to set parcels number
						// Arrays.stream(tmp.toArray(new SimpleFeature[0])).forEach(parcelCuted -> {

						int i = 1;
						SimpleFeatureIterator parcelCutedIt = tmp.features();
						try {
							while (parcelCutedIt.hasNext()) {
								SimpleFeature parcelCuted = parcelCutedIt.next();
								String newCodeDep = (String) parcel.getAttribute("CODE_DEP");
								String newCodeCom = (String) parcel.getAttribute("CODE_COM");
								String newSection = (String) parcel.getAttribute("SECTION") + "-Densifyed";
								String newNumero = String.valueOf(i++);
								String newCode = newCodeDep + newCodeCom + "000" + newSection + newNumero;
								SFBFrenchParcel.set(geomName, parcelCuted.getDefaultGeometry());
								SFBFrenchParcel.set("CODE", newCode);
								SFBFrenchParcel.set("CODE_DEP", newCodeDep);
								SFBFrenchParcel.set("CODE_COM", newCodeCom);
								SFBFrenchParcel.set("COM_ABS", "000");
								SFBFrenchParcel.set("SECTION", newSection);
								SFBFrenchParcel.set("NUMERO", newNumero);
								cutedAll.add(SFBFrenchParcel.buildFeature(null));
							}
						} catch (Exception problem) {
							problem.printStackTrace();
						} finally {
							parcelCutedIt.close();
						}
						// });
					} else {
						SFBFrenchParcel = ParcelSchema.setSFBFrenchParcelWithFeat(parcel, SFBFrenchParcel.getFeatureType());
						cutedAll.add(SFBFrenchParcel.buildFeature(null));
					}
				}
				// if no simulation needed, we ad the normal parcel
				else {
					SFBFrenchParcel = ParcelSchema.setSFBFrenchParcelWithFeat(parcel, SFBFrenchParcel.getFeatureType());
					cutedAll.add(SFBFrenchParcel.buildFeature(null));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelCollectionIt.close();
		}
		return cutedAll.collection();
	}

}
