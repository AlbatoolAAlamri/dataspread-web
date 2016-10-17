package org.zkoss.zss.model.sys.dependency;

/**
 * 
 * @author dennis
 * @since 3.5.0
 */
public interface Ref {
	RefType getType();

	String getBookName();

	String getSheetName();

	String getLastSheetName();

	int getRow();

	int getColumn();

	int getLastRow();

	int getLastColumn();

	//ZSS-815
	//since 3.7.0
	int getSheetIndex();
	
	//ZSS-815
	//since 3.7.0
	int getLastSheetIndex();

	/**
	 * @since 3.5.0
	 */
	enum RefType {
		CELL, AREA, SHEET, BOOK, NAME, OBJECT, INDIRECT, TABLE,
	}
}
