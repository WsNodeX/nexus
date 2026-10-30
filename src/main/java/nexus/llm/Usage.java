package nexus.llm;

/**
 * Token accounting for a single completion.
 */
public class Usage {
    public int promptTokens;
    public int completionTokens;
    public int totalTokens;

    public Usage() {}

    public Usage(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public static Usage empty() {
        return new Usage(0, 0, 0);
    }
}
