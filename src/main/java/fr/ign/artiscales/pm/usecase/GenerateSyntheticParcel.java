package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.division.Division;
import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.parcel.SyntheticParcel;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.indicator.Dispertion;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class GenerateSyntheticParcel {

    public static boolean DEBUG = false;

    public static void main(String[] args) {
        DEBUG = true;
        generate(12, 0.8, 150, 0.01f, 42, new File("/tmp/p.gpkg"));
        generate(12, 0.8, 150, 0.01f, 42, new File("/tmp/p2.gpkg"));
    }

    /**
     * @param nbOwner               number of owners in the simulation
     * @param giniObjective         objective for the gini value of the distribution of parcel's area sums owned by single owners
     * @param approxNumberOfParcels very approximate number of parcels in the zone (will automatically be more - could be double). The more they are, the best the gini value can be reached
     * @param tolerence             difference that the distribution can have between the objective gini value and the effective gini value
     * @param exportFile            if not null, write the parcels in a geopackage
     * @return Parcels with attributes
     */
    public static List<SyntheticParcel> generate(int nbOwner, double giniObjective, int approxNumberOfParcels, float tolerence, long seed, File exportFile) {
        Division.setSeed(seed);
        Geometry iniZone = createInitialZone();
        assert iniZone != null;
        double maximalArea = iniZone.getArea() / approxNumberOfParcels;
        HashMap<Integer, Geometry> regionIDS = new HashMap<>();
        int i = 1;
        List<SyntheticParcel> lSP = new ArrayList<>();
        for (Polygon subRegion : Polygons.getPolygons(iniZone)) {
            List<Polygon> lP = new ArrayList<>();
            lP.addAll(OBBDivision.decompose(subRegion, Lines.getLineStrings(iniZone), null, maximalArea,
                    0, 0.5, 0.5,
                    0, 0, 0, false, 0, 0
//                    10, 2, 20, false, 2, 0
            ).stream().map(Pair::getLeft).collect(Collectors.toList()));
            //dummy task to remove initial polygon which is returned by the previous method
            lP.remove(lP.stream().filter(p -> p.getArea() == subRegion.getArea()).findFirst().get());
            regionIDS.put(i++, subRegion);
            List<SyntheticParcel> lSPsubregion = new ArrayList<>();
            for (Polygon p : lP)
                lSPsubregion.add(new SyntheticParcel(p, p.getArea(), p.distance(iniZone.getCentroid()), ParcelState.countParcelNeighborhood(p, lP), 0,
                        regionIDS.keySet().stream().filter(regionID -> regionIDS.get(regionID).buffer(1).contains(p)).findFirst().get()));

            // initialize parcel ownership : everybody must and will have at least a parcel in every subregion
            if (!initializeOwnership(lSPsubregion, nbOwner))
                return null;
            lSP.addAll(lSPsubregion);
        }

        double currentGini = Dispertion.gini(lSP.stream().map(sp -> sp.area).collect(Collectors.toList()));
        int tentatives = 0;
        while (Math.abs(currentGini - giniObjective) > tolerence && tentatives < 1000000) {
            List<SyntheticParcel> newLSP = correctOwnership(lSP, nbOwner);
            if (Math.abs(Dispertion.gini(SyntheticParcel.sumOwnerOwnedArea(newLSP)) - giniObjective) <
                    Math.abs(Dispertion.gini(SyntheticParcel.sumOwnerOwnedArea(lSP)) - giniObjective)) {
                lSP = newLSP;
                currentGini = Dispertion.gini(SyntheticParcel.sumOwnerOwnedArea(lSP));
            }
            tentatives++;
        }
        if (tentatives == 1000000) {
            if (DEBUG)
                System.out.println("gini unreachable. change parameters. return null");
            return null;
        }

        //set parcel neighborhood number
        for (SyntheticParcel sp : lSP)
            sp.setIdNeighborhood(lSP);

        // not really needed infos
        if (DEBUG)
            System.out.println("final gini for parcels : " + Dispertion.gini(SyntheticParcel.sumOwnerOwnedArea(lSP)));
        if (exportFile != null)
            try {
                SyntheticParcel.exportToGPKG(lSP, exportFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        return lSP;
    }

    private static boolean initializeOwnership(List<SyntheticParcel> lSP, int nbOwner) {
        int iteration = 0;
        do {
            for (SyntheticParcel sp : lSP)
                sp.ownerID = getRandomNumberInRange(1, nbOwner);
            iteration++;
        } while (lSP.stream().map(sp -> sp.ownerID).distinct().count() != nbOwner && iteration < 10000);
        if (iteration == 10000 && DEBUG)
            System.out.println("Cannot intiate ownership (too much owner ?). Return null");
        return iteration != 10000;
    }

    private static List<SyntheticParcel> correctOwnership(List<SyntheticParcel> lSP, int nbOwner) {
        ArrayList<SyntheticParcel> newList = new ArrayList<>(lSP.size());
        for (SyntheticParcel sp : lSP) //clone
            newList.add(new SyntheticParcel(sp.geom, sp.area, sp.distanceToCenter, sp.nbNeighborhood, sp.ownerID, sp.regionID));
        do
            newList.get(getRandomNumberInRange(0, newList.size() - 1)).ownerID = getRandomNumberInRange(1, nbOwner);
        while (newList.stream().map(sp -> sp.ownerID).distinct().count() != nbOwner);
        return newList;
    }

    public static int getRandomNumberInRange(int min, int max) {
        if (min >= max)
            throw new IllegalArgumentException("max must be greater than min");
        return Division.getRandom().nextInt((max - min) + 1) + min;
    }

    public static Geometry createInitialZone() {
        try {
            return new WKTReader2().read("MultiPolygon (((0 0, 1000 0, 500 333, 0 0)),((0 0, 500 1000, 500 333,0 0)),((500 1000, 500 333, 1000 0, 500 1000)))");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}


