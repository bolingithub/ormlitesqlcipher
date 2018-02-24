package person.hbl.ormlitelibrary.helper;

import android.content.Context;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import net.sqlcipher.database.SQLiteDatabase;

import java.sql.SQLException;
import java.util.List;

import person.hbl.ormlitelibrary.utils.DatabaseUpdateUtil;

/**
 * app - core层数据库操作需要继承BaseDatabaseHelper
 * <p>
 *
 * @author H-Bolin
 * @date 2017/11/6
 */

public abstract class BaseDatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static final String TAG = BaseDatabaseHelper.class.getSimpleName();


    /*public BaseDatabaseHelper(Context context, String databaseName, SQLiteDatabase.CursorFactory factory, int databaseVersion) {
        super(context, databaseName, factory, databaseVersion);
    }*/

    public BaseDatabaseHelper(Context context, String databaseName, String password, net.sqlcipher.database.SQLiteDatabase.CursorFactory factory, int databaseVersion) {
        super(context, databaseName, password, factory, databaseVersion);
    }


    /**
     * 添加表，可直接更新表，无需重写onUpgrade()方法
     *
     * @return
     */
    public abstract List<Class> addTables();

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource) {

        List<Class> classes = addTables();

        for (Class clazz : classes) {
            try {
                TableUtils.createTable(connectionSource, clazz);
                Log.d(TAG, "创建表成功：" + clazz.getSimpleName());
            } catch (SQLException e) {
                Log.e(TAG, "创建表失败：" + clazz.getSimpleName());
                return;
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, ConnectionSource connectionSource, int oldVersion, int newVersion) {

        List<Class> classes = addTables();

        try {
            DatabaseUpdateUtil.upgradeTable(sqLiteDatabase, connectionSource, classes);
            Log.d(TAG, "升级表成功");
        } catch (SQLException e) {
            Log.e(TAG, "升级表失败");
        }
    }
}
