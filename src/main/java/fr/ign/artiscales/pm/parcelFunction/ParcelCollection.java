package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.pm.fields.artiscales.ArtiScalesSchemas;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.OpOnCollec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.TopologyException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Methods to operate routine procedures on collections of parcels
 */
public class ParcelCollection {

//    public static void main(String[] args) throws IOException {
//        File rootFile = new File("src/main/resources/ParcelComparison/");
//        sortDifferentParcel(new File(rootFile, "parcel2003.gpkg"), new File(rootFile, "parcel2018.gpkg"), new File("/tmp/Correct/"), 550, 175, true);
//    }

    /**
     * Return the same parcel collection without it <i>SPLIT</i> field (if this field has a new name, it will be adapted).
     * @param parcels parcel collection
     * @return same parcel collection without the field attribute.
     */
    public static SimpleFeatureCollection getParcelWithoutSplitField(SimpleFeatureCollection parcels){
        DefaultFeatureCollection df = new DefaultFeatureCollection();
        SimpleFeatureBuilder builder = ParcelSchema.getSFBWithoutSplit(parcels.getSchema());
        try(SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                Schemas.setFieldsToSFB(builder, it.next());
                df.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        }
        return df;
    }

    /**
     * Method that compares two set of parcel plans and sort the reference parcel plan with the ones that changed and the ones that doesn't. We compare the parcels area of the
     * reference parcel to the ones that are intersected. If they are similar with a 3% error rate, we conclude that they are the same.
     * <p>
     * This method creates four geographic files (shapefile or geopackages, regarding the projects default format) in the parcelOutFolder:
     * </p>
     * <ul>
     * <li><b>same</b> contains the reference parcels that have not evolved</li>
     * <li><b>notSame</b> contains the reference parcels that have evolved</li>
     * <li><b>simulation</b> contains the <i>notSame</i> parcels with a reduction buffer, used for a precise intersection with other parcel in Parcel Manager scenarios. The large parcels that are selected for a zone simulation (see below) aren't present.</li>
     * <li><b>zone</b> contains special zones to be simulated. They consist in a small evolved parts of large parcels that mostly haven't evolved. If we don't proceed to its calculation, the large parcel won't be urbanized. Can also be ignored</li>
     * <li><b>realParcel</b> contains only the compared parcels that have evolved</li>
     * </ul>
     *
     * @param parcelRefFile       The reference parcel plan
     * @param parcelToCompareFile The parcel plan to compare
     * @param parcelOutFolder     Folder where are stored the result geopackages
     * @throws IOException read and write files
     */
    public static void sortDifferentParcel(File parcelRefFile, File parcelToCompareFile, File parcelOutFolder) throws IOException {
        sortDifferentParcel(parcelRefFile, parcelToCompareFile, parcelOutFolder, 800, 150, false);
    }

