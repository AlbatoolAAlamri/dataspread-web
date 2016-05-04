package org.zkoss.zss.model.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;

/**
 * An implementation of a B Tree
 *
 * @param <KEY, VALUE>
 */
public class BTree<KEY, VALUE> implements Iterable<KEY> {
    protected Comparator<KEY> c;
    Factory<KEY> keyFactory;
    Factory<VALUE> valFactory;
    /**
     * The maximum number of children of a node (an odd number)
     */
    int b;

    /**
     * b div 2
     */
    int B;

    /**
     * Number of elements stored in the tree
     */
    int n;

    /**
     * The block storage mechanism
     */
    BlockStore<Node> bs;


    /**
     * The ID of the root node
     */
    String getRoot, setRoot, addRoot;

    int ri;

    int sheet_id;



    /**
     * Construct an empty BTree that uses a DefaultComparator
     *
     * @param b        the block size
     * @param keyClass the class of keys stored in this BTree
     * @param valClass the class of values stored in this BTree
     */
    public BTree(int b, Class<KEY> keyClass, Class<VALUE> valClass, int sheetid, String bookTable) {
        this(b, new DefaultComparator<KEY>(), keyClass, valClass, sheetid, bookTable);
    }

    /**
     * Construct an empty BTree
     *
     * @param b        the block size
     * @param c        the comparator to use
     * @param keyClass the class of objects stored in this BTree
     */
    public BTree(int b, Comparator<KEY> c, Class<KEY> keyClass, Class<VALUE> valClass, int sheetid, String bookTable) {
        this.c = c;
        b += 1 - (b % 2);
        this.b = b;
        B = b / 2;
        keyFactory = new Factory<KEY>(keyClass);
        valFactory = new Factory<VALUE>(valClass);
        bs = new BlockStore<Node>(bookTable);
        ri = new Node().id;
        sheet_id = sheetid;
        getRoot = "SELECT root_oid FROM " + bookTable + "_rowid_index_roots_btree WHERE sheetid = ?";
        setRoot = "UPDATE " + bookTable + "_rowid_index_roots_btree SET root_oid = ? WHERE sheetid = ?";
        addRoot = "INSERT INTO " + bookTable + "_rowid_index_roots_btree (root_oid,sheetid) VALUES (?,?)";
        addRootId();
        n = 0;

    }


    public void loadBtree(int sheetid){
        ri = getRootId(sheetid);
        sheet_id = sheetid;
    }

