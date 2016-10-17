package org.zkoss.zss.model.impl.sys;

import org.zkoss.util.logging.Log;
import org.zkoss.zss.model.SBookSeries;
import org.zkoss.zss.model.sys.dependency.Ref;
import org.zkoss.zss.model.sys.dependency.Ref.RefType;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

//import java.util.Map.Entry;


/**
 * Created by Yining on 10/4/2016.
 */
public class UpdateTableImpl extends UpdateTableAdv {

    protected static final EnumSet<RefType> _regionTypes = EnumSet.of(RefType.BOOK, RefType.SHEET, RefType.AREA,
            RefType.CELL, RefType.TABLE);
    private static final long serialVersionUID = 1L;
    private static final Log _logger = Log.lookup(UpdateTableImpl.class.getName());
    protected Set<Ref> _dirtySet = new LinkedHashSet<Ref>();
    protected SBookSeries _books;

    public UpdateTableImpl() {

    }

    @Override
    public void add(Ref target) {
        if (!_dirtySet.contains(target)) {
            _dirtySet.add(target);
            System.out.println("Add to updateTable " + target);
        }
    }

    @Override
    public void delete(Ref target) {
        if (_dirtySet.contains(target)) {
            _dirtySet.remove(target);
            System.out.println("Delete from updateTable " + target);
        }
    }

    @Override
    public boolean isDirty(Ref target) {
        return _dirtySet.contains(target);

    }

    public void clear() {
        _dirtySet.clear();
    }


    @Override
    public void merge(UpdateTableAdv updateTable) {
        if (!(updateTable instanceof UpdateTableImpl)) {
            // just in case
            _logger.error("can't merge different type of Update table: " + updateTable.getClass().getName());
            return;
        }

        // simply, just put everything in
        UpdateTableImpl another = (UpdateTableImpl) updateTable;
        _dirtySet.addAll(another._dirtySet);
    }

    @Override
    public void setBookSeries(SBookSeries series) {
        this._books = series;
    }

    //ZSS-815
    @Override
    public void adjustSheetIndex(String bookName, int index, int size) {
        // do nothing
    }

    //ZSS-820
    @Override
    public void moveSheetIndex(String bookName, int oldIndex, int newIndex) {
        // do nothing
    }
}