    /**
     * Method that compares two set of parcel plans and sort the reference parcel plan with the ones that changed and the ones that doesn't. We compare the parcels area of the
     * reference parcel to the ones that are intersected. If they are similar with a 3% error rate, we conclude that they are the same.
     * <p>
     * This method creates four geographic files (shapefile or geopackages, regarding the projects default format) in the parcelOutFolder:
     * <ul>
     * <li><b>same</b> contains the reference parcels that have not evolved</li>
     * <li><b>notSame</b> contains the reference parcels that have evolved</li>
     * <li><b>realParcel</b> contains the compared parcels that have evolved to its current 'real' state</li>
     * <li><b>simulation</b> contains reference parcels that evolved and aren't a <b>zone</b>. We apply a reduction buffer for a precise intersection with other parcel in Parcel Manager scenarios.</li>
     * </ul>
     *
     * @param parcelRefFile          The reference parcel plan
     * @param parcelToCompareFile    The parcel plan to compare
     * @param parcelOutFolder        Folder where are stored the result geopackages
     * @param minParcelSimulatedSize The minimal size of parcels of the usual urban fabric profile. If the algorithm is used outside the simulation, default value of 100 square meters is used.
     * @param maxParcelSimulatedSize The maximal size of parcel simulated (used for selection)
     * @param overwrite              do we overwrite the
     * @throws IOException read and write files
     */
    public static void sortDifferentParcel(File parcelRefFile, File parcelToCompareFile, File parcelOutFolder, double maxParcelSimulatedSize, double minParcelSimulatedSize, boolean overwrite) throws IOException {

        File fReal = new File(parcelOutFolder, "realParcel" + CollecMgmt.getDefaultGISFileType());
        File fSimulation = new File(parcelOutFolder, "simulation" + CollecMgmt.getDefaultGISFileType());
        if (!overwrite && fReal.exists() && fSimulation.exists()) {
            System.out.println("markDiffParcel(...) already calculated");
            return;
        }

        DataStore dsParcelToCompare = CollecMgmt.getDataStore(parcelToCompareFile);
        SimpleFeatureCollection parcelToCompare = dsParcelToCompare.getFeatureSource(dsParcelToCompare.getTypeNames()[0]).getFeatures();

        DataStore dsRef = CollecMgmt.getDataStore(parcelRefFile);
        SimpleFeatureCollection parcelRef = dsRef.getFeatureSource(dsRef.getTypeNames()[0]).getFeatures();
        SimpleFeatureCollection notSame = OpOnCollec.sortDiffGeom(parcelRefFile, parcelToCompareFile, parcelOutFolder, false, overwrite)[1];

        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        PropertyName pName = ff.property(parcelRef.getSchema().getGeometryDescriptor().getLocalName());

        // make a Collection of not same parcels with an inner buffer to select others
        List<Geometry> lInter = Arrays.stream(notSame.toArray(new SimpleFeature[0])).map(sf -> ((Geometry) sf.getDefaultGeometry())).collect(Collectors.toList());

        // isolate the compared parcels that have changed
        SimpleFeatureCollection realParcel = parcelToCompare.subCollection(ff.intersects(pName, ff.literal(Geom.unionGeom(lInter))));

        // We now seek if a large part of the real parcel stays intact and small parts. We can keep them or leave them
        List<Geometry> intersectionGeoms = new ArrayList<>();
        for (Geometry firstZone : Geom.importListGeom(notSame)) {
            Geometry firstZoneB = firstZone.buffer(-1);
            SimpleFeatureCollection parcelsReal = CollecTransform.selectIntersection(realParcel, firstZoneB);
            DescriptiveStatistics ds = new DescriptiveStatistics();
            Arrays.stream(parcelsReal.toArray(new SimpleFeature[0])).forEach(sf -> ds.addValue(((Geometry) sf.getDefaultGeometry()).getArea()));
            if (parcelsReal.isEmpty() || ds.getPercentile(50) > maxParcelSimulatedSize * 1.5 || ds.getPercentile(50) < minParcelSimulatedSize * 0.5
                    || (ds.getMean() > maxParcelSimulatedSize * 2 || ds.getMean() < minParcelSimulatedSize * 0.25)
                    || ((((Geometry) CollecTransform.getBiggestSF(parcelsReal).getDefaultGeometry()).getArea() > 0.8 * firstZone.getArea()) && firstZone.getArea() > 5 * maxParcelSimulatedSize)) // We skip parcels if the biggest "real" parcel represents 80% of the total zone area and is higher than 5x the max parcel size
                continue;
            intersectionGeoms.addAll(Arrays.stream(notSame.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry())
                    .filter(g -> g.intersects(firstZoneB)).collect(Collectors.toList()));
        }
        List<Geometry> listGeom = intersectionGeoms.stream().map(g -> g.buffer(-1)).collect(Collectors.toList());
        Geom.exportGeom(listGeom, fSimulation);
        CollecMgmt.exportSFC(CollecTransform.selectIntersection(realParcel, listGeom), fReal);
        dsParcelToCompare.dispose();
        dsRef.dispose();
    }

//	private static boolean sameCondition(Geometry g1, Geometry g2) {
//		HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
//		return hausDis.measure(g1, g2) > 0.92 && g1.getArea() > 0.92 * g2.getArea() && g1.getArea() < 1.08 * g2.getArea();
//	}
//	
//	private static boolean eq(Geometry g1, Geometry g2) {
//		return g1.buffer(0.1).contains(g2) && g2.buffer(0.1).contains(g1);
//	}

