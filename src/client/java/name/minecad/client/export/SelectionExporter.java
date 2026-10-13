package name.minecad.client.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import name.minecad.client.scannedblock.ScannedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Exports the selection to OBJ / STL / FBX. Faces are generated lazily and streamed straight to disk through a
 * buffered writer, so memory use stays flat regardless of selection size.
 */
public final class SelectionExporter {
    private static final int WRITE_BUFFER_CHARS = 1 << 16;

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

        MeshSource source = MeshSource.create(level, blocks, metersPerBlock);
        final int faceCount;
        try {
            faceCount = source.countFaces();
        } catch (IOException e) {
            return new ExportResult(false, "Failed to build mesh: " + e.getMessage(), null, 0);
        }
        if (faceCount == 0) {
            return new ExportResult(false, "Selection has no visible block faces to export.", null, 0);
        }

        Path tempFile = null;
        try {
            Path parent = outputFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            tempFile = Files.createTempFile(parent, "minecad-export-", ".tmp");

            String meshName = outputFile.getFileName().toString();
            try (Writer writer = new BufferedWriter(
                    Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8), WRITE_BUFFER_CHARS)) {
                switch (format) {
                    case OBJ -> writeObj(writer, source, meshName);
                    case STL -> writeStl(writer, source, meshName);
                    case FBX -> writeFbx(writer, source, meshName, faceCount);
                }
            }

