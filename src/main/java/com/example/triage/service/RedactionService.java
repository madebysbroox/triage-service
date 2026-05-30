package com.example.triage.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RedactionService {
    private record Rule(Pattern pattern, String replacement) {}

    private final List<Rule> rules = List.of(
            new Rule(Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/=-]+"), "Bearer [REDACTED_TOKEN]"),
            new Rule(Pattern.compile("(?i)(api[-_ ]?key|password|passwd|secret|token)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED_SECRET]"),
            new Rule(Pattern.compile("AKIA[0-9A-Z]{16}"), "[REDACTED_AWS_ACCESS_KEY]"),
            new Rule(Pattern.compile("(?i)aws_session_token\\s*[:=]\\s*[^\\s,;]+"), "aws_session_token=[REDACTED_AWS_SESSION_TOKEN]"),
            new Rule(Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"), "[REDACTED_PRIVATE_KEY]"),
            new Rule(Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[REDACTED_EMAIL]"),
            new Rule(Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[REDACTED_IP]"),
            new Rule(Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[REDACTED_SSN]"),
            new Rule(Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b"), "[REDACTED_CARD]"),
            new Rule(Pattern.compile("(?i)(jdbc:[^\\s]+|mongodb(?:\\+srv)?://[^\\s]+|postgres(?:ql)?://[^\\s]+)"), "[REDACTED_CONNECTION_STRING]"),
            new Rule(Pattern.compile("(?i)(sftp|ftp)://[^\\s]+"), "[REDACTED_FILE_TRANSFER_URL]"),
            new Rule(Pattern.compile("https?://[^\\s]+X-Amz-Signature=[^\\s]+"), "[REDACTED_SIGNED_URL]")
    );

    public RedactionResult redact(String input) {
        if (input == null || input.isBlank()) {
            return new RedactionResult("", 0);
        }
        String output = input;
        int count = 0;
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(output);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                count++;
                matcher.appendReplacement(buffer, rule.replacement());
            }
            matcher.appendTail(buffer);
            output = buffer.toString();
        }
        return new RedactionResult(output, count);
    }
}
