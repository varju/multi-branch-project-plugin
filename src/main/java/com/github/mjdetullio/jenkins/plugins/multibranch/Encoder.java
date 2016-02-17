package com.github.mjdetullio.jenkins.plugins.multibranch;

public class Encoder {
  public static String encode(String decoded) {
    return decoded.replaceAll("/", "_");
  }
}
