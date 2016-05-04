package org.zkoss.zss.model.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * This class fakes an external memory block storage system.  It's actually implemented as
 * a list of blocks.
 *
 * @param <T>
 * @author morin
 */
class  BlockStore<T> {

    /**
     * A list of blocks
     */
    List<T> blocks;

    String bookTable;

    String read,write,free,insert;

    /**
     * Initialise a BlockStore with block size b
     *
     */
    public BlockStore(String bookTable) {
        this.blocks = new ArrayList<T>();
        this.bookTable = bookTable;
        this.read = "SELECT key, children, recordcount, ischild, value, parent, oid FROM " + bookTable + "_rowid_index_btree WHERE oid = ?";
        this.write = "UPDATE " + bookTable + "_rowid_index_btree SET key = ?, children = ?, recordcount = ?, ischild = ?, value = ?, parent = ? WHERE oid = ?";
        this.free = "DELETE FROM " + bookTable + "_rowid_index_btree WHERE oid = ?";
        this.insert = "INSERT INTO " + bookTable + "_rowid_index_btree (key, children, recordcount, ischild, value, parent) VALUES (?,?,?,?,?,?) RETURNING oid";

    }


    /**
     * Allocate a new block and return its index
     *
     * @return the index of the newly allocated block
     */
    public int placeBlock(String[] block) {
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = ((Supplier<PreparedStatement>)() -> {
                 try {
                     PreparedStatement s = connection.prepareStatement(insert);
                     s.setObject(1, block[0]);
                     s.setObject(2, block[1]);
                     s.setObject(3, block[2]);
                     s.setBoolean(4, Boolean.valueOf(block[3]));
                     s.setObject(5, block[4]);
                     if (block[5] == null)
                         s.setObject(6, block[5]);
                     else
                         s.setObject(6, Integer.parseInt(block[5]));

                     return s;
                 } catch (SQLException e) { throw new RuntimeException(e); }
             }).get();
             ResultSet rs = stmt.executeQuery()) {
             if (rs.next())
                return rs.getInt(1);
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Free a block, adding its index to the free list
     *
     * @param i the block index to free
     */
    public void freeBlock(int i) {
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = connection.prepareStatement(free)) {
            stmt.setInt(1,i);
            stmt.execute();
            connection.commit();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Read a block
     *
     * @param i the index of the block to read
     * @return the block
     */
    public String readBlock(int i) {
        String result = "";
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = ((Supplier<PreparedStatement>)() -> {
                 try {
                     PreparedStatement s = connection.prepareStatement(read);
                     s.setInt(1, i);
                     return s;
                 } catch (SQLException e) { throw new RuntimeException(e); }
             }).get();
             ResultSet rs = stmt.executeQuery()) {
             if (rs.next())
                 result = rs.getObject(1) + ";" + rs.getObject(2) + ";" + rs.getObject(3) + ";" + rs.getBoolean(4) + ";" + rs.getObject(5) + ";" + rs.getObject(6) + ";" + rs.getObject(7);
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Write a block
     *
     * @param oid     the index of the block
     * @param block the block
     */
    public void writeBlock(int oid, String[] block) {
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = connection.prepareStatement(write)) {
            stmt.setObject(1,block[0]);
            stmt.setObject(2, block[1]);
            stmt.setObject(3, block[2]);
            stmt.setBoolean(4, Boolean.valueOf(block[3]));
            stmt.setObject(5, block[4]);
            if (block[5] == null)
                stmt.setObject(6, null);
            else
                stmt.setObject(6, Integer.parseInt(block[5]));
            stmt.setInt(7, oid);
            stmt.execute();
            connection.commit();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }
}