    /**
     * This algorithm merges parcels when they are under an area threshold. It seek the surrounding parcel that share the largest side with the small parcel and merge their
     * geometries. Parcel must touch at least. If no surrounding parcels are found touching (or intersecting) the small parcel, the parcel is deleted and left as a public space.
     * Attributes from the large parcel are kept.
     *
     * @param parcelsUnsorted   {@link SimpleFeatureCollection} to check every parcels
     * @param minimalParcelSize Threshold which parcels are under to be merged
     * @return The input {@link SimpleFeatureCollection} with small parcels merged or removed
     */
    public static SimpleFeatureCollection mergeTooSmallParcels(SimpleFeatureCollection parcelsUnsorted, double minimalParcelSize) {
        return mergeTooSmallParcels(parcelsUnsorted, minimalParcelSize, false);
    }

    /**
     * This algorithm merges parcels when they are under an area threshold. It seek the surrounding parcel that share the largest side with the small parcel and merge their
     * geometries. Parcel must touch at least. If no surrounding parcels are found touching (or intersecting) the small parcel, the parcel is deleted and left as a public space.
     * Attributes from the large parcel are kept.
     *
     * @param parcelsUnsorted   {@link SimpleFeatureCollection} to check every parcels
     * @param minimalParcelSize Threshold which parcels are under to be merged
     * @param bufferGeom        when we merge two geometries, do we buffer them with 1m and -1m ? Yes for geometries that could have a geometry precision reduction and can suffer from irregular edges. Angles and topology with other parcels could then be more or less blurry.
     * @return The input {@link SimpleFeatureCollection} with small parcels merged or removed
     */
    public static SimpleFeatureCollection mergeTooSmallParcels(SimpleFeatureCollection parcelsUnsorted, double minimalParcelSize, boolean bufferGeom) {
        List<Integer> sizeResults = new ArrayList<>();
        SimpleFeatureCollection result = recursiveMergeTooSmallParcel(parcelsUnsorted, minimalParcelSize, bufferGeom);
        sizeResults.add(result.size());
        do { // recursive application of the merge algorithm to merge little parcels to big ones one-by-one
            result = recursiveMergeTooSmallParcel(result, minimalParcelSize, bufferGeom);
            sizeResults.add(result.size());
        } // while parcels are still getting merged, we run the recursive algorithm
        while (!sizeResults.get(sizeResults.size() - 1).equals(sizeResults.get(sizeResults.size() - 2)));
        return result;
    }

