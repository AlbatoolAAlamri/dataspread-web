package org.zkoss.zss.model.impl.sys;

import org.zkoss.zss.model.SBookSeries;
import org.zkoss.zss.model.sys.UpdateTable;

import java.io.Serializable;


/**
 * Created by Yining on 10/4/2016.
 */
public abstract class UpdateTableAdv implements UpdateTable, Serializable {
    private static final long serialVersionUID = 1L;

    abstract public void setBookSeries(SBookSeries series);

    abstract public void merge(UpdateTableAdv updateTable);

    abstract public void adjustSheetIndex(String bookName, int index, int size); //ZSS-815

    abstract public void moveSheetIndex(String bookName, int oldIndex, int newIndex); //ZSS-820

}
