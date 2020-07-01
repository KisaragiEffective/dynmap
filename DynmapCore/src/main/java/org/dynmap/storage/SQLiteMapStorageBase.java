package org.dynmap.storage;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.PlayerFaces;
import org.dynmap.WebAuthManager;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class SQLiteMapStorageBase extends AbstractDataBaseMapStorage {
    private String connectionString;
    private String databaseFile;
    private static final int POOLSIZE = 5;
    private final Connection[] connectionPool = new Connection[POOLSIZE];
    private int connectionCount = 0;
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public SQLiteMapStorageBase() {
        super();
    }

    public class StorageTile extends MapStorageTile {
        private final Integer mapkey;
        private final String uri;
        protected StorageTile(DynmapWorld world, MapType map, int x, int y,
                              int zoom, MapType.ImageVariant var) {
            super(world, map, x, y, zoom, var);

            mapkey = getMapKey(world, map, var);

            if (zoom > 0) {
                uri = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + "zzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y + "." + map.getImageFormat().getFileExt();
            }
            else {
                uri = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y + "." + map.getImageFormat().getFileExt();
            }
        }

        @Override
        public boolean exists() {
            if (mapkey == null) return false;
            boolean result = false;
            Connection c = null;
            boolean err = false;
            try {
                c = getConnection();
                try (
                Statement stmt = c.createStatement();
                ResultSet rs = executeQuery(stmt, "SELECT HashCode FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";")
                ) {
                    result = rs.next();
                }
            } catch (SQLException x) {
                Log.severe("Tile exists error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return result;
        }

        @Override
        public boolean matchesHashCode(long hash) {
            if (mapkey == null) return false;
            boolean result = false;
            Connection c = null;
            boolean err = false;
            try {
                c = getConnection();
                try (
                Statement stmt = c.createStatement();
                ResultSet rs = executeQuery(stmt, "SELECT HashCode FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";")
                ) {
                    if (rs.next()) {
                        long actual = rs.getLong("HashCode");
                        result = (actual == hash);
                    }
                    rs.close();
                    stmt.close();
                }
            } catch (SQLException x) {
                Log.severe("Tile matches hash error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return result;
        }

        @Override
        public TileRead read() {
            if (mapkey == null) return null;
            TileRead rslt = null;
            Connection c = null;
            boolean err = false;
            try {
                c = getConnection();
                try (
                Statement stmt = c.createStatement();
                ResultSet rs = executeQuery(stmt, "SELECT HashCode,LastUpdate,Format,Image,ImageLen FROM Tiles WHERE MapID=" + mapkey + " AND x=" + x + " AND y=" + y + " AND zoom=" + zoom + ";")
                ) {
                    if (rs.next()) {
                        rslt = new TileRead();
                        rslt.hashCode = rs.getLong("HashCode");
                        rslt.lastModified = rs.getLong("LastUpdate");
                        rslt.format = MapType.ImageEncoding.fromOrd(rs.getInt("Format"));
                        byte[] img = rs.getBytes("Image");
                        int len = rs.getInt("ImageLen");
                        if (len <= 0) {
                            len = img.length;
                            // Trim trailing zeros from padding by BLOB field
                            while ((len > 0) && (img[len - 1] == '\0')) len--;
                        }
                        rslt.image = new BufferInputStream(img, len);
                    }
                }
            } catch (SQLException x) {
                Log.severe("Tile read error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return rslt;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage) {
            if (mapkey == null) return false;
            boolean exists = exists();
            // If delete, and doesn't exist, quit
            if ((encImage == null) && (!exists)) return false;

            boolean err = false;
            Connection c = null;
            try {
                c = getConnection();
                if (encImage == null) { // If delete
                    try (PreparedStatement stmt = c.prepareStatement("DELETE FROM Tiles WHERE MapID=? AND x=? and y=? AND zoom=?;")) {
                        stmt.setInt(1, mapkey);
                        stmt.setInt(2, x);
                        stmt.setInt(3, y);
                        stmt.setInt(4, zoom);
                        executeUpdate(stmt);
                    }
                } else if (exists) {
                    try (PreparedStatement stmt = c.prepareStatement("UPDATE Tiles SET HashCode=?, LastUpdate=?, Format=?, Image=?, ImageLen=? WHERE MapID=? AND x=? and y=? AND zoom=?;")) {
                        stmt.setLong(1, hash);
                        stmt.setLong(2, System.currentTimeMillis());
                        stmt.setInt(3, map.getImageFormat().getEncoding().ordinal());
                        stmt.setBytes(4, encImage.buf);
                        stmt.setInt(5, encImage.len);
                        stmt.setInt(6, mapkey);
                        stmt.setInt(7, x);
                        stmt.setInt(8, y);
                        stmt.setInt(9, zoom);
                        executeUpdate(stmt);
                    }
                } else {
                    try (PreparedStatement stmt = c.prepareStatement("INSERT INTO Tiles (MapID,x,y,zoom,HashCode,LastUpdate,Format,Image,ImageLen) VALUES (?,?,?,?,?,?,?,?,?);")) {
                        stmt.setInt(1, mapkey);
                        stmt.setInt(2, x);
                        stmt.setInt(3, y);
                        stmt.setInt(4, zoom);
                        stmt.setLong(5, hash);
                        stmt.setLong(6, System.currentTimeMillis());
                        stmt.setInt(7, map.getImageFormat().getEncoding().ordinal());
                        stmt.setBytes(8, encImage.buf);
                        stmt.setInt(9, encImage.len);
                        executeUpdate(stmt);
                    }
                }
                // Signal update for zoom out
                if (zoom == 0) {
                    world.enqueueZoomOutUpdate(this);
                }
            } catch (SQLException x) {
                Log.severe("Tile write error - " + x.getMessage());
                err = true;
            } finally {
                releaseConnection(c, err);
            }
            return !err;
        }

        @Override
        public boolean getWriteLock() {
            return SQLiteMapStorageBase.this.getWriteLock(uri);
        }

        @Override
        public void releaseWriteLock() {
            SQLiteMapStorageBase.this.releaseWriteLock(uri);
        }

        @Override
        public boolean getReadLock(long timeout) {
            return SQLiteMapStorageBase.this.getReadLock(uri, timeout);
        }

        @Override
        public void releaseReadLock() {
            SQLiteMapStorageBase.this.releaseReadLock(uri);
        }

        @Override
        public void cleanup() {
        }

        @Override
        public String getURI() {
            return uri;
        }

        @Override
        public void enqueueZoomOutUpdate() {
            world.enqueueZoomOutUpdate(this);
        }

        @Override
        public MapStorageTile getZoomOutTile() {
            int xx;
            int step = 1 << zoom;
            if(x >= 0)
                xx = x - (x % (2*step));
            else
                xx = x + (x % (2*step));
            int yy = -y;
            if(yy >= 0)
                yy = yy - (yy % (2*step));
            else
                yy = yy + (yy % (2*step));
            yy = -yy;
            return new SQLiteMapStorageBase.StorageTile(world, map, xx, yy, zoom+1, var);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SQLiteMapStorageBase.StorageTile) {
                SQLiteMapStorageBase.StorageTile st = (SQLiteMapStorageBase.StorageTile) o;
                return uri.equals(st.uri);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
        }
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        File theDatabase = core.getFile(core.configuration.getString("storage/dbfile", "dynmap.db"));
        databaseFile = theDatabase.getAbsolutePath();
        connectionString = "jdbc:sqlite:" + databaseFile;
        Log.info("Opening SQLite file " + databaseFile + " as map store");
        try {
            Class.forName("org.sqlite.JDBC");
            // Initialize/update tables, if needed
            return initializeTables();
        } catch (ClassNotFoundException cnfx) {
            Log.severe("SQLite-JDBC classes not found - sqlite data source not usable");
            return false;
        }
    }

    private int getSchemaVersion() {
        int ver = 0;
        boolean err = false;
        Connection c = null;
        try {
            c = getConnection();    // Get connection (create DB if needed)
            try (
            Statement stmt = c.createStatement();
            ResultSet rs = executeQuery(stmt, "SELECT level FROM SchemaVersion;")
            ) {
                if (rs.next()) {
                    ver = rs.getInt("level");
                }
                rs.close();
                stmt.close();
            }
        } catch (SQLException x) {
            err = true;
        } finally {
            if (c != null) { releaseConnection(c, err); }
        }
        return ver;
    }

    private void doUpdate(Connection c, String sql) throws SQLException {
        Statement stmt = c.createStatement();
        executeUpdate(stmt, sql);
        stmt.close();
    }

    private final HashMap<String, Integer> mapKey = new HashMap<>();

    private void doLoadMaps() {

        mapKey.clear();
        // Read the maps table - cache results
        boolean err = false;
        Connection c = null;
        try {
            c = getConnection();
            try (
            Statement stmt = c.createStatement();
            ResultSet rs = executeQuery(stmt, "SELECT * from Maps;")
            ) {
                while (rs.next()) {
                    int key = rs.getInt("ID");
                    String worldID = rs.getString("WorldID");
                    String mapID = rs.getString("MapID");
                    String variant = rs.getString("Variant");
                    mapKey.put(worldID + ":" + mapID + ":" + variant, key);
                }
            }
        } catch (SQLException x) {
            Log.severe("Error loading map table - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
    }

    private Integer getMapKey(DynmapWorld w, MapType mt, MapType.ImageVariant var) {
        String id = w.getName() + ":" + mt.getPrefix() + ":" + var.toString();
        synchronized(mapKey) {
            Integer k = mapKey.get(id);
            if (k == null) {    // No hit: new value so we need to add it to table
                Connection c = null;
                boolean err = false;
                try {
                    c = getConnection();
                    // Insert row
                    try (PreparedStatement stmt = c.prepareStatement("INSERT INTO Maps (WorldID,MapID,Variant) VALUES (?, ?, ?);")) {
                        stmt.setString(1, w.getName());
                        stmt.setString(2, mt.getPrefix());
                        stmt.setString(3, var.toString());
                        executeUpdate(stmt);
                        stmt.close();
                    }
                    //  Query key assigned
                    try (PreparedStatement stmt = c.prepareStatement("SELECT ID FROM Maps WHERE WorldID = ? AND MapID = ? AND Variant = ?;")) {
                        stmt.setString(1, w.getName());
                        stmt.setString(2, mt.getPrefix());
                        stmt.setString(3, var.toString());

                        try (ResultSet rs = executeQuery(stmt)) {
                            if (rs.next()) {
                                k = rs.getInt("ID");
                                mapKey.put(id, k);
                            }
                        }
                    }
                } catch (SQLException x) {
                    Log.severe("Error updating Maps table - " + x.getMessage());
                    err = true;
                } finally {
                    releaseConnection(c, err);
                }
            }

            return k;
        }
    }

    private boolean initializeTables() {
        Connection c = null;
        boolean err = false;
        int version = getSchemaVersion();   // Get the existing schema version for the DB (if any)
        // If new, add our tables
        if (version == 0) {
            try {
                c = getConnection();
                doUpdate(c, "CREATE TABLE Maps (ID INTEGER PRIMARY KEY AUTOINCREMENT, WorldID STRING NOT NULL, MapID STRING NOT NULL, Variant STRING NOT NULL)");
                doUpdate(c, "CREATE TABLE Tiles (MapID INT NOT NULL, x INT NOT NULL, y INT NOT NULL, zoom INT NOT NULL, HashCode INT NOT NULL, LastUpdate INT NOT NULL, Format INT NOT NULL, Image BLOB, ImageLen INT, PRIMARY KEY(MapID, x, y, zoom))");
                doUpdate(c, "CREATE TABLE Faces (PlayerName STRING NOT NULL, TypeID INT NOT NULL, Image BLOB, ImageLen INT, PRIMARY KEY(PlayerName, TypeID))");
                doUpdate(c, "CREATE TABLE MarkerIcons (IconName STRING PRIMARY KEY NOT NULL, Image BLOB, ImageLen INT)");
                doUpdate(c, "CREATE TABLE MarkerFiles (FileName STRING PRIMARY KEY NOT NULL, Content CLOB)");
                doUpdate(c, "CREATE TABLE SchemaVersion (level INT PRIMARY KEY NOT NULL)");
                doUpdate(c, "INSERT INTO SchemaVersion (level) VALUES (2)");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                err = true;
                return false;
            } finally {
                releaseConnection(c, err);
            }
        }
        else if (version == 1) {    // Add ImageLen columns
            try {
                c = getConnection();
                doUpdate(c, "ALTER TABLE Tiles ADD ImageLen INT");
                doUpdate(c, "ALTER TABLE Faces ADD ImageLen INT");
                doUpdate(c, "ALTER TABLE MarkerIcons ADD ImageLen INT");
                doUpdate(c, "UPDATE SchemaVersion SET level=2");
            } catch (SQLException x) {
                Log.severe("Error creating tables - " + x.getMessage());
                err = true;
                return false;
            } finally {
                releaseConnection(c, err);
            }
        }
        // Load maps table - cache results
        doLoadMaps();

        return true;
    }

    /**
     * Get connection. If needed, create database.
     * @return the connection
     * @throws SQLException when connecting is failed
     */
    private Connection getConnection() throws SQLException {
        Connection c = null;
        synchronized (connectionPool) {
            while (c == null) {
                for (int i = 0; i < connectionPool.length; i++) {    // See if available connection
                    if (connectionPool[i] != null) { // Found one
                        c = connectionPool[i];
                        connectionPool[i] = null;
                    }
                }
                if (c == null) {
                    if (connectionCount < POOLSIZE) {  // Still more we can have
                        c = DriverManager.getConnection(connectionString);
                        configureConnection(c);
                        connectionCount++;
                    }
                    else {
                        try {
                            connectionPool.wait();
                        } catch (InterruptedException e) {
                            throw new SQLException("Interrupted");
                        }
                    }
                }
            }
        }
        return c;
    }

    private static Connection configureConnection(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL;");
        }
        return conn;
    }

    private void releaseConnection(Connection c, boolean err) {
        if (c == null) return;
        synchronized (connectionPool) {
            if (!err)  {  // Find slot to keep it in pool
                for (int i = 0; i < POOLSIZE; i++) {
                    if (connectionPool[i] == null) {
                        connectionPool[i] = c;
                        c = null; // Mark it recovered (no close needed
                        connectionPool.notifyAll();
                        break;
                    }
                }
            }
            if (c != null) {  // If broken, just toss it
                try { c.close(); } catch (SQLException x) {}
                connectionCount--;   // And reduce count
                connectionPool.notifyAll();
            }
        }
    }

    @Override
    public MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y,
                                  int zoom, MapType.ImageVariant var) {
        return new SQLiteMapStorageBase.StorageTile(world, map, x, y, zoom, var);
    }

    @Override
    public MapStorageTile getTile(DynmapWorld world, String uri) {
        String[] suri = uri.split("/");
        if (suri.length < 2) return null;
        String mname = suri[0]; // Map URI - might include variant
        MapType mt = null;
        MapType.ImageVariant imgvar = null;
        // Find matching map type and image variant
        for (int mti = 0; (mt == null) && (mti < world.maps.size()); mti++) {
            MapType type = world.maps.get(mti);
            MapType.ImageVariant[] var = type.getVariants();
            for (int ivi = 0; (imgvar == null) && (ivi < var.length); ivi++) {
                if (mname.equals(type.getPrefix() + var[ivi].variantSuffix)) {
                    mt = type;
                    imgvar = var[ivi];
                }
            }
        }
        if (mt == null) {   // Not found?
            return null;
        }
        // Now, take the last section and parse out coordinates and zoom
        String fname = suri[suri.length-1];
        String[] coord = fname.split("[_.]");
        if (coord.length < 3) { // 3 or 4
            return null;
        }
        try {
            // [zoom]? <x> <y>
            boolean isZoomed = coord[0].charAt(0) == 'z';
            int zoom = isZoomed ? coord[0].length() : 0;
            int x = isZoomed ? Integer.parseInt(coord[1]) : Integer.parseInt(coord[0]);
            int y = isZoomed ? Integer.parseInt(coord[2]) : Integer.parseInt(coord[1]);
            return getTile(world, mt, x, y, zoom, imgvar);
        } catch (NumberFormatException nfx) {
            return null;
        }
    }

    @Override
    public void enumMapTiles(DynmapWorld world, MapType map,
                             MapStorageTileEnumCB cb) {
        List<MapType> mtlist = map != null ? Collections.singletonList(map) : new ArrayList<>(world.maps);

        // Else, add all directories under world directory (for maps)
        mtlist.forEach(mt -> {
            MapType.ImageVariant[] vars = mt.getVariants();
            Arrays.stream(vars).forEachOrdered(var -> processEnumMapTiles(world, mt, var, cb, null, null));
        });
    }

    @Override
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        List<MapType> mtlist = map != null ? Collections.singletonList(map) : new ArrayList<>(world.maps);

        // Else, add all directories under world directory (for maps)
        mtlist.forEach(mt -> {
            MapType.ImageVariant[] vars = mt.getVariants();
            Arrays.stream(vars).forEachOrdered(var -> processEnumMapTiles(world, mt, var, null, cbBase, cbEnd));
        });
    }

    private void processEnumMapTiles(DynmapWorld world, MapType map, MapType.ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        Integer mapkey = getMapKey(world, map, var);
        if (mapkey == null) {
            if(cbEnd != null)
                cbEnd.searchEnded();
            return;
        }
        boolean err = false;
        Connection c = null;
        try {
            c = getConnection();
            // Query tiles for given mapkey
            try (
            Statement stmt = c.createStatement();
            ResultSet rs = executeQuery(stmt, "SELECT x,y,zoom,Format FROM Tiles WHERE MapID=" + mapkey + ";")
            ) {
                while (rs.next()) {
                    SQLiteMapStorageBase.StorageTile st = new SQLiteMapStorageBase.StorageTile(world, map, rs.getInt("x"), rs.getInt("y"), rs.getInt("zoom"), var);
                    final MapType.ImageEncoding encoding = MapType.ImageEncoding.fromOrd(rs.getInt("Format"));
                    if (cb != null)
                        cb.tileFound(st, encoding);
                    if (cbBase != null && st.zoom == 0)
                        cbBase.tileFound(st, encoding);
                    st.cleanup();
                }
                if (cbEnd != null)
                    cbEnd.searchEnded();
            }
        } catch (SQLException x) {
            Log.severe("Tile enum error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        List<MapType> mtlist = map != null ? Collections.singletonList(map) : new ArrayList<>(world.maps);

        // Else, add all directories under world directory (for maps)
        mtlist.forEach(mt -> {
            MapType.ImageVariant[] vars = mt.getVariants();
            Arrays.stream(vars).forEachOrdered(var -> processPurgeMapTiles(world, mt, var));
        });
    }
    private void processPurgeMapTiles(DynmapWorld world, MapType map, MapType.ImageVariant var) {
        Integer mapkey = getMapKey(world, map, var);
        if (mapkey == null) return;
        boolean err = false;
        Connection c = null;
        try {
            c = getConnection();
            // Query tiles for given mapkey
            try (Statement stmt = c.createStatement()) {
                executeUpdate(stmt, "DELETE FROM Tiles WHERE MapID=" + mapkey + ";");
            }
        } catch (SQLException x) {
            Log.severe("Tile purge error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
    }

    @Override
    public boolean setPlayerFaceImage(String playername, PlayerFaces.FaceType facetype,
                                      BufferOutputStream encImage) {
        boolean exists = hasPlayerFaceImage(playername, facetype);
        // If delete, and doesn't exist, quit
        if ((encImage == null) && (!exists)) return false;

        Connection c = null;
        boolean err = false;
        try {
            c = getConnection();
            if (encImage == null) { // If delete
                try (PreparedStatement stmt = c.prepareStatement("DELETE FROM Faces WHERE PlayerName=? AND TypeIDx=?;")) {
                    stmt.setString(1, playername);
                    stmt.setInt(2, facetype.typeID);
                    executeUpdate(stmt);
                }
            } else if (exists) {
                try (PreparedStatement stmt = c.prepareStatement("UPDATE Faces SET Image=?,ImageLen=? WHERE PlayerName=? AND TypeID=?;")) {
                    stmt.setBytes(1, encImage.buf);
                    stmt.setInt(2, encImage.len);
                    stmt.setString(3, playername);
                    stmt.setInt(4, facetype.typeID);
                    executeUpdate(stmt);
                }
            } else {
                try (PreparedStatement stmt = c.prepareStatement("INSERT INTO Faces (PlayerName,TypeID,Image,ImageLen) VALUES (?,?,?,?);")) {
                    stmt.setString(1, playername);
                    stmt.setInt(2, facetype.typeID);
                    stmt.setBytes(3, encImage.buf);
                    stmt.setInt(4, encImage.len);
                    executeUpdate(stmt);
                }
            }
        } catch (SQLException x) {
            Log.severe("Face write error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return !err;
    }

    @Override
    public BufferInputStream getPlayerFaceImage(String playername,
                                                PlayerFaces.FaceType facetype) {
        Connection c = null;
        boolean err = false;
        BufferInputStream image = null;
        try {
            c = getConnection();
            try (
            PreparedStatement stmt = c.prepareStatement("SELECT Image,ImageLen FROM Faces WHERE PlayerName=? AND TypeID=?;")
            ) {
                stmt.setString(1, playername);
                stmt.setInt(2, facetype.typeID);

                try (ResultSet rs = executeQuery(stmt)) {
                    if (rs.next()) {
                        byte[] img = rs.getBytes("Image");
                        int len = rs.getInt("imageLen");
                        if (len <= 0) {
                            len = img.length;
                            // Trim trailing zeros from padding by BLOB field
                            while ((len > 0) && (img[len - 1] == '\0')) len--;
                        }
                        image = new BufferInputStream(img, len);
                    }
                }
            }
        } catch (SQLException x) {
            Log.severe("Face read error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return image;
    }

    @Override
    public boolean hasPlayerFaceImage(String playername, PlayerFaces.FaceType facetype) {
        Connection c = null;
        boolean err = false;
        boolean exists = false;
        try {
            c = getConnection();
            try (
            PreparedStatement stmt = c.prepareStatement("SELECT TypeID FROM Faces WHERE PlayerName=? AND TypeID=?;")
            ) {
                stmt.setString(1, playername);
                stmt.setInt(2, facetype.typeID);
                try (ResultSet rs = executeQuery(stmt)) {
                    exists = rs.next();
                }
            }
        } catch (SQLException x) {
            Log.severe("Face exists error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return exists;
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
        Connection c = null;
        boolean err = false;
        try {
            c = getConnection();
            boolean exists = false;
            try (
            PreparedStatement stmt = c.prepareStatement("SELECT IconName FROM MarkerIcons WHERE IconName=?;")
            ) {
                stmt.setString(1, markerid);
                try (ResultSet rs = executeQuery(stmt)) {
                    exists = rs.next();
                }
            }
            if (encImage == null) { // If delete
                // If delete, and doesn't exist, quit
                if (!exists) return false;
                try (
                PreparedStatement stmt = c.prepareStatement("DELETE FROM MarkerIcons WHERE IconName=?;")
                ) {
                    stmt.setString(1, markerid);
                    executeUpdate(stmt);
                }
            }
            else if (exists) {
                try (
                PreparedStatement stmt = c.prepareStatement("UPDATE MarkerIcons SET Image=?,ImageLen=? WHERE IconName=?;")
                ) {
                    stmt.setBytes(1, encImage.buf);
                    stmt.setInt(2, encImage.len);
                    stmt.setString(3, markerid);
                    executeUpdate(stmt);
                }
            }
            else {
                try (
                PreparedStatement stmt = c.prepareStatement("INSERT INTO MarkerIcons (IconName,Image,ImageLen) VALUES (?,?,?);")
                ) {
                    stmt.setString(1, markerid);
                    stmt.setBytes(2, encImage.buf);
                    stmt.setInt(3, encImage.len);
                    executeUpdate(stmt);
                }
            }
        } catch (SQLException x) {
            Log.severe("Marker write error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return !err;
    }

    @Override
    public BufferInputStream getMarkerImage(String markerid) {
        Connection c = null;
        boolean err = false;
        BufferInputStream image = null;
        try {
            c = getConnection();
            try (
            PreparedStatement stmt = c.prepareStatement("SELECT Image,ImageLen FROM MarkerIcons WHERE IconName=?;")
            ) {
                stmt.setString(1, markerid);
                try (ResultSet rs = executeQuery(stmt)) {
                    if (rs.next()) {
                        byte[] img = rs.getBytes("Image");
                        int len = rs.getInt("ImageLen");
                        if (len <= 0) {
                            len = img.length;
                            // Trim trailing zeros from padding by BLOB field
                            while ((len > 0) && (img[len - 1] == '\0')) len--;
                        }
                        image = new BufferInputStream(img);
                    }
                }
            }
        } catch (SQLException x) {
            Log.severe("Marker read error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return image;
    }

    @Override
    public boolean setMarkerFile(String world, String content) {
        Connection c = null;
        boolean err = false;
        try {
            c = getConnection();
            boolean exists;
            try (
            PreparedStatement stmt = c.prepareStatement("SELECT FileName FROM MarkerFiles WHERE FileName=?;")
            ) {
                stmt.setString(1, world);
                try (ResultSet rs = executeQuery(stmt)) {
                    exists = rs.next();
                }
            }
            if (content == null) { // If delete
                // If delete, and doesn't exist, quit
                if (!exists) return false;
                try (
                PreparedStatement stmt = c.prepareStatement("DELETE FROM MarkerFiles WHERE FileName=?;")
                ) {
                    stmt.setString(1, world);
                    executeUpdate(stmt);
                }
            } else if (exists) {
                try (
                PreparedStatement stmt = c.prepareStatement("UPDATE MarkerFiles SET Content=? WHERE FileName=?;")
                ) {
                    stmt.setBytes(1, content.getBytes(CHARSET));
                    stmt.setString(2, world);
                    executeUpdate(stmt);
                }
            } else {
                try (
                PreparedStatement stmt = c.prepareStatement("INSERT INTO MarkerFiles (FileName,Content) VALUES (?,?);")
                ) {
                    stmt.setString(1, world);
                    stmt.setBytes(2, content.getBytes(CHARSET));
                    executeUpdate(stmt);
                }
            }
        } catch (SQLException x) {
            Log.severe("Marker file write error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return !err;
    }

    @Override
    public String getMarkerFile(String world) {
        Connection c = null;
        boolean err = false;
        String content = null;
        try {
            c = getConnection();
            try (
            PreparedStatement stmt = c.prepareStatement("SELECT Content FROM MarkerFiles WHERE FileName=?;")
            ) {
                stmt.setString(1, world);

                try (ResultSet rs = executeQuery(stmt)) {
                    if (rs.next()) {
                        byte[] img = rs.getBytes("Content");
                        content = new String(img, CHARSET);
                    }
                }
            }
        } catch (SQLException x) {
            Log.severe("Marker file read error - " + x.getMessage());
            err = true;
        } finally {
            releaseConnection(c, err);
        }
        return content;
    }

    @Override
    public String getMarkersURI(boolean login_enabled) {
        return "standalone/SQLite_markers.php?marker=";
    }

    @Override
    public String getTilesURI(boolean login_enabled) {
        return "standalone/SQLite_tiles.php?tile=";
    }

    @Override
    public void addPaths(StringBuilder sb, DynmapCore core) {
        sb.append("$dbfile = '");
        sb.append(WebAuthManager.esc(databaseFile));
        sb.append("';\n");

        // Need to call base to add webpath
        super.addPaths(sb, core);
    }

    private ResultSet executeQuery(PreparedStatement statement) throws SQLException {
        while (true) {
            try {
                return statement.executeQuery();
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
    private ResultSet executeQuery(Statement statement, String sql) throws SQLException {
        while (true) {
            try {
                return statement.executeQuery(sql);
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
    private int executeUpdate(PreparedStatement statement) throws SQLException {
        while (true) {
            try {
                return statement.executeUpdate();
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
    private int executeUpdate(Statement statement, String sql) throws SQLException {
        while (true) {
            try {
                return statement.executeUpdate(sql);
            } catch (SQLException x) {
                if (!x.getMessage().contains("[SQLITE_BUSY]")) {
                    throw x;
                }
            }
        }
    }
}
