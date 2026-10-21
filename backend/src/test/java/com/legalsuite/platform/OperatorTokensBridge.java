package com.legalsuite.platform;

final class OperatorTokensBridge {
    private OperatorTokensBridge() {}

    static String ops(org.springframework.test.web.servlet.MockMvc mvc) throws Exception {
        return com.legalsuite.OperatorTokens.ops(mvc);
    }
}