    private static SimpleFeatureCollection recursiveMergeTooSmallParcel(SimpleFeatureCollection parcelsUnsorted, double minimalParcelSize, boolean bufferGeom) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // we sort the parcel collection to process the smallest parcels in first
        List<String> ids = new ArrayList<>();
        //easy hack to sort parcels by their size
        SortedMap<Double, SimpleFeature> index = new TreeMap<>();
        try (SimpleFeatureIterator itr = parcelsUnsorted.features()) {
            while (itr.hasNext()) {
                SimpleFeature feature = itr.next();
                //get the area an generate random numbers for the last 4 for out of 14 decimal. this hack is done to avoid exaclty same key area and delete some features
                index.put(((Geometry) feature.getDefaultGeometry()).getArea() + Math.random() / 1000000, feature);
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        for (Entry<Double, SimpleFeature> entry : index.entrySet()) {
            SimpleFeature feat = entry.getValue();
            // if the parcel has already been merged with a smaller one, we skip (and we made the hypotheses that a merged parcel will always be bigger than the threshold)
            if (ids.contains(feat.getID()))
                continue;
            Geometry geom = Polygons.getMultiPolygonGeom((Geometry) feat.getDefaultGeometry());
            if (geom.getArea() < minimalParcelSize) {
                // System.out.println(feat.getID() + " is too small");
                DefaultFeatureCollection intersect = new DefaultFeatureCollection();
                Arrays.stream(parcelsUnsorted.toArray(new SimpleFeature[0])).forEach(interParcel -> {
                    if (Geom.safeIntersect((Geometry) interParcel.getDefaultGeometry(), geom) && !interParcel.getID().equals(feat.getID()))
                        intersect.add(interParcel);
                });
                // if the small parcel is intersecting others and will be merge to them
                if (intersect.size() > 0) {
                    // System.out.println(intersect.size() + " intersecting");
                    // if the tiny parcel intersects a bigger parcel, we seek the longest side to which parcel could be incorporated
                    HashMap<String, Double> repart = new HashMap<>();
                    Arrays.stream(intersect.toArray(new SimpleFeature[0])).forEach(interParcel -> repart.put(interParcel.getID(),
                            Geom.safeIntersection(Arrays.asList((Geometry) interParcel.getDefaultGeometry(), geom.buffer(1)))
                                    .getArea()));
                    // we sort to place the biggest intersecting parcel in first
                    List<Entry<String, Double>> entryList = new ArrayList<>(repart.entrySet());
                    entryList.sort((obj1, obj2) -> obj2.getValue().compareTo(obj1.getValue()));
                    String idToMerge = entryList.get(0).getKey();
                    // if the big parcel has already been merged with a small parcel, we skip it and will return to that small parcel in a future iteration
                    if (ids.contains(idToMerge)) {
                        result.add(Schemas.setSFBSchemaWithMultiPolygon(feat).buildFeature(Attribute.makeUniqueId()));
                        continue;
                    }
                    ids.add(idToMerge);
                    // we now merge geometries and copy attributes to the new Feature
                    List<Geometry> lG = new ArrayList<>();
                    lG.add(geom);
                    SimpleFeatureBuilder build = Schemas.getSFBSchemaWithMultiPolygon(parcelsUnsorted.getSchema());
                    Arrays.stream(intersect.toArray(new SimpleFeature[0])).forEach(thaParcel -> {
                        if (thaParcel.getID().equals(idToMerge)) {
                            for (AttributeDescriptor attr : thaParcel.getFeatureType().getAttributeDescriptors()) {
                                if (attr.getLocalName().equals(CollecMgmt.getDefaultGeomName()))
                                    continue;
                                build.set(attr.getName(), thaParcel.getAttribute(attr.getName()));
                            }
                            lG.add(Polygons.getMultiPolygonGeom((Geometry) thaParcel.getDefaultGeometry()));
                        }
                    });
                    Geometry g;
                    try {
                        g = Geom.unionGeom(lG);
                        if (bufferGeom)
                            g = g.buffer(1).buffer(-1);
                    } catch (TopologyException tp) {
                        System.out.println("problem with +" + lG);
                        g = Geom.safeIntersection(lG);
                    }
                    build.set(CollecMgmt.getDefaultGeomName(), g);
                    SimpleFeature f = build.buildFeature(idToMerge);
                    result.add(f);
                }
                // no else - if the small parcel doesn't touch any other parcels, we left it as a blank space and will be left as a public space
            } else
                result.add(Schemas.setSFBSchemaWithMultiPolygon(feat).buildFeature(Attribute.makeUniqueId()));
        }
        return result;
    }

    /**
     * Add a given collection of parcels to another collection of parcel, for which the schema is kept.
     *
     * @param parcelIn  Parcels that receive the other parcels
     * @param parcelAdd Parcel to add
     * @return parcelIn {@link SimpleFeatureCollection} with added parcels
     * @deprecated
     */
    public static DefaultFeatureCollection addAllParcels(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelAdd) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        result.addAll(parcelIn);
        try (SimpleFeatureIterator parcelAddIt = parcelAdd.features()) {
            while (parcelAddIt.hasNext()) {
                SimpleFeature featAdd = parcelAddIt.next();
                SimpleFeatureBuilder fit = ArtiScalesSchemas.setSFBParcelAsASWithFeat(featAdd);
                result.add(fit.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return result;
    }


    // public static SimpleFeatureCollection completeParcelMissing(SimpleFeatureCollection parcelTot,
    // SimpleFeatureCollection parcelCuted)
    // throws NoSuchAuthorityCodeException, FactoryException {
    // DefaultFeatureCollection result = new DefaultFeatureCollection();
    // SimpleFeatureType schema = parcelTot.features().next().getFeatureType();
    // // result.addAll(parcelCuted);
    // SimpleFeatureIterator parcelCutedIt = parcelCuted.features();
    // try {
    // while (parcelCutedIt.hasNext()) {
    // SimpleFeature featCut = parcelCutedIt.next();
    // SimpleFeatureBuilder fit = GetFromGeom.setSFBParcelWithFeat(featCut, schema);
    // result.add(fit.buildFeature(null));
    // }
    // } catch (Exception problem) {
    // problem.printStackTrace();
    // } finally {
    // parcelCutedIt.close();
    // }
    //
    // SimpleFeatureIterator totIt = parcelTot.features();
    // try {
    // while (totIt.hasNext()) {
    // SimpleFeature featTot = totIt.next();
    // boolean add = true;
    // SimpleFeatureIterator cutIt = parcelCuted.features();
    // try {
    // while (cutIt.hasNext()) {
    // SimpleFeature featCut = cutIt.next();
    // if (((Geometry)
    // featTot.getDefaultGeometry()).buffer(0.1).contains(((Geometry)
    // featCut.getDefaultGeometry()))) {
    // add = false;
    // break;
    // }
    // }
    // } catch (Exception problem) {
    // problem.printStackTrace();
    // } finally {
    // cutIt.close();
    // }
    // if (add) {
    // SimpleFeatureBuilder fit = GetFromGeom.setSFBParcelWithFeat(featTot, schema);
    // result.add(fit.buildFeature(null));
    // }
    // }
    // } catch (Exception problem) {
    // problem.printStackTrace();
    // } finally {
    // totIt.close();
    // }
    //
    // return result;
    // }

//	/**
//	 * fix that
//	 * @param parcelToComplete
//	 * @param originalParcel
//	 * @return
//	 * @throws NoSuchAuthorityCodeException
//	 * @throws FactoryException
//	 * @throws IOException
//	 */
//	public static SimpleFeatureCollection completeParcelMissingWithOriginal(SimpleFeatureCollection parcelToComplete,
//			SimpleFeatureCollection originalParcel) throws NoSuchAuthorityCodeException, FactoryException, IOException {
//		DefaultFeatureCollection result = new DefaultFeatureCollection();
//		result.addAll(parcelToComplete);
//		// List<String> codeParcelAdded = new ArrayList<String>();
//
//		// SimpleFeatureType schema =
//		// parcelToComplete.features().next().getFeatureType();
//
//		// result.addAll(parcelCuted);
//
//		SimpleFeatureIterator parcelToCompletetIt = parcelToComplete.features();
//		try {
//			while (parcelToCompletetIt.hasNext()) {
//				SimpleFeature featToComplete = parcelToCompletetIt.next();
//				Geometry geomToComplete = (Geometry) featToComplete.getDefaultGeometry();
//				Geometry geomsOrigin = Vectors.unionSFC(Vectors.snapDatas(originalParcel, geomToComplete));
//				if (!geomsOrigin.buffer(1).contains(geomToComplete)) {
//					// System.out.println("this parcel has disapeard : " + geomToComplete);
//					// SimpleFeatureBuilder fit = FromGeom.setSFBParcelWithFeat(featToComplete,
//					// schema);
//					// result.add(fit.buildFeature(null));
//					// SimpleFeatureBuilder builder =
//					// FromGeom.setSFBOriginalParcelWithFeat(featToComplete, schema);
//					// result.add(builder.buildFeature(null));
//					// codeParcelAdded.add(ParcelFonction.makeParcelCode(featToComplete));
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			parcelToCompletetIt.close();
//		}
//
//		// SimpleFeatureIterator parcelOriginal = originalParcel.features();
//		// try {
//		// while (parcelOriginal.hasNext()) {
//		// SimpleFeature featOriginal = parcelOriginal.next();
//		// Geometry geom = (Geometry) featOriginal.getDefaultGeometry();
//		// Geometry geomToComplete =
//		// Vectors.unionSFC(Vectors.snapDatas(parcelToComplete, geom.buffer(10)));
//		// if (!geomToComplete.contains(geom.buffer(-1))) {
//		// System.out.println(geomToComplete);
//		// System.out.println();
//		// SimpleFeatureBuilder builder =
//		// FromGeom.setSFBOriginalParcelWithFeat(featOriginal, schema);
//		// result.add(builder.buildFeature(null));
//		// codeParcelAdded.add(ParcelFonction.makeParcelCode(featOriginal));
//		// }
//		// SimpleFeatureBuilder fit = FromGeom.setSFBParcelWithFeat(featOriginal,
//		// schema);
//		// result.add(fit.buildFeature(null));
//		// }
//		// } catch (Exception problem) {
//		// problem.printStackTrace();
//		// } finally {
//		// parcelOriginal.close();
//		// }
//
//		return result;
//	}
//
//    /**
//     * @return completed parcel collection
//     * @deprecated WARNING: NOT SURE IT'S WORKING
//     */
//    public static SimpleFeatureCollection completeParcelMissing(SimpleFeatureCollection parcelTot, SimpleFeatureCollection parcelCuted, List<String> parcelToNotAdd) throws IOException {
//        DefaultFeatureCollection result = new DefaultFeatureCollection();
//        SimpleFeatureType schema = parcelTot.features().next().getFeatureType();
//        // result.addAll(parcelCuted);
//        try (SimpleFeatureIterator parcelCutedIt = parcelCuted.features()) {
//            while (parcelCutedIt.hasNext()) {
//                SimpleFeature featCut = parcelCutedIt.next();
//                SimpleFeatureBuilder fit = ArtiScalesSchemas.setSFBParcelAsASWithFeat(featCut, schema);
//                result.add(fit.buildFeature(null));
//            }
//        } catch (Exception problem) {
//            problem.printStackTrace();
//        }
//        try (SimpleFeatureIterator totIt = parcelTot.features()) {
//            while (totIt.hasNext()) {
//                SimpleFeature featTot = totIt.next();
//                boolean add = true;
//                for (String code : parcelToNotAdd) {
//                    if (featTot.getAttribute("CODE").equals(code)) {
//                        add = false;
//                        break;
//                    }
//                }
//                if (add) {
//                    SimpleFeatureBuilder fit = ArtiScalesSchemas.setSFBParcelAsASWithFeat(featTot, schema);
//                    result.add(fit.buildFeature(null));
//                }
//            }
//        } catch (Exception problem) {
//            problem.printStackTrace();
//        }
//        return result.collection();
//    }

    /**
     * Sort a parcel collection by the feature's sizes in two collections : the ones that are less a threshold and the ones that are above that threshold
     *
     * @param parcelIn input parcel collection
     * @param size     area of the threshold to sort the parcels
     * @return a pair with parcel smaller than the threshold at left and higher than threshold at right
     */
    public static Pair<SimpleFeatureCollection, SimpleFeatureCollection> sortParcelsBySize(SimpleFeatureCollection parcelIn, double size) {
        DefaultFeatureCollection less = new DefaultFeatureCollection();
        DefaultFeatureCollection more = new DefaultFeatureCollection();
        Arrays.stream(parcelIn.toArray(new SimpleFeature[0])).forEach(feat -> {
            if (((Geometry) feat.getDefaultGeometry()).getArea() >= size)
                more.add(feat);
            else
                less.add(feat);
        });
        return new ImmutablePair<>(less, more);
    }

//    /**
//     * @return A LIST
//     * @deprecated WARNING not tested (maybe not needed)
//     */
//    public static List<String> dontAddParcel(List<String> parcelToNotAdd, SimpleFeatureCollection bigZoned) {
//        try (SimpleFeatureIterator feat = bigZoned.features()) {
//            while (feat.hasNext())
//                parcelToNotAdd.add((String) feat.next().getAttribute("CODE"));
//        } catch (Exception problem) {
//            problem.printStackTrace();
//        }
//        return parcelToNotAdd;
//    }
//
//
//	/**
//	 * This method recursively add geometries to a solo one if they touch each other not sure this is working
//	 * 
//	 * not safe at work
//	 * 
//	 * @param geomIn
//	 * @param df
//	 * @return
//	 * @throws IOException
//	 */
//	public Geometry mergeIfTouch(Geometry geomIn, DefaultFeatureCollection df) throws IOException {
//		DefaultFeatureCollection result = new DefaultFeatureCollection();
//		result.addAll(df.collection());
//
//		SimpleFeatureIterator features = df.features();
//
//		Geometry aggreg = geomIn;
//
//		try {
//			while (features.hasNext()) {
//				SimpleFeature f = features.next();
//				Geometry geomTemp = (((Geometry) f.getDefaultGeometry()));
//				if (geomIn.intersects(geomTemp) && !geomIn.equals(geomTemp)) {
//					result.remove(f);
//					aggreg = Geom.unionGeom(geomIn, geomTemp);
//					aggreg = mergeIfTouch(aggreg, result);
//					break;
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			features.close();
//		}
//		return aggreg;
//
//	}
}
