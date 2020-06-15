# Parcel Manager

Library providing functions, algorithms and workflows to operate various parcel reshaping operations.
Documentation can be found on a coming article and on the PhD thesis of Maxime Colomb.
The easiest way of using this library is to create scenarios using .json files ([see here](https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md)).
Based on the function from the library [ArtiScales-tools](https://github.com/ArtiScales/ArtiScales-tools) (no stable version yet) and on the GeoTools 21.0 library.
JavaDoc is available [here](https://artiscales.github.io/javadoc/ParcelManager/).

Still under developpement.

## Description of packages

<ul>
<li><b>analysis</b>: Contains automated analysis tools to analyze urban fabrics and produce tables and graphs.</li>
<li><b>decomposition</b>: Contains the different decomposition algorithms.</li>
<li><b>fields</b>: Contains methods and tools to automatically set feature informations on the generated parcels. Solutions for french parcels types and ArtiScales parcels types are implemented.</li>
<li><b>goal</b>: Contains the different goals for the parcel manager process. Three solutions are now implemented: 
    <ul>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/goal/ZoneDivision.java"><i>ZoneDivision</i></a>: Run a parcel division algorithm on an input zone</li>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/goal/ConsolidationDivision.java"><i>ConsolidationDivision</i></a>: Merges predesigned parcel together and run a decomposition task</li>
        <li><a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/java/fr/ign/artiscales/goal/Densification.java"><i>Densification</i></a>: Run the densification process on the predesigned parcels</li>
    </ul>
</li>
<li><b>parcelFunction</b>: Various functions for single parcels, sort by type (collection, state, schemas, attribute, marking, etc.).</li>
<li><b>scenario</b>: Contains objects used for processing an automated simulation on a large zone (<a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">technical description can be found here</a>).</li>
<li><b>scenario</b>: Contains automated workflows for predefined usages of Parcel Manager</li>
</ul>
