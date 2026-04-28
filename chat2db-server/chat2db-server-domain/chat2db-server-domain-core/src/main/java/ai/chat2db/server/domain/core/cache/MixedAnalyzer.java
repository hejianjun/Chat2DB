package ai.chat2db.server.domain.core.cache;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.wltea.analyzer.lucene.IKTokenizer;

/**
 * 自定义混合分析器，结合 IKAnalyzer 的中文分词能力和 PorterStemmer 的英文词干提取能力
 * 支持英文单词的单复数形式检索（如 cat/cats, user/users）
 */
public class MixedAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // 使用 IKTokenizer 进行中文分词，同时对英文单词进行分割
        Tokenizer tokenizer = new IKTokenizer();
        
        // 转换为小写
        TokenStream stream = new LowerCaseFilter(tokenizer);
        
        // 应用 PorterStemmer 词干提取，将单词还原为词干形式
        // 例如: cats -> cat, users -> user, running -> run
        stream = new PorterStemFilter(stream);
        
        return new TokenStreamComponents(tokenizer, stream);
    }
}
