package ai.chat2db.spi.model;

public interface IndexModel extends BaseModel<IndexModel> {
    /**
     * 获取索引名称
     * 
     * @return
     */
    String getName();

    /**
     * 获取索引备注
     * 
     * @return
     */
    String getComment();

    /**
     * 获取索引备注
     * 
     * @return
     */
    String getAiComment();

    /**
     * 获取版本
     * 
     * @return
     */
    Long getVersion();

    /**
     * 设置版本
     * 
     * @param version
     */
    void setVersion(Long version);

    /**
     * 设置索引名称
     * 
     * @param name
     */
    void setName(String name);

    /**
     * 设置索引备注
     * 
     * @param comment
     */
    void setComment(String comment);

    /**
     * 设置索引备注
     * 
     * @param aiComment
     */
    void setAiComment(String aiComment);

    @Override
    default Class<? extends IndexModel> getClassType() {
        return this.getClass();
    }
}
