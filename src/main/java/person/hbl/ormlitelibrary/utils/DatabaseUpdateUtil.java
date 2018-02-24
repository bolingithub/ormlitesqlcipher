package person.hbl.ormlitelibrary.utils;

import net.sqlcipher.Cursor;
import android.util.Log;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTable;
import com.j256.ormlite.table.TableUtils;

import net.sqlcipher.database.SQLiteDatabase;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库升级方案
 * 3.1.将现有表以别名命名
 * 3.2.创建新表
 * 3.3.迁移数据
 * 3.4.1 新增表
 * 3.4.2 删除表
 * 3.4.3 新增和删除字段
 * 3.4.删除现有表
 * <p>
 *
 * @author LanHe-Android
 * @date 2017/11/8
 */

public class DatabaseUpdateUtil {
    private static final String TAG = DatabaseUpdateUtil.class.getSimpleName();

    /**
     * 升级表
     *
     * @param sqLiteDatabase
     * @param connectionSource
     */
    public static void upgradeTable(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource, List<Class> classes) throws SQLException {
        Log.d(TAG, "数据库升级--升级表");

        // 获取旧表
        List<String> oldTableNames = getAllOldTableNames(sqLiteDatabase);
        // 获取新表
        List<String> newTableNames = getAllNewTableNames(classes);

        try {
            // 开始事务
            sqLiteDatabase.beginTransaction();

            // 新表中，不包括旧表，则删除 -- ok
            for (String oldTableName : oldTableNames) {
                if (!newTableNames.contains(oldTableName)) {
                    Log.d(TAG, "新表中，不包括旧表，则删除:" + oldTableName);
                    sqLiteDatabase.execSQL("DROP TABLE " + oldTableName);
                }
            }

            for (Class clazz : classes) {
                // 获取高版本的表名
                String newTableName = getNewTableName(clazz);

                Log.d(TAG, "获取高版本的表名：" + newTableName);

                if (!oldTableNames.contains(newTableName)) {
                    // 旧表中不包含新表名，则新增该表
                    Log.d(TAG, "旧表中不包含新表名，则新增该表:" + newTableName);
                    try {
                        String sql = (String) TableUtils.getCreateTableStatements(connectionSource, clazz).get(0);
                        sqLiteDatabase.execSQL(sql);
                    } catch (Exception e) {
                        Log.e(TAG, "创建新表失败 1");
                        TableUtils.createTable(connectionSource, clazz);
                    }
                    Log.d(TAG, "新增该表成功:" + newTableName);
                } else {
                    // 包含相同表名，则需要比较列名是否相同
                    Log.d(TAG, "包含相同表名，则需要比较列名是否相同");
                    // 获取旧表的列
                    List<String> oldColumnNames = getOldColumnNames(sqLiteDatabase, newTableName);
                    List<String> newColumnNames = getNewColumnNames(clazz);

                    // 新表和旧表的元素都相同，不更新
                    if (oldColumnNames.containsAll(newColumnNames) && oldColumnNames.size() == newColumnNames.size()) {
                        Log.d(TAG, "新表和旧表的元素都相同，不更新");
                        continue;
                    }

                    // 1.将现有表以别名命名
                    String tempTableName = newTableName + "_temp";
                    String sql = "ALTER TABLE " + newTableName + " RENAME TO " + tempTableName;
                    sqLiteDatabase.execSQL(sql);

                    Log.d(TAG, "将现有表以别名命名成功");

                    // 2.创建新表
                    try {
                        sql = (String) TableUtils.getCreateTableStatements(connectionSource, clazz).get(0);
                        sqLiteDatabase.execSQL(sql);
                    } catch (Exception e) {
                        Log.e(TAG, "创建新表失败 1");
                        TableUtils.createTable(connectionSource, clazz);
                    }

                    Log.d(TAG, "创建新表成功");

                    // 3.迁移数据
                    // 抽出旧表和新表共有的元素
                    List<String> commColumnNames = new ArrayList<>();
                    for (String columnName : newColumnNames) {
                        if (oldColumnNames.contains(columnName)) {
                            commColumnNames.add(columnName);
                        }
                    }
                    Log.d(TAG, "commColumnNames.size():" + commColumnNames.size());
                    String columns = "";
                    for (int i = 0; i < commColumnNames.size(); i++) {
                        if (i == commColumnNames.size() - 1) {
                            columns = columns + commColumnNames.get(i);
                        } else {
                            columns = columns + commColumnNames.get(i) + ",";
                        }
                    }
                    if (!columns.isEmpty()) {
                        sql = "INSERT INTO " + newTableName + " (" + columns + ") " + " SELECT " + columns + " FROM " + tempTableName;
                        Log.d(TAG, "迁移数据：" + sql);
                        sqLiteDatabase.execSQL(sql);
                    }

                    Log.d(TAG, "迁移数据成功");

                    // 4.删除现有表
                    sql = "DROP TABLE IF EXISTS " + tempTableName;
                    sqLiteDatabase.execSQL(sql);

                    Log.d(TAG, "删除现有表成功");
                }
            }
            // 提交事务
            sqLiteDatabase.setTransactionSuccessful();
            Log.d(TAG, "升级数据库完成");
        } catch (Exception e) {
            throw new SQLException("操作数据库失败");
        } finally {
            // 结束事务
            sqLiteDatabase.endTransaction();
        }
    }

