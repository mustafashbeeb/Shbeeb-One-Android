package com.shbeeb.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTransportErrorTest {
    @Test fun distinguishesInsufficientQuotaFromThrottling() {
        val quota = ChatTransport.explainFailure(
            429, """{"error":{"code":"insufficient_quota","message":"Quota exhausted"}}"""
        )
        val throttled = ChatTransport.explainFailure(
            429, """{"error":{"code":"rate_limit_exceeded","message":"Too many requests"}}"""
        )
        assertTrue(quota.contains("رصيد"))
        assertTrue(throttled.contains("حدّ الطلبات"))
    }

    @Test fun explainsAuthenticationWithoutEchoingProviderBody() {
        val key = "do-not-ever-print-this-key"
        val message = ChatTransport.explainFailure(
            401, """{"error":{"message":"$key"}}"""
        )
        assertTrue(message.contains("مفتاح API"))
        assertTrue(!message.contains(key))
    }

    @Test fun identifiesMissingModelsAndBadPayloads() {
        assertTrue(ChatTransport.explainFailure(404, "{}").contains("النموذج"))
        assertTrue(ChatTransport.explainFailure(400, "{}").contains("صيغة"))
    }

    @Test fun identifiesServerAndBalanceErrors() {
        assertTrue(ChatTransport.explainFailure(402, "{}").contains("رصيد"))
        assertTrue(ChatTransport.explainFailure(503, "{}").contains("متوقفة"))
    }

    @Test fun handlesMalformedErrorJson() {
        assertTrue(ChatTransport.explainFailure(429, "not a JSON document").contains("حدّ الطلبات"))
    }
}