            Files.move(tempFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile = null;
            return new ExportResult(true,
                    "Exported " + blocks.size() + " block(s), " + faceCount + " mesh face(s) to " + outputFile.getFileName(),
                    outputFile,
                    faceCount);
        } catch (IOException | RuntimeException e) {
            return new ExportResult(false, "Failed to write export file: " + e.getMessage(), null, 0);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    // ------------------------------------------------------------------ mesh source (lazy)

    @FunctionalInterface
    private interface FaceConsumer {
        void accept(FaceInstance face) throws IOException;
    }

    private record MeshSource(Level level, List<BlockPos> positions, int minX, int minY, int minZ, double scale) {
        static MeshSource create(Level level, List<ScannedBlock> blocks, double scale) {
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
            return new MeshSource(level, positions, minX, minY, minZ, scale);
        }

        int countFaces() throws IOException {
            int[] count = {0};
            forEachFace(face -> count[0]++);
            return count[0];
        }

        void forEachFace(FaceConsumer consumer) throws IOException {
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
                    emitBoxFaces(consumer,
                            baseX + box.minX * scale,
                            baseY + box.minY * scale,
                            baseZ + box.minZ * scale,
                            baseX + box.maxX * scale,
                            baseY + box.maxY * scale,
                            baseZ + box.maxZ * scale);
                }
            }
        }
    }

    private static void emitBoxFaces(FaceConsumer out,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) throws IOException {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
            return;
        }

        // -X
        out.accept(new FaceInstance(
                new Vec3(minX, minY, minZ),
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(-1, 0, 0)));
        // +X
        out.accept(new FaceInstance(
                new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(1, 0, 0)));
        // -Y
        out.accept(new FaceInstance(
                new Vec3(minX, minY, maxZ),
                new Vec3(minX, minY, minZ),
                new Vec3(maxX, minY, minZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(0, -1, 0)));
        // +Y
        out.accept(new FaceInstance(
                new Vec3(minX, maxY, minZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(0, 1, 0)));
        // -Z
        out.accept(new FaceInstance(
                new Vec3(maxX, minY, minZ),
                new Vec3(minX, minY, minZ),
                new Vec3(minX, maxY, minZ),
                new Vec3(maxX, maxY, minZ),
                new Vec3(0, 0, -1)));
        // +Z
        out.accept(new FaceInstance(
                new Vec3(minX, minY, maxZ),
                new Vec3(maxX, minY, maxZ),
                new Vec3(maxX, maxY, maxZ),
                new Vec3(minX, maxY, maxZ),
                new Vec3(0, 0, 1)));
    }

    // ------------------------------------------------------------------ OBJ

    private static void writeObj(Writer w, MeshSource source, String meshName) throws IOException {
        w.write("# MineCad selection export\n");
        w.write("# Units: meters (1 unit = 1 m)\n");
        w.write("o " + sanitizeMeshName(meshName) + "\n");

        int[] vertexIndex = {1};
        source.forEachFace(face -> {
            writeObjVertex(w, face.v0());
            writeObjVertex(w, face.v1());
            writeObjVertex(w, face.v2());
            writeObjVertex(w, face.v3());
            int i = vertexIndex[0];
            w.write("f " + i + " " + (i + 1) + " " + (i + 2) + " " + (i + 3) + "\n");
            vertexIndex[0] += 4;
        });
    }

    private static void writeObjVertex(Writer w, Vec3 v) throws IOException {
        w.write("v " + formatDouble(v.x()) + " " + formatDouble(v.y()) + " " + formatDouble(v.z()) + "\n");
    }

    // ------------------------------------------------------------------ STL

    private static void writeStl(Writer w, MeshSource source, String meshName) throws IOException {
        String name = sanitizeMeshName(meshName);
        w.write("solid " + name + "\n");
        source.forEachFace(face -> {
            writeStlFacet(w, face.normal(), face.v0(), face.v1(), face.v2());
            writeStlFacet(w, face.normal(), face.v0(), face.v2(), face.v3());
        });
        w.write("endsolid " + name + "\n");
    }

    private static void writeStlFacet(Writer w, Vec3 normal, Vec3 a, Vec3 b, Vec3 c) throws IOException {
        w.write("  facet normal " + formatDouble(normal.x()) + " " + formatDouble(normal.y()) + " "
                + formatDouble(normal.z()) + "\n");
        w.write("    outer loop\n");
        writeStlVertex(w, a);
        writeStlVertex(w, b);
        writeStlVertex(w, c);
        w.write("    endloop\n");
        w.write("  endfacet\n");
    }

    private static void writeStlVertex(Writer w, Vec3 v) throws IOException {
        // STL has no unit field and slicers (Cura, PrusaSlicer...) read values as millimetres,
        // so the metre-based mesh is written in millimetres.
        w.write("      vertex " + formatDouble(v.x() * 1000.0D) + " " + formatDouble(v.y() * 1000.0D) + " "
                + formatDouble(v.z() * 1000.0D) + "\n");
    }

    // ------------------------------------------------------------------ FBX

    private static void writeFbx(Writer w, MeshSource source, String meshName, int faceCount) throws IOException {
        String safeName = sanitizeMeshName(meshName);
        long vertexValues = (long) faceCount * 12L;
        long indexValues = (long) faceCount * 4L;
        long normalValues = (long) faceCount * 12L;

        w.write("; FBX 7.4.0 project file\n");
        w.write("FBXHeaderExtension:  {\n");
        w.write("  FBXHeaderVersion: 1003\n");
        w.write("  FBXVersion: 7400\n");
        w.write("}\n");
        w.write("GlobalSettings:  {\n");
        w.write("  Version: 1000\n");
        w.write("  Properties70:  {\n");
        w.write("    P: \"UpAxis\", \"int\", \"Integer\", \"\",1\n");
        w.write("    P: \"UpAxisSign\", \"int\", \"Integer\", \"\",1\n");
        w.write("    P: \"FrontAxis\", \"int\", \"Integer\", \"\",2\n");
        w.write("    P: \"FrontAxisSign\", \"int\", \"Integer\", \"\",1\n");
        w.write("    P: \"CoordAxis\", \"int\", \"Integer\", \"\",0\n");
        w.write("    P: \"CoordAxisSign\", \"int\", \"Integer\", \"\",1\n");
        // Geometry is in metres; FBX unit scale factor is centimetres per unit, so 1 unit = 100 cm.
        w.write("    P: \"UnitScaleFactor\", \"double\", \"Number\", \"\",100\n");
        w.write("    P: \"OriginalUnitScaleFactor\", \"double\", \"Number\", \"\",100\n");
        w.write("  }\n");
        w.write("}\n");
        w.write("Objects:  {\n");
        w.write("  Geometry: 1, \"Geometry::" + safeName + "\", \"Mesh\" {\n");

        // Vertices (pass 1)
        w.write("    Vertices: *" + vertexValues + " {\n");
        w.write("      a: ");
        boolean[] first = {true};
        source.forEachFace(face -> {
            writeFbxVertex(w, face.v0(), first);
            writeFbxVertex(w, face.v1(), first);
            writeFbxVertex(w, face.v2(), first);
            writeFbxVertex(w, face.v3(), first);
        });
        w.write("\n    }\n");

        // Polygon indices are purely arithmetic - no need to touch the world again.
        w.write("    PolygonVertexIndex: *" + indexValues + " {\n");
        w.write("      a: ");
        for (int face = 0; face < faceCount; face++) {
            long base = (long) face * 4L;
            if (face > 0) {
                w.write(",");
            }
            w.write(base + "," + (base + 1) + "," + (base + 2) + "," + (-(base + 3) - 1));
        }
        w.write("\n    }\n");

        w.write("    GeometryVersion: 124\n");
        w.write("    LayerElementNormal: 0 {\n");
        w.write("      Version: 101\n");
        w.write("      Name: \"\"\n");
        w.write("      MappingInformationType: \"ByPolygonVertex\"\n");
        w.write("      ReferenceInformationType: \"Direct\"\n");

        // Normals (pass 2)
        w.write("      Normals: *" + normalValues + " {\n");
        w.write("        a: ");
        first[0] = true;
        source.forEachFace(face -> {
            String n = formatDouble(face.normal().x()) + "," + formatDouble(face.normal().y()) + ","
                    + formatDouble(face.normal().z());
            for (int i = 0; i < 4; i++) {
                if (!first[0]) {
                    w.write(",");
                }
                first[0] = false;
                w.write(n);
            }
        });
        w.write("\n      }\n");
        w.write("    }\n");
        w.write("    LayerElementMaterial: 0 {\n");
        w.write("      Version: 101\n");
        w.write("      Name: \"\"\n");
        w.write("      MappingInformationType: \"AllSame\"\n");
        w.write("      ReferenceInformationType: \"IndexToDirect\"\n");
        w.write("      Materials: *1 {\n");
        w.write("        a: 0\n");
        w.write("      }\n");
        w.write("    }\n");
        w.write("    Layer: 0 {\n");
        w.write("      Version: 100\n");
        w.write("      LayerElement:  {\n");
        w.write("        Type: \"LayerElementNormal\"\n");
        w.write("        TypedIndex: 0\n");
        w.write("      }\n");
        w.write("      LayerElement:  {\n");
        w.write("        Type: \"LayerElementMaterial\"\n");
        w.write("        TypedIndex: 0\n");
        w.write("      }\n");
        w.write("    }\n");
        w.write("  }\n");
        w.write("  Model: 2, \"Model::" + safeName + "\", \"Mesh\" {\n");
        w.write("    Version: 232\n");
        w.write("    Properties70:  {\n");
        w.write("      P: \"InheritType\", \"enum\", \"\", \"\",1\n");
        w.write("    }\n");
        w.write("  }\n");
        w.write("  Material: 3, \"Material::MineCadMaterial\", \"\" {\n");
        w.write("    Version: 102\n");
        w.write("    ShadingModel: \"phong\"\n");
        w.write("    MultiLayer: 0\n");
        w.write("    Properties70:  {\n");
        w.write("      P: \"DiffuseColor\", \"Color\", \"\", \"A\",0.7,0.7,0.7\n");
        w.write("    }\n");
        w.write("  }\n");
        w.write("}\n");
        w.write("Connections:  {\n");
        w.write("  C: \"OO\",1,0\n");
        w.write("  C: \"OO\",2,0\n");
        w.write("  C: \"OO\",2,1\n");
        w.write("  C: \"OO\",3,2\n");
        w.write("}\n");
    }

    private static void writeFbxVertex(Writer w, Vec3 v, boolean[] first) throws IOException {
        if (!first[0]) {
            w.write(",");
        }
        first[0] = false;
        w.write(formatDouble(v.x()) + "," + formatDouble(v.y()) + "," + formatDouble(v.z()));
    }

    // ------------------------------------------------------------------ helpers

    private static String sanitizeMeshName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "selection";
        }
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private record FaceInstance(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 normal) {
    }

    private record Vec3(double x, double y, double z) {
    }
}

