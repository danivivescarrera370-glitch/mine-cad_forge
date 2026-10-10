package name.minecad.stl;

import name.minecad.stl.create.FacetModel;

public class STL {

    private String stlName;
    private String stlDescription = "Default STL Description"; // Default description
    private byte[] stlHeader; // Default STL header, can be modified later
    private byte[] numberOfFacets; // Default number of facets, can be modified later
    private FacetModel[] faceitModel; // This will hold the STL data, initialized as an empty list
    /**
     * Initializes a new instance of the STL class with the specified name.
     * @param stlName: The name of the STL. This can be any string that represents the STL.
     */
    public STL(String stlName, String stlDescription, int stlHeaderSize, int numberOfFacetsSize) {
        this.stlName = stlName;
        this.stlDescription = stlDescription;
        this.stlHeader = new byte[stlHeaderSize];
        this.numberOfFacets = new byte[numberOfFacetsSize];
        this.faceitModel = new FacetModel[numberOfFacetsSize];
        for (int i = 0; i < numberOfFacetsSize; i++) {
            this.faceitModel[i] = new FacetModel();
        }
    }

    public void setStlDescription(String stlDescription) {
        this.stlDescription = stlDescription;
    }

    public String getStlName() {
        return this.stlName;
    }

    public void setStlName(String newStlName) {
        this.stlName = newStlName;
        System.out.println("STL name changed to: " + newStlName);
    }

    public void createSTL(){

    }

//    public void createSTLFile(Path filePath) {
//        if (filePath == null || !Files.exists(filePath)) {
//            throw new IllegalArgumentException("Invalid file path provided for STL file.");
//        }
//        this.stlFilePath = filePath;
//        System.out.println("STL file created at: " + filePath.toString());
//    }

    // Additional methods can be added here as needed.
}