    /**
     * 获取所有新表的表名
     *
     * @return
     */
    // OK
    public static List<String> getAllNewTableNames(List<Class> classes) {
        List<String> tableNames = new ArrayList<>();
        for (Class clazz : classes) {
            String newName = getNewTableName(clazz);
            Log.d(TAG, "新表名：" + newName);
            tableNames.add(newName);
        }
        return tableNames;
    }

    /**
     * 获取新表名
     *
     * @param clazz
     * @return
     */
    // OK
    public static String getNewTableName(Class clazz) {
        DatabaseTable databaseTable = (DatabaseTable) clazz.getAnnotation(DatabaseTable.class);
        if (databaseTable != null) {
            return databaseTable.tableName();
        } else {
            // 表名是小写的
            return clazz.getSimpleName().toLowerCase();
        }
    }

    /**
     * 获取新表的列名
     *
     * @param clazz
     * @return
     */
    // OK
    private static List<String> getNewColumnNames(Class clazz) {
        List<String> newColumns = new ArrayList<>();
        Field[] entityFields = clazz.getDeclaredFields();
        for (Field field : entityFields) {
            if (field.isAnnotationPresent(DatabaseField.class)) {
                DatabaseField column = field.getAnnotation(DatabaseField.class);
                String columnName = column.columnName();
                if (columnName.isEmpty()) {
                    columnName = field.getName();
                }
                Log.d(TAG, "新表列名：" + columnName);
                newColumns.add(columnName);
            }
        }
        return newColumns;
    }


    /**
     * 获取所有旧表的表名
     *
     * @return
     */
    // OK
    public static List<String> getAllOldTableNames(SQLiteDatabase sqLiteDatabase) throws SQLException {
        List<String> tableNames = new ArrayList<>();

        Cursor cursor = null;
        try {
            cursor = sqLiteDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type='table' order by name", null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndex("name");
                if (columnIndex == -1) {
                    return tableNames;
                }
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    String oldName = cursor.getString(columnIndex);
                    Log.d(TAG, "旧表名：" + oldName);
                    tableNames.add(oldName);
                }
            }
            //　移除记录android数据库信息的表
            tableNames.remove("android_metadata");
            tableNames.remove("sqlite_sequence");
        } catch (Exception e) {
            throw new SQLException("获取所有表名失败");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return tableNames;
    }

    /**
     * 获取旧表的列名
     *
     * @param db
     * @param tableName
     * @return
     */
    // OK
    private static List<String> getOldColumnNames(SQLiteDatabase db, String tableName) throws SQLException {
        List<String> columnNames = new ArrayList<>();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndex("name");
                if (columnIndex == -1) {
                    return columnNames;
                }
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    String columnName = cursor.getString(columnIndex);
                    Log.d(TAG, "tableName:" + tableName + " --- " + columnName);
                    columnNames.add(columnName);
                }
            }
        } catch (Exception e) {
            throw new SQLException("获取列名失败 1");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return columnNames;
    }


}
