package com.pulsegraph.compliance;

import java.util.regex.Pattern;

/**
 * Treats telemetry as data. Secrets are redacted. Text that looks like an instruction is wrapped so
 * it cannot be read as a command. The original hostile string is not returned.
 */
public final class TelemetrySanitizer {

  private static final Pattern SECRET =
      Pattern.compile(
          "(?i)(bearer\\s+)[a-z0-9\\-._~+/]+=*"
              + "|sk-[a-z0-9]{8,}"
              + "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

  private static final Pattern INSTRUCTION =
      Pattern.compile(
          "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions"
              + "|you\\s+are\\s+now\\s+"
              + "|\\bsystem\\s*:"
              + "|delete\\s+the\\s+persistent\\s+volume"
              + "|\\bdrop\\s+table\\b)");

  private TelemetrySanitizer() {}

  public record SanitizedText(String text, boolean instructionStripped, boolean secretRedacted) {}

  public static SanitizedText sanitize(String raw) {
    if (raw == null || raw.isBlank()) {
      return new SanitizedText("", false, false);
    }
    boolean instruction = INSTRUCTION.matcher(raw).find();
    String text = instruction ? INSTRUCTION.matcher(raw).replaceAll("[stripped]") : raw;
    if (instruction) {
      text = "[inert telemetry] " + text;
    }
    boolean secret = SECRET.matcher(text).find();
    if (secret) {
      text = SECRET.matcher(text).replaceAll("[redacted]");
    }
    return new SanitizedText(text, instruction, secret);
  }
}
