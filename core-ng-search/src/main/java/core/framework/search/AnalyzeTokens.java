package core.framework.search;

import java.util.List;

/**
 * @author miller
 */
public class AnalyzeTokens {
    public List<Token> tokens;

    public static class Token {
        public String term;
        public int startOffset;
        public int endOffset;
        public int position;
        public String type;
    }
}
