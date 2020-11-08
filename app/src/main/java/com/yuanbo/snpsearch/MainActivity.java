package com.yuanbo.snpsearch;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.method.ReplacementTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

class DBManager {
    private static final int BUFFER_SIZE = 400000;
    public static final String DEFAULT_DB_NAME = "Q.db"; //保存的数据库文件名
    public static String APP_DATA_PATH;  //在手机里存放数据库的位置

    public SQLiteDatabase database;
    private Context context;

    DBManager(Context context) {
        this.context = context;
        APP_DATA_PATH = context.getFilesDir().getAbsolutePath();
    }

    public void openDatabase() {
        this.database = this.internalOpenDatabase(DEFAULT_DB_NAME);
    }

    public void openDatabase(String dbName) {
        this.database = this.internalOpenDatabase(dbName);
    }

    private SQLiteDatabase internalOpenDatabase(String dbName) {
        String db_full_path = APP_DATA_PATH + "/" + dbName;
        //System.out.println("db_full_path = " + db_full_path);
        try {
            if (!(new File(db_full_path).exists())) {//判断数据库文件是否存在，若不存在则执行导入，否则直接打开数据库
                //InputStream is = this.context.getResources().openRawResource(R.raw.activity_main); //欲导入的数据库
                AssetManager am = null;
                am = this.context.getAssets();
                InputStream is = am.open(dbName);
                FileOutputStream fos = new FileOutputStream(db_full_path);
                byte[] buffer = new byte[BUFFER_SIZE];
                int count = 0;
                while ((count = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, count);
                }
                fos.close();
                is.close();
            }
            return SQLiteDatabase.openOrCreateDatabase(db_full_path,
                    null);
        } catch (FileNotFoundException e) {
            Log.e("Database", "File not found");
            e.printStackTrace();
        } catch (IOException e) {
            Log.e("Database", "IO exception");
            e.printStackTrace();
        }
        return null;
    }
//do something else here<br>

    public void closeDatabase() {
        database.close();
    }
}
public class MainActivity extends AppCompatActivity {
    private Button btnSearch;
    private TextView tvSNPLine;
    private EditText etInputSNP;

    private String strSNP = "";
    private static final int MESSAGE_UPDATE_SEARCH_RESULT = 2;
    private static final String[] hgList = new String[]{"ROOT"};
    public DBManager dbHelper;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(android.os.Message msg) {
            if(msg.what == MESSAGE_UPDATE_SEARCH_RESULT){
                tvSNPLine.setText(strSNP);
                btnSearch.setClickable(true);
            }
        }
    };
    static class InputCapLowerToUpper extends ReplacementTransformationMethod {
        @Override
        protected char[] getOriginal() {
            return new char[]{ 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z' };
        }

        @Override
        protected char[] getReplacement() {
            return new char[]{ 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z' };
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSNPLine = (TextView)findViewById(R.id.snp_line);
        etInputSNP = (EditText)findViewById(R.id.input_snp_name);
        //限制只输入大写字母的地方，小写自动转换为大写
        etInputSNP.setTransformationMethod(new InputCapLowerToUpper());
        btnSearch= (Button) findViewById(R.id.search_button);

        dbHelper = new DBManager(this);
        btnSearch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                strSNP = "";
                btnSearch.setClickable(false);
                new Thread(){
                    public void run() {
                        System.out.println("Start");
                        String input_text = etInputSNP.getText().toString().toUpperCase();
                        String hgName;
                        int i = 0;

                        //snp_to_search.toUpperCase();
                        while(i < hgList.length){
                            if(searchInDB(hgList[i], input_text))
                                break;
                            System.out.println("SNP after searching = " + input_text);
                            i++;
                        }

                        if(i >= hgList.length)
                            strSNP = input_text + "查找不到";
                        Message msg=Message.obtain();
                        msg.what = MESSAGE_UPDATE_SEARCH_RESULT;
                        handler.sendMessage(msg);
                    }
                }.start();
            }
        });
    }

    protected boolean searchInDB(String hgName, String input_text) {
        Cursor cursor;
        boolean result = true;
        String snp_to_search;
        int round = 0;

        if(input_text.equals(hgName)){
            strSNP = input_text;
            return true;
        }

        snp_to_search = input_text;

        dbHelper.openDatabase(hgName + ".db");
        if(dbHelper.database == null)
            return false;
        cursor = dbHelper.database.query("SNP",null,null,null,null,null,null);

        while(true) {
            System.out.println("SNP to be searched = " + snp_to_search);
            if (snp_to_search.equals("None") ){
                //strSNP += hgNmae;
                result = true;
                break;
            }
            strSNP += (snp_to_search + " ");
            //判断游标是否为空
            if (cursor.moveToFirst()) {//遍历游标，id中查找
                String alias_id = null, snp_to_search_next = null;
                boolean found_in_rsids = false;
                int count = cursor.getCount(), i;

                for (i = 0; i < count ; i++) {
                    String id = cursor.getString(0); //get id
                    //System.out.println("index = " + i + " " + id);
                    if(id.equals(snp_to_search) || snpIsIn(id, snp_to_search)){// check if the snp is in id
                        snp_to_search = cursor.getString(5);
                        break;
                    }
                    if(round > 0) { // first round would go to check the rsids
                        cursor.move(1);
                        continue;
                    }
                    String rsids = cursor.getString(3); //get rsids
                    if(snpIsIn(rsids, snp_to_search)) {// check if the snp is in rsids
                        alias_id = id;
                        snp_to_search_next = cursor.getString(5);
                        found_in_rsids = true;
                    }
                    cursor.move(1);
                }
                if( i < count) {// found in id
                    //strSNP += "不是" + hgName + "系";
                    continue;
                 }else if(found_in_rsids) {
                    strSNP += ("(" + alias_id + ") ");
                    snp_to_search = snp_to_search_next;
                }else{
                    result = false;
                    break;
                }
            }
            round++;
        }
        cursor.close();
        dbHelper.closeDatabase();
        return result;
    }

    protected boolean snpIsIn(String rsids, String snp_to_search) {
        int startIndex = 0;
        while((startIndex = rsids.indexOf(snp_to_search, startIndex)) >= 0) {
            int endIndex = startIndex + snp_to_search.length();
            boolean a, b;
            if(startIndex != 0){
                char preChar = rsids.charAt(startIndex - 1);
                a = (preChar == '/'  || preChar == ' ' || preChar == '-');
            }
            else
                a = true;

            if(endIndex >= rsids.length()){ // reach the end
                return a;
            }
            else {
                char sufChar = rsids.charAt(endIndex);
                b = (sufChar == '/' || sufChar == '#');
            }
            if(a && b)
                return true;

            startIndex = endIndex ; // next section
        }
        return false;
    }

}