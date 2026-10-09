package com.ai.aiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * 将用户问题重写为更适合向量检索的查询。
 */
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(@Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        ChatClient.Builder builder = ChatClient.builder(dashScopeChatModel);
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    public String doQueryRewrite(String prompt) {
        Assert.hasText(prompt, "prompt 不能为空");
        Query transformedQuery = queryTransformer.transform(new Query(prompt));
        return transformedQuery.text();
    }
}
