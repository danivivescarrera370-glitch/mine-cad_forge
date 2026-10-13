package name.minecad.client.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import name.minecad.client.scannedblock.ScannedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SelectionExporter {
    private SelectionExporter() {
    }

    public enum Format {
        OBJ("obj"),
        STL("stl"),
        FBX("fbx");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        public static Format parse(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("Missing format. Use obj, stl, or fbx.");
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "obj" -> OBJ;
                case "stl" -> STL;
                case "fbx" -> FBX;
                default -> throw new IllegalArgumentException("Unsupported export format: " + raw + ". Use obj, stl, or fbx.");
            };
        }
    }

    public record ExportResult(boolean success, String message, Path outputPath, int exportedFaces) {
    }

    public static ExportResult export(Level level,
                                      List<ScannedBlock> blocks,
                                      Format format,
                                      Path outputFile,
                                      double metersPerBlock) {
        if (level == null) {
            return new ExportResult(false, "No world is loaded for export.", null, 0);
        }
        if (blocks == null || blocks.isEmpty()) {
            return new ExportResult(false, "No blocks selected to export.", null, 0);
        }
        if (format == null) {
            return new ExportResult(false, "No export format provided.", null, 0);
        }
        if (outputFile == null) {
            return new ExportResult(false, "No output file path provided.", null, 0);
        }
        if (!Double.isFinite(metersPerBlock) || metersPerBlock <= 0.0D) {
            return new ExportResult(false, "Invalid scale. Meters per block must be a positive number.", null, 0);
        }

        MeshData meshData = buildMeshData(level, blocks, metersPerBlock);
        if (meshData.faces().isEmpty()) {
            return new ExportResult(false, "Selection has no visible block faces to export.", null, 0);
        }

        String serialized;
        try {
            serialized = switch (format) {
                case OBJ -> writeObj(meshData, outputFile.getFileName().toString());
                case STL -> writeStl(meshData, outputFile.getFileName().toString());
                case FBX -> writeFbx(meshData, outputFile.getFileName().toString());
            };
        } catch (RuntimeException e) {
            return new ExportResult(false, "Failed to build " + format.name() + " data: " + e.getMessage(), null, 0);
        }

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputFile, serialized);
            return new ExportResult(true,
                    "Exported " + blocks.size() + " block(s), " + meshData.faces().size() + " mesh face(s) to " + outputFile.getFileName(),
                    outputFile,
                    meshData.faces().size());
        } catch (IOException e) {
            return new ExportResult(false, "Failed to write export file: " + e.getMessage(), null, 0);
        }
    }

    private static MeshData buildMeshData(Level level, List<ScannedBlock> blocks, double scale) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        List<BlockPos> positions = new ArrayList<>(blocks.size());

        for (ScannedBlock block : blocks) {
            if (block == null || block.pos == null) {
                continue;
            }
            BlockPos pos = block.pos;
            positions.add(pos);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }

        if (positions.isEmpty()) {
            return new MeshData(List.of());
        }

        List<FaceInstance> faces = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getShape(level, pos);
            List<AABB> boxes = shape.isEmpty() ? List.of(new AABB(0, 0, 0, 1, 1, 1)) : shape.toAabbs();

            double baseX = (pos.getX() - minX) * scale;
            double baseY = (pos.getY() - minY) * scale;
            double baseZ = (pos.getZ() - minZ) * scale;

            for (AABB box : boxes) {
                addBoxFaces(
                        faces,
                        baseX + box.minX * scale,
                        baseY + box.minY * scale,
                        baseZ + box.minZ * scale,
                        baseX + box.maxX * scale,
                        baseY + box.maxY * scale,
                        baseZ + box.maxZ * scale
                );
            }
        }
        return new MeshData(faces);
    }

    private static void addBoxFaces(List<FaceInstance> faces,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
            return;
        }

        // -X
        faces.add(new FaceInstance(
                new Vec3(minX, minY, minZ),
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(-1, 0, 0)
        ));
        // +X
        faces.add(new FaceInstance(
                new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(1, 0, 0)
        ));
        // -Y
        faces.add(new FaceInstance(
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, minY, minZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(0, -1, 0)
        ));
        // +Y
        faces.add(new FaceInstance(
                new Vec3(minX, maxY, minZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(0, 1, 0)
        ));
        // -Z
        faces.add(new FaceInstance(
                new Vec3(maxX, minY, minZ),
                new Vec3(minX, minY, minZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(0, 0, -1)
        ));
        // +Z
        faces.add(new FaceInstance(
                new Vec3(minX, minY, maxZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(0, 0, 1)
        ));
    }

    private static String writeObj(MeshData meshData, String meshName) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MineCad selection export\n");
        sb.append("# Units: meters (1 unit = 1 m)\n");
        sb.append("o ").append(sanitizeMeshName(meshName)).append('\n');

        int vertexIndex = 1;
        for (FaceInstance face : meshData.faces()) {
            appendObjVertex(sb, face.v0());
            appendObjVertex(sb, face.v1());
            appendObjVertex(sb, face.v2());
            appendObjVertex(sb, face.v3());
            sb.append("f ")
                    .append(vertexIndex).append(' ')
                    .append(vertexIndex + 1).append(' ')
                    .append(vertexIndex + 2).append(' ')
                    .append(vertexIndex + 3).append('\n');
            vertexIndex += 4;
        }

        return sb.toString();
    }

    private static void appendObjVertex(StringBuilder sb, Vec3 vertex) {
        sb.append("v ")
                .append(formatDouble(vertex.x())).append(' ')
                .append(formatDouble(vertex.y())).append(' ')
                .append(formatDouble(vertex.z())).append('\n');
    }

    private static String writeStl(MeshData meshData, String meshName) {
        String name = sanitizeMeshName(meshName);
        StringBuilder sb = new StringBuilder();
        sb.append("solid ").append(name).append('\n');

        for (FaceInstance face : meshData.faces()) {
            appendStlFacet(sb, face.normal(), face.v0(), face.v1(), face.v2());
            appendStlFacet(sb, face.normal(), face.v0(), face.v2(), face.v3());
        }

        sb.append("endsolid ").append(name).append('\n');
        return sb.toString();
    }

    private static void appendStlFacet(StringBuilder sb, Vec3 normal, Vec3 a, Vec3 b, Vec3 c) {
        sb.append("  facet normal ")
                .append(formatDouble(normal.x())).append(' ')
                .append(formatDouble(normal.y())).append(' ')
                .append(formatDouble(normal.z())).append('\n');
        sb.append("    outer loop\n");
        appendStlVertex(sb, a);
        appendStlVertex(sb, b);
        appendStlVertex(sb, c);
        sb.append("    endloop\n");
        sb.append("  endfacet\n");
    }

    private static void appendStlVertex(StringBuilder sb, Vec3 vertex) {
        // STL has no unit field and slicers (Cura, PrusaSlicer...) read values as millimetres,
        // so the metre-based mesh is written in millimetres.
        sb.append("      vertex ")
                .append(formatDouble(vertex.x() * 1000.0D)).append(' ')
                .append(formatDouble(vertex.y() * 1000.0D)).append(' ')
                .append(formatDouble(vertex.z() * 1000.0D)).append('\n');
    }

    private static String writeFbx(MeshData meshData, String meshName) {
        List<Double> vertices = new ArrayList<>();
        List<Integer> polygonVertexIndex = new ArrayList<>();
        List<Double> normals = new ArrayList<>();

        int nextVertexIndex = 0;
        for (FaceInstance face : meshData.faces()) {
            addVertex(vertices, face.v0());
            addVertex(vertices, face.v1());
            addVertex(vertices, face.v2());
            addVertex(vertices, face.v3());

            polygonVertexIndex.add(nextVertexIndex);
            polygonVertexIndex.add(nextVertexIndex + 1);
            polygonVertexIndex.add(nextVertexIndex + 2);
            polygonVertexIndex.add(-(nextVertexIndex + 4));
            nextVertexIndex += 4;

            for (int i = 0; i < 4; i++) {
                normals.add(face.normal().x());
                normals.add(face.normal().y());
                normals.add(face.normal().z());
            }
        }

        StringBuilder sb = new StringBuilder();
        String safeName = sanitizeMeshName(meshName);

        sb.append("; FBX 7.4.0 project file\n");
        sb.append("FBXHeaderExtension:  {\n");
        sb.append("  FBXHeaderVersion: 1003\n");
        sb.append("  FBXVersion: 7400\n");
        sb.append("}\n");
        sb.append("GlobalSettings:  {\n");
        sb.append("  Version: 1000\n");
        sb.append("  Properties70:  {\n");
        sb.append("    P: \"UpAxis\", \"int\", \"Integer\", \"\",1\n");
        sb.append("    P: \"UpAxisSign\", \"int\", \"Integer\", \"\",1\n");
        sb.append("    P: \"FrontAxis\", \"int\", \"Integer\", \"\",2\n");
        sb.append("    P: \"FrontAxisSign\", \"int\", \"Integer\", \"\",1\n");
        sb.append("    P: \"CoordAxis\", \"int\", \"Integer\", \"\",0\n");
        sb.append("    P: \"CoordAxisSign\", \"int\", \"Integer\", \"\",1\n");
        // Geometry is in metres; FBX unit scale factor is centimetres per unit, so 1 unit = 100 cm.
        sb.append("    P: \"UnitScaleFactor\", \"double\", \"Number\", \"\",100\n");
        sb.append("    P: \"OriginalUnitScaleFactor\", \"double\", \"Number\", \"\",100\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("Objects:  {\n");
        sb.append("  Geometry: 1, \"Geometry::").append(safeName).append("\", \"Mesh\" {\n");
        sb.append("    Vertices: *").append(vertices.size()).append(" {\n");
        sb.append("      a: ").append(joinDoubles(vertices)).append('\n');
        sb.append("    }\n");
        sb.append("    PolygonVertexIndex: *").append(polygonVertexIndex.size()).append(" {\n");
        sb.append("      a: ").append(joinInts(polygonVertexIndex)).append('\n');
        sb.append("    }\n");
        sb.append("    GeometryVersion: 124\n");
        sb.append("    LayerElementNormal: 0 {\n");
        sb.append("      Version: 101\n");
        sb.append("      Name: \"\"\n");
        sb.append("      MappingInformationType: \"ByPolygonVertex\"\n");
        sb.append("      ReferenceInformationType: \"Direct\"\n");
        sb.append("      Normals: *").append(normals.size()).append(" {\n");
        sb.append("        a: ").append(joinDoubles(normals)).append('\n');
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    LayerElementMaterial: 0 {\n");
        sb.append("      Version: 101\n");
        sb.append("      Name: \"\"\n");
        sb.append("      MappingInformationType: \"AllSame\"\n");
        sb.append("      ReferenceInformationType: \"IndexToDirect\"\n");
        sb.append("      Materials: *1 {\n");
        sb.append("        a: 0\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    Layer: 0 {\n");
        sb.append("      Version: 100\n");
        sb.append("      LayerElement:  {\n");
        sb.append("        Type: \"LayerElementNormal\"\n");
        sb.append("        TypedIndex: 0\n");
        sb.append("      }\n");
        sb.append("      LayerElement:  {\n");
        sb.append("        Type: \"LayerElementMaterial\"\n");
        sb.append("        TypedIndex: 0\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("  Model: 2, \"Model::").append(safeName).append("\", \"Mesh\" {\n");
        sb.append("    Version: 232\n");
        sb.append("    Properties70:  {\n");
        sb.append("      P: \"InheritType\", \"enum\", \"\", \"\",1\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("  Material: 3, \"Material::MineCadMaterial\", \"\" {\n");
        sb.append("    Version: 102\n");
        sb.append("    ShadingModel: \"phong\"\n");
        sb.append("    MultiLayer: 0\n");
        sb.append("    Properties70:  {\n");
        sb.append("      P: \"DiffuseColor\", \"Color\", \"\", \"A\",0.7,0.7,0.7\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("Connections:  {\n");
        sb.append("  C: \"OO\",1,0\n");
        sb.append("  C: \"OO\",2,0\n");
        sb.append("  C: \"OO\",2,1\n");
        sb.append("  C: \"OO\",3,2\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static void addVertex(List<Double> vertices, Vec3 vertex) {
        vertices.add(vertex.x());
        vertices.add(vertex.y());
        vertices.add(vertex.z());
    }

    private static String joinDoubles(List<Double> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatDouble(values.get(i)));
        }
        return sb.toString();
    }

    private static String joinInts(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static String sanitizeMeshName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "selection";
        }
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private record MeshData(List<FaceInstance> faces) {
    }


    private record FaceInstance(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 normal) {
    }

    private record Vec3(double x, double y, double z) {
    }
}

