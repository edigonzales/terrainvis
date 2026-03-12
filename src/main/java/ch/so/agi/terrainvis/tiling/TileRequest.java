package ch.so.agi.terrainvis.tiling;

public record TileRequest(int id, int tileColumn, int tileRow, TileWindow window) {
    public TileRequest {
        if (id < 0 || tileColumn < 0 || tileRow < 0 || window == null) {
            throw new IllegalArgumentException("Invalid TileRequest");
        }
    }
}