    public void addRootId(){
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = connection.prepareStatement(getRoot)) {
            stmt.setInt(1,ri);
            stmt.setInt(2,sheet_id);
            stmt.execute();
            connection.commit();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public int getRootId(int sheetid){
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = ((Supplier<PreparedStatement>)() -> {
                 try {
                     PreparedStatement s = connection.prepareStatement(getRoot);
                     s.setInt(1, sheetid);
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
        return -1;
    }

    public void setRootId(){
        try (Connection connection = DBHandler.instance.getConnection();
             PreparedStatement stmt = connection.prepareStatement(setRoot)) {
            stmt.setInt(1,ri);
            stmt.setInt(2,sheet_id);
            stmt.execute();
            connection.commit();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

    public boolean validateBTree(int m){
        boolean a = validateNode(ri, m);
        a = a && (checkHeight(ri) > 0);
        a = a && isOrdered(ri);
        return validateNode(ri,m) && (checkHeight(ri) > 0) && isOrdered(ri);
    }

    public boolean validateBTreeByCount(int m){
        boolean a = validateNodeByCount(ri, m);
        a = a && (checkHeight(ri) > 0);
        return validateNodeByCount(ri, m) && (checkHeight(ri) > 0);
    }

    public boolean isOrdered(int ui){
        Node u = new Node(bs.readBlock(ui));
        if ((ui == ri) && (u.size()==0)) return true;
        return (checkInOrder(ui,null)!=null);
    }

    public KEY checkInOrder(int ui, KEY x){
        Node u = new Node(bs.readBlock(ui));

        if (u.isLeaf()) {
            if (x!=null) {
                if (c.compare(x,u.keys[0]) > 0) return null;
            }
            int i = 0;
            while (i < u.size() - 1){
                if (c.compare(u.keys[i],u.keys[i+1]) > 0) return null;
                i++;
            }
            return u.keys[u.size()-1];
        } else {
            KEY y = checkInOrder(u.children[0], x);
            for (int i = 0; i < u.size()-1; i++)
            {
                if (y==null) return null;
                if (c.compare(y,u.keys[i]) > 0) return null;
                y = checkInOrder(u.children[i+1],u.keys[i]);
            }
            return y;
        }
    }

    public int checkHeight(int ui){
        Node u = new Node(bs.readBlock(ui));
        if (u.isLeaf()) {
            return 1;
        } else {
            int i = 0;
            int curHeight = 0;
            while (i < u.childrenSize()){
                if (u.children[i] == -1) return curHeight;
                int height = checkHeight(u.children[i]) + 1;
                if (height == 0) return -1;
                if (curHeight == 0) {
                    curHeight = height;
                } else {
                    if (curHeight != height) {
                        return -1;
                    }
                }
                i++;
            }
            return curHeight;
        }
    }

    public boolean validateNode(int ui, int m){
        Node u = new Node(bs.readBlock(ui));
        if (ui!=ri){
            if (u.isLeaf()){
                if ((u.size() > m) || (u.size() < (m + 1) / 2 - 1)) {
                    return false;
                }
            } else {
                if ((u.childrenSize() > m) || (u.childrenSize() < (m + 1) / 2) || (u.childrenSize() != u.size() + 1)) {
                    return false;
                }
                int i = 0;
                boolean valid = true;
                while (i < u.childrenSize()) {
                    if (u.children[i] == -1) return valid;
                    valid = valid && validateNode(u.children[i], m);
                    if (!valid) return false;
                    i++;
                }
            }
        } else {
            if(!u.isLeaf()) {
                int i = 0;
                boolean valid = true;
                while (i < u.childrenSize()) {
                    if (u.children[i] == -1) return valid;
                    valid = valid && validateNode(u.children[i], m);
                    if (!valid) return false;
                    i++;
                }
            }
        }
        return true;
    }

    public boolean validateNodeByCount(int ui, int m){
        Node u = new Node(bs.readBlock(ui));
        if (ui!=ri){
            if (u.isLeaf()){
                if ((u.valueSize() > m) || (u.valueSize() < (m + 1) / 2 - 1)) {
                    return false;
                }
            } else {
                if ((u.childrenSize() > m) || (u.childrenSize() < (m + 1) / 2)) {
                    return false;
                }
                int i = 0;
                boolean valid = true;
                while (i < u.childrenSize()) {
                    if (u.children[i] == -1) return valid;
                    valid = valid && validateNodeByCount(u.children[i], m);
                    if (!valid) return false;
                    i++;
                }
            }
        } else {
            if(!u.isLeaf()) {
                int i = 0;
                boolean valid = true;
                while (i < u.childrenSize()) {
                    if (u.children[i] == -1) return valid;
                    valid = valid && validateNodeByCount(u.children[i], m);
                    if (!valid) return false;
                    i++;
                }
            }
        }
        return true;
    }



    /**
     * Find the index, i, at which x should be inserted into the null-padded
     * sorted array, a
     *
     * @param a the sorted array (padded with null entries)
     * @param x the value to search for
     * @return i
     */
    protected int findIt(KEY[] a, KEY x) {
        int lo = 0, hi = a.length;
        while (hi != lo) {
            int m = (hi + lo) / 2;
            int cmp = a[m] == null ? -1 : c.compare(x, a[m]);
            if (cmp < 0)
                hi = m;      // look in first half
            else if (cmp > 0)
                lo = m + 1;    // look in second half
            else
                return m + 1; // found it
        }
        return lo;
    }

    /**
     * Find the index, i, at which x should be inserted into the null-padded
     * sorted array, a
     *
     * @param a the sorted array (padded with null entries)
     * @param row the position to search for
     * @return i
     */
    protected int findItByCount(long[] a, long row) {
        if (a == null) return 0;
        int lo = 0, hi = a.length;
        long ct = row;
        while (hi != lo) {
            if (a[lo] == 0) return lo - 1;
            if (ct > a[lo]) {
                ct -= a[lo];
                lo++;
            } else {
                return lo;
            }
        }
        return lo - 1;
    }



    /**
     * Find the index, i, at which x should be inserted into the null-padded
     * sorted array, a
     *
     * @param x   the key for the value
     * @param val the value corresponding to x
     * @return
     */
    public boolean add(KEY x, VALUE val) {
        Node w;
        w = addRecursive(x, ri, val);
        if (w != null) {   // root was split, make new root
            Node newroot = new Node();
            x = w.removeKey(0);
            bs.writeBlock(w.id, w.convert());

            // No longer a leaf node
            // First time leaf becomes a root
            newroot.leafNode = false;

            newroot.children = new int[b + 1];
            Arrays.fill(newroot.children, 0, newroot.children.length, -1);

            newroot.childrenCount = new long[b + 1];
            Arrays.fill(newroot.childrenCount, 0, newroot.childrenCount.length, 0);

            newroot.children[0] = ri;
            newroot.keys[0] = x;
            newroot.children[1] = w.id;
            //newroot.childrenCount[1] = w.childrenCount;
            //TODO: Update children count
            Node leftNode = new Node(bs.readBlock(ri));
            if (leftNode.isLeaf()) {
                newroot.childrenCount[0] = leftNode.size();
            } else {
                int i;
                for(i = 0; i < leftNode.childrenCount.length; i++){
                    newroot.childrenCount[0] += leftNode.childrenCount[i];
                }
            }
            if (w.isLeaf()) {
                newroot.childrenCount[1] = w.size();
            } else {
                int i;
                for(i = 0; i < w.childrenCount.length; i++){
                    newroot.childrenCount[1] += w.childrenCount[i];
                }
            }
            ri = newroot.id;
            setRootId();
            bs.writeBlock(ri, newroot.convert());
        }
        n++;
        return true;
    }

    /**
     * Add the value x in the subtree rooted at the node with index ui
     * <p>
     * This method adds x into the subtree rooted at the node u whose index is
     * ui. If u is split by this operation then the return value is the Node
     * that was created when u was split
     *
     * @param x   the element to add
     * @param ui  the index of the node, u, at which to add x
     * @param val
     * @return a new node that was created when u was split, or null if u was
     * not split
     */
    protected Node addRecursive(KEY x, int ui, VALUE val) {
        Node u = new Node(bs.readBlock(ui));
        int i = findIt(u.keys, x);
        //if (i < 0) throw new DuplicateValueException();
        if (u.isLeaf()) { // leaf node, just add it
            u.add(x, -1, val);
            bs.writeBlock(u.id, u.convert());
        } else {
            u.childrenCount[i]++;
            Node w = addRecursive(x, u.children[i], val);
            if (w != null) {  // child was split, w is new child
                x = w.removeKey(0);
                bs.writeBlock(w.id, w.convert());
                u.add(x, w.id, val);
                if (w.isLeaf()) {
                    u.childrenCount[i] -= w.size();
                } else {
                    int z;
                    for(z = 0; z < w.childrenCount.length; z++){
                        u.childrenCount[i] -= w.childrenCount[z];
                    }
                }
            }
            bs.writeBlock(u.id, u.convert());
        }
        return u.isFull() ? u.split() : null;
    }


    /**
     * Find the index, i, at which value should be inserted into the null-padded
     * sorted array, a
     *
     * @param row   the position for the value
     * @param val the value corresponding to x
     * @return
     */
    public boolean addByCount(long row, VALUE val) {
        Node w;
        w = addRecursiveByCount(row, ri, val);
        if (w != null) {   // root was split, make new root
            Node newroot = new Node();
            bs.writeBlock(w.id, w.convert());

            // No longer a leaf node
            // First time leaf becomes a root
            newroot.leafNode = false;

            newroot.children = new int[b + 1];
            Arrays.fill(newroot.children, 0, newroot.children.length, -1);

            newroot.childrenCount = new long[b + 1];
            Arrays.fill(newroot.childrenCount, 0, newroot.childrenCount.length, 0);

            newroot.children[0] = ri;
            newroot.children[1] = w.id;
            //newroot.childrenCount[1] = w.childrenCount;
            //TODO: Update children count
            Node leftNode = new Node(bs.readBlock(ri));
            if (leftNode.isLeaf()) {
                newroot.childrenCount[0] = leftNode.valueSize();
            } else {
                int i;
                for(i = 0; i < leftNode.childrenCount.length; i++){
                    newroot.childrenCount[0] += leftNode.childrenCount[i];
                }
            }
            if (w.isLeaf()) {
                newroot.childrenCount[1] = w.valueSize();
            } else {
                int i;
                for(i = 0; i < w.childrenCount.length; i++){
                    newroot.childrenCount[1] += w.childrenCount[i];
                }
            }
            ri = newroot.id;
            setRootId();
            bs.writeBlock(ri, newroot.convert());
        }
        n++;
        return true;
    }

    /**
     * Add the value x in the subtree rooted at the node with index ui
     * <p>
     * This method adds x into the subtree rooted at the node u whose index is
     * ui. If u is split by this operation then the return value is the Node
     * that was created when u was split
     *
     * @param row   the element to add
     * @param ui  the index of the node, u, at which to add x
     * @param val
     * @return a new node that was created when u was split, or null if u was
     * not split
     */
    protected Node addRecursiveByCount(long row, int ui, VALUE val) {
        Node u = new Node(bs.readBlock(ui));
        int i;
        //if (i < 0) throw new DuplicateValueException();
        if (u.isLeaf()) { // leaf node, just add it
            u.addByCount(row, -1, val);
            bs.writeBlock(u.id, u.convert());
        } else {
            i = findItByCount(u.childrenCount, row);
            long newn = row;
            for (int z = 0; z < i; z++){
                newn -= u.childrenCount[z];
            }
            u.childrenCount[i]++;
            Node w = addRecursiveByCount(newn, u.children[i], val);
            if (w != null) {  // child was split, w is new child
                bs.writeBlock(w.id, w.convert());
                u.addByCount(row, w.id, val);
                if (w.isLeaf()) {
                    u.childrenCount[i] -= w.valueSize();
                } else {
                    int z;
                    for(z = 0; z < w.childrenCount.length; z++){
                        u.childrenCount[i] -= w.childrenCount[z];
                    }
                }
            }
            bs.writeBlock(u.id, u.convert());
        }
        return u.isFullByCount() ? u.splitByCount() : null;
    }



    public boolean removeByCount(long row) {
        if (removeRecursiveByCount(row, ri)) {
            n--;
            Node r = new Node(bs.readBlock(ri));
            if (!r.isLeaf() && r.childrenSize() <= 1 && n > 0) { // root has only one child
                bs.freeBlock(ri);
                ri = r.children[0];
                setRootId();
            }


            return true;
        }
        return false;
    }

    /**
     * Remove the value x from the subtree rooted at the node with index ui
     *
     * @param row  the value to remove
     * @param ui the index of the subtree to remove x from
     * @return true if x was removed and false otherwise
     */
    protected boolean removeRecursiveByCount(long row, int ui) {
        if (ui < 0) return false;  // didn't find it
        Node u = new Node(bs.readBlock(ui));
        int i;
        /* Need to go to leaf to delete */
        if (u.isLeaf()) {
            i = (int) row;
            if (i > 0) {
                u.removeBoth(i - 1);
                bs.writeBlock(ui, u.convert());
                return true;
            }
        } else {
            i = findItByCount(u.childrenCount, row);
            u.childrenCount[i]--;
            long newn = row;
            for (int z = 0; z < i; z++){
                newn -= u.childrenCount[z];
            }
            if (removeRecursiveByCount(newn, u.children[i])) {
                checkUnderflowByCount(u, i);
                bs.writeBlock(ui, u.convert());
                return true;
            }
            bs.writeBlock(ui,u.convert());
        }
        return false;
    }

    /**
     * Check if an underflow has occurred in the i'th child of u and, if so, fix it
     * by borrowing from or merging with a sibling
     *
     * @param u
     * @param i
     */
    protected void checkUnderflowByCount(Node u, int i) {
        if (u.children[i] < 0) return;
        if (i == 0)
            checkUnderflowZeroByCount(u, i); // use u's right sibling
        else if (i == u.childrenSize() - 1)
            checkUnderflowNonZeroByCount(u, i);
        else if (u.childrenCount[i+1] > u.childrenCount[i-1])
                checkUnderflowZeroByCount(u, i);
        else checkUnderflowNonZeroByCount(u, i);
    }

    protected void mergeByCount(Node u, int i, Node v, Node w) {
        // w is merged with v
        int sv, sw;
        if (!v.isLeaf()) {
            sv = v.childrenSize() - 1;
            sw = w.childrenSize() - 1;
        } else {
            sv = v.valueSize();
            sw = w.valueSize();
        }

        if (v.isLeaf()) {
            System.arraycopy(w.values, 0, v.values, sv, sw);
        }
        else {
            System.arraycopy(w.children, 0, v.children, sv + 1, sw + 1);
            System.arraycopy(w.childrenCount, 0, v.childrenCount, sv + 1, sw + 1);
            v.values[i+1] = u.values[i];
        }
        // add key to v and remove it from u

        // TODO: Do not move key from u to v
        //v.keys[sv] = u.keys[i];
        // U should not be a leaf node
        u.childrenCount[i] += u.childrenCount[i+1];

        // v ids is in u.children[i+1]
        // Free block
        bs.freeBlock(u.children[i + 1]);

        System.arraycopy(u.children, i + 2, u.children, i + 1, b - i - 1);
        System.arraycopy(u.childrenCount, i + 2, u.childrenCount, i + 1, b - i - 1);
        u.children[b] = -1;
        u.childrenCount[b] = 0;
    }

    /**
     * Check if an underflow has occured in the i'th child of u and, if so, fix
     * it
     *
     * @param u a node
     * @param i the index of a child in u
     */
    protected void checkUnderflowNonZeroByCount(Node u, int i) {
        Node w = new Node(bs.readBlock(u.children[i]));  // w is child of u
        if ((w.isLeaf() && w.valueSize() < B) || (!w.isLeaf() && w.childrenSize() < B + 1)) {  // underflow at w
            Node v = new Node(bs.readBlock(u.children[i - 1])); // v left of w
            if ((v.isLeaf() && v.valueSize() > B) || (!v.isLeaf() && v.childrenSize() > B + 1)) {  // underflow at w
                shiftLRByCount(u, i - 1, v, w);
                if (v.isLeaf()) {
                    u.childrenCount[i-1]--;
                } else {
                    u.childrenCount[i-1] -= w.childrenCount[0];
                }
                if (w.isLeaf()) {
                    u.childrenCount[i]++;
                } else {
                    u.childrenCount[i] += w.childrenCount[0];
                }
                bs.writeBlock(u.children[i-1],v.convert());
                bs.writeBlock(u.children[i], w.convert());
            } else { // v will absorb w
                mergeByCount(u, i - 1, v, w);
                bs.writeBlock(u.children[i-1],v.convert());
            }
        }
    }

    /**
     * Shift keys from v into w
     *
     * @param u the parent of v and w
     * @param i the index w in u.children
     * @param v the right sibling of w
     * @param w the left sibling of v
     */
    protected void shiftLRByCount(Node u, int i, Node v, Node w) {
        int sw, sv, shift;
        if (!w.isLeaf()) {
            sw = w.childrenSize() - 1;
            sv = v.childrenSize() - 1;
        } else {
            sw = w.valueSize();
            sv = v.valueSize();
        }
        shift = ((sw + sv) / 2) - sw;  // num. keys to shift from v to w


        // make space for new keys in w

        if (v.isLeaf()) {
            // move keys and children out of v and into w

            System.arraycopy(w.values, 0, w.values, shift, sw);

            System.arraycopy(v.values, sv - shift, w.values, 0, shift);
            Arrays.fill(v.values, sv - shift, sv, null);

        } else {
            // Don't move this key for leaf
            // move keys and children out of v and into w (and u)

            System.arraycopy(w.children, 0, w.children, shift, sw + 1);
            System.arraycopy(w.childrenCount, 0, w.childrenCount, shift, sw + 1);

            System.arraycopy(v.children, sv - shift + 1, w.children, 0, shift);
            System.arraycopy(v.childrenCount, sv - shift + 1, w.childrenCount, 0, shift);
            Arrays.fill(v.children, sv - shift + 1, sv + 1, -1);
            Arrays.fill(v.childrenCount, sv - shift + 1, sv + 1, 0);
        }



    }

    protected void checkUnderflowZeroByCount(Node u, int i) {
        Node w = new Node(bs.readBlock(u.children[i])); // w is child of u
        if ((w.isLeaf() && w.valueSize() < B) || (!w.isLeaf() && w.childrenSize() < B + 1)) {  // underflow at w
            Node v = new Node(bs.readBlock(u.children[i + 1])); // v right of w
            if ((v.isLeaf() && v.valueSize() > B) || (!v.isLeaf() && v.childrenSize() > B + 1)) {  // underflow at w
                shiftRLByCount(u, i, v, w);
                if (v.isLeaf()) {
                    u.childrenCount[i+1]--;
                } else {
                    u.childrenCount[i+1] -= w.childrenCount[w.childrenSize()-1];
                }
                if (w.isLeaf()) {
                    u.childrenCount[i]++;
                } else {
                    u.childrenCount[i] += w.childrenCount[w.childrenSize()-1];
                }
                bs.writeBlock(u.children[i+1],v.convert());
                bs.writeBlock(u.children[i],w.convert());
            } else { // w will absorb v
                mergeByCount(u, i, w, v);
                bs.writeBlock(u.children[i],w.convert());
            }
        }
    }

    /**
     * Shift keys from node v into node w
     *
     * @param u the parent of v and w
     * @param i the index w in u.children
     * @param v the left sibling of w
     * @param w the right sibling of v
     */
    protected void shiftRLByCount(Node u, int i, Node v, Node w) {
        int sw, sv, shift;
        if (!w.isLeaf()) {
            sw = w.childrenSize() - 1;
            sv = v.childrenSize() - 1;
        } else {
            sw = w.valueSize();
            sv = v.valueSize();
        }
        shift = ((sw + sv) / 2) - sw;  // num. keys to shift from v to w


        // shift keys and children from v to w
        // Intermediate keys are not important and can be eliminated

        if (v.isLeaf()) // w should also be leaf
        {
            // Do not bring the key from u
            System.arraycopy(v.values, 0, w.values, sw, shift);
        }
        else {
            System.arraycopy(v.children, 0, w.children, sw + 1, shift);
            System.arraycopy(v.childrenCount, 0, w.childrenCount, sw + 1, shift);
        }


        if (v.isLeaf()) {
            System.arraycopy(v.values, shift, v.values, 0, b - shift);
            Arrays.fill(v.values, sv - shift + 1, b, null);
        } else {
            System.arraycopy(v.children, shift, v.children, 0, b - shift + 1);
            Arrays.fill(v.children, sv - shift + 1, b + 1, -1);
            System.arraycopy(v.childrenCount, shift, v.childrenCount, 0, b - shift + 1);
            Arrays.fill(v.childrenCount, sv - shift + 1, b + 1, 0);

        }
    }



    public boolean remove(KEY x) {
        if (removeRecursive(x, ri).getDone() >= 0) {
            n--;
            Node r = new Node(bs.readBlock(ri));
            if (!r.isLeaf() && r.size() == 0 && n > 0) { // root has only one child
                bs.freeBlock(ri);
                ri = r.children[0];
                setRootId();
            }


            return true;
        }
        return false;
    }

    /**
     * Remove the value x from the subtree rooted at the node with index ui
     *
     * @param x  the value to remove
     * @param ui the index of the subtree to remove x from
     * @return true if x was removed and false otherwise
     */
    protected ReturnRS removeRecursive(KEY x, int ui) {
        ReturnRS result = new ReturnRS(-1);
        if (ui < 0) return result;  // didn't find it
        Node u = new Node(bs.readBlock(ui));
        int i = findIt(u.keys, x);
        /* Need to go to leaf to delete */
        if (u.isLeaf()) {
            if (i > 0) {
                if (c.compare(u.keys[i - 1], x) == 0) {
                    // Found
                    result.setDone(0);
                    if (i == 1) {
                        result.setDone(1);
                        result.setKey(u.keys[i]);
                    }
                    u.removeBoth(i - 1);
                    bs.writeBlock(ui, u.convert());
                    return result;
                }
            }
        } else {
            u.childrenCount[i]--;
            ReturnRS rs = removeRecursive(x,u.children[i]);
            if (rs.getDone() >= 0) {
                if (i > 0) {
                    if (c.compare(u.keys[i - 1], x) == 0) {
                        u.keys[i-1] = rs.getKey();
                        rs.setDone(0);
                    }
                }
                checkUnderflow(u, i);
                bs.writeBlock(ui, u.convert());
                return rs;
            }
            bs.writeBlock(ui,u.convert());
        }
        return result;
    }

    /**
     * Check if an underflow has occurred in the i'th child of u and, if so, fix it
     * by borrowing from or merging with a sibling
     *
     * @param u
     * @param i
     */
    protected void checkUnderflow(Node u, int i) {
        if (u.children[i] < 0) return;
        if (i == 0)
            checkUnderflowZero(u, i); // use u's right sibling
        else if (i == u.childrenSize() - 1)
            checkUnderflowNonZero(u, i);
        else if (u.childrenCount[i+1] > u.childrenCount[i-1])
            checkUnderflowZero(u, i);
        else checkUnderflowNonZero(u, i);
    }

    protected void merge(Node u, int i, Node v, Node w) {
        // w is merged with v
        int sv = v.size();
        int sw = w.size();

        if (v.isLeaf()) {
            System.arraycopy(w.keys, 0, v.keys, sv, sw); // copy keys from w to v
            System.arraycopy(w.values, 0, v.values, sv, sw);
        }
        else {
            System.arraycopy(w.keys, 0, v.keys, sv + 1, sw); // copy keys from w to v
            System.arraycopy(w.children, 0, v.children, sv + 1, sw + 1);
            System.arraycopy(w.childrenCount, 0, v.childrenCount, sv + 1, sw + 1);
            v.keys[i+1] = u.keys[i];
            v.values[i+1] = u.values[i];
        }
        // add key to v and remove it from u

        // TODO: Do not move key from u to v
        //v.keys[sv] = u.keys[i];
        // U should not be a leaf node
        u.childrenCount[i] += u.childrenCount[i+1];
        System.arraycopy(u.keys, i + 1, u.keys, i, b - i - 1);
        u.keys[b - 1] = null;

        // v ids is in u.children[i+1]
        // Free block
        bs.freeBlock(u.children[i + 1]);

        System.arraycopy(u.children, i + 2, u.children, i + 1, b - i - 1);
        System.arraycopy(u.childrenCount, i + 2, u.childrenCount, i + 1, b - i - 1);
        u.children[b] = -1;
        u.childrenCount[b] = 0;
    }

    /**
     * Check if an underflow has occured in the i'th child of u and, if so, fix
     * it
     *
     * @param u a node
     * @param i the index of a child in u
     */
    protected void checkUnderflowNonZero(Node u, int i) {
        Node w = new Node(bs.readBlock(u.children[i]));  // w is child of u
        if (w.size() < B) {  // underflow at w
            Node v = new Node(bs.readBlock(u.children[i - 1])); // v left of w
            if (v.size() > B) {  // w can borrow from v
                shiftLR(u, i - 1, v, w);
                if (v.isLeaf()) {
                    u.childrenCount[i-1]--;
                } else {
                    u.childrenCount[i-1] -= w.childrenCount[0];
                }
                if (w.isLeaf()) {
                    u.childrenCount[i]++;
                } else {
                    u.childrenCount[i] += w.childrenCount[0];
                }
                bs.writeBlock(u.children[i-1],v.convert());
                bs.writeBlock(u.children[i], w.convert());
            } else { // v will absorb w
                merge(u, i - 1, v, w);
                bs.writeBlock(u.children[i-1],v.convert());
            }
        }
    }

    /**
     * Shift keys from v into w
     *
     * @param u the parent of v and w
     * @param i the index w in u.children
     * @param v the right sibling of w
     * @param w the left sibling of v
     */
    protected void shiftLR(Node u, int i, Node v, Node w) {
        int sw = w.size();
        int sv = v.size();
        int shift = ((sw + sv) / 2) - sw;  // num. keys to shift from v to w

        // make space for new keys in w
        System.arraycopy(w.keys, 0, w.keys, shift, sw);

        if (v.isLeaf()) {
            // move keys and children out of v and into w

            System.arraycopy(w.values, 0, w.values, shift, sw);
            u.keys[i] = v.keys[sv - shift];

            System.arraycopy(v.keys, sv - shift, w.keys, 0, shift);
            Arrays.fill(v.keys, sv - shift, sv, null);
            System.arraycopy(v.values, sv - shift, w.values, 0, shift);
            Arrays.fill(v.values, sv - shift, sv, null);

        } else {
            // Don't move this key for leaf
            // move keys and children out of v and into w (and u)

            w.keys[shift - 1] = u.keys[i];
            System.arraycopy(w.children, 0, w.children, shift, sw + 1);
            System.arraycopy(w.childrenCount, 0, w.childrenCount, shift, sw + 1);
            u.keys[i] = v.keys[sv - shift];

            System.arraycopy(v.keys, sv - shift + 1, w.keys, 0, shift - 1);
            Arrays.fill(v.keys, sv - shift, sv, null);

            System.arraycopy(v.children, sv - shift + 1, w.children, 0, shift);
            System.arraycopy(v.childrenCount, sv - shift + 1, w.childrenCount, 0, shift);
            Arrays.fill(v.children, sv - shift + 1, sv + 1, -1);
            Arrays.fill(v.childrenCount, sv - shift + 1, sv + 1, 0);
        }



    }

    protected void checkUnderflowZero(Node u, int i) {
        Node w = new Node(bs.readBlock(u.children[i])); // w is child of u
        if (w.size() < B) {  // underflow at w
            Node v = new Node(bs.readBlock(u.children[i + 1])); // v right of w
            if (v.size() > B) { // w can borrow from v
                shiftRL(u, i, v, w);
                if (v.isLeaf()) {
                    u.childrenCount[i+1]--;
                } else {
                    u.childrenCount[i+1] -= w.childrenCount[w.childrenSize()-1];
                }
                if (w.isLeaf()) {
                    u.childrenCount[i]++;
                } else {
                    u.childrenCount[i] += w.childrenCount[w.childrenSize()-1];
                }
                bs.writeBlock(u.children[i+1],v.convert());
                bs.writeBlock(u.children[i],w.convert());
            } else { // w will absorb v
                merge(u, i, w, v);
                bs.writeBlock(u.children[i],w.convert());
            }
        }
    }

    /**
     * Shift keys from node v into node w
     *
     * @param u the parent of v and w
     * @param i the index w in u.children
     * @param v the left sibling of w
     * @param w the right sibling of v
     */
    protected void shiftRL(Node u, int i, Node v, Node w) {
        int sw = w.size();
        int sv = v.size();
        int shift = ((sw + sv) / 2) - sw;  // num. keys to shift from v to w


        // shift keys and children from v to w
        // Intermediate keys are not important and can be eliminated

        if (v.isLeaf()) // w should also be leaf
        {
            // Do not bring the key from u
            System.arraycopy(v.keys, 0, w.keys, sw, shift);
            System.arraycopy(v.values, 0, w.values, sw, shift);
            u.keys[i] = v.keys[shift];
        }
        else {
            w.keys[sw] = u.keys[i];
            System.arraycopy(v.keys, 0, w.keys, sw + 1, shift - 1);
            System.arraycopy(v.children, 0, w.children, sw + 1, shift);
            System.arraycopy(v.childrenCount, 0, w.childrenCount, sw + 1, shift);
            u.keys[i] = v.keys[shift - 1];
        }


        // delete keys and children from v
        System.arraycopy(v.keys, shift, v.keys, 0, b - shift);
        Arrays.fill(v.keys, sv - shift, b, null);

        if (v.isLeaf()) {
            System.arraycopy(v.values, shift, v.values, 0, b - shift);
            Arrays.fill(v.values, sv - shift + 1, b, null);
        } else {
            System.arraycopy(v.children, shift, v.children, 0, b - shift + 1);
            Arrays.fill(v.children, sv - shift + 1, b + 1, -1);
            System.arraycopy(v.childrenCount, shift, v.childrenCount, 0, b - shift + 1);
            Arrays.fill(v.childrenCount, sv - shift + 1, b + 1, 0);

        }
    }







    public void clear() {
        n = 0;
        clearRecursive(ri);
    }

    public void clearRecursive(int ui){
        Node u = new Node(bs.readBlock(ui));
        if (u.isLeaf()) {
            bs.freeBlock(ui);
            return;
        } else {
            for (int i = 0; i < u.childrenSize(); i++) {
                clearRecursive(u.children[i]);
            }
        }
        bs.freeBlock(ui);
    }

    public Comparator<? super KEY> comparator() {
        return c;
    }

    public boolean exists(KEY x) {
        int ui = ri;
        while (true) {
            Node u = new Node(bs.readBlock(ui));
            int i = findIt(u.keys, x);
            if (u.isLeaf()) {
                return i > 0 && c.compare(u.keys[i - 1], x) == 0;
            }
            ui = u.children[i];
        }
    }

    public VALUE get(KEY x) {
        int ui = ri;
        while (true) {
            Node u = new Node(bs.readBlock(ui));
            int i = findIt(u.keys, x);
            if (u.isLeaf()) {
                if (i > 0 && c.compare(u.keys[i - 1], x) == 0)
                    return u.values[i - 1]; // found it
                else
                    return null;
            }
            ui = u.children[i];
        }
    }

    public VALUE getByCount(long row) {
        int ui = ri;
        long ct = row;
        while (true) {
            Node u = new Node(bs.readBlock(ui));
            int i = findItByCount(u.childrenCount, ct);
            if (u.isLeaf()) {
                i = (int) ct - 1;
                return u.values[i];
            }
            ui = u.children[i];
            for (int z = 0; z < i; z++){
                ct -= u.childrenCount[z];
            }
        }
    }

    public Iterator<KEY> iterator(KEY x) {
        return new BTIterator(x);
    }

    public int size() {
        return n;
    }

    public Iterator<KEY> iterator() {
        return new BTIterator();
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        toString(ri, sb);
        return sb.toString();
    }

    /**
     * A recursive algorithm for converting this tree into a string
     *
     * @param ui the subtree to add to the the string
     * @param sb a StringBuffer for building the string
     */
    public void toString(int ui, StringBuffer sb) {
        if (ui < 0) return;
        Node u = new Node(bs.readBlock(ui));
        sb.append("Block no:" + ui);
        sb.append(" Leaf:" + u.isLeaf() + " ");

        int i = 0;
        if (u.isLeaf()) {
            while (i < b && u.keys[i] != null) {
                sb.append(u.keys[i] + "->");
                sb.append(u.values[i] + ",");
                i++;
            }
        } else {
            while (i < b && u.keys[i] != null) {
                sb.append(u.children[i]);
                sb.append(" < " + u.keys[i] + " > ");
                i++;
            }
            sb.append(u.children[i]);
        }
        sb.append("\n");
        i = 0;
        if (!u.isLeaf()) {
            while (i < b && u.keys[i] != null) {
                toString(u.children[i], sb);
                i++;
            }
            toString(u.children[i], sb);
        }


    }

    static class DuplicateValueException extends Exception {
        private static final long serialVersionUID = 1L;
    }


    protected class ReturnRS {
        int done;
        KEY key;

        public ReturnRS(){
            done = -1;
            key = null;
        }

        public ReturnRS(int d){
            done = d;
            key = null;
        }

        public void setKey(KEY k){
            key = k;
        }

        public void setDone(int d) {
            done = d;
        }

        public KEY getKey(){
            return key;
        }

        public int getDone(){
            return done;
        }

    }

    /**
     * A node in a B-tree which has an array of up to b keys and up to b children
     */
    protected class Node {
        /**
         * This block's index
         */
        int id;

        /**
         * The keys stored in this block
         */
        KEY[] keys;

        /**
         * The ID of parent
         */
        int parent;

        /**
         * The IDs of the children of this block (if any)
         */
        int[] children;

        /**
         * The cumulative count for children.
         */
        long[] childrenCount;

        /**
         * Data stored in the leaf blocks
         */
        VALUE[] values;

        /**
         * Leaf Node, no children, has values
         */
        boolean leafNode;


        /**
         * Constructor
         */
        public Node() {
            keys = keyFactory.newArray(b);
            children = null;
            childrenCount = null;
            leafNode = true;
            values = valFactory.newArray(b);
            parent = -1; // Root node
            id = -1;
            id = bs.placeBlock(this.convert());
        }

        public Node(String node) {
            keys = keyFactory.newArray(b);
            keys = keyFactory.newArray(b);
            children = null;
            childrenCount = null;
            leafNode = true;
            values = valFactory.newArray(b);
            parent = -1;
            id = -1;
            String[] comp = node.split(";");
            String[] key = comp[0].split(", ");
            for (int i = 0; i < key.length; i++) {
                if (key[i].equalsIgnoreCase("null"))
                    keys[i] = null;
                else
                    keys[i] = (KEY) ObjectConverter.convert(key[i], Integer.class);
            }
            String[] childrenStr = comp[1].split(", ");
            String[] childrenCtStr = comp[2].split(", ");

            if (!childrenStr[0].equalsIgnoreCase("null"))
            {
                children = new int[childrenStr.length];
                childrenCount = new long[childrenCtStr.length];
                for (int i = 0; i < childrenStr.length; i++){
                    children[i] = Integer.parseInt(childrenStr[i]);
                    childrenCount[i] = Long.parseLong(childrenCtStr[i]);
                }
            } else {
                children = null;
                childrenCount = null;
            }

            leafNode = Boolean.valueOf(comp[3]);
            String[] value = comp[4].split(", ");
            for (int i = 0; i < value.length; i++) {
                if (value[i].equalsIgnoreCase("null"))
                    values[i] = null;
                else
                    values[i] = (VALUE) ObjectConverter.convert(value[i], Integer.class);
            }
            if (!comp[5].equalsIgnoreCase("null"))
                parent = Integer.parseInt(comp[5]);
            else
                parent = -1;
            if (!comp[6].equalsIgnoreCase("null"))
                id = Integer.parseInt(comp[6]);
        }

        public String[] convert() {
            String[] result = new String[7];
            result[0] = String.valueOf(keys[0]);
            for (int i = 1; i < keys.length; i++)
            {
                result[0] += ", " + keys[i];
            }
            if (children == null)
                result[1] = null;
            else {
                result[1] = Arrays.toString(children);
                result[1] = result[1].substring(1,result[1].length() - 1);
            }

            if (childrenCount == null)
                result[2] = null;
            else {
                result[2] = Arrays.toString(childrenCount);
                result[2] = result[2].substring(1,result[2].length() - 1);
            }

            result[3] = Boolean.toString(leafNode);
            result[4] = String.valueOf(values[0]);
            for (int i = 1; i < values.length; i++)
            {
                result[4] += ", " + values[i];
            }
            if (parent == -1)
                result[5] = null;
            else
                result[5] = Integer.toString(parent);
            if (id == -1)
                result[6] = null;
            else
                result[6] = Integer.toString(id);
            return result;
        }

        public boolean isLeaf() {
            return leafNode;
        }

        /**
         * Test if this block is full (contains b keys)
         *
         * @return true if the block is full
         */
        public boolean isFull() {
            return keys[keys.length - 1] != null;
        }

        /**
         * Test if this block is full (contains b keys)
         *
         * @return true if the block is full
         */
        public boolean isFullByCount() {
            if (leafNode)
                return valueSize() >= b;
            else
                return childrenSize() >= b + 1;
        }

        /**
         * Count the number of keys in this block, using binary search
         *
         * @return the number of keys in this block
         */
        public int size() {
            int lo = 0, h = keys.length;
            while (h != lo) {
                int m = (h + lo) / 2;
                if (keys[m] == null)
                    h = m;
                else
                    lo = m + 1;
            }
            return lo;
        }

        /**
         * Count the number of keys in this block, using binary search
         *
         * @return the number of keys in this block
         */
        public int valueSize() {
            int lo = 0, h = values.length;
            while (h != lo) {
                int m = (h + lo) / 2;
                if (values[m] == null)
                    h = m;
                else
                    lo = m + 1;
            }
            return lo;
        }

        /** Count the number of children in this block, using binary search
         *
         * @return the number of children in this block
         */
        public int childrenSize() {
            if (children == null) return 1;
            int lo = 0, h = children.length;
            while (h != lo) {
                int m = (h + lo) / 2;
                if (children[m] == -1)
                    h = m;
                else
                    lo = m + 1;
            }
            return lo;
        }


        /**
         * Count the number of keys in this block, using binary search
         *
         * @return Cumulative count.
         */
        long getCumulativeChildrenCount() {
            if (leafNode)
                return valueSize();
            else {
                long sum = 0;
                for (long cnt : childrenCount)
                    sum += cnt;
                return sum;
            }
        }


        /**
         * Add the value x to this block
         *
         * @param x  the value to add
         * @param ci the index of the child associated with x
         * @return true on success or false if x was not added
         */
        public boolean add(KEY x, int ci, VALUE value) {
            boolean shift = false;
            int i = findIt(keys, x);
            if (i < 0) return false;
            if (i < keys.length - 1) {
                shift = true;
                System.arraycopy(keys, i, keys, i + 1, b - i - 1);
            }
            keys[i] = x;
            if (leafNode) {
                if (shift) System.arraycopy(values, i, values, i + 1, b - i - 1);
                values[i] = value;

            } else {
                if (shift) System.arraycopy(children, i + 1, children, i + 2, b - i - 1);
                if (shift) System.arraycopy(childrenCount, i + 1, childrenCount, i + 2, b - i - 1);
                children[i + 1] = ci;
                //TODO
                Node w = new Node(bs.readBlock(ci));
                if (w.isLeaf()) {
                    childrenCount[i+1] = w.size();
                } else {
                    childrenCount[i+1] = 0;
                    int z;
                    for(z = 0; z < w.childrenCount.length; z++){
                        childrenCount[i+1] += w.childrenCount[z];
                    }
                }
            }
            return true;
        }

        /**
         * Add the value x to this block
         *
         * @param row  the value to add
         * @param ci the index of the child associated with x
         * @return true on success or false if x was not added
         */
        public boolean addByCount(long row, int ci, VALUE value) {
            boolean shift = false;
            int i = findItByCount(childrenCount, row);
            if (i < 0) return false;
            if (i < childrenSize() - 1) {
                shift = true;
            }
            if (leafNode) {
                i = (int) row - 1;
                if (i < valueSize()) System.arraycopy(values, i, values, i + 1, b - i - 1);
                values[i] = value;
            } else {
                if (shift) System.arraycopy(children, i + 1, children, i + 2, b - i - 1);
                if (shift) System.arraycopy(childrenCount, i + 1, childrenCount, i + 2, b - i - 1);
                children[i + 1] = ci;
                //TODO
                Node w = new Node(bs.readBlock(ci));
                if (w.isLeaf()) {
                    childrenCount[i+1] = w.valueSize();
                } else {
                    childrenCount[i+1] = 0;
                    int z;
                    for(z = 0; z < w.childrenCount.length; z++){
                        childrenCount[i+1] += w.childrenCount[z];
                    }
                }
            }
            return true;
        }


        /**
         * Remove the i'th value from this block - don't affect this block's
         * children
         *
         * @param i the index of the element to remove
         * @return the value of the element removed
         */
        public KEY removeKey(int i) {
            KEY y = keys[i];
            // Do not remove if it is leaf
            if (!leafNode) {
                System.arraycopy(keys, i + 1, keys, i, b - i - 1);
                keys[keys.length - 1] = null;
            }
            return y;
        }

        public KEY removeBoth(int i) {
            KEY y = keys[i];
            System.arraycopy(keys, i + 1, keys, i, b - i - 1);
            keys[keys.length - 1] = null;
            System.arraycopy(values, i + 1, values, i, b - i - 1);
            values[values.length - 1] = null;

            return y;
        }

        /**
         * Split this node into two nodes
         *
         * @return the newly created block, which has the larger keys
         */
        protected Node split() {
            Node w = new Node();
            int j = keys.length / 2;
            System.arraycopy(keys, j, w.keys, 0, keys.length - j);
            Arrays.fill(keys, j, keys.length, null);

            if (leafNode) {
                // Copy Values
                System.arraycopy(values, j, w.values, 0, values.length - j);
                Arrays.fill(values, j, values.length, null);
            } else {

                w.children = new int[b + 1];
                Arrays.fill(w.children, 0, w.children.length, -1);

                // Copy Children
                System.arraycopy(children, j + 1, w.children, 0, children.length - j - 1);
                Arrays.fill(children, j + 1, children.length, -1);

                //Create child counts
                w.childrenCount = new long[b + 1];
                Arrays.fill(w.childrenCount, 0, w.childrenCount.length, 0);

                // Copy Counts
                System.arraycopy(childrenCount, j + 1, w.childrenCount, 0, childrenCount.length - j - 1);
                Arrays.fill(childrenCount, j + 1, childrenCount.length, 0);


            }
            w.leafNode = this.leafNode;
            bs.writeBlock(id, this.convert());
            return w;
        }


        /**
         * Split this node into two nodes
         *
         * @return the newly created block, which has the larger keys
         */
        protected Node splitByCount() {
            Node w = new Node();
            int j = b / 2;

            if (leafNode) {
                // Copy Values
                System.arraycopy(values, j, w.values, 0, values.length - j);
                Arrays.fill(values, j, values.length, null);
            } else {

                w.children = new int[b + 1];
                Arrays.fill(w.children, 0, w.children.length, -1);

                // Copy Children
                System.arraycopy(children, j + 1, w.children, 0, children.length - j - 1);
                Arrays.fill(children, j + 1, children.length, -1);

                //Create child counts
                w.childrenCount = new long[b + 1];
                Arrays.fill(w.childrenCount, 0, w.childrenCount.length, 0);

                // Copy Counts
                System.arraycopy(childrenCount, j + 1, w.childrenCount, 0, childrenCount.length - j - 1);
                Arrays.fill(childrenCount, j + 1, childrenCount.length, 0);


            }
            w.leafNode = this.leafNode;
            bs.writeBlock(id, this.convert());
            return w;
        }

        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[");
            if (leafNode) {
                for (int i = 0; i < b; i++) {
                    sb.append(keys[i] == null ? "_" : keys[i].toString() + ">" + values[i] + ",");
                }
            } else {
                for (int i = 0; i < b; i++) {
                    sb.append("(" + (children[i] < 0 ? "." : children[i]) + ")");
                    sb.append(keys[i] == null ? "_" : keys[i].toString());
                }
                sb.append("(" + (children[b] < 0 ? "." : children[b]) + ")");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    // TODO: Iterator broken
    // Can make it a B+ Tree. Then work on iterator.
    protected class BTIterator implements Iterator<KEY> {
        protected List<Node> nstack;
        protected List<Integer> istack;

        public BTIterator() {
            nstack = new ArrayList<Node>(); // <Node>(Node.class);
            istack = new ArrayList<Integer>(); // <Integer>(Integer.class);
            if (n == 0) return;
            int ui = ri;
            Node u = null;
            walkDown(ui);
        }

        public BTIterator(KEY x) {
            Node u;
            int i;
            nstack = new ArrayList<Node>(); // <Node>(Node.class);
            istack = new ArrayList<Integer>(); // <Integer>(Integer.class);
            if (n == 0) return;
            int ui = ri;
            do {
                u = new Node(bs.readBlock(ui));
                i = findIt(u.keys, x);
                nstack.add(u);
                if (i < 0) {
                    istack.add(-(i + 1));
                    return;
                }
                istack.add(i);
                ui = u.children[i];
            } while (ui >= 0);
            if (i == u.size())
                advance();
        }

        public boolean hasNext() {
            return !nstack.isEmpty();
        }

        public KEY next() {
            Node u = nstack.get(nstack.size() - 1);
            int i = istack.get(istack.size() - 1);
            KEY y = u.keys[i++];
            istack.set(istack.size() - 1, i);
            advance();
            return y;
        }

        protected void advance() {
            Node u = nstack.get(nstack.size() - 1);
            int i = istack.get(istack.size() - 1);
            if (u.isLeaf()) { // this is a leaf, walk up
                while (!nstack.isEmpty() && i == u.size()) {
                    nstack.remove(nstack.size() - 1);
                    istack.remove(istack.size() - 1);
                    if (!nstack.isEmpty()) {
                        u = nstack.get(nstack.size() - 1);
                        i = istack.get(istack.size() - 1);
                    }
                }
            } else { // this is an internal node, walk down
                int ui = u.children[i];
                walkDown(ui);
            }
        }

        private void walkDown(int ui) {
            Node u;
            do {
                u = new Node(bs.readBlock(ui));
                nstack.add(u);
                istack.add(0);
                if (u.isLeaf()) break;
                ui = u.children[0];
            } while (true);
        }


        public void remove() {
            throw new UnsupportedOperationException();
        }

    }
}
