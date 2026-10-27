package com.example.triage.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionServiceTest {
    private final RedactionService service = new RedactionService();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "aws access key  | key AKIAIOSFODNN7EXAMPLE used         | AKIAIOSFODNN7EXAMPLE        | [REDACTED_AWS_ACCESS_KEY]",
            "password        | login password=hunter2 failed         | hunter2                     | password=[REDACTED_SECRET]",
            "bearer token    | Authorization: Bearer eyJhbGciOi.x.y  | eyJhbGciOi                  | Bearer [REDACTED_TOKEN]",
            "jdbc url        | connect jdbc:postgresql://db:5432/x   | jdbc:postgresql             | [REDACTED_CONNECTION_STRING]",
            "sftp url        | put sftp://user:pw@host/in            | user:pw                     | [REDACTED_FILE_TRANSFER_URL]",
            "ip address      | from 10.20.30.40 refused              | 10.20.30.40                 | [REDACTED_IP]",
            "email           | notify ops@example.org                | ops@example.org             | [REDACTED_EMAIL]",
            "presigned url   | GET https://b.s3.amazonaws.com/k?X-Amz-Signature=abc | X-Amz-Signature=abc | [REDACTED_SIGNED_URL]",
    })
    void redacts(String name, String input, String secret, String marker) {
        RedactionResult result = service.redact(input);
        assertThat(result.text()).doesNotContain(secret).contains(marker);
        assertThat(result.redactionsApplied()).isPositive();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null",
            "INFO Loading manifest manifest.xml",
    })
    void leavesOrdinaryLogLinesAlone(String line) {
        assertThat(service.redact(line).text()).isEqualTo(line);
    }

    @org.junit.jupiter.api.Test
    void redactsPrivateKeyBlocks() {
        String log = "x\n-----BEGIN RSA PRIVATE KEY-----\nMIIEow\n-----END RSA PRIVATE KEY-----\ny";
        assertThat(service.redact(log).text()).isEqualTo("x\n[REDACTED_PRIVATE_KEY]\ny");
    }
}
