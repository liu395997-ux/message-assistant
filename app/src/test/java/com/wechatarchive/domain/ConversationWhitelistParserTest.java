package com.wechatarchive.domain;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public final class ConversationWhitelistParserTest {
    @Test
    public void parsesCommasNewlinesAndChinesePunctuation() {
        assertEquals(
                Arrays.asList("项目群", "辅导员", "家人群"),
                ConversationWhitelistParser.parse(
                        " 项目群, 辅导员\n家人群\n项目群，\n"));
    }

    @Test
    public void blankInputProducesEmptyList() {
        assertEquals(0, ConversationWhitelistParser.parse(" \n，, ").size());
    }
}
