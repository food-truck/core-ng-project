package core.framework.search;

import java.util.List;

/**
 * @author miller
 */
public class AnalyzeTokens {
    public List<Token> tokens;

    public static class Token {
        public String term;
        public long startOffset;
        public long endOffset;
        public long position;
        public String type;
    }
}
