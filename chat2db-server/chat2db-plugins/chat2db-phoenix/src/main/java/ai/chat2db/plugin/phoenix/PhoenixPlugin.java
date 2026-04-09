package ai.chat2db.plugin.phoenix;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.Plugin;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.util.FileUtils;

public class PhoenixPlugin implements Plugin {
    @Override
    public DBConfig getDBConfig() {
        return FileUtils.readJsonValue(this.getClass(),"phoenix.json", DBConfig.class);
    }

    @Override
    public MetaData getMetaData() {
        return new PhoenixMetaData();
    }

    @Override
    public DBManage getDBManage() {
        return new PhoenixDBManage();
    }
}
